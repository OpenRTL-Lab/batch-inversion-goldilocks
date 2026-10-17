<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Batch Inversion Hardware Accelerator
Chisel hardware for Goldilocks batch inversion and division with interleaved contexts.
Repository: https://github.com/OpenRTL-Lab/batch-inversion-goldilocks
File: docs/mathematical-model.en.md

SPDX-FileCopyrightText: 2026 Goldilocks Batch Inversion contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Mathematical model

[简体中文](mathematical-model.md)

This document precisely defines the field, encodings, elementwise results, zero-denominator handling, and exceptional batch behavior for Goldilocks batch inversion and batch division, which share one standalone hardware operator.

## Field and parameters

All arithmetic takes place in $\mathbb{F}_p$, where

$$
p=2^{64}-2^{32}+1=18446744069414584321.
$$

Field elements use canonical 64-bit unsigned encodings satisfying $0\le x<p$. The generated maximum batch length $L_{\max}$ is a power of two between 32 and 65,536. The bundled SV uses $L_{\max}=65536$. The number of interleaved contexts is fixed at 16.

A task specifies $0\le L\le L_{\max}$, a 32-bit unsigned tag, and mode $m\in\{0,1\}$. Element indices are $i\in\{0,\ldots,L-1\}$. Each input word carries numerator $a_i$, denominator $d_i$, and `last`. In inversion mode the numerator is ignored.

## Output definition

Define the zero-denominator flag $z_i$ and result $y_i$ by

$$
z_i=\begin{cases}1,&d_i=0,\\0,&d_i\ne0,\end{cases}
\qquad
y_i=\begin{cases}
0,&d_i=0,\\
d_i^{p-2}\bmod p,&d_i\ne0\ \land\ m=0,\\
a_i d_i^{p-2}\bmod p,&d_i\ne0\ \land\ m=1.
\end{cases}
$$

For a nonzero denominator, inversion satisfies $d_i y_i=1$ and division satisfies $d_i y_i=a_i$, with equalities in $\mathbb{F}_p$. A zero denominator returns value 0 with `zero=1`; this convention does not assign a multiplicative inverse to zero.

Results retain natural input order, with `index=i`, an unchanged `tag`, and `last=1` only when $i=L-1$. The completion message reports `zeros` equal to $\sum_i z_i$. An empty batch produces no element results and one successful completion.

## Batch algorithm

Let $b_i=1$ when $d_i=0$, and $b_i=d_i$ otherwise, so every $b_i$ is nonzero. The single-chain formulation is

$$
P_{-1}=1,\qquad P_i=P_{i-1}b_i,\qquad R=P_{L-1}^{p-2}.
$$

Scan backward for $i=L-1,\ldots,0$, computing $u_i=R P_{i-1}$ and then updating $R\leftarrow R b_i$. This yields $u_i=b_i^{-1}$. Restore the zero-denominator convention using $z_i$ and select either $u_i$ or $a_i u_i$ according to the mode.

This implementation partitions indices into 16 prefix chains using $i\bmod16$, combines their products, performs one Fermat inversion, recovers each chain's inverse, and scans backward. Empty chains have product 1. Results are mathematically identical to the single-chain formulation and preserve external ordering.

## Exceptions and protocol

A mode greater than 1 or length exceeding $L_{\max}$ returns `BAD_PARAM`, accepts no elements, and produces no element results. Canonical encoding is required for denominators in inversion mode and for numerators and denominators in division mode; a violation returns `BAD_FRAME`. `last` must mark only the $L$th input element, or the batch is also `BAD_FRAME`.

A malformed frame still consumes all $L$ declared elements; an early `last` does not truncate the batch. Malformed batches produce no element results and report `length=0` and `zeros=0` in their completion messages. Zero denominators are reportable normal cases, not malformed frames. Status codes, reset, and backpressure requirements are specified in [Native interface](interface.en.md).
