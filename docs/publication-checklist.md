<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Batch Inversion Hardware Accelerator
Chisel hardware for Goldilocks batch inversion and division with interleaved contexts.
Repository: https://github.com/OpenRTL-Lab/batch-inversion-goldilocks
File: docs/publication-checklist.md

SPDX-FileCopyrightText: 2026 Goldilocks Batch Inversion contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 发布检查

[English](publication-checklist.en.md)

本文件说明发布包的产品完整性、功能与性能复现、文档及隐私检查方法；发布内容由独立设计/仿真源码、产品 SV 和双语说明构成，综合资料只提供转述摘要。

## 产品完整性

- 独立顶层 `GoldilocksBatchInversion`，产品清单包含全部 10 份依赖 SV。
- 产品清单不包含仿真环境、参考向量加载模块或验证分层文件。
- 产品不实例化 FPGA 厂商原语、不使用综合域整数除法或取模运算。
- 最大批长 65,536 产品的重新生成电路正文与附带 SV 一致。

## 可复现验证

- C 逐元素参考由 Python 独立模逆逐项核对。
- 两条产品链路均执行八类测试，包括负对照、复位恢复、背压与错误帧。
- 孤立延迟与连续稳态吞吐分别测量；忙率按有效模乘工作统计。
- 最大配置的重新生成与随包产品在独立构建中通过完整验证。

具体操作见[快速复现](reproduction.md)，测试定义和值见[性能指标](performance.md)。

## 文档与分发

- 默认 README.md 为英文，README.zh-CN.md 为独立中文全文；全部自编 Markdown 均有独立译文及双向语言链接。
- 所有作者编写的公开 Markdown 均有中文/英文版本，英文版使用英文文档链接。
- 每份 Markdown 的首段概述该文件内容；同一语言中同一文件链接名称一致。
- 采用 MIT 许可证；Release 提供完整仓库源码包。

## 隐私与公开边界

检查文件正文、文件名、Git 提交身份及附件元数据。发布包不包含本机绝对路径、缓存、执行日志、账户凭证、密钥、私有追溯摘要、签名或内部维护记录，也不包含工艺库、原始综合脚本/报告及综合快照。

数学常数和算法测试种子属于功能源码，不属于私有凭证。Git/GitHub 自身的公开提交标识和自动下载摘要属于平台正常元数据，与私有源任务追溯信息分开处理。综合结果边界见[综合摘要](synthesis-summary.md)。
