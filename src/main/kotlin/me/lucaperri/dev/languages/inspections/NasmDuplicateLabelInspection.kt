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
import me.lucaperri.dev.languages.psi.NasmLabelDef
import me.lucaperri.dev.languages.psi.NasmNamedElement
import me.lucaperri.dev.languages.psi.isNasmLocalLabel
import me.lucaperri.dev.languages.psi.nasmLocalScopeAnchor

class NasmDuplicateLabelInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val cache = mutableMapOf<PsiFile, Map<String, List<NasmNamedElement>>>()
        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is NasmNamedElement) return
                val file = element.containingFile as? NasmFile ?: return
                val name = element.name ?: return

                // Group by scope key, not bare name: NASM local labels (`.loop`) are
                // scoped to their owning non-local label, so `.loop` under `func1:`
                // and `.loop` under `func2:` are distinct symbols — not duplicates.
                val byScope = cache.getOrPut(file) {
                    PsiTreeUtil.findChildrenOfType(file, NasmNamedElement::class.java)
                        .filter { it.name != null }
                        .groupBy { scopeKey(it, file) }
                }
                val sameScope = byScope[scopeKey(element, file)] ?: return
                if (sameScope.size <= 1) return

                val first = sameScope.minByOrNull { it.textOffset } ?: return
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

    // Local labels are keyed by (owning scope + name) so identical `.foo` names in
    // different functions don't collide; every other named element stays file-global.
    private fun scopeKey(element: NasmNamedElement, file: NasmFile): String {
        val name = element.name ?: return ""
        if (element is NasmLabelDef && name.isNasmLocalLabel()) {
            val anchor = nasmLocalScopeAnchor(file, element.textRange.startOffset)
            return "local@${anchor?.textRange?.startOffset ?: -1}:$name"
        }
        return "global:$name"
    }
}
