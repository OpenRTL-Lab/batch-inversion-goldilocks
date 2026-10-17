#!/usr/bin/env python3
# OpenRTL-Header-Begin
# OpenRTL — Open-source hardware accelerators.
# Organization: https://github.com/OpenRTL-Lab
# Website: https://openrtl.top/
#
# Project: Goldilocks Batch Inversion Hardware Accelerator
# Chisel hardware for Goldilocks batch inversion and division with interleaved contexts.
# Repository: https://github.com/OpenRTL-Lab/batch-inversion-goldilocks
# File: tools/check_reference.py
#
# SPDX-FileCopyrightText: 2026 Goldilocks Batch Inversion contributors
# Copyright applies to original project contributions only.
# SPDX-License-Identifier: MIT
# See LICENSE and applicable third-party notices.
# OpenRTL-Header-End

"""用 Python 的独立模逆实现逐项检查 C 模型生成的向量。"""
import argparse
from pathlib import Path

P = (1 << 64) - (1 << 32) + 1


def check(vectors, length):
    names = ('input.hex', 'inverse.hex', 'division.hex')
    data = [(vectors / name).read_text().splitlines() for name in names]
    if any(len(values) != 4 * length for values in data):
        raise ValueError('参考向量数量不正确')
    for index, (raw, inverse, quotient) in enumerate(zip(*data)):
        packed = int(raw, 16)
        denominator = packed & ((1 << 64) - 1)
        numerator = packed >> 64
        assert 0 <= denominator < P and 0 <= numerator < P
        value = pow(denominator, -1, P) if denominator else 0
        zero_bit = int(denominator == 0) << 64
        assert int(inverse, 16) == value + zero_bit, (index, 'inverse')
        assert int(quotient, 16) == numerator * value % P + zero_bit, (index, 'division')
    assert pow(2, -1, P) == (P + 1) // 2
    assert pow(P - 1, -1, P) == P - 1
    return 4 * length


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('vectors', type=Path)
    parser.add_argument('--length', type=int, required=True)
    args = parser.parse_args()
    print(f'PYTHON_REFERENCE_PASS checked={check(args.vectors, args.length)}')
