package me.lucaperri.dev.languages.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import me.lucaperri.dev.languages.psi.NasmExternStmt
import me.lucaperri.dev.languages.psi.NasmFile

class DeclareAsExternFix(private val symbolName: String) : LocalQuickFix {

    override fun getFamilyName(): String = "Declare as extern"
    override fun getName(): String = "Declare '$symbolName' as extern"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val file = descriptor.psiElement.containingFile as? NasmFile ?: return
        val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return

        val externs = PsiTreeUtil.findChildrenOfType(file, NasmExternStmt::class.java)
            .sortedBy { it.textOffset }

        if (externs.isNotEmpty()) {
            document.insertString(externs.last().textRange.endOffset, "\nextern $symbolName")
        } else {
            document.insertString(0, "extern $symbolName\n")
        }
        PsiDocumentManager.getInstance(project).commitDocument(document)
    }
}
