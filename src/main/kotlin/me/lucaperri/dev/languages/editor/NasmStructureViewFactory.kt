package me.lucaperri.dev.languages.editor

import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewModelBase
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.ide.util.treeView.smartTree.SortableTreeElement
import com.intellij.ide.util.treeView.smartTree.Sorter
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import me.lucaperri.dev.languages.psi.NasmFile
import me.lucaperri.dev.languages.psi.NasmNamedElement
import me.lucaperri.dev.languages.psi.NasmSectionStmt
import javax.swing.Icon

class NasmStructureViewFactory : PsiStructureViewFactory {
    override fun getStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder? {
        if (psiFile !is NasmFile) return null
        return object : TreeBasedStructureViewBuilder() {
            override fun createStructureViewModel(editor: Editor?): StructureViewModel =
                NasmStructureViewModel(psiFile)
        }
    }
}

private class NasmStructureViewModel(file: NasmFile) :
    StructureViewModelBase(file, NasmFileTreeElement(file)),
    StructureViewModel.ElementInfoProvider {

    init {
        withSorters(Sorter.ALPHA_SORTER)
    }

    override fun isAlwaysShowsPlus(element: StructureViewTreeElement): Boolean = false
    override fun isAlwaysLeaf(element: StructureViewTreeElement): Boolean =
        element !is NasmFileTreeElement
}

private class NasmFileTreeElement(private val file: NasmFile) :
    StructureViewTreeElement, SortableTreeElement {

    override fun getValue(): Any = file
    override fun navigate(requestFocus: Boolean) = file.navigate(requestFocus)
    override fun canNavigate(): Boolean = file.canNavigate()
    override fun canNavigateToSource(): Boolean = file.canNavigateToSource()
    override fun getAlphaSortKey(): String = file.name

    override fun getPresentation(): ItemPresentation = object : ItemPresentation {
        override fun getPresentableText(): String = file.name
        override fun getIcon(unused: Boolean): Icon? = file.fileType.icon
    }

    override fun getChildren(): Array<StructureViewTreeElement> {
        val items = mutableListOf<StructureViewTreeElement>()
        for (child in file.children) {
            when {
                child is NasmSectionStmt -> items += NasmSectionTreeElement(child)
                child is NasmNamedElement -> items += NasmLabelTreeElement(child)
            }
        }
        return items.toTypedArray()
    }
}

private class NasmLabelTreeElement(private val stmt: NasmNamedElement) :
    StructureViewTreeElement, SortableTreeElement {

    private val name: String = stmt.name ?: "<label>"

    override fun getValue(): Any = stmt
    override fun navigate(requestFocus: Boolean) =
        (stmt as? com.intellij.pom.Navigatable)?.navigate(requestFocus) ?: Unit
    override fun canNavigate(): Boolean = (stmt as? com.intellij.pom.Navigatable)?.canNavigate() == true
    override fun canNavigateToSource(): Boolean =
        (stmt as? com.intellij.pom.Navigatable)?.canNavigateToSource() == true
    override fun getAlphaSortKey(): String = name
    override fun getChildren(): Array<StructureViewTreeElement> = emptyArray()

    override fun getPresentation(): ItemPresentation = object : ItemPresentation {
        override fun getPresentableText(): String = name
        override fun getIcon(unused: Boolean): Icon? = null
    }
}

private class NasmSectionTreeElement(private val stmt: NasmSectionStmt) :
    StructureViewTreeElement, SortableTreeElement {

    private val name: String = stmt.text.trim()

    override fun getValue(): Any = stmt
    override fun navigate(requestFocus: Boolean) =
        (stmt as? com.intellij.pom.Navigatable)?.navigate(requestFocus) ?: Unit
    override fun canNavigate(): Boolean = (stmt as? com.intellij.pom.Navigatable)?.canNavigate() == true
    override fun canNavigateToSource(): Boolean =
        (stmt as? com.intellij.pom.Navigatable)?.canNavigateToSource() == true
    override fun getAlphaSortKey(): String = name
    override fun getChildren(): Array<StructureViewTreeElement> = emptyArray()

    override fun getPresentation(): ItemPresentation = object : ItemPresentation {
        override fun getPresentableText(): String = name
        override fun getIcon(unused: Boolean): Icon? = null
    }
}
