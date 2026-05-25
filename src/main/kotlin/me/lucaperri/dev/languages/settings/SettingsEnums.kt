package me.lucaperri.dev.languages.settings

enum class PlatformOverride(val displayName: String) {
    AUTO("Auto-detect"),
    WINDOWS("Windows (WSL)"),
    LINUX("Linux"),
    MACOS("macOS");

    override fun toString(): String = displayName
}

enum class NasmArch(val displayName: String, val nasmFormat: String) {
    X64("x86-64 (elf64)", "elf64"),
    X32("x86 (elf32)", "elf32");

    override fun toString(): String = displayName
}

enum class ProjectType(val displayName: String) {
    NASM("NASM (x86/x64)"),
    MIPS("MIPS (MIPS32)");

    override fun toString(): String = displayName
}

// Selects the GNU assembler `-march=` flag for MIPS builds.
// `marchFlag == null` means "don't pass -march" (use toolchain default).
// MIPS32R2 is the default for the plugin because most MIPS32r2-era instructions
// (rotr, ext, ins, clz, seb, di/ei, ...) are exercised by the showcase template
// but rejected by older toolchain defaults with "not available on your processor".
enum class MipsArch(val displayName: String, val marchFlag: String?) {
    DEFAULT  ("Toolchain default",      null),
    MIPS1    ("mips1 (R2000/R3000)",    "mips1"),
    MIPS2    ("mips2 (R6000)",          "mips2"),
    MIPS3    ("mips3 (R4000)",          "mips3"),
    MIPS4    ("mips4 (R8000/R10000)",   "mips4"),
    MIPS32   ("mips32",                 "mips32"),
    MIPS32R2 ("mips32r2 (recommended)", "mips32r2"),
    MIPS32R6 ("mips32r6",               "mips32r6"),
    MIPS64   ("mips64",                 "mips64"),
    MIPS64R2 ("mips64r2",               "mips64r2"),
    MIPS64R6 ("mips64r6",               "mips64r6");

    override fun toString(): String = displayName
}
