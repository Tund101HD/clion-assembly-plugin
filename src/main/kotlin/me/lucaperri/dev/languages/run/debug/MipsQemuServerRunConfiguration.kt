package me.lucaperri.dev.languages.run.debug

import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMExternalizerUtil
import me.lucaperri.dev.languages.settings.MipsArch
import org.jdom.Element
import java.io.File

class MipsQemuServerRunConfiguration(project: Project, factory: ConfigurationFactory, name: String)
    : LocatableConfigurationBase<RunConfigurationOptions>(project, factory, name) {

    var scriptName    = ""
    var workingDirectory = ""
    var assemblerArgs = ""
    var cInterop      = false
    var gdbPort       = 1234
    // null = inherit from Settings → Assembly → General → Default MIPS architecture
    var marchOverride: MipsArch? = null

    override fun getConfigurationEditor(): SettingsEditor<MipsQemuServerRunConfiguration> =
        MipsQemuServerRunConfigurationEditor()

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
        MipsQemuServerCommandLineState(environment, this)

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
        JDOMExternalizerUtil.writeField(element, "ASSEMBLER_ARGS",    assemblerArgs)
        JDOMExternalizerUtil.writeField(element, "C_INTEROP",         cInterop.toString())
        JDOMExternalizerUtil.writeField(element, "GDB_PORT",          gdbPort.toString())
        JDOMExternalizerUtil.writeField(element, "MARCH_OVERRIDE",    marchOverride?.name ?: "")
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        scriptName       = JDOMExternalizerUtil.readField(element, "SCRIPT_NAME")       ?: ""
        workingDirectory = JDOMExternalizerUtil.readField(element, "WORKING_DIRECTORY") ?: ""
        assemblerArgs    = JDOMExternalizerUtil.readField(element, "ASSEMBLER_ARGS")    ?: ""
        cInterop         = JDOMExternalizerUtil.readField(element, "C_INTEROP") == "true"
        gdbPort          = JDOMExternalizerUtil.readField(element, "GDB_PORT")?.toIntOrNull() ?: 1234
        marchOverride    = JDOMExternalizerUtil.readField(element, "MARCH_OVERRIDE")
            ?.takeIf { it.isNotBlank() }
            ?.let { name -> runCatching { MipsArch.valueOf(name) }.getOrNull() }
    }
}
