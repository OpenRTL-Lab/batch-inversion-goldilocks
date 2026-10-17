<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Batch Inversion Hardware Accelerator
Chisel hardware for Goldilocks batch inversion and division with interleaved contexts.
Repository: https://github.com/OpenRTL-Lab/batch-inversion-goldilocks
File: README.md

SPDX-FileCopyrightText: 2026 Goldilocks Batch Inversion contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Goldilocks Batch Inversion and Division Hardware Accelerator

[简体中文](README.zh-CN.md)

This repository provides a standalone Goldilocks finite-field batch inversion and batch division hardware accelerator developed in Chisel. Through its native interface, it can be integrated into ASIC or FPGA designs to accelerate polynomial-denominator inversion, batch normalization, and other computations requiring batches of finite-field inverses or quotients in zero-knowledge proving systems.

It is licensed under the MIT License, permitting free commercial use, modification, and redistribution, provided the copyright and license notices are retained; see [Third-party notices](THIRD_PARTY_NOTICES.en.md) for third-party tooling.

[Release v1.1.0](https://github.com/OpenRTL-Lab/batch-inversion-goldilocks/releases/tag/v1.1.0) · [Native interface](docs/interface.en.md) · [Quick reproduction](docs/reproduction.en.md) · [Performance metrics](docs/performance.en.md) · [Synthesis summary](docs/synthesis-summary.en.md)

## Computation and task units

One task is a batch of L denominator values d_i, optionally accompanied by numerators a_i. Inversion returns 1/d_i; division returns a_i/d_i, in input-index order. Elements are canonical unsigned 64-bit Goldilocks values, 0 <= x < p for p = 2^64 - 2^32 + 1. Montgomery-style batch inversion names the product-and-recovery algorithm here; external values are not Montgomery-encoded residues. A zero denominator returns zero and a per-element flag, with a distinct task status.

The 16 scan contexts are internal prefix-product chains, scheduled by element index modulo 16 through one shared multiplier. They are not 16 tasks or external input lanes. At most two whole tasks are outstanding: output of the previous batch can overlap computation of the next, but records of their input batches must not be interleaved. This overlap explains a steady task interval shorter than isolated task latency.

## At a glance

| Integration question | Released hardware |
| --- | --- |
| Computation | Montgomery-style batch inversion and elementwise numerator/denominator division |
| Field | Goldilocks p = 2^64 - 2^32 + 1; canonical 64-bit elements |
| Bundled configuration | 16 interleaved scan contexts, maximum batch length 65,536; top `GoldilocksBatchInversion` |
| Native boundary | Task, ordered numerator/denominator stream, indexed value/zero flag and completion |
| Task overlap | At most two tasks outstanding; complete input batches are not interleaved |

## Quick start

Download the source archive from [Release v1.1.0](https://github.com/OpenRTL-Lab/batch-inversion-goldilocks/releases/tag/v1.1.0), or clone the current release snapshot:

```bash
git clone --branch v1.1.0 --depth 1 https://github.com/OpenRTL-Lab/batch-inversion-goldilocks.git
cd batch-inversion-goldilocks
```

Install the exact prerequisites in [Toolchain](docs/toolchain.en.md), then run from the repository root with a fresh output directory:

```bash
python3 tools/emit_sv.py --out build/emit --max-length 65536 --tasks 16
```

This emission command is not a complete RTL regression. The documented verification entry is:

```bash
python3 tools/verify.py --out build/verify --max-length 65536 --tasks 16 --jobs 8
```

Follow [Quick reproduction](docs/reproduction.en.md) for expected results, negative-control interpretation and the distinction between short functional and full performance runs.

## Verification and integration boundary

Independent C and Python references check every vector before RTL tests. Generated and bundled SV each run eight documented categories: ideal inversion/division, varied lengths and zero denominators with stalls, reset, a deliberate reference error, malformed tasks, and isolated inversion/division. The maximum-length-32 command is a quick installation check, not a substitute for the released 65,536-element configuration.

Zero denominators produce value zero and a per-element zero flag, with ZERO_DENOMINATOR completion rather than ordinary SUCCESS. Empty batches, BAD_PARAM and BAD_FRAME have distinct behavior. Tags must distinguish outstanding tasks; supply each batch in order and wait for real completion. The 16 contexts are a hardware scheduling choice, not 16 independent external request lanes. This unit does not implement proof relations or BN254 arithmetic.

### Reading performance correctly

For ideal-interface batches of 65,536 elements, isolated inversion/division latency is 264,965/330,501 cycles, while measured steady completion intervals are 199,427/264,963 cycles because output and next-batch computation overlap. Shared-multiplier useful-issue utilization is valid multiplication issue cycles divided by steady-window cycles: 98.6732%/99.0014%. It is not the sum of valid pipeline stages or a count of independent dividers.

These are documented RTL cycle measurements under ideal native interfaces. MHz-based time/rate conversions are not board or silicon measurements. The reported Design Compiler results apply only to the documented configuration, library corner and constraints; they do not establish place-and-route signoff or system throughput. See [Performance metrics](docs/performance.en.md) and [Synthesis summary](docs/synthesis-summary.en.md) for conditions.

## Repository contents

- [Chisel design](operators/batch-inversion/src/main/scala/zk/batchinv/): 16 interleaved scan contexts and a shared pipelined modular multiplier.
- [Generation entry point](operators/batch-inversion/src/tool/scala/zk/batchinv/) and [Generation script](tools/emit_sv.py): configurable maximum batch length.
- [Generated SV](rtl/v1.1.0/gl64-ctx16-l65536/) and [Product file list](rtl/v1.1.0/gl64-ctx16-l65536/files.f): standalone product circuit supporting up to 65,536 elements per batch.
- [C reference model](operators/batch-inversion/cmodel/reference.c), [Python reference cross-check](tools/check_reference.py), [Simulation driver](operators/batch-inversion/sim/driver.cpp), and [Verification script](tools/verify.py): reproducible functional and performance verification.
- [Native interface](docs/interface.en.md), [Architecture](docs/architecture.en.md), and [Toolchain](docs/toolchain.en.md): integration and usage guidance.

## Applications

Use this module when a computation requires inverses of a batch of nonzero Goldilocks denominators or elementwise quotients of numerators and denominators. Examples include polynomial rational-expression evaluation in provers, denominator processing in lookup or permutation relations, and finite-field batch normalization required by a protocol. These are integration examples rather than an exhaustive list of uses.

The task channel selects the batch length and operation. Field elements and results use natural order; zero denominators are flagged individually and task tags are returned unchanged. Both control and data planes use native ready/valid handshakes for connection to memory controllers, other operators, or custom schedulers.

## Performance metrics

For 65,536-element batches, isolated inversion/division latency is 264,965/330,501 clocks and the steady-state task interval is 199,427/264,963 clocks. At 500 MHz these rates correspond to approximately 164.31/123.67 million elements per second. Definitions, test procedures, and compute utilization are documented in [Performance metrics](docs/performance.en.md).

## Mathematical model

The circuit returns elementwise denominator inverses or numerator/denominator quotients over the field with modulus $p=2^{64}-2^{32}+1$, with defined behavior for zero denominators, empty batches, and malformed input. See [Mathematical model](docs/mathematical-model.en.md) for the precise specification.

## Quick reproduction

The complete flow covers independent C/Python reference checking, Chisel generation, simulation of regenerated and bundled SV, and performance measurement. Installation and execution instructions are provided in [Quick reproduction](docs/reproduction.en.md).

## Synthesis results

This configuration passes 500 MHz setup constraints with 10% setup clock uncertainty using TSMC 28 nm HPC+ at SSG 0.81 V −40°C, with zero setup violations. All 39,217 measured paths have fewer than 50 logic levels, with a maximum of 43. Conditions and area results are provided in [Synthesis summary](docs/synthesis-summary.en.md).

## Publication scope

The release includes design and generation code, product SV, reference models, verification and performance-test code, tool versions, and summarized synthesis results. We apologize that foundry licensing and commercial-tool material restrictions prevent us from providing the process libraries, original DC scripts and reports, mapped netlists, or synthesis snapshots.

This project delivers a standalone operator with native interfaces. System protocols, SoC buses, and board connections can be integrated according to the user's system requirements. See [Publication checklist](docs/publication-checklist.en.md) for the release-content checks.

## OpenRTL and custom integration

Browse [OpenRTL](https://github.com/OpenRTL-Lab), the [ZK accelerator catalogue](https://github.com/OpenRTL-Lab/zk-accelerators) and [OpenRTL website](https://openrtl.top/) for related modules and the cooperation/contact entry. Custom datapaths, interfaces and target-platform adaptation are optional services; MIT commercial use does not require buying a service. Do not submit confidential specifications in public issues.

## Release sources and integration

The version tag identifies the current source tree, including source notices and documentation. Release downloads provide hardware sources and bundled RTL for this version. Archive annotations and documentation can differ from the online tree. For RTL integration, use the product file list rather than verification environments; use the verification and synthesis documents for supported configurations and measurement conditions.
