<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Batch Inversion Hardware Accelerator
Chisel hardware for Goldilocks batch inversion and division with interleaved contexts.
Repository: https://github.com/OpenRTL-Lab/batch-inversion-goldilocks
File: docs/reproduction.en.md

SPDX-FileCopyrightText: 2026 Goldilocks Batch Inversion contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Quick reproduction

[简体中文](reproduction.md)

This document provides the complete flow from tool checks and independent reference verification through Chisel generation, product lint, RTL functionality, and performance measurements. By default it tests both regenerated and bundled maximum-length product SV.

## Obtain the release snapshot

[Release v1.1.0](https://github.com/OpenRTL-Lab/batch-inversion-goldilocks/releases/tag/v1.1.0)

```bash
git clone --branch v1.1.0 --depth 1 https://github.com/OpenRTL-Lab/batch-inversion-goldilocks.git
cd batch-inversion-goldilocks
```

The version tag identifies the current source tree, including source notices and documentation. Release downloads provide hardware sources and bundled RTL for this version. Archive annotations and documentation can differ from the online tree. For RTL integration, use the product file list rather than verification environments; use the verification and synthesis documents for supported configurations and measurement conditions.

## Preparation

Install the open-source tools listed in [Toolchain](toolchain.en.md), then run the commands below from the repository root. Python scripts use only the standard library. Ensure the output directory does not exist and storage is writable. Scripts create relative directories under `build/` and never automatically delete or overwrite prior results. The first Mill build resolves dependencies; later builds can reuse the user's caches.

`--max-length` sets static batch capacity (a power of two, 32..65,536), not the number of tasks. `--tasks` (4..256) sets continuous benchmark task count per operation, not the fixed 16 internal scan contexts. `--jobs` controls simulator compilation parallelism only.

## Complete verification

This command generates C reference vectors and cross-checks every element with Python modular inversion, generates the Chisel environment and product circuit, checks the dependency closure, and builds separate simulators using regenerated and bundled SV. Each path runs eight functional/performance test categories:

```sh
python3 tools/verify.py --out build/verify --max-length 65536 --tasks 16 --jobs 8
```

Success prints the verification-passed message. `build/verify/completion.json` reports `status=0`, `bundled_product_matches=true`, and 16 test results: eight per product path. Here `tasks=16` specifies the number of continuous benchmark tasks, not the fixed number of 16 hardware contexts.

For a quick installation and flow check, this command generates a maximum-length-32 configuration and runs the same eight categories. It does not replace the bundled maximum-length-65,536 product:

```sh
python3 tools/verify.py --out build/verify-small --max-length 32 --tasks 16 --jobs 8
```

## Circuit generation only

To generate the circuit, reference vectors, and file lists without building a Verilator simulator, run:

```sh
python3 tools/emit_sv.py --out build/emit --max-length 65536 --tasks 16
```

`--max-length` supports powers of two from 32 to 65,536. `--tasks` supports 4 to 256 and controls only continuous-task count in the verification environment. The standalone product top is `GoldilocksBatchInversion`; its file list is `build/emit/rtl/product.f`. `simulation.f` additionally includes the environment, vector memories, and verification-layer files.

To lint only the bundled product and its dependencies, without loading the simulation environment, run:

```sh
verilator --lint-only --assert --top-module GoldilocksBatchInversion -f rtl/v1.1.0/gl64-ctx16-l65536/files.f
```

## Tests and success criteria

| Test name | Content and required result |
| --- | --- |
| `ideal-inverse` / `ideal-division` | Continuous full batches; correct values, tags, indices, `last`, and completions; sustained measurements recorded |
| `varied` | Empty/short batches, boundary elements, zero denominators, and backpressure; checks 16 completions and all results |
| `reset` | Reset during computation and output; two `RESET_ABORT` events and 16 successful post-reset tasks |
| `negative` | One intentionally corrupted reference result; requires `pass=0,mismatches=1` and process exit code 1 |
| `errors` | Six invalid-parameter/malformed-frame tasks; correct completions and no element results |
| `single-inverse` / `single-division` | One batch with no previous outstanding task; isolated latency and first/last-result clocks |

The negative control's failure is expected and checked by the outer script; it is not a verification failure. Other tests require `pass=1,mismatches=0`. Unexpected failures on either product path cause a nonzero script exit.

## Outputs and performance extraction

`completion.json` summarizes configuration, tests, and performance. Continuous-test `performance` entries contain `sustained` windows, task intervals, throughput, multiplication issues, and valid pipeline occupancy. Isolated tests provide `task_latencies_clocks`. Prefixes `generated-*` and `bundled-*` distinguish product paths, whose performance results must match exactly.

Each step saves its own log. Reference vectors are in `vectors/`, generated SV in `rtl/`, and simulator builds in `obj-generated/` and `obj-bundled/`. Verification records include `ACCEPT`, `FIRST_OUTPUT`, `LAST_OUTPUT`, `COMPLETE`, and `UNITS`. Metric definitions and published values are in [Performance metrics](performance.en.md).

After a failure, inspect the step log, fix tool/environment issues, and retry with a new `--out`, such as `build/verify-r2`, preserving earlier evidence. Reference models, functional verification, and cycle measurements require no DC installation, process library, or physical hardware.
