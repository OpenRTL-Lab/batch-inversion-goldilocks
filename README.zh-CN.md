<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Batch Inversion Hardware Accelerator
Chisel hardware for Goldilocks batch inversion and division with interleaved contexts.
Repository: https://github.com/OpenRTL-Lab/batch-inversion-goldilocks
File: README.zh-CN.md

SPDX-FileCopyrightText: 2026 Goldilocks Batch Inversion contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# batch-inversion-goldilocks 硬件加速器

[English](README.md)

本仓库提供使用 Chisel 开发的 Goldilocks 有限域批量求逆与批量除法独立硬件加速器。它可通过原生接口集成到 ASIC 或 FPGA，为零知识证明中的多项式分母求逆、批量归一化及其他需要成批有限域逆元或商的计算提供加速。

采用 MIT 许可证，允许免费商用、修改和再分发，须保留版权及许可声明；第三方工具说明见[第三方声明](THIRD_PARTY_NOTICES.md)。

[Release v1.1.0](https://github.com/OpenRTL-Lab/batch-inversion-goldilocks/releases/tag/v1.1.0) · [原生接口](docs/interface.md) · [快速复现](docs/reproduction.md) · [性能指标](docs/performance.md) · [综合摘要](docs/synthesis-summary.md)

## 计算与任务单位

一个任务是一批 L 个分母 d_i，可另带分子 a_i；求逆按输入索引顺序返回 1/d_i，除法返回 a_i/d_i。域元素是 p = 2^64 - 2^32 + 1 下的无符号 64 位规范值，满足 0 ≤ x < p。这里 Montgomery 式批逆指乘积与逆向恢复算法，外部值不是 Montgomery 残数编码。零分母返回零和逐元素标记，并使用区别于普通成功的任务状态。

16 个扫描上下文是按元素索引模 16 调度、共用一条模乘流水线的内部前缀乘积链，不是 16 个任务或外部输入通道。最多两个完整任务在途：前批输出可与后批计算重叠，但两批输入记录不可交错。这种重叠使稳态任务间隔可以短于孤立任务延迟。

## 配置速览

| 集成关注点 | 已发布硬件 |
| --- | --- |
| 计算 | Montgomery 式批量求逆及逐元素分子/分母除法 |
| 有限域 | Goldilocks p = 2^64 - 2^32 + 1；64 位规范域元素 |
| 随包配置 | 16 个交织扫描上下文，最大批长 65,536；顶层 `GoldilocksBatchInversion` |
| 原生边界 | 任务、有序分子/分母流、带索引的值/零标记及完成 |
| 任务重叠 | 最多两个任务在途；完整批次的输入不可交错 |

## 快速开始

从 [Release v1.1.0](https://github.com/OpenRTL-Lab/batch-inversion-goldilocks/releases/tag/v1.1.0) 下载源码包，或克隆当前发布快照：

```bash
git clone --branch v1.1.0 --depth 1 https://github.com/OpenRTL-Lab/batch-inversion-goldilocks.git
cd batch-inversion-goldilocks
```

按 [工具链版本](docs/toolchain.md) 安装指定版本的依赖，然后在仓库根目录使用新的输出目录运行：

```bash
python3 tools/emit_sv.py --out build/emit --max-length 65536 --tasks 16
```

此生成命令不是完整 RTL 回归。文档规定的验证入口为：

```bash
python3 tools/verify.py --out build/verify --max-length 65536 --tasks 16 --jobs 8
```

预期结果、负控含义及短功能测试与完整性能测试的区别见 [快速复现](docs/reproduction.md)。

## 验证与集成边界

RTL 测试前，独立 C 和 Python 参考逐元素核对所有向量。生成与随包 SV 分别执行文档规定的八类测试：理想求逆/除法、带停顿的变长与零分母、复位、故意修改参考值、错误任务及孤立求逆/除法。最大批长 32 的命令只是快速安装检查，不能替代已发布的 65,536 元素配置。

零分母输出值零和逐元素 zero 标记，以 ZERO_DENOMINATOR 完成，而不是普通 SUCCESS。空批、BAD_PARAM 和 BAD_FRAME 各有定义。在途任务须有不同标签；每批按顺序供给，等待真实完成。16 个上下文是硬件调度选择，不是 16 条独立外部请求通道。本单元不实现证明关系或 BN254 算术。

### 正确理解性能

理想接口下，65,536 元素批次的孤立求逆/除法延迟为 264,965/330,501 周期；由于输出与下一批计算重叠，实测稳态完成间隔为 199,427/264,963 周期。共享模乘有效发射忙率为有效乘法发射周期除以稳态窗口周期，即 98.6732%/99.0014%；不是各有效流水级相加，也不是独立除法器的数量。

上述周期数来自理想原生接口下的 RTL 测量，按 MHz 换算的时间和速率不是板级或硅后实测。Design Compiler 结果只适用于文档所列配置、库角和约束，不代表布局布线签核或系统吞吐率；条件见 [性能指标](docs/performance.md) 和 [综合摘要](docs/synthesis-summary.md)。

## 仓库内容

- [Chisel 设计](operators/batch-inversion/src/main/scala/zk/batchinv/)：16 路交织扫描与共享流水线模乘。
- [生成入口](operators/batch-inversion/src/tool/scala/zk/batchinv/)和[生成脚本](tools/emit_sv.py)：可配置最大批长。
- [生成好的 SV](rtl/v1.1.0/gl64-ctx16-l65536/)及[产品文件清单](rtl/v1.1.0/gl64-ctx16-l65536/files.f)：最大批长 65,536 的独立产品电路。
- [C 参考模型](operators/batch-inversion/cmodel/reference.c)、[Python 参考互检](tools/check_reference.py)、[仿真驱动](operators/batch-inversion/sim/driver.cpp)及[验证脚本](tools/verify.py)：可复现功能与性能验证。
- [原生接口](docs/interface.md)、[架构设计](docs/architecture.md)和[工具链版本](docs/toolchain.md)：集成与使用说明。

## 应用场景

需要对 Goldilocks 域中的一批非零分母求逆，或逐项计算分子与分母之商时，可以使用本模块。例如：证明器中的多项式有理表达式求值、查表或置换关系中的分母批处理，以及协议所需的域元素批量归一化。这些是集成示例，并不限制模块的其他用途。

集成方通过任务通道指定批长和计算模式，按自然顺序供给域元素并接收结果；零分母单独标记，任务标签原样返回。控制面与数据面均采用原生 ready/valid 握手，便于与存储控制器、其他算子或用户自己的调度器连接。

## 性能指标

65,536 元素批长下，批量求逆/除法的孤立任务延迟为 264,965/330,501 拍，稳态任务间隔为 199,427/264,963 拍；按 500 MHz 换算，吞吐率约为 1.6431 亿/1.2367 亿元素每秒。指标定义、测法与计算忙率见[性能指标](docs/performance.md)。

## 数学模型

电路在模数 $p=2^{64}-2^{32}+1$ 的有限域中逐项返回分母逆元或分子与分母的商，并定义零分母、空批次及非法输入的接口行为；严格模型见[数学模型](docs/mathematical-model.md)。

## 快速复现

提供从独立 C/Python 参考核对、Chisel 生成到重新生成与附带 SV 双链路仿真、性能统计的完整流程；安装与运行方法见[快速复现](docs/reproduction.md)。

## 综合结果

本配置在 TSMC 28 nm HPC+、SSG 0.81 V −40°C 下通过 500 MHz、10% setup 时钟不确定度约束，setup 违例为 0；39,217 条统计路径的逻辑级数均小于 50，最大 43。条件及面积结果见[综合摘要](docs/synthesis-summary.md)。

## 公开边界

公开内容包括设计与生成代码、产品 SV、参考模型、验证与性能测试代码、工具版本和综合结果转述。很抱歉，由于工艺库授权及商业工具资料的使用限制，无法提供工艺库、原始 DC 脚本和报告、映射网表或综合快照。

项目交付独立算子与原生接口，系统级协议、SoC 总线或板级连接由使用者按自己的系统需求集成。发布内容检查方法见[发布检查](docs/publication-checklist.md)。

## OpenRTL与定制集成

通过[OpenRTL组织主页](https://github.com/OpenRTL-Lab)、[ZK加速器集合](https://github.com/OpenRTL-Lab/zk-accelerators)及[OpenRTL网站](https://openrtl.top/)查找相关模块与合作联系入口。需要定制数据通路、接口或目标平台适配时可联系维护者；MIT商用权不附加购买服务的要求，不要在公开Issue提交保密规格。

## 发布源码与集成

版本标签标识包含来源声明和文档的当前源码树；Release 下载提供本版本硬件源码与随包 RTL。源码包与在线目录的注释及文档可能不同。RTL 集成使用产品文件清单，不把验证环境作为产品电路；支持配置、验证范围和测量条件见验证及综合文档。
