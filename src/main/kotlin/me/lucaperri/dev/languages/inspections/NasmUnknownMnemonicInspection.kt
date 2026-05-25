package me.lucaperri.dev.languages.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import me.lucaperri.dev.languages.highlighting.NasmInstructions
import me.lucaperri.dev.languages.highlighting.NasmRegisters
import me.lucaperri.dev.languages.psi.NasmFile
import me.lucaperri.dev.languages.psi.NasmIdStmt
import me.lucaperri.dev.languages.psi.NasmPreprocDirective
import me.lucaperri.dev.languages.psi.NasmTypes

// Flags the leading mnemonic of an instruction statement when it isn't a known
// x86/x64 instruction, a register, or a macro/constant defined earlier in the
// file via the NASM preprocessor.
class NasmUnknownMnemonicInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val macroCache = mutableMapOf<PsiFile, Set<String>>()
        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is NasmIdStmt) return
                val mnemonic = element.node.findChildByType(NasmTypes.IDENTIFIER)?.psi ?: return
                val text = mnemonic.text
                val lower = text.lowercase()
                if (lower in NasmInstructions.ALL) return
                if (lower in NasmRegisters.ALL) return
                val file = element.containingFile as? NasmFile ?: return
                val macros = macroCache.getOrPut(file) { collectDefinedNames(file) }
                if (text in macros) return
                holder.registerProblem(
                    mnemonic,
                    "Unknown mnemonic '$text'",
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                )
            }
        }
    }

    private fun collectDefinedNames(file: NasmFile): Set<String> {
        val names = mutableSetOf<String>()
        for (directive in PsiTreeUtil.findChildrenOfType(file, NasmPreprocDirective::class.java)) {
            val keyword = directive.node.findChildByType(NasmTypes.DIRECTIVE)?.text?.lowercase()
                ?: continue
            if (keyword !in DEFINING_DIRECTIVES) continue
            // The first IDENTIFIER after the directive keyword is the defined symbol.
            val firstId = PsiTreeUtil.collectElements(directive) {
                it.node?.elementType == NasmTypes.IDENTIFIER
            }.firstOrNull()
            firstId?.let { names += it.text }
        }
        return names
    }

    companion object {
        private val DEFINING_DIRECTIVES = setOf(
            "%macro", "%imacro", "%define", "%idefine", "%xdefine", "%ixdefine",
            "%assign", "%iassign", "%defstr", "%idefstr", "%define", "%undef"
        )
    }
}
