package me.lucaperri.dev.languages.run.project

import me.lucaperri.dev.languages.run.toolchain.PlatformHelper

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.util.ui.FormBuilder
import me.lucaperri.dev.languages.settings.AsmExecutableSettings
import me.lucaperri.dev.languages.settings.NasmArch
import me.lucaperri.dev.languages.settings.ProjectType
import java.awt.Dimension
import javax.swing.*

class NewAsmProjectDialog(project: Project) : DialogWrapper(project) {

    private val nameField    = JTextField("hello_asm", 22)
    private val nasmButton   = JRadioButton("NASM (x86/x64)")
    private val mipsButton   = JRadioButton("MIPS (MIPS32)")
    private val x64Button    = JRadioButton("elf64 — 64-bit Linux")
    private val x32Button    = JRadioButton("elf32 — 32-bit Linux")
    private val pureAsmButton = JRadioButton("Pure ASM", true)
    private val cInteropButton = JRadioButton("C Interop")

    enum class Type { NASM, MIPS }
    data class Result(val name: String, val type: Type, val format: String, val cInterop: Boolean)

    private val noteLabel = JLabel()

    init {
        title = "New Assembly CMake Project"
        ButtonGroup().apply { add(nasmButton);    add(mipsButton)    }
        ButtonGroup().apply { add(x64Button);     add(x32Button)     }
        ButtonGroup().apply { add(pureAsmButton); add(cInteropButton) }

        val settings = runCatching { AsmExecutableSettings.getInstance() }.getOrNull()
        val defaultType = settings?.defaultProjectType ?: ProjectType.NASM
        val defaultArch = settings?.defaultNasmArch ?: NasmArch.X64
        if (defaultType == ProjectType.MIPS) mipsButton.isSelected = true else nasmButton.isSelected = true
        if (defaultArch == NasmArch.X32) x32Button.isSelected = true else x64Button.isSelected = true

        nasmButton.addItemListener { updateControls() }
        mipsButton.addItemListener { updateControls() }
        updateControls()
        init()
    }

    private fun updateControls() {
        x64Button.isEnabled = nasmButton.isSelected
        x32Button.isEnabled = nasmButton.isSelected
        noteLabel.text = buildNote()
        noteLabel.revalidate()
    }

    private fun buildNote(): String {
        val isMips = mipsButton.isSelected
        return if (PlatformHelper.isWindows) {
            val debugLine = if (isMips)
                "Debug: needs qemu-mips-static&nbsp;-g&nbsp;&lt;port&gt; + mips-linux-gnu-gdb<br>" +
                "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;and a CLion Remote Debug configuration."
            else
                "Debug: add a WSL toolchain (Settings → Build → Toolchains → WSL)."
            "<html><i>Windows — assembler runs inside WSL.<br>" +
            "Run: zero setup via the Run Configuration (right-click → Run).<br>" +
            "$debugLine<br>" +
            "CMakeLists.txt targets WSL/Linux toolchain paths.</i></html>"
        } else {
            val debugLine = if (isMips)
                "Debug via: qemu-mips-static&nbsp;-g&nbsp;&lt;port&gt; + mips-linux-gnu-gdb<br>" +
                "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;and a CLion Remote Debug configuration."
            else
                "Debug: works with the native GDB toolchain."
            "<html><i>${PlatformHelper.description()}<br>$debugLine</i></html>"
        }
    }

    override fun createCenterPanel(): JComponent {
        val typeRow = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(nasmButton); add(Box.createHorizontalStrut(12)); add(mipsButton)
        }
        val fmtRow = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(x64Button); add(Box.createHorizontalStrut(12)); add(x32Button)
        }
        val modeRow = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(pureAsmButton); add(Box.createHorizontalStrut(12)); add(cInteropButton)
        }
        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Project name:", nameField)
            .addLabeledComponent("Language:", typeRow)
            .addLabeledComponent("Format:", fmtRow)
            .addLabeledComponent("Mode:", modeRow)
            .addComponent(noteLabel)
            .addComponentFillVertically(JPanel(), 0)
            .panel
            .also { it.preferredSize = Dimension(480, it.preferredSize.height) }
    }

    override fun doValidate(): ValidationInfo? {
        val n = nameField.text.trim()
        if (n.isBlank()) return ValidationInfo("Project name is required", nameField)
        if (!n.matches(Regex("[A-Za-z][A-Za-z0-9_\\-]*")))
            return ValidationInfo("Must start with a letter; only letters, digits, _ and - allowed", nameField)
        return null
    }

    fun result(): Result {
        val type     = if (nasmButton.isSelected) Type.NASM else Type.MIPS
        val format   = if (type == Type.MIPS || x64Button.isSelected) "elf64" else "elf32"
        val cInterop = cInteropButton.isSelected
        return Result(nameField.text.trim(), type, format, cInterop)
    }
}
