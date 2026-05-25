package me.lucaperri.dev.languages.run.debug

import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMExternalizerUtil
import org.jdom.Element
import java.io.File

// Cross-arch NASM debug: assemble+link with DWARF symbols, then start a
// qemu-x86_64 GDB stub so an x86-64 binary can be debugged on a host that
// can't execute it natively (ARM Linux / ARM Windows). Mirrors
// MipsQemuServerRunConfiguration. Use as the "Before launch" step of a
// Remote GDB Server debug configuration.
class NasmQemuServerRunConfiguration(project: Project, factory: ConfigurationFactory, name: String)
    : LocatableConfigurationBase<RunConfigurationOptions>(project, factory, name) {

    var scriptName       = ""
    var workingDirectory = ""
    var nasmFormat       = "elf64"
    var cInterop         = false
    var gdbPort          = 1234

    override fun getConfigurationEditor(): SettingsEditor<NasmQemuServerRunConfiguration> =
        NasmQemuServerRunConfigurationEditor()

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
        NasmQemuServerCommandLineState(environment, this)

    override fun checkConfiguration() {
        if (scriptName.isBlank()) throw RuntimeConfigurationError("Source file not specified")
        if (gdbPort !in 1..65535) throw RuntimeConfigurationError("GDB port must be 1–65535")
    }

    override fun suggestedName(): String? =
        if (scriptName.isBlank()) null
        else "${File(scriptName).nameWithoutExtension} (QEMU Server)"

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        JDOMExternalizerUtil.writeField(element, "SCRIPT_NAME",       scriptName)
        JDOMExternalizerUtil.writeField(element, "WORKING_DIRECTORY", workingDirectory)
        JDOMExternalizerUtil.writeField(element, "NASM_FORMAT",       nasmFormat)
        JDOMExternalizerUtil.writeField(element, "C_INTEROP",         cInterop.toString())
        JDOMExternalizerUtil.writeField(element, "GDB_PORT",          gdbPort.toString())
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        scriptName       = JDOMExternalizerUtil.readField(element, "SCRIPT_NAME")       ?: ""
        workingDirectory = JDOMExternalizerUtil.readField(element, "WORKING_DIRECTORY") ?: ""
        nasmFormat       = JDOMExternalizerUtil.readField(element, "NASM_FORMAT")       ?: "elf64"
        cInterop         = JDOMExternalizerUtil.readField(element, "C_INTEROP") == "true"
        gdbPort          = JDOMExternalizerUtil.readField(element, "GDB_PORT")?.toIntOrNull() ?: 1234
    }
}
