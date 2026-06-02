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

// Selects the GNU assembler `-march=` and `-mabi=` flags for MIPS builds.
// `marchFlag == null` means "don't pass -march" (use toolchain default).
// `mabiFlag == null` means "don't pass -mabi" (use toolchain default).
//
// On ARM64 Linux hosts the installed MIPS cross-toolchain may default to a
// 64-bit ABI regardless of the binary name, producing:
//   "mips-as gp=32 used with a 64-bit ABI"
// Passing an explicit -mabi= alongside -march= prevents this mismatch for
// every arch that has an unambiguous ABI width.
// MIPS32R2 is the default for the plugin because most MIPS32r2-era instructions
// (rotr, ext, ins, clz, seb, di/ei, ...) are exercised by the showcase template
// but rejected by older toolchain defaults with "not available on your processor".
enum class MipsArch(val displayName: String, val marchFlag: String?, val mabiFlag: String?) {
    DEFAULT  ("Toolchain default",      null,        null),
    MIPS1    ("mips1 (R2000/R3000)",    "mips1",     "32"),
    MIPS2    ("mips2 (R6000)",          "mips2",     "32"),
    MIPS3    ("mips3 (R4000)",          "mips3",     "32"),
    MIPS4    ("mips4 (R8000/R10000)",   "mips4",     "32"),
    MIPS32   ("mips32",                 "mips32",    "32"),
    MIPS32R2 ("mips32r2 (recommended)", "mips32r2",  "32"),
    MIPS32R6 ("mips32r6",               "mips32r6",  "32"),
    MIPS64   ("mips64",                 "mips64",    "64"),
    MIPS64R2 ("mips64r2",               "mips64r2",  "64"),
    MIPS64R6 ("mips64r6",               "mips64r6",  "64");

    override fun toString(): String = displayName
}

// Selects the GNU assembler `-mabi=` flag, or derives it automatically from
// the chosen MipsArch.  AUTO is the right choice for most users — it forwards
// the arch's natural ABI width (32-bit for MIPS32* variants, 64-bit for
// MIPS64* variants) and passes nothing for "Toolchain default".
//
// The explicit options are useful when targeting an unusual combination, e.g.
// running N32 code on a MIPS64 toolchain, or EABI for bare-metal targets.
enum class MipsAbi(val displayName: String, val mabiFlag: String?) {
    AUTO ("Auto (derived from architecture)", null),
    O32  ("o32 — traditional 32-bit ABI",     "32"),
    N32  ("n32 — new 32-bit ABI (64-bit regs, 32-bit pointers)", "n32"),
    N64  ("n64 — 64-bit ABI",                 "64"),
    EABI ("eabi — embedded ABI",              "eabi");

    // Returns the -mabi= value to actually pass to `as`/`gcc`.
    // AUTO defers to the arch's built-in ABI inference; every other value wins
    // unconditionally, allowing the user to override mismatched toolchain defaults.
    fun resolve(arch: MipsArch): String? = if (this == AUTO) arch.mabiFlag else mabiFlag

    override fun toString(): String = displayName
}
