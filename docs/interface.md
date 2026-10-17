<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Batch Inversion Hardware Accelerator
Chisel hardware for Goldilocks batch inversion and division with interleaved contexts.
Repository: https://github.com/OpenRTL-Lab/batch-inversion-goldilocks
File: docs/interface.md

SPDX-FileCopyrightText: 2026 Goldilocks Batch Inversion contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 原生接口

[English](interface.en.md)

本文件说明独立顶层 `GoldilocksBatchInversion` 的任务、输入、结果和完成通道，明确字段宽度、握手、顺序、零分母和异常处理，便于接入用户自己的控制面与数据面。

域元素采用无符号规范编码 0 ≤ x < p，p = 2^64 - 2^32 + 1，不是 Montgomery 残数。模式0返回分母逆元，模式1返回分子/分母的商。生成容量是32至65,536之间的2的幂（随包65,536）；任务实际 length 可为0至容量之间的任意整数，不要求是2的幂。

## 通用时序

所有接口使用同一个 `clock`，在上升沿采样；`reset` 为同步、高电平有效。各通道在 `valid && ready` 时完成一次传输。发送方在阻塞时保持 `valid` 及载荷不变，直到握手。表中输入/输出方向均相对于加速器。

| 通道/信号 | 方向 | 载荷与语义 |
| --- | --- | --- |
| `io_enable` | 输入 | 1 位；前一拍采样值控制新任务接收，不暂停已接收任务 |
| `io_busy` | 输出 | 1 位；有尚未消费完成消息的已接收任务时为 1 |
| `io_task` | 输入 | `tag` 32 位、`mode` 2 位、`length` 32 位 |
| `io_input` | 输入 | `numerator` 64 位、`denominator` 64 位、`last` 1 位 |
| `io_output` | 输出 | `tag` 32 位、`index` 32 位、`value` 64 位、`zero`/`last` 各 1 位 |
| `io_completion` | 输出 | `tag` 32 位、`status` 2 位、`length`/`zeros` 各 32 位 |

SV 展开名称如 `io_task_valid`、`io_task_ready`、`io_task_bits_length`，其他通道遵循同一规则。`clock` 和 `reset` 不带 `io_` 前缀。

## 任务与数据

`mode=0` 求逆，`mode=1` 除法。单批长度为 0 至生成配置最大批长。按任务接收顺序供给整批数据，不交织不同批次的输入；第 `length` 个元素置 `last=1`。输入、输出均采用自然顺序，结果带原标签和从 0 开始的索引。

最多允许两个已接收但尚未消费完成消息的任务。调用方为这些在途任务提供不同标签。输出先于对应完成消息产生；批次和完成消息保持任务顺序。输入、输出及完成通道均允许背压。完成消息的实际握手时间计入任务延迟。

## 状态编码

| `status` | 名称 | 行为 |
| --- | --- | --- |
| 0 | `SUCCESS` | 成功；空批次直接完成 |
| 1 | `ZERO_DENOMINATOR` | 有零分母；仍输出完整批次，相关元素 `zero=1,value=0` |
| 2 | `BAD_PARAM` | 非法模式或超过最大批长；不接收数据、无结果 |
| 3 | `BAD_FRAME` | 非规范元素或错误的 `last`；接收完整声明批长、无结果 |

正常完成时 `length` 为任务批长，`zeros` 为零分母数量。`BAD_PARAM` 与 `BAD_FRAME` 的 `length=0,zeros=0`。求逆模式忽略分子，包括其编码检查；除法模式检查分子和分母。错误帧不会因提前 `last` 而截断。数学定义见[数学模型](mathematical-model.md)。

## 复位与存储

复位撤销全部在途任务、输出和完成状态；调用方需重新提交任务。存储内容不要求复位清零，复位后不会返回先前任务的成功完成。产品使用同步存储抽象与单时钟控制，存储映射方式见[架构设计](architecture.md)。
