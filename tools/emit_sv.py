#!/usr/bin/env python3
# OpenRTL-Header-Begin
# OpenRTL — Open-source hardware accelerators.
# Organization: https://github.com/OpenRTL-Lab
# Website: https://openrtl.top/
#
# Project: Goldilocks Batch Inversion Hardware Accelerator
# Chisel hardware for Goldilocks batch inversion and division with interleaved contexts.
# Repository: https://github.com/OpenRTL-Lab/batch-inversion-goldilocks
# File: tools/emit_sv.py
#
# SPDX-FileCopyrightText: 2026 Goldilocks Batch Inversion contributors
# Copyright applies to original project contributions only.
# SPDX-License-Identifier: MIT
# See LICENSE and applicable third-party notices.
# OpenRTL-Header-End

"""生成参考向量、Chisel 验证环境与独立产品 SV 文件清单。"""
from source_headers import stamp_generated
import argparse
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
OP = Path('operators/batch-inversion')
TOP = 'GoldilocksBatchInversion'
BUNDLED = Path('rtl/v1.1.0/gl64-ctx16-l65536')


def relative_build(value):
    path = Path(value)
    if path.is_absolute() or '..' in path.parts or not path.parts or path.parts[0] != 'build':
        raise ValueError('输出必须是 build 内的相对目录')
    return path


def run(command, output, label, expected=0):
    with (output / (label + '.log')).open('x') as log:
        result = subprocess.run([str(x) for x in command], cwd=ROOT,
                                stdout=log, stderr=subprocess.STDOUT)
    if result.returncode != expected:
        raise RuntimeError(f'{label} 失败，请检查本次输出目录的对应日志')


def code(text):
    return re.sub(r'/\*.*?\*/|//[^\n]*', '', text, flags=re.S)


def product_closure(rtl):
    stamp_generated(rtl)
    definitions, texts = {}, {}
    for path in sorted(rtl.glob('*.sv')):
        text = re.sub(r'[ \t]*//[^\n]*\.scala:\d+:\d+[^\n]*', '', path.read_text())
        path.write_text(text)
        texts[path] = code(text)
        for module in re.findall(r'\bmodule\s+(\w+)', texts[path]):
            assert module not in definitions, '模块重复'
            definitions[module] = path
    selected, pending = set(), [TOP]
    while pending:
        path = definitions[pending.pop()]
        if path in selected:
            continue
        selected.add(path)
        instances = set(re.findall(
            r'^[ \t]*(\w+)\b[ \t]+(?:#\s*\([^;]*?\)\s*)?\w+\s*\(',
            texts[path], re.M | re.S))
        pending.extend(instances & definitions.keys())
    for path in selected:
        stripped = re.sub(r'"(?:[^"\\]|\\.)*"', '""', texts[path])
        assert not re.search(r'\$readmem|%|\b(?:DSP48\w*|RAMB\w*|URAM\w*|BUFG\w*|IOBUF|MMCME\w*)\b', stripped)
        assert 'BatchInversionSimTop' not in stripped
    return sorted(selected)


def simulation_list(rtl, output, bundled=False):
    files = sorted(rtl.glob('*.sv'))
    if bundled:
        selected = set(product_closure(rtl))
        files = [path for path in files if path not in selected] + sorted(BUNDLED.glob('*.sv'))
    verification = sorted(path for path in (rtl / 'verification').rglob('*.sv')
                          if not path.name.startswith('layers-'))
    bind = rtl / 'verification/assert/layers-BatchInversionSimTop-Verification-Assert.sv'
    assert bind.is_file(), '缺少验证环境绑定文件'
    includes = [rtl / 'verification', rtl / 'verification/assert']
    output.write_text(''.join('+incdir+' + str(path) + '\n' for path in includes)
                      + ''.join(str(path) + '\n' for path in files + verification + [bind]))


def generate(output, max_length=65536, tasks=16):
    os.chdir(ROOT)
    output = relative_build(output)
    if output.exists():
        raise ValueError('输出目录已存在；请使用新目录，不会自动删除旧结果')
    if not (32 <= max_length <= 65536) or max_length & (max_length - 1):
        raise ValueError('最大批长必须是 32 至 65536 之间的 2 的幂')
    if not (4 <= tasks <= 256):
        raise ValueError('连续任务数必须介于 4 与 256 之间')
    output.mkdir(parents=True)
    vectors = output / 'vectors'
    vectors.mkdir()
    run([os.environ.get('CC', 'cc'), '-std=c11', '-O2', '-Wall', '-Wextra', '-Werror',
         OP / 'cmodel/reference.c', '-o', output / 'reference'], output, 'c-compile')
    run([output / 'reference', max_length, vectors], output, 'c-reference')
    run([sys.executable, 'tools/check_reference.py', vectors, '--length', max_length],
        output, 'python-reference')
    rtl = output / 'rtl'
    run(shlex.split(os.environ.get('MILL', 'mill')) + ['-i', 'batchInversion.tool.runMain',
         'zk.batchinv.Emit', rtl, max_length, vectors, tasks], output, 'emit')
    product = product_closure(rtl)
    (rtl / 'product.f').write_text(''.join(str(path) + '\n' for path in product))
    simulation_list(rtl, rtl / 'simulation.f')
    (output / 'configuration.json').write_text(json.dumps(
        {'version': '1.1.0', 'max_length': max_length, 'contexts': 16, 'tasks': tasks}, indent=2) + '\n')
    return rtl


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', default='build/emit')
    parser.add_argument('--max-length', type=int, default=65536)
    parser.add_argument('--tasks', type=int, default=16)
    args = parser.parse_args()
    generate(args.out, args.max_length, args.tasks)
    print('生成通过；产品文件见输出目录 rtl/product.f，验证环境不属于产品')
