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
import me.lucaperri.dev.languages.psi.NasmExternStmt
import me.lucaperri.dev.languages.psi.NasmFile
import me.lucaperri.dev.languages.psi.NasmGlobalStmt
import me.lucaperri.dev.languages.psi.NasmIdStmt
import me.lucaperri.dev.languages.psi.NasmLabelRef
import me.lucaperri.dev.languages.psi.NasmPreprocDirective
import me.lucaperri.dev.languages.psi.NasmSectionStmt
import me.lucaperri.dev.languages.psi.NasmTypes

class NasmUndefinedSymbolInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val externCache = mutableMapOf<PsiFile, Set<String>>()
        val macroCache = mutableMapOf<PsiFile, Set<String>>()
        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is NasmLabelRef) return
                val parent = element.parent ?: return
                if (parent is NasmExternStmt || parent is NasmGlobalStmt) return
                if (PsiTreeUtil.getParentOfType(element, NasmSectionStmt::class.java) != null) return
                if (PsiTreeUtil.getParentOfType(element, NasmPreprocDirective::class.java) != null) return
                val identifier = element.node.findChildByType(NasmTypes.IDENTIFIER)?.psi ?: return
                val name = identifier.text
                val lower = name.lowercase()
                if (lower in NasmRegisters.ALL) return
                // `rep movsb`, `lock xadd` etc.: instruction name in operand position of a prefix mnemonic.
                if (lower in NasmInstructions.ALL) {
                    val stmt = PsiTreeUtil.getParentOfType(element, NasmIdStmt::class.java)
                    val mnemonic = stmt?.node?.findChildByType(NasmTypes.IDENTIFIER)?.text?.lowercase()
                    if (mnemonic in PREFIX_MNEMONICS) return
                }
                if (name.startsWith("__") && name.endsWith("__")) return
                val file = element.containingFile as? NasmFile ?: return
                val externs = externCache.getOrPut(file) { collectExternNames(file) }
                if (name in externs) return
                val macros = macroCache.getOrPut(file) { collectMacroNames(file) }
                if (name in macros) return
                val resolved = element.reference?.resolve()
                if (resolved != null && resolved.containingFile === file) return
                val fixes = buildList {
                    if (isCallMnemonic(element)) add(DeclareAsExternFix(name))
                }
                holder.registerProblem(
                    identifier,
                    "Undefined symbol '$name'",
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    *fixes.toTypedArray()
                )
            }
        }
    }

    private fun collectExternNames(file: NasmFile): Set<String> {
        val names = mutableSetOf<String>()
        for (stmt in PsiTreeUtil.findChildrenOfType(file, NasmExternStmt::class.java)) {
            for (ref in stmt.labelRefList) {
                ref.node.findChildByType(NasmTypes.IDENTIFIER)?.text?.let { names += it }
            }
        }
        return names
    }

    private fun collectMacroNames(file: NasmFile): Set<String> {
        val names = mutableSetOf<String>()
        for (directive in PsiTreeUtil.findChildrenOfType(file, NasmPreprocDirective::class.java)) {
            val keyword = directive.node.findChildByType(NasmTypes.DIRECTIVE)?.text?.lowercase()
                ?: continue
            if (keyword !in DEFINING_DIRECTIVES) continue
            PsiTreeUtil.collectElements(directive) {
                it.node?.elementType == NasmTypes.IDENTIFIER
            }.firstOrNull()?.let { names += it.text }
        }
        return names
    }

    private fun isCallMnemonic(labelRef: NasmLabelRef): Boolean {
        val stmt = PsiTreeUtil.getParentOfType(labelRef, NasmIdStmt::class.java) ?: return false
        val mnemonic = stmt.node.findChildByType(NasmTypes.IDENTIFIER)?.text?.lowercase()
        return mnemonic == "call"
    }

    companion object {
        private val PREFIX_MNEMONICS = setOf("rep", "repe", "repz", "repne", "repnz", "lock")

        private val DEFINING_DIRECTIVES = setOf(
            "%macro", "%imacro", "%define", "%idefine", "%xdefine", "%ixdefine",
            "%assign", "%iassign", "%defstr", "%idefstr", "%undef"
        )
    }
}
