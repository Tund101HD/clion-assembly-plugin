package me.lucaperri.dev.languages.run.debug

import com.intellij.openapi.fileTypes.FileType
import com.jetbrains.cidr.execution.debugger.breakpoints.CidrLineBreakpointFileTypesProvider
import me.lucaperri.dev.languages.filetypes.MipsFileType
import me.lucaperri.dev.languages.filetypes.NasmFileType

// Tells CLion's native (GDB/LLDB) debugger that NASM and MIPS source files
// support C/C++-style line breakpoints. Without this, the gutter offers no
// line breakpoints in .nasm/.mips files — the user can only break via an
// in-source int3/trap. With it (and a -g -F dwarf / -g debug build so the ELF
// carries line info), breakpoints set in the gutter bind to source lines.
//
// Registered via the cidr.debugger.lineBreakpointFileTypesProvider EP; mirrors
// CLion's own OCLineBreakpointFileTypesProvider and Rider's asm provider.
class AsmLineBreakpointFileTypesProvider : CidrLineBreakpointFileTypesProvider {
    override fun getFileTypes(): Set<FileType> =
        setOf(NasmFileType.INSTANCE, MipsFileType.INSTANCE)
}
