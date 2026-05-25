package me.lucaperri.dev.languages.settings

import com.intellij.openapi.options.Configurable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

// Top-level grouping node for the "Assembly" settings tree.
// Has no settings of its own — the three sub-pages (General / Executables /
// Debugger) carry the actual UI. Selecting this node in the tree shows a
// short orientation label.
class AssemblyRootConfigurable : Configurable {

    override fun getDisplayName(): String = "Assembly"

    override fun createComponent(): JComponent {
        val label = JLabel(
            "<html><body style='width: 480px'><i>Configure NASM and MIPS assembly support. " +
            "Use the sub-sections to set executable paths, defaults, and debugger options.</i></body></html>"
        )
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(12)
        panel.add(label, BorderLayout.NORTH)
        return panel
    }

    override fun isModified(): Boolean = false
    override fun apply() {}
    override fun reset() {}
}
