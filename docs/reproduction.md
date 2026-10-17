<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Batch Inversion Hardware Accelerator
Chisel hardware for Goldilocks batch inversion and division with interleaved contexts.
Repository: https://github.com/OpenRTL-Lab/batch-inversion-goldilocks
File: docs/reproduction.md

SPDX-FileCopyrightText: 2026 Goldilocks Batch Inversion contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 快速复现

[English](reproduction.en.md)

本文件提供从工具检查、独立参考核对、Chisel 生成、产品 lint 到 RTL 功能与性能统计的完整复现步骤；默认同时验证重新生成和随仓库附带的最大批长产品 SV。

## 获取版本快照

[Release v1.1.0](https://github.com/OpenRTL-Lab/batch-inversion-goldilocks/releases/tag/v1.1.0)

```bash
git clone --branch v1.1.0 --depth 1 https://github.com/OpenRTL-Lab/batch-inversion-goldilocks.git
cd batch-inversion-goldilocks
```

版本标签标识包含来源声明和文档的当前源码树；Release 下载提供本版本硬件源码与随包 RTL。源码包与在线目录的注释及文档可能不同。RTL 集成使用产品文件清单，不把验证环境作为产品电路；支持配置、验证范围和测量条件见验证及综合文档。

## 准备

按[工具链版本](toolchain.md)安装开源工具，并在仓库根目录运行下列命令。Python 脚本只使用标准库。确认输出目录不存在且磁盘可写；脚本仅创建 `build/` 下的相对目录，不自动删除或覆盖历史结果。初次 Mill 构建会解析依赖，后续可复用用户环境中的缓存。

`--max-length` 设置静态批长容量（32..65,536之间的2的幂），不是任务数量。`--tasks`（4..256）设置每种操作的连续性能任务数，不改变固定16个内部扫描上下文。`--jobs` 只控制仿真器编译并行度。

## 完整验证

下面命令生成 C 参考向量并逐项用 Python 模逆互检，生成 Chisel 验证环境和产品电路，检查产品闭包，再分别用重新生成与附带 SV 构建仿真器；每条链路执行八类功能/性能测试：

```sh
python3 tools/verify.py --out build/verify --max-length 65536 --tasks 16 --jobs 8
```

成功时打印“验证通过”，`build/verify/completion.json` 中 `status=0`、`bundled_product_matches=true`，并有 16 个测试结果，即两条产品链路各八项。这里 `tasks=16` 是每种连续性能测试的任务数量，不改变 16 个硬件交织上下文。

如希望先用小配置快速检查安装与链路，下面命令将最大批长配置为 32，运行同样八类测试；小配置不替换随仓库的 65,536 产品：

```sh
python3 tools/verify.py --out build/verify-small --max-length 32 --tasks 16 --jobs 8
```

## 单独生成电路

只需要生成电路、参考向量和文件清单时，运行下列命令；它不构建 Verilator 仿真器：

```sh
python3 tools/emit_sv.py --out build/emit --max-length 65536 --tasks 16
```

`--max-length` 支持 32 至 65,536 之间的 2 的幂。`--tasks` 支持 4 至 256，仅决定验证环境的连续任务数。独立产品顶层为 `GoldilocksBatchInversion`，生成清单在 `build/emit/rtl/product.f`；`simulation.f` 额外包含环境、向量存储和验证分层文件。

要独立 lint 随仓库产品电路，以下命令从相对产品文件清单加载全部依赖，不加载仿真环境：

```sh
verilator --lint-only --assert --top-module GoldilocksBatchInversion -f rtl/v1.1.0/gl64-ctx16-l65536/files.f
```

## 测试与成功判据

| 测试名 | 内容与判据 |
| --- | --- |
| `ideal-inverse` / `ideal-division` | 连续满批次；所有值、标签、索引、`last`、完成状态正确，并输出稳态统计 |
| `varied` | 空批、短批、边界元素、零分母及背压；检查 16 个完成及所有结果 |
| `reset` | 计算中及输出中复位；检测两次 `RESET_ABORT`，复位后 16 个正式任务通过 |
| `negative` | 刻意破坏一个参考结果；必须 `pass=0,mismatches=1`，进程退出码为 1 |
| `errors` | 六个非法参数/错误帧任务；完成正确且无元素结果 |
| `single-inverse` / `single-division` | 单批、无前序在途任务，测孤立延迟与首/末结果时钟 |

负对照的失败是预期并由外层脚本核对；它不是整个验证失败。其他项要求 `pass=1,mismatches=0`。任一产品链路异常会使脚本返回非零退出码。

## 输出与性能读取

`completion.json` 汇总配置、测试值和性能。连续测试的 `performance` 内 `sustained` 提供窗口、任务间隔、吞吐率、模乘发射及有效流水线占用；孤立测试的 `task_latencies_clocks` 提供单任务延迟。`generated-*` 与 `bundled-*` 分别标记两种产品，每种性能结果必须完全一致。

每一步保存独立日志。参考向量见 `vectors/`，生成产品见 `rtl/`，仿真构建见 `obj-generated/` 与 `obj-bundled/`。验证层输出 `ACCEPT`、`FIRST_OUTPUT`、`LAST_OUTPUT`、`COMPLETE`、`UNITS` 等周期记录；定义及发布值见[性能指标](performance.md)。

失败时先查对应步骤日志，修正工具/环境后使用新的 `--out` 目录重试。例如 `build/verify-r2`；不覆盖失败证据。参考模型、功能验证与周期测量不依赖 DC、工艺库或实际硬件。
