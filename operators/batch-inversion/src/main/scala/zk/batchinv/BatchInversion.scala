/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: Goldilocks Batch Inversion Hardware Accelerator
 * Chisel hardware for Goldilocks batch inversion and division with interleaved contexts.
 * Repository: https://github.com/OpenRTL-Lab/batch-inversion-goldilocks
 * File: operators/batch-inversion/src/main/scala/zk/batchinv/BatchInversion.scala
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

case class Config(maxLength: Int = 65536) {
  require(maxLength >= 32 && maxLength <= 65536 && isPow2(maxLength))
  val addressBits = log2Ceil(maxLength)
}
class Task extends Bundle { val tag=UInt(32.W); val mode=UInt(2.W); val length=UInt(32.W) }
class InputWord extends Bundle { val numerator=UInt(64.W); val denominator=UInt(64.W); val last=Bool() }
class OutputWord extends Bundle {
  val tag=UInt(32.W); val index=UInt(32.W); val value=UInt(64.W); val zero=Bool(); val last=Bool()
}
class Completion extends Bundle { val tag=UInt(32.W); val status=UInt(2.W); val length=UInt(32.W); val zeros=UInt(32.W) }
class Stored extends Bundle { val value=UInt(64.W); val zero=Bool() }

class GoldilocksBatchInversion(cfg: Config) extends Module {
  val io=IO(new Bundle {
    val enable=Input(Bool()); val task=Flipped(Decoupled(new Task))
    val input=Flipped(Decoupled(new InputWord)); val output=Decoupled(new OutputWord)
    val completion=Decoupled(new Completion); val busy=Output(Bool())
  })
  val p=BigInt("ffffffff00000001",16).U(64.W)
  object State extends ChiselEnum {
    val Idle, Forward, ForwardDrain, Combine, CombineWait, Square, SquareWait,
      PowerMul, PowerWait, Recover, RecoverWait, RecoverInverse, RecoverInverseWait,
      WaitReader, Preload, PreloadDrain, Reverse, ReverseDrain, StartOutput, Immediate = Value
  }
  import State._
  val phase=RegInit(Idle)
  val task=Reg(new Task); val index=RegInit(0.U(32.W)); val bad=RegInit(false.B)
  val zeros=RegInit(0.U(32.W)); val returned=RegInit(0.U(32.W)); val status=Reg(UInt(2.W))
  val denom=SyncReadMem(cfg.maxLength,UInt(64.W))
  val prefix=SyncReadMem(cfg.maxLength,UInt(64.W))
  val numer=SyncReadMem(cfg.maxLength,UInt(64.W))
  val results=SyncReadMem(cfg.maxLength,new Stored)
  val accum=Reg(Vec(16,UInt(64.W))); val combinedPrefix=Reg(Vec(16,UInt(64.W)))
  val inverse=Reg(Vec(16,UInt(64.W))); val scalar=Reg(UInt(64.W)); val base=Reg(UInt(64.W))
  val saved=Reg(UInt(64.W)); val scalarLane=Reg(UInt(4.W)); val bit=Reg(UInt(6.W))
  val slot=Reg(UInt(4.W)); val preload=Reg(UInt(5.W))
  val active=RegInit(VecInit(Seq.fill(16)(false.B)))
  val step=Reg(Vec(16,UInt(2.W))); val addr=Reg(Vec(16,UInt(32.W)))
  val curD=Reg(Vec(16,UInt(64.W))); val curP=Reg(Vec(16,UInt(64.W))); val curN=Reg(Vec(16,UInt(64.W)))
  val nextD=Reg(Vec(16,UInt(64.W))); val nextP=Reg(Vec(16,UInt(64.W))); val nextN=Reg(Vec(16,UInt(64.W)))
  val carrySaved=Reg(Vec(16,UInt(64.W))); val elementInverse=Reg(Vec(16,UInt(64.W)))
  val inflight=RegInit(0.U(2.W))
  val completed=Module(new Queue(new Completion,2))
  io.completion <> completed.io.deq
  completed.io.enq.valid := false.B; completed.io.enq.bits := 0.U.asTypeOf(new Completion)
  val enabled=RegNext(io.enable,false.B)
  io.task.ready := enabled && phase===Idle && inflight<2.U
  val inputQueue=Module(new Queue(new InputWord,2,pipe=false,flow=false))
  val received=RegInit(0.U(32.W))
  inputQueue.io.enq.valid := io.input.valid && phase===Forward && received<task.length
  inputQueue.io.enq.bits := io.input.bits
  io.input.ready := inputQueue.io.enq.ready && phase===Forward && received<task.length
  inputQueue.io.deq.ready := phase===Forward
  val buffered=inputQueue.io.deq
  when(io.input.fire) {received:=received+1.U}
  io.busy := inflight =/= 0.U
  when(io.task.fire =/= io.completion.fire) {
    inflight := Mux(io.task.fire,inflight+1.U,inflight-1.U)
  }
  val outputActive=RegInit(false.B); val outputTask=Reg(new Task)
  val outputStatus=Reg(UInt(2.W)); val outputZeros=Reg(UInt(32.W))
  val readIndex=RegInit(0.U(32.W))
  val queue=Module(new Queue(new OutputWord,4))
  io.output <> queue.io.deq
  val pending=RegInit(false.B)
  val credit=queue.io.count +& pending.asUInt
  val readOutput=outputActive && readIndex<outputTask.length &&
    credit < 4.U
  val readValue=results.read(readIndex(cfg.addressBits-1,0),readOutput)
  val delayedIndex=RegEnable(readIndex,readOutput)
  pending := readOutput
  queue.io.enq.valid := pending
  queue.io.enq.bits.tag := outputTask.tag
  queue.io.enq.bits.index := delayedIndex
  queue.io.enq.bits.value := readValue.value
  queue.io.enq.bits.zero := readValue.zero
  queue.io.enq.bits.last := delayedIndex===outputTask.length-1.U
  when(readOutput) { readIndex:=readIndex+1.U }
  when(pending) { assert(queue.io.enq.ready,"output read credit overflow") }
  val finishOutput=io.output.fire && io.output.bits.last
  when(finishOutput) {
    outputActive:=false.B
    completed.io.enq.valid:=true.B
    completed.io.enq.bits.tag:=outputTask.tag
    completed.io.enq.bits.status:=outputStatus
    completed.io.enq.bits.length:=outputTask.length
    completed.io.enq.bits.zeros:=outputZeros
    assert(completed.io.enq.ready,"completion capacity overflow")
  }
  val mul=Module(new BatchGoldilocksMul)
  val issue=WireDefault(false.B); val kind=WireDefault(0.U(4.W))
  val lane=WireDefault(0.U(4.W)); val address=WireDefault(0.U(32.W)); val zero=WireDefault(false.B)
  mul.io.a:=0.U;mul.io.b:=0.U
  require(BatchGoldilocksMul.Latency<16,"result must write back before context reuse")
  val valid=ShiftRegister(issue,BatchGoldilocksMul.Latency,false.B,true.B)
  val resultKind=ShiftRegister(kind,BatchGoldilocksMul.Latency);val resultLane=ShiftRegister(lane,BatchGoldilocksMul.Latency)
  val resultAddress=ShiftRegister(address,BatchGoldilocksMul.Latency);val resultZero=ShiftRegister(zero,BatchGoldilocksMul.Latency)
  val product=mul.io.product
  val readMemory=WireDefault(false.B);val memoryAddress=WireDefault(0.U(32.W))
  val memoryLane=WireDefault(0.U(4.W));val readNext=WireDefault(false.B)
  val dRead=denom.read(memoryAddress(cfg.addressBits-1,0),readMemory)
  val pRead=prefix.read(memoryAddress(cfg.addressBits-1,0),readMemory)
  val nRead=numer.read(memoryAddress(cfg.addressBits-1,0),readMemory)
  val memoryValid=RegNext(readMemory,false.B);val memorySlot=RegNext(memoryLane);val memoryNext=RegNext(readNext)
  when(memoryValid) {
    when(memoryNext) { nextD(memorySlot):=dRead;nextP(memorySlot):=pRead;nextN(memorySlot):=nRead }
    .otherwise {curD(memorySlot):=dRead;curP(memorySlot):=pRead;curN(memorySlot):=nRead}
  }
  def scalarIssue(k: Int,a:UInt,b:UInt): Unit = {
    issue:=true.B;kind:=k.U;mul.io.a:=a;mul.io.b:=b;lane:=scalarLane
  }
  def finishPower():Unit = {
    when(bit===0.U) { scalarLane:=15.U;phase:=Recover }
    .otherwise {bit:=bit-1.U;phase:=Square}
  }
  def advanceLane():Unit = {
    when(addr(slot)<16.U) {active(slot):=false.B}
    .otherwise {
      addr(slot):=addr(slot)-16.U
      curD(slot):=nextD(slot);curP(slot):=nextP(slot);curN(slot):=nextN(slot)
    }
    step(slot):=0.U
  }
  when(io.task.fire) {
    task:=io.task.bits;index:=0.U;received:=0.U;zeros:=0.U;returned:=0.U;bad:=false.B
    accum.foreach(_:=1.U);active.foreach(_:=false.B)
    status:=Mux(io.task.bits.mode>1.U || io.task.bits.length>cfg.maxLength.U,2.U,0.U)
    phase:=Mux(io.task.bits.mode>1.U || io.task.bits.length>cfg.maxLength.U || io.task.bits.length===0.U,Immediate,Forward)
  }
  switch(phase) {
    is(Forward) {
      when(buffered.fire) {
        val l=index(3,0);val d=buffered.bits.denominator
        val illegal=d>=p || (task.mode===1.U && buffered.bits.numerator>=p)
        val factor=Mux(d===0.U || d>=p,1.U,d)
        denom.write(index(cfg.addressBits-1,0),d)
        prefix.write(index(cfg.addressBits-1,0),accum(l))
        numer.write(index(cfg.addressBits-1,0),buffered.bits.numerator)
        issue:=true.B;kind:=0.U;lane:=l;address:=index;mul.io.a:=accum(l);mul.io.b:=factor
        bad:=bad || illegal || (buffered.bits.last =/= (index===task.length-1.U))
        when(d===0.U) {zeros:=zeros+1.U}
        index:=index+1.U
        when(index===task.length-1.U) {phase:=ForwardDrain}
      }
    }
    is(Combine) {
      scalarIssue(1,scalar,accum(scalarLane));combinedPrefix(scalarLane):=scalar;phase:=CombineWait
    }
    is(Square) {scalarIssue(2,scalar,scalar);phase:=SquareWait}
    is(PowerMul) {scalarIssue(3,scalar,base);phase:=PowerWait}
    is(Recover) {saved:=scalar;scalarIssue(4,scalar,accum(scalarLane));phase:=RecoverWait}
    is(RecoverInverse) {scalarIssue(5,saved,combinedPrefix(scalarLane));phase:=RecoverInverseWait}
    is(WaitReader) {when(!outputActive) {preload:=0.U;returned:=0.U;phase:=Preload}}
    is(Preload) {
      val a=task.length-1.U-preload;val l=a(3,0)
      readMemory:=true.B;memoryAddress:=a;memoryLane:=l
      addr(l):=a;step(l):=0.U;active(l):=true.B
      preload:=preload+1.U
      when(preload+1.U===Mux(task.length<16.U,task.length,16.U)) {
        slot:=(task.length-1.U)(3,0);phase:=PreloadDrain
      }
    }
    is(PreloadDrain) {phase:=Reverse}
    is(Reverse) {
      slot:=slot-1.U
      when(active(slot)) {
        issue:=true.B;lane:=slot;address:=addr(slot);zero:=curD(slot)===0.U
        when(step(slot)===0.U) {
          kind:=6.U;mul.io.a:=inverse(slot);mul.io.b:=Mux(curD(slot)===0.U,1.U,curD(slot))
          carrySaved(slot):=inverse(slot);step(slot):=1.U
          when(addr(slot)>=16.U) {
            readMemory:=true.B;readNext:=true.B;memoryAddress:=addr(slot)-16.U;memoryLane:=slot
          }
        }.elsewhen(step(slot)===1.U) {
          kind:=7.U;mul.io.a:=carrySaved(slot);mul.io.b:=curP(slot)
          when(task.mode===0.U) {advanceLane()}.otherwise {step(slot):=2.U}
        }.otherwise {
          kind:=8.U;mul.io.a:=elementInverse(slot);mul.io.b:=curN(slot);advanceLane()
        }
      }
      when(!active.asUInt.orR) {phase:=ReverseDrain}
    }
    is(StartOutput) {
      assert(!outputActive && !pending && queue.io.count===0.U)
      outputTask:=task;outputStatus:=Mux(zeros===0.U,0.U,1.U);outputZeros:=zeros
      outputActive:=true.B;readIndex:=0.U;phase:=Idle
    }
    is(Immediate) {
      when(!outputActive && !finishOutput) {
        completed.io.enq.valid:=true.B
        completed.io.enq.bits.tag:=task.tag;completed.io.enq.bits.status:=status
        completed.io.enq.bits.length:=0.U;completed.io.enq.bits.zeros:=0.U
        when(completed.io.enq.fire) {phase:=Idle}
      }
    }
  }
  when(valid) {
    switch(resultKind) {
      is(0.U) {
        accum(resultLane):=product;returned:=returned+1.U
        when(returned+1.U===task.length) {
          when(bad) {status:=3.U;phase:=Immediate}
          .otherwise {scalar:=1.U;scalarLane:=0.U;phase:=Combine}
        }
      }
      is(1.U) {
        scalar:=product
        when(scalarLane===15.U) {base:=product;bit:=62.U;phase:=Square}
        .otherwise {scalarLane:=scalarLane+1.U;phase:=Combine}
      }
      is(2.U) {
        scalar:=product
        when(bit===32.U) {finishPower()}.otherwise {phase:=PowerMul}
      }
      is(3.U) {scalar:=product;finishPower()}
      is(4.U) {scalar:=product;phase:=RecoverInverse}
      is(5.U) {
        inverse(resultLane):=product
        when(scalarLane===0.U) {phase:=WaitReader}
        .otherwise {scalarLane:=scalarLane-1.U;phase:=Recover}
      }
      is(6.U) {inverse(resultLane):=product}
      is(7.U) {elementInverse(resultLane):=product}
    }
    when((resultKind===7.U && task.mode===0.U) || resultKind===8.U) {
      val item=Wire(new Stored);item.value:=Mux(resultZero,0.U,product);item.zero:=resultZero
      results.write(resultAddress(cfg.addressBits-1,0),item)
      returned:=returned+1.U
      when(returned+1.U===task.length) {phase:=StartOutput}
    }
  }
  // 仅验证层日志，不加入可综合统计寄存器。
  chisel3.layer.block(chisel3.layers.Verification) {
    val work=RegInit(false.B);val clocks=RegInit(0.U(64.W))
    val counts=RegInit(VecInit(Seq.fill(9)(0.U(64.W))))
    val occupied=RegInit(0.U(64.W));val powerClocks=RegInit(0.U(64.W))
    val history=RegInit(0.U((BatchGoldilocksMul.Latency-1).W));history:=Cat(history(BatchGoldilocksMul.Latency-3,0),issue)
    when(io.task.fire) {work:=true.B;clocks:=0.U;occupied:=0.U;powerClocks:=0.U;counts.foreach(_:=0.U)}
    when(work) {clocks:=clocks+1.U}
    when(issue) {counts(kind):=counts(kind)+1.U}
    when(work && (issue || history.orR)) {occupied:=occupied+1.U}
    when(phase===Square || phase===SquareWait || phase===PowerMul || phase===PowerWait) {powerClocks:=powerClocks+1.U}
    when(phase===StartOutput) {
      printf(p"UNITS tag=${task.tag} clocks=${clocks} occupied=${occupied} powerClocks=${powerClocks} c0=${counts(0)} c1=${counts(1)} c2=${counts(2)} c3=${counts(3)} c4=${counts(4)} c5=${counts(5)} c6=${counts(6)} c7=${counts(7)} c8=${counts(8)}\n")
      work:=false.B
    }
    when(phase===Immediate) {work:=false.B}
  }
}
