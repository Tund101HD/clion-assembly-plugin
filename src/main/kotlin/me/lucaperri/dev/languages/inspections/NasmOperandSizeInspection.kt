package me.lucaperri.dev.languages.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import me.lucaperri.dev.languages.psi.NasmIdStmt
import me.lucaperri.dev.languages.psi.NasmTypes

class NasmOperandSizeInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is NasmIdStmt) return
                val mnemonic = element.node
                    .findChildByType(NasmTypes.IDENTIFIER)?.text?.lowercase() ?: return
                val operands = element.operandList?.operandList ?: return
                if (operands.size < 2) return

                when (mnemonic) {
                    in NasmOperandSize.SKIP_MNEMONICS -> return
                    in NasmOperandSize.SYMMETRIC_MNEMONICS -> {
                        val size0 = NasmOperandSize.resolveSize(operands[0]) ?: return
                        val size1 = NasmOperandSize.resolveSize(operands[1]) ?: return
                        if (size0 != size1) {
                            holder.registerProblem(
                                NasmOperandSize.highlightElement(operands[1]),
                                "Operand size mismatch: destination is $size0-bit but source is $size1-bit",
                                ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                            )
                        }
                    }
                    in NasmOperandSize.DEST_LARGER_MNEMONICS -> {
                        val size0 = NasmOperandSize.resolveSize(operands[0]) ?: return
                        val size1 = NasmOperandSize.resolveSize(operands[1]) ?: return
                        if (size0 <= size1) {
                            holder.registerProblem(
                                NasmOperandSize.highlightElement(operands[0]),
                                "'$mnemonic' destination ($size0-bit) must be wider than source ($size1-bit)",
                                ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                            )
                        }
                    }
                }
            }
        }
}
