package me.lucaperri.dev.languages.run

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.util.ui.FormBuilder
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField

class NasmRunConfigurationEditor : SettingsEditor<NasmRunConfiguration>() {
    private val scriptField        = TextFieldWithBrowseButton()
    private val workDirField       = TextFieldWithBrowseButton()
    private val programParamsField = JTextField()
    private val formatCombo        = ComboBox(arrayOf("elf64", "elf32", "macho64", "win64", "win32"))
    private val cInteropCheck      = JCheckBox("C interop — link with C runtime (removes -nostdlib)")

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
    }

    override fun resetEditorFrom(config: NasmRunConfiguration) {
        scriptField.text         = config.scriptName
        workDirField.text        = config.workingDirectory
        programParamsField.text  = config.programParameters
        formatCombo.selectedItem = config.nasmFormat
        cInteropCheck.isSelected = config.cInterop
    }

    override fun applyEditorTo(config: NasmRunConfiguration) {
        config.scriptName        = scriptField.text.trim()
        config.workingDirectory  = workDirField.text.trim()
        config.programParameters = programParamsField.text.trim()
        config.nasmFormat        = formatCombo.selectedItem as? String ?: "elf64"
        config.cInterop          = cInteropCheck.isSelected
    }

    override fun createEditor(): JComponent = FormBuilder.createFormBuilder()
        .addLabeledComponent("Source file:", scriptField)
        .addLabeledComponent("Working directory:", workDirField)
        .addLabeledComponent("Program arguments:", programParamsField)
        .addLabeledComponent("Target format:", formatCombo)
        .addComponent(cInteropCheck)
        .addComponentFillVertically(JPanel(), 0)
        .panel
}
