package me.lucaperri.dev.languages.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import me.lucaperri.dev.languages.highlighting.MipsInstructions
import me.lucaperri.dev.languages.psi.MipsIdStmt
import me.lucaperri.dev.languages.psi.MipsTypes

// Flags the leading mnemonic of a MIPS instruction statement when it isn't a
// known instruction or pseudo-instruction. MIPS has no in-file macro system in
// this grammar (assembler directives are `.`-prefixed DIRECTIVE tokens parsed as
// directive_stmt, not id_stmt), so no macro allowlist is needed.

class MipsUnknownMnemonicInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is MipsIdStmt) return
                val mnemonic = element.node.findChildByType(MipsTypes.IDENTIFIER)?.psi ?: return
                val text = mnemonic.text
                if (text.startsWith("$")) return
                if (text.lowercase() in MipsInstructions.ALL) return
                holder.registerProblem(
                    mnemonic,
                    "Unknown mnemonic '$text'",
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                )
            }
        }
}
