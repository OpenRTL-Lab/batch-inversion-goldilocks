<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Batch Inversion Hardware Accelerator
Chisel hardware for Goldilocks batch inversion and division with interleaved contexts.
Repository: https://github.com/OpenRTL-Lab/batch-inversion-goldilocks
File: THIRD_PARTY_NOTICES.md

SPDX-FileCopyrightText: 2026 Goldilocks Batch Inversion contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 第三方声明

[English](THIRD_PARTY_NOTICES.en.md)

本文件说明项目的 MIT 授权范围、生成与仿真所需的第三方工具及数学参数来源；仓库交付设计和验证源码，工具与工艺资料由使用者按各自授权获取。

## 项目授权

本项目代码、生成的产品 SV 和作者编写的文档采用 [MIT 许可证](LICENSE)。允许免费商用、修改和再分发，需保留许可证中的版权及许可声明。许可证中的保证与责任条款以原文为准。

## 数学参数与算法

Goldilocks 模数为 $2^{64}-2^{32}+1$。批量求逆采用前缀乘积、一次 Fermat 求逆与反向恢复的数学方法，并以 16 个交织上下文实现；C 模型使用逐元素幂求逆，Python 使用独立模逆检查。数学定义见[数学模型](docs/mathematical-model.md)。本仓库未包含外部协议的专有参数表或第三方证明器的系统实现。

## 工具

生成依赖 Mill、Scala、Chisel 和 CIRCT，验证依赖 Python、Verilator、GCC/G++。仓库不打包这些工具的二进制或依赖缓存；用户自行安装并遵守对应软件许可证。实际验证版本见[工具链版本](docs/toolchain.md)。

综合数据由 Synopsys Design Compiler NXT 和 TSMC 工艺库获得。很抱歉，由于相关授权限制，无法随本项目提供商业软件、工艺库或原始综合材料；公开的转述结果见[综合摘要](docs/synthesis-summary.md)。
