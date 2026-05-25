package me.lucaperri.dev.languages.editor

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import me.lucaperri.dev.languages.psi.MipsNamedElement
import me.lucaperri.dev.languages.psi.MipsTypes

// See NasmLineMarkerProvider.
class MipsLineMarkerProvider : RelatedItemLineMarkerProvider() {

    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>
    ) {
        if (element.node?.elementType !== MipsTypes.IDENTIFIER) return
        val def = element.parent as? MipsNamedElement ?: return
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
