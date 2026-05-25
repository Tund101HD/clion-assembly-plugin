package me.lucaperri.dev.languages.run.debug

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.util.ui.FormBuilder
import javax.swing.*

class NasmQemuServerRunConfigurationEditor : SettingsEditor<NasmQemuServerRunConfiguration>() {
    private val scriptField    = TextFieldWithBrowseButton()
    private val workDirField   = TextFieldWithBrowseButton()
    private val formatCombo    = ComboBox(arrayOf("elf64", "elf32"))
    private val cInteropCheck  = JCheckBox("C interop — link the C runtime (main entry point)")
    private val gdbPortSpinner = JSpinner(SpinnerNumberModel(1234, 1, 65535, 1))

    init {
        scriptField.addBrowseFolderListener(
            null,
            FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
                .withTitle("Select NASM Source File")
        )
        workDirField.addBrowseFolderListener(
            null,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
                .withTitle("Select Working Directory")
        )
        // Tighten the spinner width so it doesn't stretch to fill the row.
        (gdbPortSpinner.editor as? JSpinner.DefaultEditor)?.textField?.columns = 6
    }

    override fun resetEditorFrom(config: NasmQemuServerRunConfiguration) {
        scriptField.text         = config.scriptName
        workDirField.text        = config.workingDirectory
        formatCombo.selectedItem  = config.nasmFormat
        cInteropCheck.isSelected = config.cInterop
        gdbPortSpinner.value     = config.gdbPort
    }

    override fun applyEditorTo(config: NasmQemuServerRunConfiguration) {
        config.scriptName       = scriptField.text.trim()
        config.workingDirectory = workDirField.text.trim()
        config.nasmFormat       = (formatCombo.selectedItem as? String) ?: "elf64"
        config.cInterop         = cInteropCheck.isSelected
        config.gdbPort          = gdbPortSpinner.value as Int
    }

    override fun createEditor(): JComponent = FormBuilder.createFormBuilder()
        .addLabeledComponent("Source file:", scriptField)
        .addLabeledComponent("Working directory:", workDirField)
        .addLabeledComponent("Output format:", formatCombo)
        .addComponent(cInteropCheck)
        .addLabeledComponent("GDB port:", gdbPortSpinner)
        .addComponentFillVertically(JPanel(), 0)
        .panel
}
