package me.lucaperri.dev.languages.run.project

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.GeneratorPeerImpl
import com.intellij.platform.ProjectGeneratorPeer
import com.intellij.util.ui.FormBuilder
import com.jetbrains.cidr.cpp.cmake.projectWizard.generators.CLionProjectGenerator
import me.lucaperri.dev.languages.NasmLanguage
import me.lucaperri.dev.languages.run.AsmRunConfigurationCreator
import me.lucaperri.dev.languages.run.toolchain.PlatformHelper
import me.lucaperri.dev.languages.settings.AsmExecutableSettings
import me.lucaperri.dev.languages.settings.NasmArch
import me.lucaperri.dev.languages.settings.ProjectType
import javax.swing.*

// CLion's New-Project wizard renders custom generator UI through
// CLionProjectGenerator.getSettingsPanel() — *not* through the generic
// DirectoryProjectGenerator peer hooks, which it ignores. Hooking into this
// puts our Language/Format/Mode controls in the wizard's "Advanced Settings"
// expandable section underneath the standard Name/Location fields.
class AsmCLionProjectGenerator : CLionProjectGenerator<Unit>() {

    private val nasmButton     = JRadioButton("NASM (x86/x64)")
    private val mipsButton     = JRadioButton("MIPS (MIPS32)")
    private val x64Button      = JRadioButton("elf64 — 64-bit Linux")
    private val x32Button      = JRadioButton("elf32 — 32-bit Linux")
    private val pureAsmButton  = JRadioButton("Pure ASM", true)
    private val cInteropButton = JRadioButton("C Interop")
    private val noteLabel      = JLabel()

    init {
        ButtonGroup().apply { add(nasmButton);    add(mipsButton)    }
        ButtonGroup().apply { add(x64Button);     add(x32Button)     }
        ButtonGroup().apply { add(pureAsmButton); add(cInteropButton) }

        val settings = runCatching { AsmExecutableSettings.getInstance() }.getOrNull()
        if (settings?.defaultProjectType == ProjectType.MIPS) mipsButton.isSelected = true else nasmButton.isSelected = true
        if (settings?.defaultNasmArch == NasmArch.X32) x32Button.isSelected = true else x64Button.isSelected = true

        nasmButton.addItemListener { updateControls() }
        mipsButton.addItemListener { updateControls() }
        updateControls()
    }

    override fun getName(): String = "Assembly"

    override fun getLogo(): Icon = NasmLanguage.icon

    // Peer is required by the wizard but carries no settings — we read the
    // radio-button state directly in generateProject(). Returning Unit keeps
    // the T parameter non-null (CLionProjectGenerator's @NotNull contract).
    override fun createPeer(): ProjectGeneratorPeer<Unit> =
        object : GeneratorPeerImpl<Unit>(Unit, JPanel()) {}

    override fun getSettingsPanel(): JComponent? =
        FormBuilder.createFormBuilder()
            .addLabeledComponent("Language:", horizontalRow(nasmButton, mipsButton))
            .addLabeledComponent("Format:",   horizontalRow(x64Button, x32Button))
            .addLabeledComponent("Mode:",     horizontalRow(pureAsmButton, cInteropButton))
            .addComponent(noteLabel)
            .panel

    override fun generateProject(project: Project, baseDir: VirtualFile, settings: Unit, module: Module) {
        val name     = baseDir.name
        val isNasm   = nasmButton.isSelected
        val format   = if (isNasm && x32Button.isSelected) "elf32" else "elf64"
        val cInterop = cInteropButton.isSelected
        val ext      = if (isNasm) "nasm" else "mips"

        var cmakeVFile: VirtualFile? = null
        WriteAction.runAndWait<RuntimeException> {
            val cmake = baseDir.findOrCreateChildData(this, "CMakeLists.txt")
            cmakeVFile = cmake
            VfsUtil.saveText(
                cmake,
                if (isNasm) AsmProjectTemplates.nasmCmake(name, format, cInterop)
                else        AsmProjectTemplates.mipsCmake(name, cInterop)
            )
            VfsUtil.saveText(
                baseDir.findOrCreateChildData(this, "main.$ext"),
                if (isNasm) AsmProjectTemplates.nasmSrc(format, cInterop)
                else        AsmProjectTemplates.mipsSrc(cInterop)
            )
            // Only write .gitignore when the project doesn't already have one,
            // so we don't clobber a user's existing rules.
            if (baseDir.findChild(".gitignore") == null) {
                VfsUtil.saveText(
                    baseDir.findOrCreateChildData(this, ".gitignore"),
                    AsmProjectTemplates.gitignore()
                )
            }
        }
        cmakeVFile?.let { f ->
            AsmRunConfigurationCreator.linkAndConfigureCmakeFile(project, f)
                ?.let { AsmRunConfigurationCreator.maybeGuide(project, setOf(it)) }
        }
    }

    private fun updateControls() {
        val isNasm = nasmButton.isSelected
        x64Button.isEnabled = isNasm
        x32Button.isEnabled = isNasm
        noteLabel.text = buildNote(!isNasm)
    }

    private fun buildNote(isMips: Boolean): String =
        if (PlatformHelper.isWindows) {
            val debug = if (isMips)
                "Debug: needs qemu-mips-static&nbsp;-g&nbsp;&lt;port&gt; + mips-linux-gnu-gdb."
            else
                "Debug: configure a WSL toolchain (Settings → Build → Toolchains → WSL)."
            "<html><i>Windows — assembler runs inside WSL.<br>" +
            "Run: zero setup via the Assembly Run Configuration.<br>$debug</i></html>"
        } else if (isMips)
            "<html><i>${PlatformHelper.description()} — debug via qemu-mips-static&nbsp;-g&nbsp;&lt;port&gt; + " +
            "mips-linux-gnu-gdb and a CLion Remote Debug configuration.</i></html>"
        else
            "<html><i>${PlatformHelper.description()} — debug works with the native GDB toolchain.</i></html>"

    private fun horizontalRow(vararg components: JComponent): JPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        components.forEachIndexed { i, c ->
            if (i > 0) add(Box.createHorizontalStrut(12))
            add(c)
        }
    }
}
