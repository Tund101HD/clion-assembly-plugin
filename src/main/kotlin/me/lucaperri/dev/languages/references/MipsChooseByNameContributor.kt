package me.lucaperri.dev.languages.references

import com.intellij.navigation.ChooseByNameContributor
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import me.lucaperri.dev.languages.filetypes.MipsFileType
import me.lucaperri.dev.languages.psi.MipsFile
import me.lucaperri.dev.languages.psi.MipsNamedElement

class MipsChooseByNameContributor : ChooseByNameContributor {

    override fun getNames(project: Project, includeNonProjectItems: Boolean): Array<String> =
        allLabels(project, includeNonProjectItems).mapNotNull { it.name }.distinct().toList().toTypedArray()

    override fun getItemsByName(
        name: String,
        pattern: String,
        project: Project,
        includeNonProjectItems: Boolean
    ): Array<NavigationItem> =
        allLabels(project, includeNonProjectItems)
            .filter { it.name == name }
            .filterIsInstance<NavigationItem>()
            .toList()
            .toTypedArray()

    private fun allLabels(project: Project, includeNonProject: Boolean): Sequence<MipsNamedElement> {
        val scope = if (includeNonProject) GlobalSearchScope.allScope(project)
                    else GlobalSearchScope.projectScope(project)
        val manager = PsiManager.getInstance(project)
        return FileTypeIndex.getFiles(MipsFileType.INSTANCE, scope).asSequence()
            .mapNotNull { manager.findFile(it) as? MipsFile }
            .flatMap { PsiTreeUtil.findChildrenOfType(it, MipsNamedElement::class.java).asSequence() }
    }
}
