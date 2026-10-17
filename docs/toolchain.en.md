<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Batch Inversion Hardware Accelerator
Chisel hardware for Goldilocks batch inversion and division with interleaved contexts.
Repository: https://github.com/OpenRTL-Lab/batch-inversion-goldilocks
File: docs/toolchain.en.md

SPDX-FileCopyrightText: 2026 Goldilocks Batch Inversion contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Toolchain

[简体中文](toolchain.md)

This document lists the open-source tool versions used for independent generation and functional/performance verification, together with command entry points and configurable environment settings for reproduction on the reader's own computer.

| Tool | Tested version | Purpose |
| --- | --- | --- |
| Mill | 1.1.7 | Build and Chisel execution |
| Scala | 2.13.18 | Design language |
| Chisel and compiler plugin | 7.13.0 | Circuit description and generation |
| CIRCT firtool | 1.149.0 | SV lowering and splitting |
| OpenJDK | 21.0.11 | Mill/Scala runtime |
| Python | 3.12.13 | Flow scripts and independent modular-inverse reference |
| Verilator | 5.050 | Product lint and RTL simulation |
| GCC/G++ | 16.2.0 | C reference and C++ simulation build |

Install these tools so that `java`, `mill`, `python3`, `verilator`, `cc`, and `g++` are callable from PATH. `build.mill` fixes Scala, Chisel, and plugin versions, resolved through public Maven dependencies. The first build needs network access or a dependency cache prepared by the reader. Use the Chisel-compatible firtool resolution mechanism with the version above.

If the Mill launcher is not named `mill`, set `MILL` to its command, for example `MILL='bash mill-launcher'`. `CC` may select the C compiler. Cache locations are controlled by the user's Mill/Coursier environment. Scripts do not bind a machine, proxy, account, or cache location.

To confirm installed versions, run the following from the repository root. This checks tools only; it neither generates circuits nor runs tests:

```sh
java -version
mill --version
python3 --version
verilator --version
cc --version
g++ --version
```

Generation and verification are described in [Quick reproduction](reproduction.en.md). The documented tool versions define the tested combination; other combinations require complete functional and cycle verification.
