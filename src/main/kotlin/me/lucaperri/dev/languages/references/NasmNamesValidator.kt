package me.lucaperri.dev.languages.references

import com.intellij.lang.refactoring.NamesValidator
import com.intellij.openapi.project.Project
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.RenameInputValidator
import com.intellij.util.ProcessingContext
import me.lucaperri.dev.languages.highlighting.NasmRegisters
import me.lucaperri.dev.languages.psi.NasmNamedElement
import me.lucaperri.dev.languages.psi.isNasmLocalLabel

// Without a registered NamesValidator the platform falls back to Java identifier
// rules, which reject the leading `.` of every NASM local label — so any rename of
// `.loop` failed with "Inserted identifier is not valid". Mirrors IDENTIFIER in Nasm.flex.
class NasmNamesValidator : NamesValidator {
    override fun isKeyword(name: String, project: Project?): Boolean =
        name.lowercase().let { it in NasmRegisters.ALL || it in RESERVED }

    override fun isIdentifier(name: String, project: Project?): Boolean =
        IDENTIFIER.matches(name) && !isKeyword(name, project)

    companion object {
        private val IDENTIFIER = Regex("""[a-zA-Z_.?][a-zA-Z0-9_.?$]*""")
        private val RESERVED = setOf(
            "section", "segment", "global", "extern", "equ", "times",
            "db", "dw", "dd", "dq", "dt", "do", "resb", "resw", "resd", "resq", "rest",
            "byte", "word", "dword", "qword", "tword", "oword", "yword", "zword"
        )
    }
}

// Renaming must keep a label's scope kind: `.loop` -> `loop` would silently turn a
// local label into a new global scope and re-parent every `.x` label below it.
// A matching RenameInputValidator takes precedence over the NamesValidator, so the
// identifier check is repeated here.
class NasmRenameInputValidator : RenameInputValidator {
    private val names = NasmNamesValidator()

    override fun getPattern(): ElementPattern<out PsiElement> =
        PlatformPatterns.psiElement(NasmNamedElement::class.java)

    override fun isInputValid(newName: String, element: PsiElement, context: ProcessingContext): Boolean {
        if (!names.isIdentifier(newName, element.project)) return false
        val oldName = (element as NasmNamedElement).name ?: return true
        return oldName.isNasmLocalLabel() == newName.isNasmLocalLabel()
    }
}
