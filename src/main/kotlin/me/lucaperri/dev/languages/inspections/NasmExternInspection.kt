package me.lucaperri.dev.languages.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import me.lucaperri.dev.languages.psi.NasmExternStmt
import me.lucaperri.dev.languages.psi.NasmFile
import me.lucaperri.dev.languages.psi.NasmGlobalStmt
import me.lucaperri.dev.languages.psi.NasmLabelRef
import me.lucaperri.dev.languages.psi.NasmSymbolDef
import me.lucaperri.dev.languages.psi.NasmTypes
import me.lucaperri.dev.languages.psi.findNonLocalDefinition
import me.lucaperri.dev.languages.psi.foreignNasmDefinitions
import me.lucaperri.dev.languages.settings.AsmExecutableSettings

// Checks `extern foo` against the rest of the project:
//   - `foo` is defined in this same file           -> error (nasm rejects it too)
//   - `foo` exists elsewhere but isn't `global`    -> warning on the extern and every
//     use of `foo`; an error once Settings → Assembly → General hides such labels
//   - no NASM file defines `foo` at all             -> error, unless switched off in
//     settings or `foo` is a well-known libc / WinAPI symbol (EXTERNAL_LIBRARY_SYMBOLS)
class NasmExternInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val file = holder.file as? NasmFile ?: return PsiElementVisitor.EMPTY_VISITOR
        val settings = AsmExecutableSettings.getInstance()
        val externs by lazy { externNames(file) }
        val foreign = mutableMapOf<String, List<NasmSymbolDef>>()

        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is NasmLabelRef) return
                val parent = element.parent
                if (parent is NasmGlobalStmt) return
                val identifier = element.node.findChildByType(NasmTypes.IDENTIFIER)?.psi ?: return
                val name = identifier.text
                if (name !in externs) return
                val inExtern = parent is NasmExternStmt

                if (file.findNonLocalDefinition(name) != null) {
                    if (inExtern) {
                        holder.registerProblem(
                            identifier,
                            "'$name' is declared extern but defined in this file",
                            ProblemHighlightType.GENERIC_ERROR
                        )
                    }
                    return
                }

                val defs = foreign.getOrPut(name) { foreignNasmDefinitions(file, name) }
                when {
                    defs.any { it.isGlobal } -> Unit
                    defs.isNotEmpty() -> holder.registerProblem(
                        identifier,
                        "'$name' is defined in ${defs.first().file.name} but not declared global there",
                        if (settings.hideNonGlobalLabels) ProblemHighlightType.GENERIC_ERROR
                        else ProblemHighlightType.WARNING
                    )
                    inExtern && settings.reportUndefinedExterns && name !in EXTERNAL_LIBRARY_SYMBOLS ->
                        holder.registerProblem(
                            identifier,
                            "'$name' is not defined in any NASM file of the project",
                            ProblemHighlightType.GENERIC_ERROR
                        )
                }
            }
        }
    }

    private fun externNames(file: NasmFile): Set<String> =
        PsiTreeUtil.findChildrenOfType(file, NasmExternStmt::class.java)
            .flatMap { it.labelRefList }
            .mapNotNull { it.node.findChildByType(NasmTypes.IDENTIFIER)?.text }
            .toSet()

    companion object {
        // Symbols NASM programs commonly import from libc or the Windows API. They
        // live outside the project's NASM files, so "not defined anywhere" would be
        // a false positive for them.
        val EXTERNAL_LIBRARY_SYMBOLS = setOf(
            // libc: stdio
            "printf", "fprintf", "sprintf", "snprintf", "dprintf", "vprintf", "vfprintf",
            "scanf", "fscanf", "sscanf", "puts", "fputs", "putchar", "fputc", "putc",
            "getchar", "fgetc", "getc", "gets", "fgets", "fopen", "fclose", "fread",
            "fwrite", "fflush", "fseek", "ftell", "rewind", "perror",
            "stdin", "stdout", "stderr",
            // libc: stdlib / string / ctype / math
            "malloc", "calloc", "realloc", "free", "exit", "_exit", "abort", "atexit",
            "atoi", "atol", "atof", "strtol", "strtoul", "strtod", "abs", "labs",
            "rand", "srand", "qsort", "bsearch", "getenv", "system",
            "strlen", "strcpy", "strncpy", "strcat", "strncat", "strcmp", "strncmp",
            "strchr", "strrchr", "strstr", "strdup", "memcpy", "memmove", "memset",
            "memcmp", "memchr", "toupper", "tolower", "isdigit", "isalpha", "isspace",
            "sqrt", "pow", "sin", "cos", "tan", "floor", "ceil", "fabs", "exp", "log",
            // libc: POSIX / time
            "read", "write", "open", "close", "lseek", "time", "clock", "sleep",
            "usleep", "nanosleep", "getpid", "fork", "execve", "mmap", "munmap",
            "errno", "__errno_location", "__libc_start_main", "__stack_chk_fail",
            // Windows API (kernel32 / user32 / msvcrt)
            "ExitProcess", "GetStdHandle", "WriteFile", "ReadFile", "WriteConsoleA",
            "WriteConsoleW", "ReadConsoleA", "ReadConsoleW", "GetLastError",
            "GetModuleHandleA", "GetModuleHandleW", "GetProcAddress", "LoadLibraryA",
            "LoadLibraryW", "VirtualAlloc", "VirtualFree", "HeapAlloc", "HeapFree",
            "GetProcessHeap", "CreateFileA", "CreateFileW", "CloseHandle", "Sleep",
            "MessageBoxA", "MessageBoxW", "wsprintfA",
            "_printf", "_puts", "_malloc", "_free",
        )
    }
}
