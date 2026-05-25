package me.lucaperri.dev.languages.inspections

import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

class AsmConsoleFilterProvider : ConsoleFilterProvider {
    override fun getDefaultFilters(project: Project): Array<Filter> = arrayOf(AsmErrorFilter(project))
}

private class AsmErrorFilter(private val project: Project) : Filter {

    // Matches:  path/to/file.asm:42[:col]:  message
    //           path\to\file.nasm(42)       :  message  (MSVC style — col optional)
    private val gnuStyle = Regex("""([^\s:()'"<>|*?]+\.(?:asm|nasm|mips|s|S|inc)):(\d+)(?::(\d+))?""")
    private val msvcStyle = Regex("""([^\s:()'"<>|*?]+\.(?:asm|nasm|mips|s|S|inc))\((\d+)(?:,(\d+))?\)""")

    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        val items = mutableListOf<Filter.ResultItem>()
        val lineStartInDoc = entireLength - line.length

        for (m in (gnuStyle.findAll(line) + msvcStyle.findAll(line))) {
            val path = m.groupValues[1]
            val lineNum = m.groupValues[2].toIntOrNull() ?: continue
            val colNum = m.groupValues.getOrNull(3)?.toIntOrNull() ?: 0
            val vf = resolve(path) ?: continue
            val startOffset = lineStartInDoc + m.range.first
            val endOffset = lineStartInDoc + m.range.last + 1
            items += Filter.ResultItem(
                startOffset,
                endOffset,
                OpenFileHyperlinkInfo(project, vf, (lineNum - 1).coerceAtLeast(0), (colNum - 1).coerceAtLeast(0))
            )
        }
        if (items.isEmpty()) return null
        return Filter.Result(items)
    }

    private fun resolve(path: String): VirtualFile? {
        val lfs = LocalFileSystem.getInstance()
        lfs.findFileByPath(path)?.let { return it }
        val basePath = project.basePath ?: return null
        return lfs.findFileByPath(File(basePath, path).absolutePath)
    }
}
