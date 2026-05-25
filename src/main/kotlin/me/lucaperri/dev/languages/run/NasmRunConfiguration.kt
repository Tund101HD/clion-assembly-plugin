package me.lucaperri.dev.languages.run

import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMExternalizerUtil
import me.lucaperri.dev.languages.settings.AsmExecutableSettings
import org.jdom.Element
import java.io.File

class NasmRunConfiguration(project: Project, factory: ConfigurationFactory, name: String)
    : LocatableConfigurationBase<RunConfigurationOptions>(project, factory, name) {

    var scriptName = ""
    var workingDirectory = ""
    var programParameters = ""
    var nasmFormat: String = defaultNasmFormat()
    var cInterop = false

    private fun defaultNasmFormat(): String =
        runCatching { AsmExecutableSettings.getInstance().defaultNasmArch.nasmFormat }
            .getOrDefault("elf64")

    override fun getConfigurationEditor(): SettingsEditor<NasmRunConfiguration> =
        NasmRunConfigurationEditor()

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
        NasmCommandLineState(environment, this)

    override fun checkConfiguration() {
        if (scriptName.isBlank()) throw RuntimeConfigurationError("Source file not specified")
    }

    override fun suggestedName(): String? =
        if (scriptName.isBlank()) null else File(scriptName).nameWithoutExtension

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        JDOMExternalizerUtil.writeField(element, "SCRIPT_NAME", scriptName)
        JDOMExternalizerUtil.writeField(element, "WORKING_DIRECTORY", workingDirectory)
        JDOMExternalizerUtil.writeField(element, "PROGRAM_PARAMETERS", programParameters)
        JDOMExternalizerUtil.writeField(element, "NASM_FORMAT", nasmFormat)
        JDOMExternalizerUtil.writeField(element, "C_INTEROP", cInterop.toString())
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        scriptName         = JDOMExternalizerUtil.readField(element, "SCRIPT_NAME") ?: ""
        workingDirectory   = JDOMExternalizerUtil.readField(element, "WORKING_DIRECTORY") ?: ""
        programParameters  = JDOMExternalizerUtil.readField(element, "PROGRAM_PARAMETERS") ?: ""
        nasmFormat         = JDOMExternalizerUtil.readField(element, "NASM_FORMAT") ?: "elf64"
        cInterop           = JDOMExternalizerUtil.readField(element, "C_INTEROP") == "true"
    }
}
