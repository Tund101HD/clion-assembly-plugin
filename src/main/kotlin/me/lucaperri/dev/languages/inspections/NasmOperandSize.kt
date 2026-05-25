package me.lucaperri.dev.languages.inspections

import com.intellij.psi.PsiElement
import me.lucaperri.dev.languages.psi.NasmOperand
import me.lucaperri.dev.languages.psi.NasmTypes

enum class NasmRegClass(val displayName: String) {
    GPR("general-purpose"),
    SEG("segment"),
    XMM("XMM"),
    YMM("YMM"),
    ZMM("ZMM"),
    MMX("MMX"),
    ST("x87 FPU"),
    K("AVX-512 opmask")
}

object NasmOperandSize {

    val REGISTER_BITS: Map<String, Int> = buildMap {
        for (r in listOf("al", "bl", "cl", "dl", "ah", "bh", "ch", "dh",
                         "spl", "bpl", "sil", "dil",
                         "r8b", "r9b", "r10b", "r11b", "r12b", "r13b", "r14b", "r15b"))
            put(r, 8)
        for (r in listOf("ax", "bx", "cx", "dx", "si", "di", "bp", "sp",
                         "r8w", "r9w", "r10w", "r11w", "r12w", "r13w", "r14w", "r15w"))
            put(r, 16)
        for (r in listOf("eax", "ebx", "ecx", "edx", "esi", "edi", "ebp", "esp",
                         "r8d", "r9d", "r10d", "r11d", "r12d", "r13d", "r14d", "r15d"))
            put(r, 32)
        for (r in listOf("rax", "rbx", "rcx", "rdx", "rsi", "rdi", "rbp", "rsp",
                         "r8", "r9", "r10", "r11", "r12", "r13", "r14", "r15", "rip"))
            put(r, 64)
        for (i in 0..31) { put("xmm$i", 128); put("ymm$i", 256); put("zmm$i", 512) }
    }

    private val SIZE_SPEC_BITS: Map<String, Int> = mapOf(
        "byte" to 8, "word" to 16, "dword" to 32, "qword" to 64,
        "tword" to 80, "oword" to 128, "yword" to 256, "zword" to 512
    )

    // Instructions where all sized operands must have the same bit-width.
    val SYMMETRIC_MNEMONICS: Set<String> = setOf(
        "mov", "add", "sub", "adc", "sbb", "and", "or", "xor", "cmp", "test", "xchg",
        "imul",
        "bsf", "bsr", "bt", "btc", "btr", "bts",
        "cmove", "cmovne", "cmovg", "cmovge", "cmovl", "cmovle",
        "cmova", "cmovae", "cmovb", "cmovbe", "cmovs", "cmovns",
        "cmovo", "cmovno", "cmovc", "cmovnc",
        "popcnt", "lzcnt", "tzcnt",
    )

    // Instructions where destination must be strictly wider than source.
    val DEST_LARGER_MNEMONICS: Set<String> = setOf("movzx", "movsx", "movsxd")

    // Instructions whose second operand is intentionally a different size (e.g. shift count
    // is always cl/imm regardless of the data width), or whose src is a memory address (lea).
    val SKIP_MNEMONICS: Set<String> = setOf(
        "shl", "shr", "sar", "sal", "rol", "ror", "rcl", "rcr",
        "lea",
    )

    /**
     * Returns the operand's bit-width when it can be determined statically, null otherwise.
     * - SIZE_SPEC token (e.g. `byte [mem]`)  → from SIZE_SPEC_BITS
     * - Plain register expression (e.g. `rax`) → from REGISTER_BITS
     * - Anything else (immediate, complex address, unknown) → null
     */
    fun resolveSize(operand: NasmOperand): Int? {
        val sizeSpec = operand.node.findChildByType(NasmTypes.SIZE_SPEC)?.text?.lowercase()
        if (sizeSpec != null) return SIZE_SPEC_BITS[sizeSpec]
        val expr = operand.expression ?: return null
        return REGISTER_BITS[expr.text.lowercase()]
    }

    /**
     * Returns the most precise PSI element to highlight for a size-mismatch problem:
     * the SIZE_SPEC token if present, the register IDENTIFIER leaf otherwise.
     */
    fun highlightElement(operand: NasmOperand): PsiElement {
        operand.node.findChildByType(NasmTypes.SIZE_SPEC)?.psi?.let { return it }
        val expr = operand.expression ?: return operand
        return expr.labelRefList.firstOrNull()
            ?.node?.findChildByType(NasmTypes.IDENTIFIER)?.psi
            ?: operand
    }

    // ── Register class classification ────────────────────────────────────────

    private val SEG_REGS = setOf("cs", "ds", "es", "fs", "gs", "ss")
    private val MMX_REGS = (0..7).map { "mm$it" }.toSet()
    private val ST_REGS  = (0..7).map { "st$it" }.toSet() + setOf("st")
    private val K_REGS   = (0..7).map { "k$it"  }.toSet()

    /** Returns the register class for [name], or null if [name] is not a known register. */
    fun classifyReg(name: String): NasmRegClass? {
        if (name in REGISTER_BITS) return when {
            name.startsWith("xmm") -> NasmRegClass.XMM
            name.startsWith("ymm") -> NasmRegClass.YMM
            name.startsWith("zmm") -> NasmRegClass.ZMM
            else                   -> NasmRegClass.GPR
        }
        return when (name) {
            in SEG_REGS -> NasmRegClass.SEG
            in MMX_REGS -> NasmRegClass.MMX
            in ST_REGS  -> NasmRegClass.ST
            in K_REGS   -> NasmRegClass.K
            else        -> null
        }
    }

    /**
     * Returns the register name if the operand is a plain register reference,
     * or null for memory operands, immediates, and complex address expressions.
     */
    fun registerNameOf(operand: NasmOperand): String? {
        if (operand.memoryOperand != null) return null
        val expr = operand.expression ?: return null
        val text = expr.text.lowercase()
        return if (classifyReg(text) != null) text else null
    }
}
