package me.lucaperri.dev.languages.editor

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import me.lucaperri.dev.languages.psi.NasmNamedElement
import me.lucaperri.dev.languages.psi.NasmTypes

// Adds a "navigate to usages" gutter icon next to every label definition.
// Markers are attached to the IDENTIFIER leaf (not the label_def element) per
// the platform contract that line markers target leaf elements.
class NasmLineMarkerProvider : RelatedItemLineMarkerProvider() {

    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>
    ) {
        if (element.node?.elementType !== NasmTypes.IDENTIFIER) return
        val def = element.parent as? NasmNamedElement ?: return
        if (def.nameIdentifier !== element) return

        val targets = ReferencesSearch.search(def).findAll().mapNotNull { it.element }
        if (targets.isEmpty()) return

        val builder = NavigationGutterIconBuilder
            .create(AllIcons.Hierarchy.Subtypes)
            .setTargets(targets)
            .setTooltipText("Navigate to usages of '${def.name}'")
        result.add(builder.createLineMarkerInfo(element))
    }
}
