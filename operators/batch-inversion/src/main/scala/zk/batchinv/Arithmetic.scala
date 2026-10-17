/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: Goldilocks Batch Inversion Hardware Accelerator
 * Chisel hardware for Goldilocks batch inversion and division with interleaved contexts.
 * Repository: https://github.com/OpenRTL-Lab/batch-inversion-goldilocks
 * File: operators/batch-inversion/src/main/scala/zk/batchinv/Arithmetic.scala
 *
 * SPDX-FileCopyrightText: 2026 Goldilocks Batch Inversion contributors
 * Copyright applies to original project contributions only.
 * SPDX-License-Identifier: MIT
 * See LICENSE and applicable third-party notices.
 * OpenRTL-Header-End
 */

package zk.batchinv

import chisel3._
import chisel3.util._


/** 可移植的倍增前缀进位；每一层合并2^k位，避免线性宽进位链。
  * 结果显式截断到 width 位；调用者为需要进位的运算多保留一位。
  */
object BatchPrefixArithmetic {
  def add(a: UInt, b: UInt, width: Int, carryIn: Bool = false.B): UInt = {
    require(width >= 2)
    val left = Wire(UInt(width.W)); left := a
    val right = Wire(UInt(width.W)); right := b
    val bits = left ^ right
    var propagate: UInt = bits
    var generate: UInt = left & right
    var distance = 1
    while (distance < width) {
      val shiftedGenerate = (generate << distance)(width - 1, 0)
      val shiftedPropagate = Cat(propagate(width - distance - 1, 0),
        ((BigInt(1) << distance) - 1).U(distance.W))
      val nextGenerate = generate | (propagate & shiftedGenerate)
      val nextPropagate = propagate & shiftedPropagate
      generate = nextGenerate
      propagate = nextPropagate
      distance *= 2
    }
    val carries = ((generate << 1) | ((propagate << 1) & Fill(width + 1, carryIn)) |
      carryIn.asUInt)(width - 1, 0)
    bits ^ carries
  }
  def subtract(a: UInt, b: UInt, width: Int): UInt = {
    val right = Wire(UInt(width.W)); right := b
    add(a, ~right, width, true.B)
  }
}

object BatchGoldilocksMul {
  val Latency = 15
}

/** 标准单元模乘，固定15拍、II=1；五处宽算术在bit31/32真实寄存切分。
  */
class BatchGoldilocksMul extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(64.W))
    val b = Input(UInt(64.W))
    val product = Output(UInt(64.W))
  })
  private val p = BigInt("ffffffff00000001", 16).U(66.W)
  // 返回第二拍的组合值；调用者必须登记输出，低半carry不可跨两拍重算。
  private def splitSum(a: UInt, b: UInt, width: Int, cin: Bool, name: String): UInt = {
    val x = Wire(UInt(width.W)); x := a
    val y = Wire(UInt(width.W)); y := b
    val lo = RegNext(BatchPrefixArithmetic.add(Cat(0.U(1.W), x(31, 0)),
      Cat(0.U(1.W), y(31, 0)), 33, cin)).suggestName(name + "LowCarry")
    val hiA = RegNext(x(width - 1, 32)).suggestName(name + "HighA")
    val hiB = RegNext(y(width - 1, 32)).suggestName(name + "HighB")
    Cat(BatchPrefixArithmetic.add(hiA, hiB, width - 32, lo(32)), lo(31, 0))
  }
  private def sum2(a: UInt, b: UInt, width: Int, name: String): UInt =
    RegNext(splitSum(a, b, width, false.B, name)).suggestName(name)
  private def subtract2(a: UInt, b: UInt, width: Int, name: String): UInt = {
    val y = Wire(UInt(width.W)); y := b
    RegNext(splitSum(a, ~y, width, true.B, name)).suggestName(name)
  }
  private def conditionalSubtract2(a: UInt, name: String): UInt = {
    val reduced = splitSum(a, ~p, 66, true.B, name)
    val original = RegNext(a).suggestName(name + "Original")
    val select = RegNext(a >= p).suggestName(name + "Select")
    RegNext(Mux(select, reduced, original)).suggestName(name)
  }
  private val operandA = RegNext(io.a)
  private val operandB = RegNext(io.b)
  private val p0 = RegNext(operandA(31, 0) * operandB(31, 0))
  private val p1 = RegNext(operandA(31, 0) * operandB(63, 32))
  private val p2 = RegNext(operandA(63, 32) * operandB(31, 0))
  private val p3 = RegNext(operandA(63, 32) * operandB(63, 32))
  private val outerLow = RegNext(p0)
  private val cross = RegNext(BatchPrefixArithmetic.add(p1, p2, 65))
  private val outerHigh = RegNext(p3)
  private val low = RegNext(BatchPrefixArithmetic.add(outerLow, Cat(cross(31, 0), 0.U(32.W)), 65))
  private val crossHigh = RegNext(cross(64, 32))
  private val highDelayed = RegNext(outerHigh)
  private val productLow = ShiftRegister(low(63, 0), 2)
  private val productHigh = RegNext(splitSum(
    Cat(0.U(1.W), highDelayed), Cat(0.U(32.W), crossHigh), 65, low(64), "productHigh"))
  private val t0 = RegNext(productLow(31, 0))
  private val highSum = RegNext(BatchPrefixArithmetic.add(productLow(63, 32), productHigh(31, 0), 33))
  private val subtrahend = RegNext(BatchPrefixArithmetic.add(productHigh(31, 0), productHigh(63, 32), 33))
  private val positive = sum2(Cat(0.U(1.W), highSum, t0), p, 66, "positive")
  private val delayedSub = ShiftRegister(subtrahend, 2)
  private val candidate = subtract2(positive, Cat(0.U(33.W), delayedSub), 66, "candidate")
  private val once = conditionalSubtract2(candidate, "once")
  private val twice = conditionalSubtract2(once, "twice")
  io.product := twice(63, 0)
}
