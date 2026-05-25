package me.lucaperri.dev.languages.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import me.lucaperri.dev.languages.psi.NasmIdStmt
import me.lucaperri.dev.languages.psi.NasmTypes

class NasmArityInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is NasmIdStmt) return
                val mnemonicNode = element.node.findChildByType(NasmTypes.IDENTIFIER) ?: return
                val mnemonic = mnemonicNode.text.lowercase()
                val range = ARITY[mnemonic] ?: return
                val actual = element.operandList?.operandList?.size ?: 0

                if (actual in range) return

                val expected = if (range.first == range.last) "${range.first}"
                               else "${range.first}–${range.last}"
                val problem = if (actual < range.first) "too few operands" else "too many operands"
                holder.registerProblem(
                    mnemonicNode.psi,
                    "'$mnemonic' expects $expected operand(s), got $actual ($problem)",
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                )
            }
        }

    companion object {
        private val ARITY: Map<String, IntRange> = buildMap {
            fun zero(vararg names: String) = names.forEach { put(it, 0..0) }
            fun zeroOrOne(vararg names: String) = names.forEach { put(it, 0..1) }
            fun one(vararg names: String) = names.forEach { put(it, 1..1) }
            fun two(vararg names: String) = names.forEach { put(it, 2..2) }
            fun twoOrThree(vararg names: String) = names.forEach { put(it, 2..3) }
            fun three(vararg names: String) = names.forEach { put(it, 3..3) }

            zero("nop", "hlt", "cli", "sti", "cld", "std", "clc", "stc", "cmc",
                 "cwde", "cdqe", "cdq", "cqo", "cbw", "cwd",
                 "cpuid", "rdtsc", "rdtscp", "pause",
                 "pusha", "popa", "pushad", "popad",
                 "pushf", "popf", "pushfq", "popfq", "pushfd", "popfd",
                 "leave", "sysret", "sysenter",
                 "iret", "iretd", "iretq",
                 "int3", "into", "ud2", "xlatb")

            // syscall is 0-operand at the instruction level (Linux ABI uses registers)
            zero("syscall")

            zeroOrOne("ret", "retn", "retf")

            one("push", "pop", "inc", "dec", "not", "neg", "mul", "div", "idiv",
                "call", "jmp",
                "je", "jne", "jz", "jnz",
                "jl", "jle", "jg", "jge", "jnl", "jnle", "jng", "jnge",
                "ja", "jae", "jb", "jbe", "jnb", "jnbe",
                "jc", "jnc", "jo", "jno", "js", "jns", "jp", "jnp", "jpe", "jpo",
                "loop", "loope", "loopne", "loopz", "loopnz",
                "jcxz", "jecxz", "jrcxz",
                "int", "bswap",
                "seto", "setno", "sete", "setne", "setl", "setle", "setg", "setge",
                "seta", "setae", "setb", "setbe", "sets", "setns",
                "setc", "setnc", "setp", "setnp")

            two("mov", "add", "sub", "adc", "sbb", "and", "or", "xor", "cmp", "test",
                "xchg", "lea", "movzx", "movsx", "movsxd",
                "bsf", "bsr", "bt", "btc", "btr", "bts",
                "xadd", "cmpxchg",
                "shl", "shr", "sal", "sar", "rol", "ror", "rcl", "rcr",
                "popcnt", "lzcnt", "tzcnt",
                "cmove", "cmovne", "cmovg", "cmovge", "cmovl", "cmovle",
                "cmova", "cmovae", "cmovb", "cmovbe", "cmovs", "cmovns",
                "cmovo", "cmovno", "cmovc", "cmovnc")

            twoOrThree("imul")

            three("shld", "shrd")
        }
    }
}
