package me.lucaperri.dev.languages.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.util.ui.FormBuilder
import me.lucaperri.dev.languages.run.toolchain.PlatformHelper
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

// Settings → Assembly → General.
// Hosts plugin-wide preferences that aren't paths: platform override,
// default NASM architecture, and default project type for the New Project
// dialog.
class AssemblyGeneralConfigurable : Configurable {

    private var platformCombo: ComboBox<PlatformOverride>? = null
    private var archCombo: ComboBox<NasmArch>? = null
    private var mipsArchCombo: ComboBox<MipsArch>? = null
    private var typeCombo: ComboBox<ProjectType>? = null
    private var detectedLabel: JLabel? = null

    override fun getDisplayName(): String = "General"

    override fun createComponent(): JComponent {
        val settings = AsmExecutableSettings.getInstance()

        platformCombo = ComboBox(DefaultComboBoxModel(PlatformOverride.values())).apply {
            selectedItem = settings.platformOverride
            addActionListener { detectedLabel?.text = detectedHtml() }
        }
        archCombo = ComboBox(DefaultComboBoxModel(NasmArch.values())).apply {
            selectedItem = settings.defaultNasmArch
        }
        mipsArchCombo = ComboBox(DefaultComboBoxModel(MipsArch.values())).apply {
            selectedItem = settings.defaultMipsArch
        }
        typeCombo = ComboBox(DefaultComboBoxModel(ProjectType.values())).apply {
            selectedItem = settings.defaultProjectType
        }
        detectedLabel = JLabel(detectedHtml())

        val mipsArchHelp = JLabel(
            "<html><body style='width: 480px'><i>Passed as <code>-march=&lt;value&gt;</code> " +
            "to the MIPS assembler and embedded in CMAKE_ASM_FLAGS of new MIPS projects. " +
            "Choose <code>mips32r2</code> or higher for <code>rotr</code>, <code>ext</code>, " +
            "<code>ins</code>, <code>clz</code>, <code>seb</code>, and the MIPS32r2 " +
            "instruction set.</i></body></html>"
        )

        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Platform override:", platformCombo!!)
            .addComponent(detectedLabel!!)
            .addSeparator()
            .addLabeledComponent("Default NASM architecture:", archCombo!!)
            .addLabeledComponent("Default MIPS architecture:", mipsArchCombo!!)
            .addComponent(mipsArchHelp)
            .addLabeledComponent("Default new-project type:", typeCombo!!)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    private fun detectedHtml(): String {
        // Reflect the in-form selection rather than the persisted value, so the
        // user sees the consequence of their choice before clicking Apply.
        val previewOverride = platformCombo?.selectedItem as? PlatformOverride
        val persisted = AsmExecutableSettings.getInstance().platformOverride
        val body = if (previewOverride != null && previewOverride != persisted) {
            "<i>Preview: ${describeFor(previewOverride)} — apply to take effect.</i>"
        } else {
            "<i>Detected: ${PlatformHelper.description()}</i>"
        }
        return "<html><body style='width: 480px'>$body</body></html>"
    }

    private fun describeFor(o: PlatformOverride): String = when (o) {
        PlatformOverride.AUTO    -> "auto-detect"
        PlatformOverride.WINDOWS -> "Windows (WSL required)"
        PlatformOverride.LINUX   -> "Linux"
        PlatformOverride.MACOS   -> "macOS"
    }

    override fun isModified(): Boolean {
        val s = AsmExecutableSettings.getInstance()
        return (platformCombo?.selectedItem as? PlatformOverride) != s.platformOverride
            || (archCombo?.selectedItem as? NasmArch) != s.defaultNasmArch
            || (mipsArchCombo?.selectedItem as? MipsArch) != s.defaultMipsArch
            || (typeCombo?.selectedItem as? ProjectType) != s.defaultProjectType
    }

    override fun apply() {
        val s = AsmExecutableSettings.getInstance()
        (platformCombo?.selectedItem as? PlatformOverride)?.let { s.platformOverride = it }
        (archCombo?.selectedItem as? NasmArch)?.let { s.defaultNasmArch = it }
        (mipsArchCombo?.selectedItem as? MipsArch)?.let { s.defaultMipsArch = it }
        (typeCombo?.selectedItem as? ProjectType)?.let { s.defaultProjectType = it }
        detectedLabel?.text = detectedHtml()
    }

    override fun reset() {
        val s = AsmExecutableSettings.getInstance()
        platformCombo?.selectedItem = s.platformOverride
        archCombo?.selectedItem = s.defaultNasmArch
        mipsArchCombo?.selectedItem = s.defaultMipsArch
        typeCombo?.selectedItem = s.defaultProjectType
        detectedLabel?.text = detectedHtml()
    }

    override fun disposeUIResources() {
        platformCombo = null
        archCombo = null
        mipsArchCombo = null
        typeCombo = null
        detectedLabel = null
    }
}
