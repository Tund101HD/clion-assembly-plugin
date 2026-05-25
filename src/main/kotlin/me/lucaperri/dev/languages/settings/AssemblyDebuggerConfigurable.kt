package me.lucaperri.dev.languages.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.util.ui.FormBuilder
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

// Settings → Assembly → Debugger.
// Scaffolding for upcoming CIDR/GDB integration (Phase 8). The fields here
// are persisted but currently have no runtime effect — they are wired up
// once the debugger code paths land.
class AssemblyDebuggerConfigurable : Configurable {

    private var autoLaunchCheck: JCheckBox? = null
    private var qemuX86Field: TextFieldWithBrowseButton? = null
    private var preferQemuCheck: JCheckBox? = null

    override fun getDisplayName(): String = "Debugger"

    override fun createComponent(): JComponent {
        val settings = AsmExecutableSettings.getInstance()

        autoLaunchCheck = JCheckBox("Auto-launch GDB when starting a QEMU debug configuration").apply {
            isSelected = settings.autoLaunchGdbOnQemu
        }
        qemuX86Field = TextFieldWithBrowseButton().apply {
            text = settings.qemuX86_64Path
            addBrowseFolderListener(
                null,
                FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
                    .withTitle("Select qemu-x86_64-static Executable")
            )
        }
        preferQemuCheck = JCheckBox("Always use QEMU for NASM debug (even on x86 hosts)").apply {
            isSelected = settings.preferQemuForNasmDebug
        }

        val note = "<html><body style='width: 480px'><i>Controls the QEMU-backed GDB debug " +
            "configs auto-created for assembly targets. NASM cross-arch debug uses " +
            "qemu-x86_64-static (from the qemu-user-static package).</i></body></html>"

        return FormBuilder.createFormBuilder()
            .addComponent(JLabel(note))
            .addSeparator()
            .addComponent(autoLaunchCheck!!)
            .addLabeledComponent("qemu-x86_64-static (for ARM Windows / Apple Silicon NASM debug):", qemuX86Field!!)
            .addComponent(preferQemuCheck!!)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    override fun isModified(): Boolean {
        val s = AsmExecutableSettings.getInstance()
        return autoLaunchCheck?.isSelected != s.autoLaunchGdbOnQemu
            || qemuX86Field?.text?.trim() != s.qemuX86_64Path
            || preferQemuCheck?.isSelected != s.preferQemuForNasmDebug
    }

    override fun apply() {
        val s = AsmExecutableSettings.getInstance()
        autoLaunchCheck?.isSelected?.let { s.autoLaunchGdbOnQemu = it }
        s.qemuX86_64Path = qemuX86Field?.text?.trim().orEmpty()
        preferQemuCheck?.isSelected?.let { s.preferQemuForNasmDebug = it }
    }

    override fun reset() {
        val s = AsmExecutableSettings.getInstance()
        autoLaunchCheck?.isSelected = s.autoLaunchGdbOnQemu
        qemuX86Field?.text = s.qemuX86_64Path
        preferQemuCheck?.isSelected = s.preferQemuForNasmDebug
    }

    override fun disposeUIResources() {
        autoLaunchCheck = null
        qemuX86Field = null
        preferQemuCheck = null
    }
}
