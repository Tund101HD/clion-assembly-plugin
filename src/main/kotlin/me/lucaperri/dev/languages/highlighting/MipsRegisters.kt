package me.lucaperri.dev.languages.highlighting

object MipsRegisters {
    val ALL: Set<String> = buildSet {
        // Symbolic names
        addAll(listOf("\$zero","\$at","\$gp","\$sp","\$fp","\$ra"))
        addAll(listOf("\$v0","\$v1"))
        for (i in 0..3) add("\$a$i")
        for (i in 0..9) add("\$t$i")
        for (i in 0..7) add("\$s$i")
        for (i in 0..1) add("\$k$i")

        // Numeric names $0..$31
        for (i in 0..31) add("\$$i")

        // FPU
        for (i in 0..31) add("\$f$i")

        // HI/LO conceptual
        add("\$hi"); add("\$lo")
    }
}
