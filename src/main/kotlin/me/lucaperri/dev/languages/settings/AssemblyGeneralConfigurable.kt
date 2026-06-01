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
    private var mipsAbiCombo: ComboBox<MipsAbi>? = null
    private var mipsAbiHintLabel: JLabel? = null
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
        mipsAbiHintLabel = JLabel(abiHintHtml(
            settings.defaultMipsArch,
            settings.defaultMipsAbi
        ))
        mipsArchCombo = ComboBox(DefaultComboBoxModel(MipsArch.values())).apply {
            selectedItem = settings.defaultMipsArch
            addActionListener { mipsAbiHintLabel?.text = abiHintHtml(
                selectedItem as? MipsArch ?: MipsArch.MIPS32R2,
                mipsAbiCombo?.selectedItem as? MipsAbi ?: MipsAbi.AUTO
            )}
        }
        mipsAbiCombo = ComboBox(DefaultComboBoxModel(MipsAbi.values())).apply {
            selectedItem = settings.defaultMipsAbi
            addActionListener { mipsAbiHintLabel?.text = abiHintHtml(
                mipsArchCombo?.selectedItem as? MipsArch ?: MipsArch.MIPS32R2,
                selectedItem as? MipsAbi ?: MipsAbi.AUTO
            )}
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
        val mipsAbiHelp = JLabel(
            "<html><body style='width: 480px'><i>Passed as <code>-mabi=&lt;value&gt;</code>. " +
            "<b>Auto</b> derives the ABI from the architecture (32-bit for mips32* variants, " +
            "64-bit for mips64* variants). Override with an explicit value if your toolchain " +
            "defaults to a different ABI — this is the most common fix for the " +
            "<code>gp=32 used with a 64-bit ABI</code> error on ARM64 hosts.</i></body></html>"
        )

        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Platform override:", platformCombo!!)
            .addComponent(detectedLabel!!)
            .addSeparator()
            .addLabeledComponent("Default NASM architecture:", archCombo!!)
            .addLabeledComponent("Default MIPS architecture:", mipsArchCombo!!)
            .addComponent(mipsArchHelp)
            .addLabeledComponent("Default MIPS ABI:", mipsAbiCombo!!)
            .addComponent(mipsAbiHintLabel!!)
            .addComponent(mipsAbiHelp)
            .addLabeledComponent("Default new-project type:", typeCombo!!)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    private fun abiHintHtml(arch: MipsArch, abi: MipsAbi): String {
        if (abi != MipsAbi.AUTO) return ""
        val flag = arch.mabiFlag
            ?: return "<html><body style='width: 480px'><i>Auto: no <code>-mabi</code> flag passed (toolchain default)</i></body></html>"
        return "<html><body style='width: 480px'><i>Auto: passes <code>-mabi=$flag</code> for <code>${arch.marchFlag}</code></i></body></html>"
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
            || (mipsAbiCombo?.selectedItem as? MipsAbi) != s.defaultMipsAbi
            || (typeCombo?.selectedItem as? ProjectType) != s.defaultProjectType
    }

    override fun apply() {
        val s = AsmExecutableSettings.getInstance()
        (platformCombo?.selectedItem as? PlatformOverride)?.let { s.platformOverride = it }
        (archCombo?.selectedItem as? NasmArch)?.let { s.defaultNasmArch = it }
        (mipsArchCombo?.selectedItem as? MipsArch)?.let { s.defaultMipsArch = it }
        (mipsAbiCombo?.selectedItem as? MipsAbi)?.let { s.defaultMipsAbi = it }
        (typeCombo?.selectedItem as? ProjectType)?.let { s.defaultProjectType = it }
        detectedLabel?.text = detectedHtml()
    }

    override fun reset() {
        val s = AsmExecutableSettings.getInstance()
        platformCombo?.selectedItem = s.platformOverride
        archCombo?.selectedItem = s.defaultNasmArch
        mipsArchCombo?.selectedItem = s.defaultMipsArch
        mipsAbiCombo?.selectedItem = s.defaultMipsAbi
        typeCombo?.selectedItem = s.defaultProjectType
        detectedLabel?.text = detectedHtml()
        mipsAbiHintLabel?.text = abiHintHtml(s.defaultMipsArch, s.defaultMipsAbi)
    }

    override fun disposeUIResources() {
        platformCombo = null
        archCombo = null
        mipsArchCombo = null
        mipsAbiCombo = null
        mipsAbiHintLabel = null
        typeCombo = null
        detectedLabel = null
    }
}
