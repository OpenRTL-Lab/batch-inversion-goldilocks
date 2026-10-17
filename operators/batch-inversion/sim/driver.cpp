/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: Goldilocks Batch Inversion Hardware Accelerator
 * Chisel hardware for Goldilocks batch inversion and division with interleaved contexts.
 * Repository: https://github.com/OpenRTL-Lab/batch-inversion-goldilocks
 * File: operators/batch-inversion/sim/driver.cpp
 *
 * SPDX-FileCopyrightText: 2026 Goldilocks Batch Inversion contributors
 * Copyright applies to original project contributions only.
 * SPDX-License-Identifier: MIT
 * See LICENSE and applicable third-party notices.
 * OpenRTL-Header-End
 */

#include "VBatchInversionSimTop.h"
#include "verilated.h"
#include <cstdint>
#include <cstdlib>
#include <iostream>
int main(int argc,char **argv){
  VerilatedContext ctx;ctx.commandArgs(argc,argv);VBatchInversionSimTop top{&ctx};
  auto tick=[&](){top.clock=0;top.eval();ctx.timeInc(1);top.clock=1;top.eval();ctx.timeInc(1);};
  top.reset=1;top.trigger=0;top.testMode=argc>1?std::atoi(argv[1]):0;
  for(int i=0;i<8;i++)tick();top.reset=0;tick();top.trigger=1;tick();top.trigger=0;
  for(std::uint64_t i=0;!top.done && !ctx.gotFinish() && i<50000020ULL;i++)tick();
  bool ok=top.done && top.pass && top.mismatchCount==0;
  std::cout<<"RESULT pass="<<ok<<" checked="<<top.checked<<" mismatches="<<top.mismatchCount
    <<" completed="<<top.completedCount<<" cycles="<<top.cycles<<"\n";top.final();return ok?0:1;
}
