<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Batch Inversion Hardware Accelerator
Chisel hardware for Goldilocks batch inversion and division with interleaved contexts.
Repository: https://github.com/OpenRTL-Lab/batch-inversion-goldilocks
File: docs/synthesis-summary.md

SPDX-FileCopyrightText: 2026 Goldilocks Batch Inversion contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 综合摘要

[English](synthesis-summary.en.md)

本配置经 Synopsys Design Compiler NXT 在 TSMC 28 nm HPC+、SSG 0.81 V −40°C 下完成 SRAM 映射综合，通过 500 MHz、10% setup 时钟不确定度约束；setup 违例为 0，全部 39,217 条统计路径的逻辑级数严格小于 50，最大 43。

## 配置与约束

| 项目 | 条件 |
| --- | --- |
| 独立顶层 | `GoldilocksBatchInversion` |
| 电路配置 | 最大批长 65,536；16 个交织上下文；一条 15 拍模乘通路 |
| 综合软件 | Design Compiler NXT X-2025.06-SP3 |
| 工艺 | TSMC 28 nm HPC+，标准单元与 SRAM 宏 |
| 工艺角 | SSG，0.81 V，−40°C |
| 时钟 | 500 MHz，周期 2.0 ns |
| Setup 时钟不确定度 | 0.2 ns，即周期的 10% |
| 时钟转换时间 | 0.1 ns |
| 输入/输出最大延迟 | 1.0 ns |
| 输入转换时间 | 0.125 ns |
| 最大转换时间 | 0.25 ns |
| 输出负载 | 0.02（工艺库电容单位） |
| 复位 | 同步、高电平有效 |

这里报告的是上述约束下 DC 映射综合阶段的结果；500 MHz 下的吞吐换算见[性能指标](performance.md)。主存储和队列以匹配同步读写行为的存储结构参与映射。

WNS 是最差 setup 裕量，TNS 是负 setup 裕量之和。下列逻辑级数统计组合数据路径的位置，不是算术流水线周期数。

## Setup 与逻辑级数

| 指标 | 数值 |
| --- | ---: |
| WNS | +0.000030279 ns |
| TNS（负 slack 总和） | 0 ns |
| Setup 违例端点 | 0 |
| 已约束 setup 端点 | 39,217 |
| 最大逻辑级数 | 43 |
| 严格小于 50 级的统计路径 | 39,217，100% |

每个已约束端点统计其最差的一条 setup 数据路径。逻辑级数只按数据路径上输出引脚叶名严格等于 `Z` 或 `ZN` 的位置计数；每个位置计一级，不以报告行数或 cell arc 数量替代。包括内部到内部 37,235 条、内部到 IO 232 条、IO 到内部 1,750 条。

超过 30 级有 5,712 条（14.5651%），超过 40 级有 2,975 条（7.5860%），超过 50 或 60 级均为 0。这些是累计阈值，不能相加。

## 面积与存储资源

| 分类（互不重叠） | 面积（µm²） |
| --- | ---: |
| 组合逻辑，不含缓冲器/反相器 | 26,417.860 |
| 缓冲器/反相器 | 1,560.160 |
| 非组合逻辑 | 25,818.394 |
| SRAM 宏 | 11,779,552.500 |
| 总 cell 面积 | 11,833,348.914 |

总 cell 面积约为 11.833349 mm²；存储物理宏为 320 个，宏面积占 99.5454%。标准单元总面积约 53,796.414 µm²，共享模乘子模块含子层次面积为 9,674.658 µm²。DC 总 cell 面积是映射单元面积求和，不包含布局留白或布线面积。

主存储层次面积分别为：结果存储 4,713,489.842 µm²，分子存储 2,357,489.182 µm²，分母存储 2,357,486.928 µm²，前缀存储 2,357,486.536 µm²。上述子层次已包含在总面积中，不应重复相加。

## 资料提供方式

本文以转述方式提供已完成综合的配置、约束及关键结果。很抱歉，由于工艺库授权与商业工具资料使用限制，无法提供库文件、原始 DC Tcl/SDC、原始报告、映射网表及综合快照。RTL 与仿真全链路均在仓库中公开，可按[快速复现](reproduction.md)独立运行。
