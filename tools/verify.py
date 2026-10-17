#!/usr/bin/env python3
# OpenRTL-Header-Begin
# OpenRTL — Open-source hardware accelerators.
# Organization: https://github.com/OpenRTL-Lab
# Website: https://openrtl.top/
#
# Project: Goldilocks Batch Inversion Hardware Accelerator
# Chisel hardware for Goldilocks batch inversion and division with interleaved contexts.
# Repository: https://github.com/OpenRTL-Lab/batch-inversion-goldilocks
# File: tools/verify.py
#
# SPDX-FileCopyrightText: 2026 Goldilocks Batch Inversion contributors
# Copyright applies to original project contributions only.
# SPDX-License-Identifier: MIT
# See LICENSE and applicable third-party notices.
# OpenRTL-Header-End

"""复现独立 C/Python 对照、生成与随仓库产品仿真，以及理想接口性能。"""
import argparse
import json
import os
from pathlib import Path
import re
import sys
import traceback
from emit_sv import BUNDLED, OP, ROOT, TOP, code, generate, product_closure, run, simulation_list


def entries(log, label):
    return [{key: int(value) for key, value in re.findall(r'(\w+)\s*=\s*(\d+)', line)}
            for line in re.findall(r'^' + label + r' (.*)$', log, re.M)]


def performance(log, length, tasks):
    accepts, completes, units = (entries(log, label) for label in ('ACCEPT', 'COMPLETE', 'UNITS'))
    assert len(accepts) == len(completes) == len(units) == tasks
    a = {item['tag']: item['cycle'] for item in accepts}
    c = {item['tag']: item['cycle'] for item in completes}
    first = {item['tag']: item['cycle'] for item in entries(log, 'FIRST_OUTPUT')}
    last = {item['tag']: item['cycle'] for item in entries(log, 'LAST_OUTPUT')}
    assert sorted(a) == list(range(tasks))
    mode = accepts[0]['mode']
    expected = (3 + mode) * length + 173
    for unit in units:
        assert sum(unit[f'c{i}'] for i in range(9)) == expected
        assert unit['c0'] == length and unit['c1'] == 16
        assert unit['c2'] == 63 and unit['c3'] == 62 and unit['c4'] == unit['c5'] == 16
        assert unit['c6'] == unit['c7'] == length and unit['c8'] == mode * length
        assert unit['clocks'] >= unit['occupied'] >= expected
    result = {'task_latencies_clocks': [c[i] - a[i] for i in range(tasks)],
              'first_output_latencies_clocks': [first[i] - a[i] for i in range(tasks)],
              'last_output_latencies_clocks': [last[i] - a[i] for i in range(tasks)],
              'units': units, 'valid_multiplications_per_task': expected}
    if tasks > 1:
        count = tasks - 2
        window = c[tasks - 1] - c[1]
        accept_window = a[tasks - 1] - a[1]
        assert window == accept_window, '稳态接受和完成窗口必须一致'
        issues = sum(sum(units[i][f'c{k}'] for k in range(9)) for i in range(1, tasks - 1))
        occupied = sum(units[i]['occupied'] for i in range(1, tasks - 1))
        result['sustained'] = {'completed_tasks': count, 'window_clocks': window,
            'cycles_per_task': window / count, 'cycles_per_element': window / (count * length),
            'tasks_per_clock': count / window, 'elements_per_clock': count * length / window,
            'mul_issue_clocks': issues, 'mul_idle_clocks': window - issues,
            'mul_issue_utilization': issues / window, 'mul_occupied_clocks': occupied,
            'mul_pipeline_utilization': occupied / window}
    return result


def verify(output, max_length, tasks, jobs):
    rtl = generate(output, max_length, tasks)
    output = Path(output)
    report = {'status': 1, 'max_length': max_length, 'contexts': 16, 'tasks': tasks,
              'ideal_interfaces': True, 'tests': {}, 'performance': {}, 'dc_run': False}
    try:
        run(['verilator', '--lint-only', '--assert', '--top-module', TOP, '-f', rtl / 'product.f'],
            output, 'product-lint')
        builds = [('generated', rtl / 'simulation.f')]
        if max_length == 65536:
            generated = {path.name: re.sub(r'\s+', '', code(path.read_text())) for path in product_closure(rtl)}
            bundled = {path.name: re.sub(r'\s+', '', code(path.read_text())) for path in BUNDLED.glob('*.sv')}
            assert generated == bundled, '随仓库产品与重新生成产品的电路正文不一致'
            report['bundled_product_matches'] = True
            simulation_list(rtl, rtl / 'bundled-simulation.f', bundled=True)
            run(['verilator', '--lint-only', '--assert', '--top-module', TOP, '-f', BUNDLED / 'files.f'],
                output, 'bundled-lint')
            builds.append(('bundled', rtl / 'bundled-simulation.f'))
        os.environ['CCACHE_DISABLE'] = '1'
        for kind, file_list in builds:
            obj = (ROOT / output / ('obj-' + kind)).resolve()
            run(['verilator', '-O3', '--assert', '--cc', '--exe', '--build', '-j', jobs,
                 '-DENABLE_INITIAL_MEM_', '--top-module', 'BatchInversionSimTop',
                 '-Mdir', obj, '-CFLAGS', '-O2 -std=c++17', '-f', file_list,
                 ROOT / OP / 'sim/driver.cpp'], output, 'verilator-' + kind)
            binary = obj / 'VBatchInversionSimTop'
            for mode, label in enumerate(('ideal-inverse', 'ideal-division', 'varied', 'reset',
                                          'negative', 'errors', 'single-inverse', 'single-division')):
                name = kind + '-' + label
                run([binary, mode], output, name, expected=1 if mode == 4 else 0)
                log = (output / (name + '.log')).read_text()
                values = entries(log, 'RESULT')
                assert len(values) == 1
                value = values[0]
                assert value['pass'] == int(mode != 4) and value['mismatches'] == int(mode == 4)
                expected_tasks = tasks if mode < 2 else 1 if mode > 5 else 6 if mode == 5 else 16
                assert value['completed'] == expected_tasks
                if mode == 3:
                    assert len(entries(log, 'RESET_ABORT')) == 2
                if mode in (2, 3):
                    assert value['checked'] == 158 + 4 * max_length
                if mode == 5:
                    assert value['checked'] == 0
                if mode < 2 or mode > 5:
                    assert value['checked'] == expected_tasks * max_length
                    report['performance'][name] = performance(log, max_length, expected_tasks)
                report['tests'][name] = value
            if kind == 'bundled':
                for label in ('ideal-inverse', 'ideal-division', 'single-inverse', 'single-division'):
                    assert report['performance']['generated-' + label] == report['performance']['bundled-' + label]
        report['status'] = 0
    except Exception:
        report['error'] = traceback.format_exc()
        print(report['error'], file=sys.stderr)
    temporary = output / 'completion.pending'
    temporary.write_text(json.dumps(report, indent=2) + '\n')
    temporary.replace(output / 'completion.json')
    print('验证通过' if report['status'] == 0 else '验证失败；请查看输出目录 completion.json')
    return report['status']


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', default='build/verify')
    parser.add_argument('--max-length', type=int, default=65536)
    parser.add_argument('--tasks', type=int, default=16)
    parser.add_argument('--jobs', type=int, default=8)
    args = parser.parse_args()
    if not 1 <= args.jobs <= 64:
        parser.error('jobs 必须介于 1 与 64 之间')
    raise SystemExit(verify(args.out, args.max_length, args.tasks, args.jobs))
