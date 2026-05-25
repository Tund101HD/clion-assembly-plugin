package me.lucaperri.dev.languages.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import me.lucaperri.dev.languages.psi.MipsIdStmt
import me.lucaperri.dev.languages.psi.MipsTypes

class MipsAlignmentInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is MipsIdStmt) return
                val mnemonic = element.node
                    .findChildByType(MipsTypes.IDENTIFIER)?.text?.lowercase() ?: return
                val required = ALIGNMENT[mnemonic] ?: return

                val operands = element.operandList?.operandList ?: return
                val memOp = operands.firstNotNullOfOrNull { it.memoryOperand } ?: return
                val immediate = memOp.immediate ?: return

                val numText = immediate.node.findChildByType(MipsTypes.NUMBER)?.text ?: return
                val raw = if (numText.startsWith("0x") || numText.startsWith("0X"))
                    numText.drop(2).toLongOrNull(16)
                else
                    numText.toLongOrNull()
                val value = raw ?: return
                val signed = if (immediate.node.findChildByType(MipsTypes.MINUS) != null) -value else value

                if (signed % required != 0L) {
                    val offsetEl: PsiElement = immediate.node.findChildByType(MipsTypes.NUMBER)?.psi
                        ?: immediate
                    holder.registerProblem(
                        offsetEl,
                        "'$mnemonic' requires a $required-byte aligned offset, but $signed is not divisible by $required",
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                    )
                }
            }
        }

    companion object {
        private val ALIGNMENT: Map<String, Int> = mapOf(
            "lw"  to 4, "sw"  to 4,
            "ll"  to 4, "sc"  to 4,
            "lh"  to 2, "sh"  to 2, "lhu" to 2,
            "ld"  to 8, "sd"  to 8,
        )
    }
}
