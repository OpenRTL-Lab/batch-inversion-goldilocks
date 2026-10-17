<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Batch Inversion Hardware Accelerator
Chisel hardware for Goldilocks batch inversion and division with interleaved contexts.
Repository: https://github.com/OpenRTL-Lab/batch-inversion-goldilocks
File: THIRD_PARTY_NOTICES.en.md

SPDX-FileCopyrightText: 2026 Goldilocks Batch Inversion contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Third-party notices

[简体中文](THIRD_PARTY_NOTICES.md)

This document explains the project's MIT licensing scope, third-party tools required for generation and simulation, and the mathematical parameters. The repository delivers design and verification sources; tools and process materials are obtained under their respective licenses.

## Project license

Project code, generated product SV, and authored documentation use the [MIT License](LICENSE). Free commercial use, modification, and redistribution are permitted with the copyright and permission notices retained. Warranty and liability terms are governed by the license text.

## Mathematical parameters and algorithm

The Goldilocks modulus is $2^{64}-2^{32}+1$. Batch inversion uses prefix products, one Fermat inversion, and backward recovery, implemented with 16 interleaved contexts. The C model performs per-element exponentiation and Python independently checks modular inverses. See [Mathematical model](docs/mathematical-model.en.md). No proprietary external-protocol parameter tables or third-party prover system implementations are included.

## Tools

Generation uses Mill, Scala, Chisel, and CIRCT; verification uses Python, Verilator, and GCC/G++. Tool binaries and dependency caches are not bundled. Users install tools separately and comply with their licenses. Tested versions are listed in [Toolchain](docs/toolchain.en.md).

Synthesis data was obtained with Synopsys Design Compiler NXT and TSMC process libraries. We apologize that applicable licensing restrictions prevent us from distributing commercial software, process libraries, or original synthesis materials with this project. Publicly summarized results are in [Synthesis summary](docs/synthesis-summary.en.md).
