package me.lucaperri.dev.languages.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project
import me.lucaperri.dev.languages.NasmLanguage

class NasmRunConfigurationType : ConfigurationTypeBase(
    ID, "NASM Assembly", "Assemble and run a NASM x86/64 file", NasmLanguage.icon
) {
    init {
        addFactory(object : ConfigurationFactory(this) {
            override fun getId() = "NasmRunConfigurationFactory"
            override fun createTemplateConfiguration(project: Project): RunConfiguration =
                NasmRunConfiguration(project, this, "")
        })
    }

    companion object {
        const val ID = "NasmRunConfiguration"
        fun getInstance(): NasmRunConfigurationType =
            ConfigurationTypeUtil.findConfigurationType(NasmRunConfigurationType::class.java)
    }
}
