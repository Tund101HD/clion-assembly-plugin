package me.lucaperri.dev.languages.run.debug

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.util.ui.FormBuilder
import me.lucaperri.dev.languages.run.MarchChoice
import javax.swing.*

class MipsQemuServerRunConfigurationEditor : SettingsEditor<MipsQemuServerRunConfiguration>() {
    private val scriptField        = TextFieldWithBrowseButton()
    private val workDirField       = TextFieldWithBrowseButton()
    private val assemblerArgsField = JTextField()
    private val cInteropCheck      = JCheckBox("C interop — compile via mips-linux-gnu-gcc")
    private val gdbPortSpinner     = JSpinner(SpinnerNumberModel(1234, 1, 65535, 1))
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
        // Tighten the spinner width so it doesn't stretch to fill the row.
        (gdbPortSpinner.editor as? JSpinner.DefaultEditor)?.textField?.columns = 6
    }

    override fun resetEditorFrom(config: MipsQemuServerRunConfiguration) {
        scriptField.text         = config.scriptName
        workDirField.text        = config.workingDirectory
        assemblerArgsField.text  = config.assemblerArgs
        cInteropCheck.isSelected = config.cInterop
        gdbPortSpinner.value     = config.gdbPort
        marchCombo.selectedItem  = MarchChoice.fromArch(config.marchOverride)
    }

    override fun applyEditorTo(config: MipsQemuServerRunConfiguration) {
        config.scriptName       = scriptField.text.trim()
        config.workingDirectory = workDirField.text.trim()
        config.assemblerArgs    = assemblerArgsField.text.trim()
        config.cInterop         = cInteropCheck.isSelected
        config.gdbPort          = gdbPortSpinner.value as Int
        config.marchOverride    = (marchCombo.selectedItem as? MarchChoice)?.arch
    }

    override fun createEditor(): JComponent = FormBuilder.createFormBuilder()
        .addLabeledComponent("Source file:", scriptField)
        .addLabeledComponent("Working directory:", workDirField)
        .addLabeledComponent("MIPS architecture (-march):", marchCombo)
        .addLabeledComponent("Extra assembler flags:", assemblerArgsField)
        .addComponent(cInteropCheck)
        .addLabeledComponent("GDB port:", gdbPortSpinner)
        .addComponentFillVertically(JPanel(), 0)
        .panel
}
