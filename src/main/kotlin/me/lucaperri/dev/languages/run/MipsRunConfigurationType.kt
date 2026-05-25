package me.lucaperri.dev.languages.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project
import me.lucaperri.dev.languages.MipsLanguage

class MipsRunConfigurationType : ConfigurationTypeBase(
    ID, "MIPS Assembly", "Assemble and run a MIPS file via emulator", MipsLanguage.icon
) {
    init {
        addFactory(object : ConfigurationFactory(this) {
            override fun getId() = "MipsRunConfigurationFactory"
            override fun createTemplateConfiguration(project: Project): RunConfiguration =
                MipsRunConfiguration(project, this, "")
        })
    }

    companion object {
        const val ID = "MipsRunConfiguration"
        fun getInstance(): MipsRunConfigurationType =
            ConfigurationTypeUtil.findConfigurationType(MipsRunConfigurationType::class.java)
    }
}
