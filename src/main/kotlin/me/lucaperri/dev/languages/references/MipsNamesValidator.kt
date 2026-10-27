package me.lucaperri.dev.languages.references

import com.intellij.lang.refactoring.NamesValidator
import com.intellij.openapi.project.Project
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.RenameInputValidator
import com.intellij.util.ProcessingContext
import me.lucaperri.dev.languages.highlighting.MipsRegisters
import me.lucaperri.dev.languages.psi.MipsNamedElement

// Same Java-identifier fallback problem as NASM: GNU-as local labels (`.done:`)
// start with a dot and could not be renamed. Global names mirror the label form of
// IDENTIFIER in Mips.flex, local names mirror DIRECTIVE.
class MipsNamesValidator : NamesValidator {
    override fun isKeyword(name: String, project: Project?): Boolean =
        name.lowercase() in MipsRegisters.ALL

    override fun isIdentifier(name: String, project: Project?): Boolean =
        (GLOBAL.matches(name) || LOCAL.matches(name)) && !isKeyword(name, project)

    companion object {
        private val GLOBAL = Regex("""[a-zA-Z_][a-zA-Z0-9_]*(\.[a-zA-Z]+)*""")
        private val LOCAL = Regex("""\.[a-zA-Z_][a-zA-Z0-9_]*""")
    }
}

// A rename must not turn `.done` into `done` (or back): local and global labels
// are different grammar elements, so the renamed text would no longer parse as the
// same kind of label.
class MipsRenameInputValidator : RenameInputValidator {
    private val names = MipsNamesValidator()

    override fun getPattern(): ElementPattern<out PsiElement> =
        PlatformPatterns.psiElement(MipsNamedElement::class.java)

    override fun isInputValid(newName: String, element: PsiElement, context: ProcessingContext): Boolean {
        if (!names.isIdentifier(newName, element.project)) return false
        val oldName = (element as MipsNamedElement).name ?: return true
        return oldName.startsWith(".") == newName.startsWith(".")
    }
}
