package me.lucaperri.dev.languages.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import me.lucaperri.dev.languages.psi.NasmIdStmt
import me.lucaperri.dev.languages.psi.NasmTypes

class NasmOperandTypeInspection : LocalInspectionTool() {

    companion object {
        private val XMM         = setOf(NasmRegClass.XMM)
        private val XMM_OR_MMX  = setOf(NasmRegClass.XMM, NasmRegClass.MMX)
        private val XMM_OR_YMM  = setOf(NasmRegClass.XMM, NasmRegClass.YMM)
        private val ST          = setOf(NasmRegClass.ST)

        // Map: mnemonic → (allowed register classes, human-readable expectation)
        private val CONSTRAINTS: Map<String, Pair<Set<NasmRegClass>, String>> = buildMap {
            fun xmmOnly  (vararg m: String) = m.forEach { put(it, XMM        to "XMM register") }
            fun xmmOrMmx (vararg m: String) = m.forEach { put(it, XMM_OR_MMX to "XMM or MMX register") }
            fun xmmOrYmm (vararg m: String) = m.forEach { put(it, XMM_OR_YMM to "XMM or YMM register") }
            fun stOnly   (vararg m: String) = m.forEach { put(it, ST         to "x87 FPU (ST) register") }

            // SSE single-precision packed / scalar
            xmmOnly("movaps","movups","movss","movhlps","movlhps",
                    "addps","addss","subps","subss","mulps","mulss","divps","divss",
                    "sqrtps","sqrtss","rsqrtps","rsqrtss","rcpps","rcpss",
                    "maxps","maxss","minps","minss",
                    "andps","andnps","orps","xorps",
                    "cmpps","cmpss","shufps","unpcklps","unpckhps")
            // SSE2 double-precision packed / scalar
            xmmOnly("movapd","movupd","movsd",
                    "addpd","addsd","subpd","subsd","mulpd","mulsd","divpd","divsd",
                    "sqrtpd","sqrtsd","maxpd","maxsd","minpd","minsd",
                    "andpd","andnpd","orpd","xorpd",
                    "cmppd","cmpsd","shufpd","unpcklpd","unpckhpd")
            // SSE comparison → EFLAGS (both operands must be XMM)
            xmmOnly("comiss","ucomiss","comisd","ucomisd")
            // SSE2 integer with no 64-bit MMX equivalent
            xmmOnly("pshufd","psrldq","pslldq","pmulld",
                    "roundss","roundsd","roundps","roundpd")
            // AES-NI
            xmmOnly("aesenc","aesenclast","aesdec","aesdeclast","aesimc")

            // SSE2 integer that have both XMM and 64-bit MMX forms
            xmmOrMmx("pxor","pand","pandn","por",
                     "paddb","paddw","paddd","paddq",
                     "psubb","psubw","psubd","psubq",
                     "pcmpeqb","pcmpeqw","pcmpeqd",
                     "pcmpgtb","pcmpgtw","pcmpgtd",
                     "pshufb","pmuludq")

            // AVX VEX-encoded (128-bit XMM or 256-bit YMM forms)
            xmmOrYmm("vmovaps","vmovups","vmovss","vmovsd",
                     "vaddps","vaddss","vaddpd","vaddsd",
                     "vsubps","vsubss","vsubpd","vsubsd",
                     "vmulps","vmulss","vmulpd","vmulsd",
                     "vdivps","vdivss","vdivpd","vdivsd",
                     "vsqrtss","vsqrtsd","vmaxss","vminss",
                     "vxorps","vxorpd","vandps","vandpd","vorps",
                     "vpxor","vpand","vpor",
                     "vpcmpeqb","vpcmpeqd",
                     "vfmadd213ss","vfmadd213sd","vfmadd213ps","vfmadd213pd",
                     "vfmadd231ss","vfmadd231sd",
                     "vfmsub213ss","vfmsub213sd")

            // x87: explicit register operands must be ST(n)
            stOnly("fxch",
                   "fcom","fcomp","fucom","fucomp",
                   "fcomi","fcomip","fucomi","fucomip",
                   "fadd","faddp","fsub","fsubp","fsubr","fsubrp",
                   "fmul","fmulp","fdiv","fdivp","fdivr","fdivrp",
                   "fcmovb","fcmovnb","fcmove","fcmovne",
                   "fcmovbe","fcmovnbe","fcmovu","fcmovnu")
        }
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is NasmIdStmt) return
                val mnemonic = element.node
                    .findChildByType(NasmTypes.IDENTIFIER)?.text?.lowercase() ?: return
                val (allowed, expected) = CONSTRAINTS[mnemonic] ?: return
                val operands = element.operandList?.operandList ?: return

                for (operand in operands) {
                    val regName = NasmOperandSize.registerNameOf(operand) ?: continue
                    val cls = NasmOperandSize.classifyReg(regName) ?: continue
                    if (cls !in allowed) {
                        holder.registerProblem(
                            NasmOperandSize.highlightElement(operand),
                            "'$mnemonic' requires a $expected, not a ${cls.displayName} register ('$regName')",
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                        )
                    }
                }
            }
        }
}
