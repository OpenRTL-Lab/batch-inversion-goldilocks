<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Batch Inversion Hardware Accelerator
Chisel hardware for Goldilocks batch inversion and division with interleaved contexts.
Repository: https://github.com/OpenRTL-Lab/batch-inversion-goldilocks
File: docs/toolchain.md

SPDX-FileCopyrightText: 2026 Goldilocks Batch Inversion contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 工具链版本

[English](toolchain.en.md)

本文件列出独立生成与功能/性能复测实际使用的开源工具版本，并说明命令入口及可配置环境，帮助读者在自己的计算机上复现同一设计。

| 工具 | 验证版本 | 用途 |
| --- | --- | --- |
| Mill | 1.1.7 | 构建与 Chisel 运行入口 |
| Scala | 2.13.18 | 设计语言 |
| Chisel 与编译插件 | 7.13.0 | 电路描述与生成 |
| CIRCT firtool | 1.149.0 | SV 降级与拆分 |
| OpenJDK | 21.0.11 | Mill/Scala 运行 |
| Python | 3.12.13 | 流程与独立模逆参考 |
| Verilator | 5.050 | 产品 lint、RTL 仿真 |
| GCC/G++ | 16.2.0 | C 参考和 C++ 仿真构建 |

先安装这些工具，使 `java`、`mill`、`python3`、`verilator`、`cc`、`g++` 可从 PATH 调用。Scala、Chisel 及插件版本由 `build.mill` 固定，并通过公开 Maven 依赖解析；首次构建需要网络或读者自己准备的依赖缓存。firtool 使用与 Chisel 对应的解析安装机制，选择上表版本。

若 Mill 启动器名称不是 `mill`，可通过 `MILL` 指定命令，例如 `MILL='bash mill-launcher'`。可选 `CC` 指定 C 编译器，缓存位置由用户的 Mill/Coursier 环境选择。项目脚本不绑定特定机器、代理、账户或缓存位置。

为确认安装版本，在仓库根目录执行以下检查；该步骤只检查工具，不生成电路或运行测试：

```sh
java -version
mill --version
python3 --version
verilator --version
cc --version
g++ --version
```

实际生成及验证步骤见[快速复现](reproduction.md)。文档所列工具版本构成已测组合，其他工具组合的功能与周期结果须以完整验证为准。
