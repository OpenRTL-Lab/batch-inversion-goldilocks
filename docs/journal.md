<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Batch Inversion Hardware Accelerator
Chisel hardware for Goldilocks batch inversion and division with interleaved contexts.
Repository: https://github.com/OpenRTL-Lab/batch-inversion-goldilocks
File: docs/journal.md

SPDX-FileCopyrightText: 2026 Goldilocks Batch Inversion contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 当前版本说明

[English](journal.en.md)

Goldilocks Batch Inversion Hardware Accelerator，版本 1.1.0：使用 Chisel 实现、采用交织上下文的 Goldilocks 批量求逆和除法硬件。

## 交付与使用

仓库提供 Chisel 源码、生成的产品 SystemVerilog、原生接口文档、数学参考模型和复现工具。产品文件清单区分实际电路与验证环境。

## 验证边界

复现文档定义已测配置、命令和成功判据；性能结果给出工作负载与理想原生接口条件。综合摘要说明目标配置、工具、工艺角与约束；仿真和综合不等于上板或硅后认证。

## 来源与许可

原创文件具有组织、项目和许可声明；严格格式数据使用相邻许可伴随文件。第三方材料保留相应版权与许可，项目许可见根目录 LICENSE。

## 源码交付

版本标签标识包含来源声明和文档的当前源码树；Release 下载提供本版本硬件源码与随包 RTL。源码包与在线目录的注释及文档可能不同。RTL 集成使用产品文件清单，不把验证环境作为产品电路；支持配置、验证范围和测量条件见验证及综合文档。
