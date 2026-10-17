/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: Goldilocks Batch Inversion Hardware Accelerator
 * Chisel hardware for Goldilocks batch inversion and division with interleaved contexts.
 * Repository: https://github.com/OpenRTL-Lab/batch-inversion-goldilocks
 * File: operators/batch-inversion/src/main/scala/zk/batchinv/Environment.scala
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
import chisel3.util.experimental.loadMemoryFromFileInline

/** 所有原生通道的驱动、计分和时间戳在Chisel ENV内，C++不能碰DUT数据。 */
class BatchInversionSimTop(cfg:Config, vectors:String, repeats:Int) extends Module {
  require(repeats>=4 && repeats<=256)
  val trigger=IO(Input(Bool()));val testMode=IO(Input(UInt(3.W)))
  val done=IO(Output(Bool()));val pass=IO(Output(Bool()));val mismatchCount=IO(Output(UInt(32.W)))
  val checked=IO(Output(UInt(64.W)));val cycles=IO(Output(UInt(64.W)));val completedCount=IO(Output(UInt(32.W)))
  val running=RegInit(false.B);val finished=RegInit(false.B);val tick=RegInit(0.U(64.W))
  val errors=RegInit(0.U(32.W));val words=RegInit(0.U(64.W));val completions=RegInit(0.U(32.W))
  val send=RegInit(0.U(32.W));val inputIndex=RegInit(0.U(32.W));val sending=RegInit(false.B)
  val outputIndex=RegInit(0.U(32.W));val outputTag=RegInit(0.U(32.W))
  val resetHold=RegInit(0.U(3.W));val resetStage=RegInit(0.U(2.W))
  val aborting=testMode===3.U && resetStage<2.U
  val mode=RegInit(0.U(3.W));when(trigger && !running && !finished) {running:=true.B;mode:=testMode}
  when(running) {tick:=tick+1.U}
  val varied=mode===2.U || mode===3.U
  val total=Mux(mode===0.U || mode===1.U,repeats.U,Mux(mode===6.U || mode===7.U,1.U,Mux(mode===5.U,6.U,16.U)))
  def lengthFor(id:UInt):UInt = {
    val lengths=VecInit(Seq(0,1,15,16,17,31,cfg.maxLength-1,cfg.maxLength).map(_.U(32.W)))
    Mux(mode===5.U,Mux(id===0.U,0.U,Mux(id===1.U,(cfg.maxLength+1).U,4.U)),
      Mux(varied,lengths(id(2,0)),cfg.maxLength.U))
  }
  def directionFor(id:UInt):UInt = Mux(mode===5.U,Mux(id===0.U,2.U,Mux(id===2.U || id===5.U,1.U,0.U)),
    Mux(varied,id(0).asUInt,Mux(mode===1.U || mode===7.U,1.U,0.U)))
  def patternFor(id:UInt):UInt = Mux(varied,id(3,2),0.U)
  val inputMemory=Mem(4*cfg.maxLength,UInt(128.W))
  val inverseMemory=Mem(4*cfg.maxLength,UInt(65.W));val divisionMemory=Mem(4*cfg.maxLength,UInt(65.W))
  loadMemoryFromFileInline(inputMemory,s"$vectors/input.hex")
  loadMemoryFromFileInline(inverseMemory,s"$vectors/inverse.hex")
  loadMemoryFromFileInline(divisionMemory,s"$vectors/division.hex")
  val softReset=resetHold=/=0.U
  val dut=withReset(reset.asBool || softReset) {Module(new GoldilocksBatchInversion(cfg))}
  when(softReset) {resetHold:=resetHold-1.U}
  val active=running && !softReset
  val paceInput= !varied || tick(2,0)=/=0.U
  dut.io.enable:= !varied || tick(3,0)=/=0.U
  dut.io.task.valid:=active && !sending && send<total
  dut.io.task.bits.tag:=send;dut.io.task.bits.mode:=directionFor(send);dut.io.task.bits.length:=lengthFor(send)
  val romAddress=(patternFor(send)*cfg.maxLength.U+inputIndex)(log2Ceil(4*cfg.maxLength)-1,0)
  val payload=inputMemory(romAddress)
  dut.io.input.valid:=active && sending && paceInput
  dut.io.input.bits.denominator:=Mux(mode===5.U && send===2.U && inputIndex===0.U,"hffffffff00000001".U,payload(63,0))
  dut.io.input.bits.numerator:=Mux(mode===5.U && send===5.U && inputIndex===0.U,"hffffffff00000001".U,payload(127,64))
  dut.io.input.bits.last:=Mux(mode===5.U && send===3.U,false.B,
    Mux(mode===5.U && send===4.U,inputIndex===0.U || inputIndex===3.U,inputIndex===lengthFor(send)-1.U))
  dut.io.output.ready:=active && (!varied || tick(3,0)>=5.U) && !(aborting && resetStage===1.U)
  dut.io.completion.ready:=active && (!varied || tick(4,0)>=7.U)
  def fail(condition:Bool):Unit = {when(condition && !aborting) {errors:=errors+1.U;printf(p"MISMATCH cycle=${tick} tag=${send}\n")}}
  when(dut.io.task.fire) {
    inputIndex:=0.U
    when(lengthFor(send)===0.U || mode===5.U && send<=1.U) {send:=send+1.U}.otherwise {sending:=true.B}
    when(!aborting) {printf(p"ACCEPT tag=${send} cycle=${tick} length=${lengthFor(send)} mode=${directionFor(send)}\n")}
  }
  when(dut.io.input.fire) {
    when(inputIndex===0.U && !aborting) {printf(p"FIRST_INPUT tag=${send} cycle=${tick}\n")}
    inputIndex:=inputIndex+1.U
    when(inputIndex===lengthFor(send)-1.U) {
      sending:=false.B;send:=send+1.U
      when(!aborting) {printf(p"LAST_INPUT tag=${send} cycle=${tick}\n")}
    }
  }
  val refAddr=(patternFor(dut.io.output.bits.tag)*cfg.maxLength.U+dut.io.output.bits.index)(log2Ceil(4*cfg.maxLength)-1,0)
  val expected=Mux(directionFor(dut.io.output.bits.tag)===1.U,divisionMemory(refAddr),inverseMemory(refAddr))
  val golden=expected ^ Mux(mode===4.U && dut.io.output.bits.tag===0.U && dut.io.output.bits.index===0.U,1.U,0.U)
  when(dut.io.output.fire && !aborting) {
    words:=words+1.U
    fail(mode===5.U || dut.io.output.bits.tag>=total || dut.io.output.bits.index>=lengthFor(dut.io.output.bits.tag) ||
      dut.io.output.bits.value=/=golden(63,0) || dut.io.output.bits.zero=/=golden(64) ||
      dut.io.output.bits.last=/=(dut.io.output.bits.index===lengthFor(dut.io.output.bits.tag)-1.U) ||
      dut.io.output.bits.index=/=outputIndex || (outputIndex=/=0.U && dut.io.output.bits.tag=/=outputTag))
    when(dut.io.output.bits.index===0.U) {outputTag:=dut.io.output.bits.tag;printf(p"FIRST_OUTPUT tag=${dut.io.output.bits.tag} cycle=${tick}\n")}
    when(dut.io.output.bits.last) {outputIndex:=0.U;printf(p"LAST_OUTPUT tag=${dut.io.output.bits.tag} cycle=${tick}\n")}
    .otherwise {outputIndex:=outputIndex+1.U}
  }
  when(dut.io.completion.fire && !aborting) {
    val tag=dut.io.completion.bits.tag;val len=lengthFor(tag)
    val zerosExpected=Wire(UInt(32.W))
    val edges=VecInit(Seq(0.U(32.W),1.U(32.W),0.U(32.W),0.U(32.W)))
    // 混合零在index mod257=0；边界模式在index mod8=0。ENV只用于验证。
    zerosExpected:=Mux(patternFor(tag)===1.U,len,Mux(patternFor(tag)===2.U,0.U,
      Mux(patternFor(tag)===3.U,(len+7.U)/8.U,Mux(len===0.U,0.U,(len+256.U)/257.U))))
    val expectedStatus=Mux(mode===5.U,Mux(tag<=1.U,2.U,3.U),Mux(zerosExpected===0.U,0.U,1.U))
    fail(tag=/=completions || dut.io.completion.bits.status=/=expectedStatus ||
      dut.io.completion.bits.length=/=Mux(mode===5.U,0.U,len) ||
      dut.io.completion.bits.zeros=/=Mux(mode===5.U,0.U,zerosExpected))
    completions:=completions+1.U
    printf(p"COMPLETE tag=${tag} cycle=${tick} length=${dut.io.completion.bits.length} status=${dut.io.completion.bits.status}\n")
    when(completions+1.U===total) {running:=false.B;finished:=true.B}
  }
  // 在输入过程中与待输出期间分别同步复位DUT，之后重发正式任务，不输出旧成功。
  when(active && aborting && ((resetStage===0.U && dut.io.input.fire && inputIndex===3.U) ||
      (resetStage===1.U && dut.io.output.valid))) {
    resetHold:=3.U;resetStage:=resetStage+1.U;send:=0.U;sending:=false.B;inputIndex:=0.U;outputIndex:=0.U
    printf(p"RESET_ABORT stage=${resetStage} cycle=${tick}\n")
  }
  val held=RegEnable(dut.io.output.bits.asUInt,dut.io.output.valid && !dut.io.output.ready)
  val blocked=RegNext(dut.io.output.valid && !dut.io.output.ready,false.B)
  when(blocked && !softReset && !RegNext(softReset,false.B)) {assert(dut.io.output.valid && dut.io.output.bits.asUInt===held,"output unstable")}
  val heldC=RegEnable(dut.io.completion.bits.asUInt,dut.io.completion.valid && !dut.io.completion.ready)
  val blockedC=RegNext(dut.io.completion.valid && !dut.io.completion.ready,false.B)
  when(blockedC && !softReset && !RegNext(softReset,false.B)) {assert(dut.io.completion.valid && dut.io.completion.bits.asUInt===heldC,"completion unstable")}
  when(running && tick>=50000000.U) {errors:=errors+1.U;running:=false.B;finished:=true.B}
  done:=finished;pass:=finished && errors===0.U;mismatchCount:=errors;checked:=words;cycles:=tick;completedCount:=completions
}
