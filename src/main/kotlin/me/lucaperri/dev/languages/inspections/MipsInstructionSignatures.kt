package me.lucaperri.dev.languages.inspections

import me.lucaperri.dev.languages.psi.MipsAssignmentDef
import me.lucaperri.dev.languages.psi.MipsOperand

enum class MipsOperandKind(val displayName: String) {
    REG("register"), IMM("immediate"), MEM("memory operand"), LABEL("label")
}

object MipsInstructionSignatures {

    fun MipsOperand.inferKind(): MipsOperandKind? {
        if (memoryOperand != null) return MipsOperandKind.MEM
        if (immediate != null) return MipsOperandKind.IMM
        if (localLabelRef != null) return MipsOperandKind.LABEL
        val ref = labelRef ?: return null
        if (ref.text.startsWith("$")) return MipsOperandKind.REG
        // GAS-style assignment (`msg_len = . - msg`) defines a numeric constant,
        // not an address label — so `li $a2, msg_len` is a valid immediate.
        // Resolve once and reclassify; an unresolved ref stays LABEL.
        val resolved = ref.references.firstOrNull()?.resolve()
        if (resolved is MipsAssignmentDef) return MipsOperandKind.IMM
        return MipsOperandKind.LABEL
    }

    val SIGNATURES: Map<String, List<List<MipsOperandKind>>> = buildMap {
        val R = MipsOperandKind.REG
        val I = MipsOperandKind.IMM
        val M = MipsOperandKind.MEM
        val L = MipsOperandKind.LABEL

        // Pre-defined common shapes
        val r3   = listOf(listOf(R, R, R))
        val rri  = listOf(listOf(R, R, I))
        val ri   = listOf(listOf(R, I))
        val rm   = listOf(listOf(R, M))
        val rr   = listOf(listOf(R, R))
        val rl   = listOf(listOf(R, L))
        val rrl  = listOf(listOf(R, R, L))
        val r1   = listOf(listOf(R))
        val l1   = listOf(listOf(L))
        val none = listOf(emptyList<MipsOperandKind>())

        // R-type: rd, rs, rt
        for (m in listOf("add","addu","sub","subu","and","or","xor","nor",
                         "slt","sltu","mul","movn","movz","sllv","srlv","srav","rotrv"))
            put(m, r3)

        // I-type arithmetic: rt, rs, imm
        for (m in listOf("addi","addiu","andi","ori","xori","slti","sltiu"))
            put(m, rri)

        put("lui", ri)

        // Shift / rotate by immediate: rd, rt, sa
        for (m in listOf("sll","srl","sra","rotr"))
            put(m, listOf(listOf(R, R, I)))

        // Load: rt, offset(base)
        for (m in listOf("lw","lh","lhu","lb","lbu","ll","lwl","lwr"))
            put(m, rm)

        // Store: rt, offset(base)
        for (m in listOf("sw","sh","sb","sc","swl","swr"))
            put(m, rm)

        // Multiply / divide: rs, rt  (result in HI/LO)
        for (m in listOf("mult","multu","div","divu","madd","maddu","msub","msubu"))
            put(m, rr)

        // HI/LO moves: single register
        for (m in listOf("mfhi","mflo","mthi","mtlo"))
            put(m, r1)

        // Branches: rs, rt, label
        for (m in listOf("beq","bne","beql","bnel","bgt","bge","blt","ble","bgtu","bltu","bgeu","bleu"))
            put(m, rrl)

        // Branches: rs, label
        for (m in listOf("blez","bgtz","bltz","bgez","bltzal","bgezal",
                         "blezl","bgtzl","bltzl","bgezl","beqz","bnez"))
            put(m, rl)

        // Unconditional jumps to label
        for (m in listOf("j","jal","b","bal"))
            put(m, l1)

        // Jump to register; jalr has two forms: jalr $rs  or  jalr $rd, $rs
        put("jr", r1)
        put("jalr", listOf(listOf(R), listOf(R, R)))

        // Pseudo-instructions: rd, rs
        for (m in listOf("move","not","neg","negu","abs","clz","clo"))
            put(m, rr)

        // Pseudo: load immediate / address
        put("li", ri)
        put("la", rl)

        // Zero-operand instructions
        for (m in listOf("syscall","nop","break","eret","sync","tlbr","tlbwi","tlbwr","tlbp"))
            put(m, none)

        // Trap instructions: rs, rt  or  rs, imm
        for (m in listOf("teq","tne","tlt","tltu","tge","tgeu"))
            put(m, listOf(listOf(R, R), listOf(R, I)))
        for (m in listOf("teqi","tnei","tlti","tltiu","tgei","tgeiu"))
            put(m, ri)

        // MIPS32r2 bit-field: rd, rt, pos, size
        put("ext", listOf(listOf(R, R, I, I)))
        put("ins", listOf(listOf(R, R, I, I)))
    }
}
