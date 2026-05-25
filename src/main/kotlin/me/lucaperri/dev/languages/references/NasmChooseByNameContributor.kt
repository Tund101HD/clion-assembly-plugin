package me.lucaperri.dev.languages.references

import com.intellij.navigation.ChooseByNameContributor
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import me.lucaperri.dev.languages.filetypes.NasmFileType
import me.lucaperri.dev.languages.psi.NasmFile
import me.lucaperri.dev.languages.psi.NasmNamedElement

class NasmChooseByNameContributor : ChooseByNameContributor {

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

    private fun allLabels(project: Project, includeNonProject: Boolean): Sequence<NasmNamedElement> {
        val scope = if (includeNonProject) GlobalSearchScope.allScope(project)
                    else GlobalSearchScope.projectScope(project)
        val manager = PsiManager.getInstance(project)
        return FileTypeIndex.getFiles(NasmFileType.INSTANCE, scope).asSequence()
            .mapNotNull { manager.findFile(it) as? NasmFile }
            .flatMap { PsiTreeUtil.findChildrenOfType(it, NasmNamedElement::class.java).asSequence() }
    }
}
