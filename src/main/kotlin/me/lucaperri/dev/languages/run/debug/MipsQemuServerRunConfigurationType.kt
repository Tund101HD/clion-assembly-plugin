package me.lucaperri.dev.languages.run.debug

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project
import me.lucaperri.dev.languages.MipsLanguage

class MipsQemuServerRunConfigurationType : ConfigurationTypeBase(
    ID,
    "MIPS QEMU Debug Server",
    "Assemble and start a QEMU GDB stub for MIPS debugging",
    MipsLanguage.icon
) {
    init {
        addFactory(object : ConfigurationFactory(this) {
            override fun getId() = "MipsQemuServerRunConfigurationFactory"
            override fun createTemplateConfiguration(project: Project): RunConfiguration =
                MipsQemuServerRunConfiguration(project, this, "")
        })
    }

    companion object {
        const val ID = "MipsQemuServerRunConfiguration"
        fun getInstance(): MipsQemuServerRunConfigurationType =
            ConfigurationTypeUtil.findConfigurationType(MipsQemuServerRunConfigurationType::class.java)
    }
}
