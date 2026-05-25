package me.lucaperri.dev.languages.run.project

import me.lucaperri.dev.languages.run.AsmRunConfigurationCreator

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile

class NewAsmProjectAction : DumbAwareAction(
    "Assembly CMake Project",
    "Generate CMakeLists.txt and a starter assembly file",
    null
) {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val targetDir: VirtualFile = run {
            val ctxFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
            if (ctxFile != null) {
                if (ctxFile.isDirectory) ctxFile else ctxFile.parent
            } else {
                val desc = FileChooserDescriptorFactory.createSingleFolderDescriptor()
                    .withTitle("Select Project Directory")
                FileChooserFactory.getInstance()
                    .createFileChooser(desc, project, null)
                    .choose(project)
                    .firstOrNull() ?: return
            }
        }

        val dialog = NewAsmProjectDialog(project)
        if (!dialog.showAndGet()) return
        val (name, type, format, cInterop) = dialog.result()

        val isNasm = type == NewAsmProjectDialog.Type.NASM
        val ext    = if (isNasm) "nasm" else "mips"

        var cmakeVFile: VirtualFile? = null
        WriteCommandAction.runWriteCommandAction(project, "Create Assembly Project", null, {
            val cmake = targetDir.findOrCreateChildData(this, "CMakeLists.txt")
            cmakeVFile = cmake
            VfsUtil.saveText(
                cmake,
                if (isNasm) AsmProjectTemplates.nasmCmake(name, format, cInterop)
                else        AsmProjectTemplates.mipsCmake(name, cInterop)
            )
            VfsUtil.saveText(
                targetDir.findOrCreateChildData(this, "main.$ext"),
                if (isNasm) AsmProjectTemplates.nasmSrc(format, cInterop)
                else        AsmProjectTemplates.mipsSrc(cInterop)
            )
        })

        cmakeVFile?.let { f ->
            AsmRunConfigurationCreator.linkAndConfigureCmakeFile(project, f)
                ?.let { AsmRunConfigurationCreator.maybeGuide(project, setOf(it)) }
        }
    }
}
