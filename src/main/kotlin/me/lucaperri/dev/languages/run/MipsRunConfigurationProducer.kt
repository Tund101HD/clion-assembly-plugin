package me.lucaperri.dev.languages.run

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import me.lucaperri.dev.languages.filetypes.MipsFileType

class MipsRunConfigurationProducer : LazyRunConfigurationProducer<MipsRunConfiguration>() {

    override fun getConfigurationFactory(): ConfigurationFactory =
        MipsRunConfigurationType.getInstance().configurationFactories[0]

    override fun setupConfigurationFromContext(
        configuration: MipsRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>
    ): Boolean {
        val file = context.location?.virtualFile ?: return false
        if (file.fileType !is MipsFileType) return false
        configuration.scriptName       = file.path
        configuration.workingDirectory = file.parent?.path.orEmpty()
        configuration.name             = file.nameWithoutExtension
        sourceElement.set(context.psiLocation)
        return true
    }

    override fun isConfigurationFromContext(
        configuration: MipsRunConfiguration,
        context: ConfigurationContext
    ): Boolean {
        val file = context.location?.virtualFile ?: return false
        return configuration.scriptName == file.path
    }
}
