<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Batch Inversion Hardware Accelerator
Chisel hardware for Goldilocks batch inversion and division with interleaved contexts.
Repository: https://github.com/OpenRTL-Lab/batch-inversion-goldilocks
File: docs/synthesis-summary.en.md

SPDX-FileCopyrightText: 2026 Goldilocks Batch Inversion contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Synthesis summary

[简体中文](synthesis-summary.md)

This configuration has completed SRAM-mapped synthesis in Synopsys Design Compiler NXT using TSMC 28 nm HPC+ at SSG 0.81 V −40°C, passing 500 MHz constraints with 10% setup clock uncertainty. There are zero setup violations, and all 39,217 measured paths have strictly fewer than 50 logic levels, with a maximum of 43.

## Configuration and constraints

| Item | Condition |
| --- | --- |
| Standalone top | `GoldilocksBatchInversion` |
| Circuit configuration | Maximum length 65,536; 16 interleaved contexts; one 15-clock multiplier pipeline |
| Synthesis software | Design Compiler NXT X-2025.06-SP3 |
| Process | TSMC 28 nm HPC+, standard cells and SRAM macros |
| Corner | SSG, 0.81 V, −40°C |
| Clock | 500 MHz, period 2.0 ns |
| Setup clock uncertainty | 0.2 ns, 10% of the period |
| Clock transition | 0.1 ns |
| Maximum input/output delay | 1.0 ns |
| Input transition | 0.125 ns |
| Maximum transition | 0.25 ns |
| Output load | 0.02 in library capacitance units |
| Reset | Synchronous, active high |

These are DC mapped-synthesis results under the stated constraints. Throughput converted at 500 MHz is provided in [Performance metrics](performance.en.md). Main memories and queues participate in mapping with storage structures matching their synchronous read/write behavior.

WNS is the minimum setup slack; TNS sums negative setup slacks. Logic depth below counts combinational data-path positions, not arithmetic pipeline cycles.

## Setup and logic depth

| Metric | Value |
| --- | ---: |
| WNS | +0.000030279 ns |
| TNS, sum of negative slack | 0 ns |
| Setup-violating endpoints | 0 |
| Constrained setup endpoints | 39,217 |
| Maximum logic depth | 43 |
| Measured paths strictly below 50 levels | 39,217, 100% |

The worst setup data path is measured for each constrained endpoint. Logic levels count only output-pin positions whose leaf names are exactly `Z` or `ZN`, one level per position. Report lines and cell-arc counts are not substitutes. The set includes 37,235 internal-to-internal, 232 internal-to-IO, and 1,750 IO-to-internal paths.

There are 5,712 paths above 30 levels (14.5651%), 2,975 above 40 (7.5860%), and none above 50 or 60. These are cumulative thresholds and must not be added.

## Area and storage resources

| Mutually exclusive category | Area (µm²) |
| --- | ---: |
| Combinational logic excluding buffers/inverters | 26,417.860 |
| Buffers/inverters | 1,560.160 |
| Noncombinational logic | 25,818.394 |
| SRAM macros | 11,779,552.500 |
| Total cell area | 11,833,348.914 |

Total cell area is approximately 11.833349 mm². The design maps to 320 physical memory macros, accounting for 99.5454% of cell area. Standard-cell area totals approximately 53,796.414 µm²; the shared multiplier hierarchy accounts for 9,674.658 µm². DC total cell area sums mapped cells and excludes placement whitespace and routing area.

Main storage hierarchy areas are 4,713,489.842 µm² for results, 2,357,489.182 µm² for numerators, 2,357,486.928 µm² for denominators, and 2,357,486.536 µm² for prefixes. These are already included in the total and must not be added again.

## Available materials

This document summarizes the completed synthesis configuration, constraints, and key results. We apologize that process-library licensing and commercial-tool material restrictions prevent us from providing library files, original DC Tcl/SDC, original reports, mapped netlists, or snapshots. RTL and the complete simulation flow are included and can be independently run using [Quick reproduction](reproduction.en.md).
