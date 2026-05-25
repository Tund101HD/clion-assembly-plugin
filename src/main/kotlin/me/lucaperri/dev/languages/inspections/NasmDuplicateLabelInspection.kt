package me.lucaperri.dev.languages.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.PsiTreeUtil
import me.lucaperri.dev.languages.psi.NasmFile
import me.lucaperri.dev.languages.psi.NasmNamedElement

class NasmDuplicateLabelInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val cache = mutableMapOf<PsiFile, Map<String, List<NasmNamedElement>>>()
        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is NasmNamedElement) return
                val file = element.containingFile as? NasmFile ?: return
                val name = element.name ?: return

                val byName = cache.getOrPut(file) {
                    PsiTreeUtil.findChildrenOfType(file, NasmNamedElement::class.java)
                        .groupBy { it.name ?: "" }
                        .filterKeys { it.isNotEmpty() }
                }
                val sameName = byName[name] ?: return
                if (sameName.size <= 1) return

                val first = sameName.minByOrNull { it.textOffset } ?: return
                if (first === element) return

                val firstPointer = SmartPointerManager.getInstance(file.project)
                    .createSmartPsiElementPointer(first)
                holder.registerProblem(
                    element.nameIdentifier ?: element,
                    "Duplicate label '$name'",
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    NavigateToLabelFix(firstPointer, name)
                )
            }
        }
    }
}
