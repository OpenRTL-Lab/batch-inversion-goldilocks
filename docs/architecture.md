<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Batch Inversion Hardware Accelerator
Chisel hardware for Goldilocks batch inversion and division with interleaved contexts.
Repository: https://github.com/OpenRTL-Lab/batch-inversion-goldilocks
File: docs/architecture.md

SPDX-FileCopyrightText: 2026 Goldilocks Batch Inversion contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 架构设计

[English](architecture.en.md)

本加速器以一次整批求逆替代逐元素求逆，用 16 条交织前缀链保持共享模乘流水线连续工作，并将前一批输出与下一批计算重叠；求逆和除法共用数据通路，最大批长可在生成时配置。

L 是一个任务的分母数，Lmax 是生成时固定的批长容量，随包为65,536。输入输出为无符号规范整数 0 ≤ x < p，p = 2^64 - 2^32 + 1。16个上下文保存按 i mod 16 分配的前缀链状态，共用一个模乘器，不是独立任务或外部通道。求逆返回 1/d_i，除法返回 a_i/d_i；零分母规则见[数学模型](mathematical-model.md)。

## 数据通路

处理顺序为：前向扫描并保存前缀 → 合并 16 条链乘积 → 单次 Fermat 求逆 → 恢复链逆元 → 反向扫描恢复元素 → 自然顺序输出。除法模式在恢复元素逆元后追加与分子的模乘。算法定义见[数学模型](mathematical-model.md)。

前向和反向阶段按索引模 16 交织上下文。共享 `BatchGoldilocksMul` 延迟为 15 拍，发射间隔为 1 拍；返回值可在同一上下文再次调度前写回，避免依赖链使长批次的流水线空转。底层由四个 32×32 部分乘积、寄存后的合并与 Goldilocks 模约减组成；宽加法进位采用分块/前缀结构，并在约减链中设置寄存边界。

## 计算与任务重叠

控制器处理一个计算批次，最多容纳两个在途任务。上一批结果从结果存储器顺序输出时，下一批可执行前向扫描、链乘积合并和求逆；下一批反向写结果前等待上一批结果消费完毕，避免覆盖。输入队列深度为 2，输出队列深度为 4，完成队列深度为 2。

输出存储读取使用队列空位与未返回读取的合并信用计数，先保留位置再发起同步读取。该机制把外部 `ready` 与大存储器的读使能解耦，同时保证背压时不丢数据。任务之间的重叠使稳态间隔小于孤立任务延迟；实际数值见[性能指标](performance.md)。

## 存储与生成

| 存储 | 位宽 | 深度 | 用途 |
| --- | --- | --- | --- |
| `denom` | 64 | 最大批长 | 分母及反向更新 |
| `prefix` | 64 | 最大批长 | 元素此前的链前缀 |
| `numer` | 64 | 最大批长 | 除法分子 |
| `results` | 65 | 最大批长 | 结果及零分母标志 |

上述四个主存储共占 $257L_{\max}$ 位；最大批长 65,536 时为 16,842,752 位，即 2.0078125 MiB，不含小队列及寄存器。产品 SV 使用通用同步存储模块，不实例化 FPGA 厂商原语；主存储具有独立读写端口、同步一拍读延迟，设计避免同地址读写冲突。集成时可保持行为模型，也可映射为满足相同端口、延迟及使能语义的目标 SRAM。

`Config(maxLength)` 支持 32 至 65,536 的 2 的幂，生成后该值为静态硬件尺寸；任务实际长度动态指定。16 个上下文、15 拍模乘和接口字段宽度在本实现中固定。产品顶层为 `GoldilocksBatchInversion`；仿真环境、参考向量加载与统计断言分层文件不在产品文件清单中。

## 设计与验证分离

产品清单只包含顶层及其依赖的 10 份 SV。验证环境负责激励、逐元素对照、协议断言和时钟统计，生成脚本自动构建产品依赖闭包。验证脚本对重新生成与附带 SV 的电路正文进行比较，并分别运行完整测试，详见[快速复现](reproduction.md)。
