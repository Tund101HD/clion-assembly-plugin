package me.lucaperri.dev.languages.highlighting

object MipsInstructions {

    // ── Suffix building blocks (NOT standalone instructions) ─────────────────
    // These are the format/predicate suffix fragments used to build compound MIPS
    // mnemonics like `add.s` (FP single), `c.eq.s` (FP equal), `round.w.d` (round
    // double → 32-bit int). On their own they are not valid instructions —
    // `eq` on a line by itself is a syntax error to GAS. They live here as named
    // constants so audit greps over this file pair them with the constructed
    // mnemonic instead of flagging them as missing entries.
    private val FP_FORMATS:        List<String> = listOf("s", "d")                 // single, double
    private val INT_RESULT_WIDTHS: List<String> = listOf("w", "l")                 // 32-bit, 64-bit
    private val FP_COMPARE_PREDS:  List<String> = listOf(
        "f","un","eq","ueq","olt","ult","ole","ule",   // non-signaling
        "sf","ngle","seq","ngl","lt","nge","le","ngt", // signaling
    )

    val ALL: Set<String> = buildSet {
        // ── Arithmetic ────────────────────────────────────────────────────────
        addAll(listOf(
            "add","addi","addiu","addu","sub","subu",
            "mul","mult","multu","div","divu",
            "madd","maddu","msub","msubu",
            "abs","neg","negu","rem","remu",
        ))

        // ── Logical / bit ─────────────────────────────────────────────────────
        addAll(listOf(
            "and","andi","or","ori","xor","xori","nor","not",
            "sll","srl","sra","sllv","srlv","srav","rol","ror","rotr","rotrv",
            "clz","clo","ext","ins","seb","seh","wsbh",
        ))

        // ── Compare / set ─────────────────────────────────────────────────────
        addAll(listOf(
            "slt","slti","sltu","sltiu","seq","sne","sge","sgeu","sgt","sgtu","sle","sleu",
        ))

        // ── Branch ────────────────────────────────────────────────────────────
        addAll(listOf(
            "beq","bne","blez","bgtz","bltz","bgez","bltzal","bgezal",
            "beqz","bnez","blt","bltu","bgt","bgtu","ble","bleu","bge","bgeu","b","bal",
            // Branch-likely (MIPS32; squashes delay slot when not taken)
            "beql","bnel","blezl","bgtzl","bltzl","bgezl","bltzall","bgezall",
        ))

        // ── Jumps ─────────────────────────────────────────────────────────────
        addAll(listOf(
            "j","jal","jr","jalr",
            // Hazard-barrier variants (MIPS32r2)
            "jalr.hb","jr.hb",
        ))

        // ── Load / store ──────────────────────────────────────────────────────
        addAll(listOf(
            "lw","sw","lh","sh","lb","sb","lhu","lbu","lwl","lwr","swl","swr",
            "lui","li","la","ll","sc",
            "cache","pref",
        ))

        // ── FP load / store ───────────────────────────────────────────────────
        addAll(listOf(
            "lwc1","swc1","ldc1","sdc1",
            "lwxc1","swxc1","ldxc1","sdxc1",
            "lwc2","swc2","ldc2","sdc2",
        ))

        // ── Move (GPR/HI/LO/coprocessor) ──────────────────────────────────────
        addAll(listOf(
            "move","mfhi","mflo","mthi","mtlo",
            "movn","movz",
            "mfc0","mtc0","mfc1","mtc1","cfc1","ctc1","dmfc1","dmtc1",
            "mfc2","mtc2","cfc2","ctc2",
            "rdpgpr","wrpgpr","rdhwr",
        ))

        // ── Floating point: basic arithmetic (s = single, d = double) ─────────
        for (fmt in FP_FORMATS) {
            addAll(listOf(
                "add.$fmt","sub.$fmt","mul.$fmt","div.$fmt",
                "abs.$fmt","neg.$fmt","sqrt.$fmt","mov.$fmt",
                "recip.$fmt","rsqrt.$fmt",
                "madd.$fmt","msub.$fmt","nmadd.$fmt","nmsub.$fmt",
            ))
        }

        // ── Floating point: conversion ────────────────────────────────────────
        addAll(listOf(
            "cvt.s.d","cvt.d.s","cvt.s.w","cvt.d.w","cvt.w.s","cvt.w.d",
            "cvt.l.s","cvt.l.d","cvt.s.l","cvt.d.l",
        ))

        // ── Floating point: round/truncate/ceil/floor to integer ──────────────
        for (dstWidth in INT_RESULT_WIDTHS) for (srcFmt in FP_FORMATS) {
            addAll(listOf(
                "round.$dstWidth.$srcFmt",
                "trunc.$dstWidth.$srcFmt",
                "ceil.$dstWidth.$srcFmt",
                "floor.$dstWidth.$srcFmt",
            ))
        }

        // ── Floating point: compares (16 predicates × .s/.d) ──────────────────
        for (pred in FP_COMPARE_PREDS) for (fmt in FP_FORMATS) {
            add("c.$pred.$fmt")
        }

        // ── Floating point: branches / conditional moves ──────────────────────
        addAll(listOf(
            "bc1t","bc1f","bc1tl","bc1fl",
            "movt.s","movt.d","movf.s","movf.d",
            "movn.s","movn.d","movz.s","movz.d",
        ))

        // ── System / misc / traps / sync ──────────────────────────────────────
        addAll(listOf(
            "syscall","break","nop","ssnop","ehb","eret","wait","sync",
            "di","ei","sdbbp",
            "trap","teq","tne","tlt","tltu","tge","tgeu",
            "teqi","tnei","tlti","tltiu","tgei","tgeiu",
            "tlbr","tlbwi","tlbwr","tlbp",
        ))
    }
}
