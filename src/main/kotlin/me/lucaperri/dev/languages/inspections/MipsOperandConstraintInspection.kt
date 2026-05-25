package me.lucaperri.dev.languages.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import me.lucaperri.dev.languages.inspections.MipsInstructionSignatures.inferKind
import me.lucaperri.dev.languages.psi.MipsIdStmt
import me.lucaperri.dev.languages.psi.MipsOperand
import me.lucaperri.dev.languages.psi.MipsTypes

class MipsOperandConstraintInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is MipsIdStmt) return
                val mnemonicNode = element.node.findChildByType(MipsTypes.IDENTIFIER) ?: return
                val mnemonic = mnemonicNode.text.lowercase()
                val signatures = MipsInstructionSignatures.SIGNATURES[mnemonic] ?: return
                val operands = element.operandList?.operandList ?: emptyList()

                val arityMatches = signatures.filter { it.size == operands.size }

                if (arityMatches.isEmpty()) {
                    val expected = signatures.map { it.size }.distinct().sorted()
                    val expectedStr = expected.joinToString(" or ")
                    holder.registerProblem(
                        mnemonicNode.psi,
                        "'$mnemonic' expects $expectedStr operand(s), got ${operands.size}",
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                    )
                    return
                }

                // Check if any arity-matching signature also matches by type.
                val typeMatch = arityMatches.firstOrNull { sig ->
                    operands.indices.all { i -> matchesKind(operands[i], sig[i]) }
                }
                if (typeMatch != null) return

                // Report type violations against the first arity-matching signature.
                val bestSig = arityMatches.first()
                for (i in operands.indices) {
                    val expected = bestSig[i]
                    val actual = operands[i].inferKind() ?: continue
                    if (actual != expected) {
                        holder.registerProblem(
                            operands[i],
                            "Operand ${i + 1} of '$mnemonic' must be a ${expected.displayName}, " +
                                "found ${actual.displayName}",
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                        )
                    }
                }
            }
        }

    private fun matchesKind(operand: MipsOperand, expected: MipsOperandKind): Boolean {
        val actual = operand.inferKind() ?: return true
        return actual == expected
    }
}
