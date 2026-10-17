<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Batch Inversion Hardware Accelerator
Chisel hardware for Goldilocks batch inversion and division with interleaved contexts.
Repository: https://github.com/OpenRTL-Lab/batch-inversion-goldilocks
File: docs/performance.en.md

SPDX-FileCopyrightText: 2026 Goldilocks Batch Inversion contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Performance metrics

[简体中文](performance.md)

This document reports measured task latency, sustained throughput, and shared-multiplier utilization under ideal native-interface timing. For 65,536-element inversion/division batches, steady-state intervals are 199,427/264,963 clocks and issue utilization is 98.6732%/99.0014%, reproducible with the public verification flow.

L is the number of elements in one batch task. Each internal context is a prefix-product chain for indices i mod 16; all 16 share one multiplier, not 16 independent tasks. Frequency-based conversions below are not board or silicon measurements.

## Configuration and method

The maximum batch length is 65,536, with 16 interleaved contexts and one modular-multiplication pipeline of latency 15 clocks and initiation interval 1. Inversion and division are measured separately. Inputs remain valid, output and completion remain ready, and `enable=1`. Cycle-accurate RTL simulation introduces no external system bandwidth, latency, or shared-port restrictions. Backpressure tests check functionality and are excluded from these ideal-interface measurements.

Let $A_i,F_i,O_i,C_i$ be the clock indices of task acceptance, first result transfer, last result transfer, and completion transfer. Isolated latency is $C_0-A_0$ and first-result latency is $F_0-A_0$. Continuous tests submit 16 equal-length tasks and use $W=C_{15}-C_1$, containing 14 completion intervals while excluding initial-fill and final-drain effects.

$$
I=\frac{W}{14},\qquad
T_{\mathrm{task}}=\frac{14}{W},\qquad
T_{\mathrm{element}}=\frac{14L}{W}.
$$

Throughput units are tasks/clock and elements/clock. The script checks that the corresponding acceptance window satisfies $A_{15}-A_1=W$. At frequency $f$, divide clocks by $f$ for time and multiply per-clock throughput by $f$ for per-second throughput. Sustained throughput is not inferred from the reciprocal of isolated latency.

## Latency and throughput

| Metric, $L=65536$ | Inversion | Division |
| --- | ---: | ---: |
| Isolated task latency (clocks) | 264,965 | 330,501 |
| First-result latency (clocks) | 199,429 | 264,965 |
| Last-result latency (clocks) | 264,964 | 330,500 |
| Steady-state task interval (clocks/task) | 199,427 | 264,963 |
| Completion window for 14 intervals (clocks) | 2,791,978 | 3,709,482 |
| Throughput (elements/clock) | 0.3286215006 | 0.2473401947 |
| Amortized clocks/element | 3.0430145264 | 4.0430145264 |
| Isolated latency converted at 500 MHz (µs) | 529.930 | 661.002 |
| Task throughput converted at 500 MHz (tasks/s) | 2,507.1831 | 1,887.0559 |
| Element throughput converted at 500 MHz (million/s) | 164.3108 | 123.6701 |

The final three rows convert measured clocks using 500 MHz; corresponding DC conditions are in [Synthesis summary](synthesis-summary.en.md). Overlap between previous-batch output and next-batch computation explains why the sustained interval is shorter than isolated latency.

## Compute units and utilization

The main shared arithmetic resource is `BatchGoldilocksMul`, comprising four concurrent 32×32 partial multipliers and pipelined combination/reduction logic. Prefix scans, chain combination, exponentiation, inverse recovery, and division all use this unit; there is no separate per-element divider. Utilization counts useful work, not physical switching caused by invalid pipeline operands.

Issue utilization is valid multiplication issue clocks divided by window clocks. Pipeline utilization is clocks with at least one valid operation in the pipeline divided by window clocks. A clock with several valid stages still counts once; stage occupancies are not summed into this metric.

| Steady-state window of 14 tasks | Inversion | Division |
| --- | ---: | ---: |
| Valid multiplication issue clocks | 2,754,934 | 3,672,438 |
| Clocks without a valid issue | 37,044 | 37,044 |
| Issue utilization | 98.6732% | 99.0014% |
| Clocks with a valid pipeline stage | 2,789,234 | 3,706,738 |
| Completely empty pipeline clocks | 2,744 | 2,744 |
| Pipeline utilization | 99.9017% | 99.9260% |

Per-task counters record compute-phase clocks, valid pipeline occupancy, and issue counts by the following classes. Window sums use tasks 1 through 14. Verification also checks the sum of all issue classes against the expected total, rather than using one phase's utilization as an end-to-end metric.

The following per-task formulas describe nonempty batches; an empty batch completes without the scan/inversion sequence.

| Issue class | Inversion count/task | Division count/task |
| --- | ---: | ---: |
| c0: forward prefix | $L$ | $L$ |
| c1: chain-product combination | 16 | 16 |
| c2: Fermat squaring | 63 | 63 |
| c3: Fermat multiplication | 62 | 62 |
| c4: chain-recovery update | 16 | 16 |
| c5: chain-inverse recovery | 16 | 16 |
| c6: backward accumulator update | $L$ | $L$ |
| c7: element-inverse recovery | $L$ | $L$ |
| c8: numerator multiplication | 0 | $L$ |
| Total | $3L+173$ | $4L+173$ |

The feedback-dependent Fermat phase issues 125 valid multiplications over 2,000 clocks: issue utilization 6.25% and valid pipeline occupancy 93.75%. Whole-batch metrics include every phase. For large batches, interleaved forward/backward scans account for most work.

## Functional evidence and reproduction

A complete product test covers continuous inversion, continuous division, variable lengths/boundaries/backpressure, reset during computation and output, a corrupted-reference negative control, invalid parameters/frames, isolated inversion, and isolated division. Each product path checks 2,752,828 positive-test outputs. The negative control checks 1,048,576 outputs and must find exactly one intentionally injected mismatch and return failure, confirming that the checker detects errors.

Verification covers maximum-length configurations 32 and 65,536. The 65,536 configuration checks regenerated and bundled SV in independent builds. Both products agree on every output and the cycle metrics above. Commands, output files, and success criteria are provided in [Quick reproduction](reproduction.en.md).
