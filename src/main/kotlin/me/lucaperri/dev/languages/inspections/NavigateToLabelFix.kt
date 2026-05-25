package me.lucaperri.dev.languages.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer

class NavigateToLabelFix(
    private val target: SmartPsiElementPointer<out PsiElement>,
    private val labelName: String
) : LocalQuickFix {

    override fun getFamilyName(): String = "Navigate to first definition"
    override fun getName(): String = "Navigate to first definition of '$labelName'"

    override fun startInWriteAction(): Boolean = false

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = target.element ?: return
        (element as? Navigatable)?.takeIf { it.canNavigate() }?.navigate(true)
    }
}
