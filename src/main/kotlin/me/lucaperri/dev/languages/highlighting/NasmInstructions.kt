package me.lucaperri.dev.languages.highlighting

object NasmInstructions {
    val ALL: Set<String> = buildSet {
        // Data transfer
        addAll(listOf(
            "mov","movzx","movsx","movsxd","movabs","xchg","cmpxchg","cmpxchg8b","cmpxchg16b",
            "lea","push","pop","pushf","popf","pushfq","popfq","pushfd","popfd","pusha","popa","pushad","popad",
            "cbw","cdq","cwd","cqo","cwde","cdqe",
        ))

        // Arithmetic
        addAll(listOf(
            "add","adc","sub","sbb","mul","imul","div","idiv","inc","dec","neg",
        ))

        // Logical / bit
        addAll(listOf(
            "and","or","xor","not","test","cmp",
            "shl","shr","sal","sar","rol","ror","rcl","rcr","shld","shrd",
            "bt","bts","btr","btc","bsf","bsr","popcnt","tzcnt","lzcnt","bswap",
        ))

        // BMI1 / BMI2
        addAll(listOf(
            "andn","bextr","blsi","blsmsk","blsr",
            "bzhi","mulx","pdep","pext","rorx","sarx","shlx","shrx",
        ))

        // Control flow
        addAll(listOf(
            "jmp","je","jne","jz","jnz",
            "jl","jle","jg","jge","jnge","jnl","jng","jnle",
            "ja","jae","jb","jbe","jnbe","jnb","jnae","jna",
            "jc","jnc","jo","jno","js","jns","jp","jnp","jpe","jpo","jcxz","jecxz","jrcxz",
            "loop","loope","loopne","loopz","loopnz",
            "call","ret","retn","retf","iret","iretd","iretq",
            "enter","leave","int","int3","into","syscall","sysenter","sysexit","sysret",
        ))

        // Set on condition
        addAll(listOf(
            "sete","setne","setz","setnz",
            "setl","setle","setg","setge","setnge","setnl","setng","setnle",
            "seta","setae","setb","setbe","setnbe","setnb","setnae","setna",
            "setc","setnc","seto","setno","sets","setns","setp","setnp","setpe","setpo",
        ))

        // CMOV
        addAll(listOf(
            "cmove","cmovne","cmovz","cmovnz",
            "cmovl","cmovle","cmovg","cmovge","cmovnge","cmovnl","cmovng","cmovnle",
            "cmova","cmovae","cmovb","cmovbe","cmovnbe","cmovnb","cmovnae","cmovna",
            "cmovc","cmovnc","cmovo","cmovno","cmovs","cmovns","cmovp","cmovnp","cmovpe","cmovpo",
        ))

        // String
        addAll(listOf(
            "movs","movsb","movsw","movsd","movsq",
            "cmps","cmpsb","cmpsw","cmpsd","cmpsq",
            "scas","scasb","scasw","scasd","scasq",
            "lods","lodsb","lodsw","lodsd","lodsq",
            "stos","stosb","stosw","stosd","stosq",
            "rep","repe","repz","repne","repnz",
        ))

        // System & misc
        addAll(listOf(
            "nop","hlt","wait","fwait","ud2","cpuid","rdtsc","rdtscp","rdmsr","wrmsr","rdpmc","xlatb","xlat",
            "clc","stc","cmc","cld","std","cli","sti","lahf","sahf","clflush","mfence","sfence","lfence",
            "pause","monitor","mwait","lock","xadd","prefetch","prefetchnta","prefetcht0","prefetcht1","prefetcht2",
            "crc32",
        ))

        // x87 FPU
        addAll(listOf(
            "fld","fld1","fldz","fldpi","fldl2e","fldl2t","fldlg2","fldln2",
            "fst","fstp","fist","fistp","fisttp","fild",
            "fadd","faddp","fiadd","fsub","fsubp","fisub","fsubr","fsubrp","fisubr",
            "fmul","fmulp","fimul","fdiv","fdivp","fidiv","fdivr","fdivrp","fidivr",
            "fsqrt","fsin","fcos","fsincos","fptan","fpatan","f2xm1","fyl2x","fyl2xp1",
            "fabs","fchs","frndint","fscale","fxch","fxam","fxtract",
            "fcom","fcomp","fcompp","fcomi","fcomip","ftst",
            "fucom","fucomp","fucompp","fucomi","fucomip",
            "ffree","finit","fninit","fclex","fnclex","fnop","fdecstp","fincstp",
            "fsave","fnsave","frstor","fstenv","fnstenv","fldenv","fstcw","fnstcw","fldcw","fstsw","fnstsw",
            "fcmovb","fcmove","fcmovbe","fcmovu","fcmovnb","fcmovne","fcmovnbe","fcmovnu",
        ))

        // SSE / SSE2 scalar + packed (float)
        addAll(listOf(
            "movss","movsd","movaps","movups","movapd","movupd","movdqa","movdqu","movhps","movlps","movhpd","movlpd","movmskps","movmskpd",
            "movd","movq","movntps","movntpd","movntdq","movnti","movddup","movsldup","movshdup",
            "addss","addsd","addps","addpd","subss","subsd","subps","subpd",
            "mulss","mulsd","mulps","mulpd","divss","divsd","divps","divpd",
            "sqrtss","sqrtsd","sqrtps","sqrtpd","rsqrtss","rsqrtps","rcpss","rcpps",
            "minss","minsd","minps","minpd","maxss","maxsd","maxps","maxpd",
            "cmpss","cmpsd","cmpps","cmppd",
            "andps","andpd","andnps","andnpd","orps","orpd","xorps","xorpd",
            "ucomiss","ucomisd","comiss","comisd",
            "cvtsi2ss","cvtsi2sd","cvtss2si","cvtsd2si","cvtss2sd","cvtsd2ss",
            "cvttss2si","cvttsd2si","cvtps2pd","cvtpd2ps","cvtdq2ps","cvtps2dq","cvttps2dq","cvtdq2pd","cvtpd2dq","cvttpd2dq",
            "shufps","shufpd","unpcklps","unpcklpd","unpckhps","unpckhpd",
            "haddps","haddpd","hsubps","hsubpd","addsubps","addsubpd","lddqu",
        ))

        // SSE2 integer (MMX-style on XMM)
        addAll(listOf(
            "paddb","paddw","paddd","paddq","paddsb","paddsw","paddusb","paddusw",
            "psubb","psubw","psubd","psubq","psubsb","psubsw","psubusb","psubusw",
            "pmullw","pmulhw","pmulhuw","pmuludq","pmaddwd","psadbw",
            "pand","por","pxor","pandn",
            "pcmpeqb","pcmpeqw","pcmpeqd","pcmpgtb","pcmpgtw","pcmpgtd",
            "psllw","pslld","psllq","psllq","psrlw","psrld","psrlq","psraw","psrad",
            "pslldq","psrldq",
            "packsswb","packuswb","packssdw","punpcklbw","punpcklwd","punpckldq","punpcklqdq",
            "punpckhbw","punpckhwd","punpckhdq","punpckhqdq",
            "pmovmskb","pshufd","pshufhw","pshuflw","pshufw","pinsrw","pextrw","pmaxsw","pminsw","pmaxub","pminub","pavgb","pavgw",
        ))

        // SSSE3
        addAll(listOf(
            "pshufb","phaddw","phaddd","phaddsw","phsubw","phsubd","phsubsw",
            "pmaddubsw","pmulhrsw","pabsb","pabsw","pabsd","palignr","psignb","psignw","psignd",
        ))

        // SSE4.1 / SSE4.2
        addAll(listOf(
            "roundps","roundpd","roundss","roundsd",
            "blendps","blendpd","blendvps","blendvpd","pblendvb","pblendw","pblendd",
            "dpps","dppd","mpsadbw","ptest",
            "insertps","extractps","pinsrb","pinsrd","pinsrq","pextrb","pextrd","pextrq",
            "pminsb","pminsd","pminuw","pminud","pmaxsb","pmaxsd","pmaxuw","pmaxud",
            "pmuldq","pmulld","phminposuw",
            "pcmpestri","pcmpestrm","pcmpistri","pcmpistrm",
            "pcmpgtq","pcmpeqq",
            "pmovsxbw","pmovsxbd","pmovsxbq","pmovsxwd","pmovsxwq","pmovsxdq",
            "pmovzxbw","pmovzxbd","pmovzxbq","pmovzxwd","pmovzxwq","pmovzxdq",
            "movntdqa","packusdw",
        ))

        // AES-NI / PCLMUL
        addAll(listOf(
            "aesenc","aesenclast","aesdec","aesdeclast","aeskeygenassist","aesimc",
            "pclmulqdq",
        ))

        // AVX / AVX2 — VEX-encoded forms. Includes v-prefixed counterparts of the
        // SSE/SSE2 ops above, plus AVX/AVX2-specific ones (broadcast, permute, insert/extract).
        addAll(listOf(
            "vzeroupper","vzeroall",
            "vmovss","vmovsd","vmovaps","vmovups","vmovapd","vmovupd","vmovdqa","vmovdqu","vmovd","vmovq",
            "vmovhps","vmovlps","vmovhpd","vmovlpd","vmovmskps","vmovmskpd","vmovddup","vmovsldup","vmovshdup",
            "vaddss","vaddsd","vaddps","vaddpd","vsubss","vsubsd","vsubps","vsubpd",
            "vmulss","vmulsd","vmulps","vmulpd","vdivss","vdivsd","vdivps","vdivpd",
            "vsqrtss","vsqrtsd","vsqrtps","vsqrtpd","vrsqrtss","vrsqrtps","vrcpss","vrcpps",
            "vminss","vminsd","vminps","vminpd","vmaxss","vmaxsd","vmaxps","vmaxpd",
            "vcmpss","vcmpsd","vcmpps","vcmppd",
            "vandps","vandpd","vandnps","vandnpd","vorps","vorpd","vxorps","vxorpd",
            "vucomiss","vucomisd","vcomiss","vcomisd",
            "vshufps","vshufpd","vunpcklps","vunpcklpd","vunpckhps","vunpckhpd",
            "vhaddps","vhaddpd","vhsubps","vhsubpd","vaddsubps","vaddsubpd",
            "vroundps","vroundpd","vroundss","vroundsd",
            "vblendps","vblendpd","vblendvps","vblendvpd","vpblendvb","vpblendw","vpblendd",
            "vdpps","vdppd","vmpsadbw","vptest",
            "vinsertps","vextractps","vinsertf128","vextractf128","vinserti128","vextracti128",
            "vbroadcastss","vbroadcastsd","vbroadcastf128","vbroadcasti128",
            "vpbroadcastb","vpbroadcastw","vpbroadcastd","vpbroadcastq",
            "vperm2f128","vperm2i128","vpermd","vpermps","vpermpd","vpermq","vpermilps","vpermilpd",
            "vpaddb","vpaddw","vpaddd","vpaddq","vpsubb","vpsubw","vpsubd","vpsubq",
            "vpand","vpor","vpxor","vpandn",
            "vpsllw","vpslld","vpsllq","vpsrlw","vpsrld","vpsrlq","vpsraw","vpsrad",
            "vpcmpeqb","vpcmpeqw","vpcmpeqd","vpcmpeqq","vpcmpgtb","vpcmpgtw","vpcmpgtd","vpcmpgtq",
            "vpmullw","vpmulld","vpmuldq","vpmuludq","vpmaddwd","vpmaddubsw",
            "vpshufb","vpshufd","vpshufhw","vpshuflw","vpalignr",
            "vpminsb","vpminsw","vpminsd","vpminub","vpminuw","vpminud",
            "vpmaxsb","vpmaxsw","vpmaxsd","vpmaxub","vpmaxuw","vpmaxud",
            "vpmovmskb","vpinsrb","vpinsrw","vpinsrd","vpinsrq","vpextrb","vpextrw","vpextrd","vpextrq",
            "vpacksswb","vpackuswb","vpackssdw","vpackusdw",
            "vpunpcklbw","vpunpcklwd","vpunpckldq","vpunpcklqdq",
            "vpunpckhbw","vpunpckhwd","vpunpckhdq","vpunpckhqdq",
            "vcvtsi2ss","vcvtsi2sd","vcvtss2si","vcvtsd2si","vcvtss2sd","vcvtsd2ss",
            "vcvttss2si","vcvttsd2si","vcvtps2pd","vcvtpd2ps","vcvtdq2ps","vcvtps2dq","vcvttps2dq",
            "vcvtdq2pd","vcvtpd2dq","vcvttpd2dq",
            "vaesenc","vaesenclast","vaesdec","vaesdeclast","vaeskeygenassist","vaesimc","vpclmulqdq",
        ))

        // FMA (intel FMA3) — 12 mnemonic stems × 3 operand orders × {ss,sd,ps,pd}
        addAll(listOf(
            "vfmadd132ss","vfmadd213ss","vfmadd231ss","vfmadd132sd","vfmadd213sd","vfmadd231sd",
            "vfmadd132ps","vfmadd213ps","vfmadd231ps","vfmadd132pd","vfmadd213pd","vfmadd231pd",
            "vfmsub132ss","vfmsub213ss","vfmsub231ss","vfmsub132sd","vfmsub213sd","vfmsub231sd",
            "vfmsub132ps","vfmsub213ps","vfmsub231ps","vfmsub132pd","vfmsub213pd","vfmsub231pd",
            "vfnmadd132ss","vfnmadd213ss","vfnmadd231ss","vfnmadd132sd","vfnmadd213sd","vfnmadd231sd",
            "vfnmadd132ps","vfnmadd213ps","vfnmadd231ps","vfnmadd132pd","vfnmadd213pd","vfnmadd231pd",
            "vfnmsub132ss","vfnmsub213ss","vfnmsub231ss","vfnmsub132sd","vfnmsub213sd","vfnmsub231sd",
            "vfnmsub132ps","vfnmsub213ps","vfnmsub231ps","vfnmsub132pd","vfnmsub213pd","vfnmsub231pd",
            "vfmaddsub132ps","vfmaddsub213ps","vfmaddsub231ps","vfmaddsub132pd","vfmaddsub213pd","vfmaddsub231pd",
            "vfmsubadd132ps","vfmsubadd213ps","vfmsubadd231ps","vfmsubadd132pd","vfmsubadd213pd","vfmsubadd231pd",
        ))

        // AVX-512 opmask ops (kmask register manipulation; size suffix = mask width in bits)
        for (suf in listOf("b","w","d","q")) {
            addAll(listOf(
                "kmov$suf","kand$suf","kandn$suf","kor$suf","kxor$suf","kxnor$suf","knot$suf",
                "kshiftl$suf","kshiftr$suf","ktest$suf","kortest$suf","kadd$suf",
            ))
        }
        addAll(listOf("kunpckbw","kunpckwd","kunpckdq"))
    }
}
