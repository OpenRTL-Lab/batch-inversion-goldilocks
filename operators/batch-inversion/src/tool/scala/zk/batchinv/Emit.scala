/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: Goldilocks Batch Inversion Hardware Accelerator
 * Chisel hardware for Goldilocks batch inversion and division with interleaved contexts.
 * Repository: https://github.com/OpenRTL-Lab/batch-inversion-goldilocks
 * File: operators/batch-inversion/src/tool/scala/zk/batchinv/Emit.scala
 *
 * SPDX-FileCopyrightText: 2026 Goldilocks Batch Inversion contributors
 * Copyright applies to original project contributions only.
 * SPDX-License-Identifier: MIT
 * See LICENSE and applicable third-party notices.
 * OpenRTL-Header-End
 */

package zk.batchinv
import circt.stage.ChiselStage
import java.nio.file.{Files,Paths}
object Emit extends App {
  require(args.length==4,"usage: Emit build/ID/rtl maxLength vectors repeats")
  val out=Paths.get(args(0));require(!out.isAbsolute && out.startsWith(Paths.get("build")) && !Files.exists(out))
  val cfg=Config(args(1).toInt);require(Files.isDirectory(Paths.get(args(2))))
  Files.createDirectories(out)
  ChiselStage.emitSystemVerilog(new BatchInversionSimTop(cfg,args(2),args(3).toInt),
    firtoolOpts=Array("-O=release","-disable-all-randomization","--lowering-options=disallowLocalVariables","-split-verilog",s"-o=$out"))
}
