package me.lucaperri.dev.languages.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.util.ui.FormBuilder
import me.lucaperri.dev.languages.run.toolchain.PlatformHelper
import me.lucaperri.dev.languages.run.toolchain.WslToolchainDetector
import me.lucaperri.dev.languages.run.toolchain.WslToolchainProbe
import java.awt.datatransfer.StringSelection
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

// Settings → Assembly → Executables.
// Lets the user point the external annotators and run-pipeline at assembler,
// linker, emulator, and debugger binaries that aren't on PATH.
class AssemblyExecutablesConfigurable : Configurable {

    private var nasmField: TextFieldWithBrowseButton? = null
    private var ldField: TextFieldWithBrowseButton? = null
    private var mipsField: TextFieldWithBrowseButton? = null
    private var mipsLdField: TextFieldWithBrowseButton? = null
    private var mipsEmulatorField: TextFieldWithBrowseButton? = null
    private var gdbField: TextFieldWithBrowseButton? = null

    override fun getDisplayName(): String = "Executables"

    override fun createComponent(): JComponent {
        val settings = AsmExecutableSettings.getInstance()

        fun browseField(path: String, title: String) = TextFieldWithBrowseButton().apply {
            text = path
            addBrowseFolderListener(
                null,
                FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor().withTitle(title)
            )
        }

        nasmField         = browseField(settings.nasmPath,         "Select NASM Executable")
        ldField           = browseField(settings.ldPath,           "Select Linker (ld / x86-64 linker)")
        mipsField         = browseField(settings.mipsAsPath,       "Select MIPS Assembler (mips-*-as)")
        mipsLdField       = browseField(settings.mipsLdPath,       "Select MIPS Linker (mips-*-ld)")
        mipsEmulatorField = browseField(settings.mipsEmulatorPath, "Select MIPS Emulator (qemu-mips-static)")
        gdbField          = browseField(settings.gdbPath,          "Select GDB Executable")

        val distro = WslToolchainDetector.wslDistroName()
        val platformNote = if (PlatformHelper.isWindows) {
            if (distro != null)
                "Detected: ${PlatformHelper.description()}. Commands run in WSL distro <b>$distro</b> " +
                "(from your active CLion toolchain). Leave a path blank to probe that distro's PATH."
            else
                "Detected: ${PlatformHelper.description()}. <b>No WSL toolchain detected — using the " +
                "default WSL distro.</b> Set your WSL toolchain as the <b>default</b> (Settings → Build, " +
                "Execution, Deployment → Toolchains, move it to the top with ↑) so the plugin probes and " +
                "builds in the same distribution CMake uses."
        } else
            "Detected: ${PlatformHelper.description()} — paths below are resolved on the host. Leave blank to probe PATH."

        val verifyButton = JButton("Verify Toolchain").apply {
            addActionListener { verifyToolchain(this) }
        }
        val copyButton = JButton("Copy Install Command").apply {
            addActionListener { copyInstallCommand(this) }
        }

        return FormBuilder.createFormBuilder()
            .addComponent(JLabel("<html><body style='width: 480px'><i>$platformNote</i></body></html>"))
            .addSeparator()
            .addLabeledComponent("NASM executable:", nasmField!!)
            .addLabeledComponent("Linker driver (gcc):", ldField!!)
            .addSeparator()
            .addLabeledComponent("MIPS assembler (mips-linux-gnu-as):", mipsField!!)
            .addLabeledComponent("MIPS linker (mips-linux-gnu-ld):", mipsLdField!!)
            .addLabeledComponent("MIPS emulator (qemu-mips-static):", mipsEmulatorField!!)
            .addSeparator()
            .addLabeledComponent("GDB executable:", gdbField!!)
            .addSeparator()
            .addComponent(verifyButton)
            .addComponent(JLabel("<html><body style='width: 480px'><i>Probes the toolchain " +
                "(WSL on Windows, host otherwise) for the tools each build/debug path needs, " +
                "and fills in any blank paths above that it finds. Uses the paths as currently " +
                "shown.</i></body></html>"))
            .addComponent(copyButton)
            .addComponent(JLabel("<html><body style='width: 480px'><i>Copies the one-line " +
                "<code>apt</code> command that installs the full NASM + MIPS build/debug pipeline " +
                "(run it inside your WSL distribution).</i></body></html>"))
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    private fun copyInstallCommand(parent: JComponent) {
        val cmd = WslToolchainProbe.fullInstallCommand()
        CopyPasteManager.getInstance().setContents(StringSelection(cmd))
        Messages.showMessageDialog(parent, "Copied to clipboard:\n\n$cmd",
            "Install Command", Messages.getInformationIcon())
    }

    // Resolves every tool's absolute path (against the paths currently shown in
    // the form, not yet-saved state), shows a per-capability checklist, and
    // fills any blank path field with a discovered location.
    private fun verifyToolchain(parent: JComponent) {
        val live = AsmExecutableSettings.getInstance()
        val snapshot = AsmExecutableSettings().apply {
            loadState(live.getState().copy(
                nasmPath         = nasmField?.text?.trim().orEmpty(),
                ldPath           = ldField?.text?.trim().orEmpty(),
                mipsAsPath       = mipsField?.text?.trim().orEmpty(),
                mipsLdPath       = mipsLdField?.text?.trim().orEmpty(),
                mipsEmulatorPath = mipsEmulatorField?.text?.trim().orEmpty(),
                gdbPath          = gdbField?.text?.trim().orEmpty(),
            ))
        }

        val allTools = WslToolchainProbe.Tool.values().toList()
        val paths = ProgressManager.getInstance().runProcessWithProgressSynchronously<
            Map<WslToolchainProbe.Tool, String>, RuntimeException>(
            { WslToolchainProbe.resolvePaths(allTools, snapshot) },
            "Verifying Assembly Toolchain", true, null
        )

        val report = buildString {
            for (cap in WslToolchainProbe.Capability.values()) {
                appendLine(cap.label)
                for (tool in cap.tools) {
                    val p = paths[tool].orEmpty()
                    if (p.isNotEmpty()) appendLine("  ✓ ${tool.bin} → $p")
                    else appendLine("  ✗ ${tool.bin}   → sudo apt install ${tool.aptPackage}")
                }
                appendLine()
            }
        }.trimEnd()

        val filled = fillBlankFieldsFrom(paths)
        val foundCount = paths.values.count { it.isNotEmpty() }
        val blankCount = listOf(nasmField, ldField, mipsField, mipsLdField, mipsEmulatorField, gdbField)
            .count { it?.text.isNullOrBlank() }
        val suffix = when {
            filled > 0 ->
                "\n\nFilled $filled blank path field(s) from discovered locations — click Apply to save."
            foundCount == 0 ->
                "\n\nNo tools were discovered. If the distro shown above is wrong, set your WSL toolchain " +
                "as the default in Settings → Build, Execution, Deployment → Toolchains. Otherwise, " +
                "install the missing packages with the \"Copy Install Command\" button, or set explicit " +
                "paths below.\n\n(For diagnostics, enable INFO logging for category " +
                "\"#me.lucaperri.dev.languages.run.toolchain.WslToolchainProbe\" via Help → Diagnostic " +
                "Tools → Debug Log Settings, then re-run Verify and check idea.log.)"
            blankCount == 0 ->
                "\n\nAll path fields are already populated — nothing to fill. Clear a field and re-run " +
                "Verify Toolchain to replace it with a discovered path."
            else ->
                "\n\nNo blank fields matched a discovered tool. The probed paths above list what was found."
        }
        Messages.showMessageDialog(parent, report + suffix, "Toolchain Verification", Messages.getInformationIcon())
    }

    // Fills blank path fields with discovered absolute paths; returns the count.
    private fun fillBlankFieldsFrom(paths: Map<WslToolchainProbe.Tool, String>): Int {
        var n = 0
        fun fill(field: TextFieldWithBrowseButton?, tool: WslToolchainProbe.Tool) {
            val p = paths[tool].orEmpty()
            if (p.isNotEmpty() && field != null && field.text.isNullOrBlank()) {
                field.text = p; n++
            }
        }
        fill(nasmField,         WslToolchainProbe.Tool.NASM)
        fill(ldField,           WslToolchainProbe.Tool.GCC)
        fill(mipsField,         WslToolchainProbe.Tool.MIPS_AS)
        fill(mipsLdField,       WslToolchainProbe.Tool.MIPS_LD)
        fill(mipsEmulatorField, WslToolchainProbe.Tool.QEMU_MIPS)
        fill(gdbField,          WslToolchainProbe.Tool.GDB_MULTIARCH)
        return n
    }

    override fun isModified(): Boolean {
        val s = AsmExecutableSettings.getInstance()
        return nasmField?.text?.trim() != s.nasmPath
            || ldField?.text?.trim() != s.ldPath
            || mipsField?.text?.trim() != s.mipsAsPath
            || mipsLdField?.text?.trim() != s.mipsLdPath
            || mipsEmulatorField?.text?.trim() != s.mipsEmulatorPath
            || gdbField?.text?.trim() != s.gdbPath
    }

    override fun apply() {
        val s = AsmExecutableSettings.getInstance()
        s.nasmPath         = nasmField?.text?.trim().orEmpty()
        s.ldPath           = ldField?.text?.trim().orEmpty()
        s.mipsAsPath       = mipsField?.text?.trim().orEmpty()
        s.mipsLdPath       = mipsLdField?.text?.trim().orEmpty()
        s.mipsEmulatorPath = mipsEmulatorField?.text?.trim().orEmpty()
        s.gdbPath          = gdbField?.text?.trim().orEmpty()
    }

    override fun reset() {
        val s = AsmExecutableSettings.getInstance()
        nasmField?.text         = s.nasmPath
        ldField?.text           = s.ldPath
        mipsField?.text         = s.mipsAsPath
        mipsLdField?.text       = s.mipsLdPath
        mipsEmulatorField?.text = s.mipsEmulatorPath
        gdbField?.text          = s.gdbPath
    }

    override fun disposeUIResources() {
        nasmField = null
        ldField = null
        mipsField = null
        mipsLdField = null
        mipsEmulatorField = null
        gdbField = null
    }
}
