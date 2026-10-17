<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Batch Inversion Hardware Accelerator
Chisel hardware for Goldilocks batch inversion and division with interleaved contexts.
Repository: https://github.com/OpenRTL-Lab/batch-inversion-goldilocks
File: docs/architecture.en.md

SPDX-FileCopyrightText: 2026 Goldilocks Batch Inversion contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Architecture

[简体中文](architecture.md)

The accelerator replaces per-element inversions with one inversion for an entire batch, uses 16 interleaved prefix chains to keep a shared modular-multiplication pipeline busy, and overlaps previous-batch output with next-batch computation. Inversion and division share the datapath, with configurable generated maximum batch length.

L is the number of denominators in one task; Lmax is the generated batch capacity, bundled as 65,536. Operands/results are canonical unsigned integers 0 <= x < p, p = 2^64 - 2^32 + 1. The 16 contexts hold prefix-chain states indexed by i mod 16 and share one multiplier; they are not separate tasks or external lanes. Inversion returns 1/d_i and division returns a_i/d_i; zero-denominator behavior is defined in [Mathematical model](mathematical-model.en.md).

## Datapath

Processing proceeds through forward prefix scan → combination of 16 chain products → one Fermat inversion → chain-inverse recovery → backward element recovery → natural-order output. Division adds a numerator multiplication after recovery of each element inverse. See [Mathematical model](mathematical-model.en.md).

Forward and backward scans interleave contexts according to the index modulo 16. The shared `BatchGoldilocksMul` has latency 15 clocks and initiation interval 1 clock. Results write back before the same context is scheduled again, allowing long batches to keep the dependent pipeline productive. Its arithmetic uses four 32×32 partial products, registered combination, and Goldilocks modular reduction. Wide carries use block/prefix structures with register boundaries in the reduction chain.

## Compute and task overlap

The controller computes one batch at a time and supports at most two outstanding tasks. While a previous batch is read sequentially from result storage, the next batch may perform its forward scan, chain-product combination, and inversion. Before backward result writes begin, it waits for the previous results to be consumed. Input, output, and completion queue depths are 2, 4, and 2 respectively.

Result reads use combined credits for queue occupancy and outstanding synchronous reads. A queue slot is reserved before a read is issued, decoupling external `ready` from large-memory read enables while preserving data under backpressure. Inter-task overlap allows a steady-state interval shorter than isolated latency; measurements are in [Performance metrics](performance.en.md).

## Storage and generation

| Storage | Width | Depth | Purpose |
| --- | --- | --- | --- |
| `denom` | 64 | Maximum batch length | Denominators and backward updates |
| `prefix` | 64 | Maximum batch length | Pre-element chain prefixes |
| `numer` | 64 | Maximum batch length | Division numerators |
| `results` | 65 | Maximum batch length | Results and zero-denominator flags |

The four main memories store $257L_{\max}$ bits: 16,842,752 bits, or 2.0078125 MiB, at a maximum length of 65,536, excluding small queues and registers. Product SV uses generic synchronous-memory modules rather than FPGA-vendor primitives. Main memories have independent read/write ports and one-clock synchronous read latency; the design avoids same-address read/write conflicts. Integration may retain the behavioral models or map them to SRAMs preserving these port, latency, and enable semantics.

`Config(maxLength)` supports powers of two from 32 through 65,536. This is a static circuit dimension; actual task lengths are dynamic. The 16 contexts, 15-clock multiplier, and interface widths are fixed in this implementation. The product top is `GoldilocksBatchInversion`; simulation environments, reference-vector loaders, and layered statistics/assertion files are excluded from the product file list.

## Separation of design and verification

The product list contains only the top and its dependencies in 10 SV files. The verification environment supplies stimulus, elementwise checking, protocol assertions, and clock measurements. The generation script constructs the product dependency closure, and verification compares regenerated and bundled circuit bodies before running complete tests on each. See [Quick reproduction](reproduction.en.md).
