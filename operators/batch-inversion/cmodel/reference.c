/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: Goldilocks Batch Inversion Hardware Accelerator
 * Chisel hardware for Goldilocks batch inversion and division with interleaved contexts.
 * Repository: https://github.com/OpenRTL-Lab/batch-inversion-goldilocks
 * File: operators/batch-inversion/cmodel/reference.c
 *
 * SPDX-FileCopyrightText: 2026 Goldilocks Batch Inversion contributors
 * Copyright applies to original project contributions only.
 * SPDX-License-Identifier: MIT
 * See LICENSE and applicable third-party notices.
 * OpenRTL-Header-End
 */

// 独立逐元素Fermat参考，不采用DUT的批前缀算法。
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <inttypes.h>
static const uint64_t P=UINT64_C(18446744069414584321);
static uint64_t mul(uint64_t a,uint64_t b){return (__uint128_t)a*b%P;}
static uint64_t inv(uint64_t a){
  uint64_t e=P-2,r=1;
  while(e){if(e&1)r=mul(r,a);a=mul(a,a);e>>=1;}return r;
}
static uint64_t random_word(uint64_t *s){
  *s^=*s<<13;*s^=*s>>7;*s^=*s<<17;return *s;
}
int main(int argc,char **argv){
  if(argc!=3)return 2;
  unsigned n=(unsigned)strtoul(argv[1],0,10);char path[4096];FILE *f[3];
  const char *names[]={"input.hex","inverse.hex","division.hex"};
  for(int i=0;i<3;i++){snprintf(path,sizeof(path),"%s/%s",argv[2],names[i]);f[i]=fopen(path,"wx");if(!f[i])return 2;}
  uint64_t seed=UINT64_C(0x6ac27be11fd54391),checked=0;
  for(unsigned pattern=0;pattern<4;pattern++)for(unsigned i=0;i<n;i++){
    uint64_t d=random_word(&seed)%P,a=random_word(&seed)%P;
    if(pattern==0 && i%257==0)d=0;
    if(pattern==1)d=0;
    if(pattern==2)d=P-1;
    if(pattern==3){uint64_t edge[]={0,1,2,3,P-1,P-2,UINT64_C(0xffffffff),UINT64_C(0x100000000)};d=edge[i%8];a=edge[(i+3)%8];}
    uint64_t z=d?inv(d):0,q=d?mul(z,a):0;
    if(d && (mul(d,z)!=1 || mul(d,q)!=a))return 3;
    fprintf(f[0],"%016" PRIx64 "%016" PRIx64 "\n",a,d);
    fprintf(f[1],"%01x%016" PRIx64 "\n",d==0,z);
    fprintf(f[2],"%01x%016" PRIx64 "\n",d==0,q);
    checked++;
  }
  for(int i=0;i<3;i++)if(fclose(f[i]))return 2;
  printf("C_REFERENCE_PASS checked=%" PRIu64 "\n",checked);return 0;
}
