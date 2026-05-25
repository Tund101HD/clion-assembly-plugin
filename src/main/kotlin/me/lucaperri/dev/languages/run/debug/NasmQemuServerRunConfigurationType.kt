package me.lucaperri.dev.languages.run.debug

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project
import me.lucaperri.dev.languages.NasmLanguage

class NasmQemuServerRunConfigurationType : ConfigurationTypeBase(
    ID,
    "NASM QEMU Debug Server",
    "Assemble and start a qemu-x86_64 GDB stub for cross-arch NASM debugging",
    NasmLanguage.icon
) {
    init {
        addFactory(object : ConfigurationFactory(this) {
            override fun getId() = "NasmQemuServerRunConfigurationFactory"
            override fun createTemplateConfiguration(project: Project): RunConfiguration =
                NasmQemuServerRunConfiguration(project, this, "")
        })
    }

    companion object {
        const val ID = "NasmQemuServerRunConfiguration"
        fun getInstance(): NasmQemuServerRunConfigurationType =
            ConfigurationTypeUtil.findConfigurationType(NasmQemuServerRunConfigurationType::class.java)
    }
}
