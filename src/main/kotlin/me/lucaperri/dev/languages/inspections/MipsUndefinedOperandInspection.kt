package me.lucaperri.dev.languages.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import me.lucaperri.dev.languages.highlighting.MipsRegisters
import me.lucaperri.dev.languages.psi.MipsDirectiveStmt
import me.lucaperri.dev.languages.psi.MipsLabelRef
import me.lucaperri.dev.languages.psi.MipsLocalLabelRef
import me.lucaperri.dev.languages.psi.MipsMemoryOperand
import me.lucaperri.dev.languages.psi.MipsTypes

class MipsUndefinedOperandInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                when (element) {
                    is MipsLabelRef -> checkLabelRef(element, holder)
                    is MipsLocalLabelRef -> checkLocalLabelRef(element, holder)
                    is MipsMemoryOperand -> checkMemoryOperand(element, holder)
                }
            }
        }

    private fun checkLabelRef(element: MipsLabelRef, holder: ProblemsHolder) {
        if (PsiTreeUtil.getParentOfType(element, MipsDirectiveStmt::class.java) != null) return
        val name = element.text
        if (name.startsWith("$")) {
            if (name !in MipsRegisters.ALL) {
                holder.registerProblem(
                    element,
                    "Unknown register '$name'",
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                )
            }
        } else {
            if (element.references.none { it.resolve() != null }) {
                holder.registerProblem(
                    element,
                    "Undefined label '$name'",
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                )
            }
        }
    }

    private fun checkLocalLabelRef(element: MipsLocalLabelRef, holder: ProblemsHolder) {
        val directive = element.node.findChildByType(MipsTypes.DIRECTIVE)?.psi ?: return
        if (element.references.none { it.resolve() != null }) {
            holder.registerProblem(
                directive,
                "Undefined local label '${directive.text}'",
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING
            )
        }
    }

    private fun checkMemoryOperand(element: MipsMemoryOperand, holder: ProblemsHolder) {
        val identifier = element.node.findChildByType(MipsTypes.IDENTIFIER)?.psi ?: return
        val name = identifier.text
        if (name.startsWith("$")) {
            if (name !in MipsRegisters.ALL) {
                holder.registerProblem(
                    identifier,
                    "Unknown register '$name'",
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                )
            }
        } else {
            holder.registerProblem(
                identifier,
                "Expected a register, found '$name'",
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING
            )
        }
    }
}
