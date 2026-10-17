<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Batch Inversion Hardware Accelerator
Chisel hardware for Goldilocks batch inversion and division with interleaved contexts.
Repository: https://github.com/OpenRTL-Lab/batch-inversion-goldilocks
File: docs/mathematical-model.md

SPDX-FileCopyrightText: 2026 Goldilocks Batch Inversion contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 数学模型

[English](mathematical-model.en.md)

本文件严格定义 Goldilocks 批量求逆和批量除法的域、输入编码、逐元素输出、零分母处理及批次异常语义；两种操作共享同一个独立硬件算子。

## 域与参数

所有运算位于 $\mathbb{F}_p$，其中

$$
p=2^{64}-2^{32}+1=18446744069414584321.
$$

域元素采用 64 位无符号规范整数编码 $0\le x<p$。最大批长 $L_{\max}$ 在生成电路时选择，为 32 至 65,536 之间的 2 的幂；附带 SV 取 $L_{\max}=65536$。交织上下文数固定为 16。

一次任务的批长为 $0\le L\le L_{\max}$，标签为 32 位无符号整数，模式 $m\in\{0,1\}$。元素索引 $i\in\{0,\ldots,L-1\}$。每条实际握手的输入记录携带分子 $a_i$、分母 $d_i$ 及 `last`；求逆模式忽略分子。

## 输出定义

定义零分母指示 $z_i$ 与输出 $y_i$：

$$
z_i=\begin{cases}1,&d_i=0,\\0,&d_i\ne0,\end{cases}
\qquad
y_i=\begin{cases}
0,&d_i=0,\\
d_i^{p-2}\bmod p,&d_i\ne0\ \land\ m=0,\\
a_i d_i^{p-2}\bmod p,&d_i\ne0\ \land\ m=1.
\end{cases}
$$

因此，对非零分母，求逆满足 $d_i y_i=1$，除法满足 $d_i y_i=a_i$；等式均在 $\mathbb{F}_p$ 中成立。零分母返回值 0 及 `zero=1`，并不赋予零元素乘法逆元。

结果按输入自然顺序输出，`index=i`、`tag` 原样返回，只有 $i=L-1$ 的结果置 `last=1`。完成消息的 `zeros` 为 $\sum_i z_i$。空批次不产生元素结果，只产生成功完成消息。

## 批量算法

令 $b_i=1$（当 $d_i=0$），否则 $b_i=d_i$。所有 $b_i$ 非零。单链形式可写为

$$
P_{-1}=1,\qquad P_i=P_{i-1}b_i,\qquad R=P_{L-1}^{p-2}.
$$

反向扫描 $i=L-1,\ldots,0$，依次计算 $u_i=R P_{i-1}$ 并更新 $R\leftarrow R b_i$，便有 $u_i=b_i^{-1}$。输出时根据 $z_i$ 恢复零分母约定，并按模式选择 $u_i$ 或 $a_i u_i$。

本实现将索引按 $i\bmod16$ 分为 16 条前缀链，合并链乘积后执行一次 Fermat 求逆，再恢复各链的逆元并反向扫描。空链乘积为 1，数学结果与上述单链形式一致；外部元素顺序不变。

## 异常与协议

`mode` 大于 1 或批长超过 $L_{\max}$：返回 `BAD_PARAM`，不接收元素，不产生元素结果。规范输入要求求逆模式的分母、除法模式的分子与分母均小于 $p$；不满足时返回 `BAD_FRAME`。`last` 必须只标记第 $L$ 个输入元素，否则也为 `BAD_FRAME`。

错误帧仍接收声明的全部 $L$ 个元素；提前出现的 `last` 不能截断批次。错误批次不输出元素，完成消息的 `length` 与 `zeros` 均为 0。零分母属于正常可报告情况，不是错误帧。状态编码与复位、背压要求见[原生接口](interface.md)。
