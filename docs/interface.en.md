<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Batch Inversion Hardware Accelerator
Chisel hardware for Goldilocks batch inversion and division with interleaved contexts.
Repository: https://github.com/OpenRTL-Lab/batch-inversion-goldilocks
File: docs/interface.en.md

SPDX-FileCopyrightText: 2026 Goldilocks Batch Inversion contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Native interface

[简体中文](interface.md)

This document specifies the task, input, result, and completion channels of the standalone `GoldilocksBatchInversion` top, including widths, handshakes, ordering, zero denominators, and exceptions for integration into custom control and data planes.

Field words use canonical unsigned encoding 0 <= x < p for p = 2^64 - 2^32 + 1, not Montgomery residues. Mode 0 returns denominator inverses; mode 1 returns numerator/denominator quotients. The generated maximum is a power of two from 32 to 65,536 (bundled: 65,536); a task may use any integer length from 0 through that maximum, not only powers of two.

## Common timing

All channels use one `clock` and sample on rising edges. `reset` is synchronous and active high. A transfer occurs on `valid && ready`. A blocked sender holds `valid` and the payload unchanged until handshake. Directions below are relative to the accelerator.

| Channel/signal | Direction | Payload and meaning |
| --- | --- | --- |
| `io_enable` | Input | 1 bit; its previous-clock sampled value controls new task admission and does not pause accepted tasks |
| `io_busy` | Output | 1 bit; value 1 while accepted tasks still have unconsumed completion messages |
| `io_task` | Input | 32-bit `tag`, 2-bit `mode`, 32-bit `length` |
| `io_input` | Input | 64-bit `numerator`, 64-bit `denominator`, 1-bit `last` |
| `io_output` | Output | 32-bit `tag`, 32-bit `index`, 64-bit `value`, 1-bit `zero` and `last` |
| `io_completion` | Output | 32-bit `tag`, 2-bit `status`, 32-bit `length` and `zeros` |

Flattened SV names include `io_task_valid`, `io_task_ready`, and `io_task_bits_length`; other channels use the same convention. `clock` and `reset` have no `io_` prefix.

## Tasks and data

Mode 0 performs inversion and mode 1 division. Batch lengths range from 0 to the generated maximum. Supply complete batches in task-acceptance order without interleaving their input streams, asserting `last` only on the `length`th element. Inputs and outputs use natural order; results carry the original tag and a zero-based index.

At most two tasks may be accepted without their completion messages being consumed. The caller assigns distinct tags to outstanding tasks. Element results precede the corresponding completion; batches and completions preserve task order. Input, output, and completion channels support backpressure. The actual completion handshake is included in task latency.

## Status codes

| `status` | Name | Behavior |
| --- | --- | --- |
| 0 | `SUCCESS` | Successful; an empty batch completes immediately without elements |
| 1 | `ZERO_DENOMINATOR` | Contains zero denominators; returns the complete batch, with `zero=1,value=0` on those elements |
| 2 | `BAD_PARAM` | Invalid mode or excessive length; accepts no data and produces no results |
| 3 | `BAD_FRAME` | Noncanonical elements or incorrect `last`; consumes the declared batch length and produces no results |

Normal completion reports the task length and zero-denominator count. Both `BAD_PARAM` and `BAD_FRAME` report `length=0,zeros=0`. Inversion ignores the numerator, including its encoding check; division checks both numerator and denominator. An early `last` does not truncate a malformed frame. See [Mathematical model](mathematical-model.en.md).

## Reset and storage

Reset cancels all outstanding tasks, results, and completion state; the caller resubmits tasks. Storage need not be cleared on reset, and successful completions from pre-reset tasks are not returned afterward. The product uses synchronous storage abstractions and single-clock control; mapping is discussed in [Architecture](architecture.en.md).
