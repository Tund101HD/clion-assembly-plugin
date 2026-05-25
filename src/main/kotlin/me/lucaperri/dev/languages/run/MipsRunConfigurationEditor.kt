package me.lucaperri.dev.languages.run

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.util.ui.FormBuilder
import me.lucaperri.dev.languages.settings.MipsArch
import javax.swing.DefaultComboBoxModel
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField

class MipsRunConfigurationEditor : SettingsEditor<MipsRunConfiguration>() {
    private val scriptField        = TextFieldWithBrowseButton()
    private val workDirField       = TextFieldWithBrowseButton()
    private val programParamsField = JTextField()
    private val assemblerArgsField = JTextField()
    private val cInteropCheck      = JCheckBox("C interop — compile via mips-linux-gnu-gcc (enables calling C functions)")
    private val marchCombo         = ComboBox(DefaultComboBoxModel(MarchChoice.OPTIONS))

    init {
        scriptField.addBrowseFolderListener(
            null,
            FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
                .withTitle("Select MIPS Source File")
        )
        workDirField.addBrowseFolderListener(
            null,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
                .withTitle("Select Working Directory")
        )
    }

    override fun resetEditorFrom(config: MipsRunConfiguration) {
        scriptField.text         = config.scriptName
        workDirField.text        = config.workingDirectory
        programParamsField.text  = config.programParameters
        assemblerArgsField.text  = config.assemblerArgs
        cInteropCheck.isSelected = config.cInterop
        marchCombo.selectedItem  = MarchChoice.fromArch(config.marchOverride)
    }

    override fun applyEditorTo(config: MipsRunConfiguration) {
        config.scriptName        = scriptField.text.trim()
        config.workingDirectory  = workDirField.text.trim()
        config.programParameters = programParamsField.text.trim()
        config.assemblerArgs     = assemblerArgsField.text.trim()
        config.cInterop          = cInteropCheck.isSelected
        config.marchOverride     = (marchCombo.selectedItem as? MarchChoice)?.arch
    }

    override fun createEditor(): JComponent = FormBuilder.createFormBuilder()
        .addLabeledComponent("Source file:", scriptField)
        .addLabeledComponent("Working directory:", workDirField)
        .addLabeledComponent("Program arguments:", programParamsField)
        .addLabeledComponent("MIPS architecture (-march):", marchCombo)
        .addLabeledComponent("Extra assembler flags:", assemblerArgsField)
        .addComponent(cInteropCheck)
        .addComponentFillVertically(JPanel(), 0)
        .panel
}

// Wrapper so the combo can show "Inherit from global" (= null) alongside the
// concrete MipsArch values without overloading the enum itself with a sentinel.
internal data class MarchChoice(val arch: MipsArch?) {
    override fun toString(): String =
        arch?.toString() ?: "Inherit from Settings → Assembly → General"

    companion object {
        val OPTIONS: Array<MarchChoice> =
            arrayOf(MarchChoice(null)) + MipsArch.values().map { MarchChoice(it) }
        fun fromArch(a: MipsArch?): MarchChoice = MarchChoice(a)
    }
}
