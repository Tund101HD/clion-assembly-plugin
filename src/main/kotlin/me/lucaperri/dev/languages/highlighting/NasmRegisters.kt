package me.lucaperri.dev.languages.highlighting

object NasmRegisters {
    val ALL: Set<String> = buildSet {
        // 64-bit GPRs
        addAll(listOf("rax","rbx","rcx","rdx","rsi","rdi","rbp","rsp",
                      "r8","r9","r10","r11","r12","r13","r14","r15"))
        // 32-bit GPRs
        addAll(listOf("eax","ebx","ecx","edx","esi","edi","ebp","esp",
                      "r8d","r9d","r10d","r11d","r12d","r13d","r14d","r15d"))
        // 16-bit GPRs
        addAll(listOf("ax","bx","cx","dx","si","di","bp","sp",
                      "r8w","r9w","r10w","r11w","r12w","r13w","r14w","r15w"))
        // 8-bit GPRs
        addAll(listOf("al","bl","cl","dl","ah","bh","ch","dh","spl","bpl","sil","dil",
                      "r8b","r9b","r10b","r11b","r12b","r13b","r14b","r15b"))
        // Instruction pointers
        addAll(listOf("rip","eip","ip"))
        // Segment
        addAll(listOf("cs","ds","es","fs","gs","ss"))
        // FPU + MMX
        for (i in 0..7) add("st$i"); for (i in 0..7) add("mm$i")
        // SSE / AVX / AVX-512
        for (i in 0..31) add("xmm$i")
        for (i in 0..31) add("ymm$i")
        for (i in 0..31) add("zmm$i")
        // AVX-512 mask
        for (i in 0..7) add("k$i")
        // Control / debug
        addAll(listOf("cr0","cr2","cr3","cr4","cr8",
                      "dr0","dr1","dr2","dr3","dr6","dr7"))
    }
}
