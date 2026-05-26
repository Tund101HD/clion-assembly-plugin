package me.lucaperri.dev.languages.run.project

import me.lucaperri.dev.languages.run.toolchain.PlatformHelper
import me.lucaperri.dev.languages.run.toolchain.WslToolchainProbe

import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project

internal object AsmProjectTemplates {

    // ── CMakeLists.txt builders ──────────────────────────────────────────────

    fun nasmCmake(name: String, format: String, cInterop: Boolean): String {
        val linkFlags = buildString {
            if (!cInterop) append("-nostdlib")
            // Modern Ubuntu/Debian gcc defaults to PIE for executables; pure-asm
            // ELF objects with absolute .bss/.data references fail to link with
            // "relocation R_X86_64_32S against `.bss` can not be used when making
            // a PIE object". -no-pie restores the classic non-PIC layout that
            // matches what the source code is written against.
            if (format == "elf64" || format == "elf32") {
                if (isNotEmpty()) append(" "); append("-no-pie")
            }
            if (format == "elf32") { if (isNotEmpty()) append(" "); append("-m32") }
        }
        val wslNote = if (PlatformHelper.isWindows) """
# Windows / WSL: add a WSL toolchain in CLion (Settings → Build → Toolchains → WSL)
# and set it as the active toolchain for this project, or use the NASM Run
# Configuration (right-click main.nasm → Run) for a zero-setup build.

""" else ""
        return buildString {
            if (wslNote.isNotEmpty()) append(wslNote.trimStart('\n'))
            append("""
cmake_minimum_required(VERSION 3.20)
# C is enabled alongside ASM_NASM so the gcc link rule (CMAKE_C_LINK_EXECUTABLE)
# exists — the executable below links with LINKER_LANGUAGE C.
project($name ASM_NASM C)

# Debug builds carry DWARF symbols for source-level stepping in GDB.
# Release builds stay clean — no debug info, suitable for distribution.
set(CMAKE_ASM_NASM_FLAGS_DEBUG   "-g -F dwarf")
set(CMAKE_ASM_NASM_FLAGS_RELEASE "")

set(CMAKE_ASM_NASM_COMPILE_OBJECT
    "<CMAKE_ASM_NASM_COMPILER> <DEFINES> <INCLUDES> <FLAGS> -o <OBJECT> <SOURCE>")

# All .nasm files in this directory are assembled and linked together, so
# multi-file projects (cross-file extern/global) just work. CONFIGURE_DEPENDS
# re-globs on build, so adding a file doesn't need a manual CMake reload.
file(GLOB NASM_SOURCES CONFIGURE_DEPENDS "${'$'}{CMAKE_CURRENT_SOURCE_DIR}/*.nasm")
add_executable($name ${'$'}{NASM_SOURCES})
set_target_properties($name PROPERTIES LINKER_LANGUAGE C)
target_compile_options($name PRIVATE -f $format)
""".trimStart())
            if (linkFlags.isNotEmpty())
                append("set_target_properties($name PROPERTIES LINK_FLAGS \"$linkFlags\")\n")
        }
    }

    fun mipsCmake(name: String, cInterop: Boolean): String {
        // Pull the user's MIPS ISA preference from settings so generated projects
        // pick the same -march the run-configs and live diagnostics use.
        val march = me.lucaperri.dev.languages.settings.AsmExecutableSettings
            .getInstance().defaultMipsArch.marchFlag
        val marchFlag = if (march != null) " -march=$march" else ""
        val wslNote = if (PlatformHelper.isWindows) """
# Windows / WSL: add a WSL toolchain in CLion (Settings → Build → Toolchains → WSL)
# and set it as the active toolchain for this project, or use the MIPS Run
# Configuration (right-click main.mips → Run) for a zero-setup build.

""" else ""
        return if (cInterop) buildString {
            if (wslNote.isNotEmpty()) append(wslNote.trimStart('\n'))
            append("""
cmake_minimum_required(VERSION 3.20)

# The cross-compiler MUST be selected before project(): CMake probes the
# toolchain during project(), so a set() afterwards is ignored and CMake falls
# back to the host compiler.
set(CMAKE_SYSTEM_NAME Linux)
set(CMAKE_SYSTEM_PROCESSOR mips)
set(CMAKE_C_COMPILER   mips-linux-gnu-gcc)
set(CMAKE_ASM_COMPILER mips-linux-gnu-gcc)

project($name C ASM)

# Debug builds carry DWARF symbols for GDB; Release stays clean for distribution.
set(CMAKE_C_FLAGS_DEBUG     "-g")
set(CMAKE_C_FLAGS_RELEASE   "-O2")
set(CMAKE_ASM_FLAGS         "${'$'}{CMAKE_ASM_FLAGS}$marchFlag")
set(CMAKE_ASM_FLAGS_DEBUG   "-g")
set(CMAKE_ASM_FLAGS_RELEASE "")

# All .mips files in this directory are assembled and linked together.
# CONFIGURE_DEPENDS re-globs on build so new files don't need a manual reload.
file(GLOB MIPS_SOURCES CONFIGURE_DEPENDS "${'$'}{CMAKE_CURRENT_SOURCE_DIR}/*.mips")
# .mips is not a default CMake assembly extension — tag the sources as ASM so
# CMake knows how to compile and link them.
set_source_files_properties(${'$'}{MIPS_SOURCES} PROPERTIES LANGUAGE ASM)
add_executable($name ${'$'}{MIPS_SOURCES})
target_compile_options($name PRIVATE -x assembler)
target_link_options($name PRIVATE -static)
""".trimStart())
        } else buildString {
            if (wslNote.isNotEmpty()) append(wslNote.trimStart('\n'))
            append("""
cmake_minimum_required(VERSION 3.20)

# The cross-compiler MUST be selected before project(): CMake probes the
# toolchain during project(), so a set() afterwards is ignored and CMake falls
# back to the host compiler. The gcc driver assembles and links pure asm.
set(CMAKE_SYSTEM_NAME Linux)
set(CMAKE_SYSTEM_PROCESSOR mips)
set(CMAKE_ASM_COMPILER mips-linux-gnu-gcc)
set(CMAKE_C_COMPILER   mips-linux-gnu-gcc)

project($name ASM)

# Debug builds carry DWARF symbols for GDB; Release stays clean for distribution.
set(CMAKE_ASM_FLAGS         "${'$'}{CMAKE_ASM_FLAGS}$marchFlag")
set(CMAKE_ASM_FLAGS_DEBUG   "-g")
set(CMAKE_ASM_FLAGS_RELEASE "")

# All .mips files in this directory are assembled and linked together.
# CONFIGURE_DEPENDS re-globs on build so new files don't need a manual reload.
file(GLOB MIPS_SOURCES CONFIGURE_DEPENDS "${'$'}{CMAKE_CURRENT_SOURCE_DIR}/*.mips")
# .mips is not a default CMake assembly extension — tag the sources as ASM so
# CMake knows how to compile and link them.
set_source_files_properties(${'$'}{MIPS_SOURCES} PROPERTIES LANGUAGE ASM)
add_executable($name ${'$'}{MIPS_SOURCES})
# Assemble via the gcc driver; pure asm links without the C runtime.
target_compile_options($name PRIVATE -x assembler)
target_link_options($name PRIVATE -nostdlib -static)
""".trimStart())
        }
    }

    // ── .gitignore ──────────────────────────────────────────────────────────

    // CMake's build dir + the run-config's quick-build output dir. Both are
    // populated on first build/run, so users want them gitignored from day one.
    fun gitignore(): String = """
cmake-build-*/
.asm-build/
""".trimStart()

    // ── Starter source builders ──────────────────────────────────────────────

    fun nasmSrc(format: String, cInterop: Boolean): String = if (cInterop) {
        if (format == "elf32") """
section .data
    msg db "Hello, World!", 10, 0

section .text
    global main
    extern printf

main:
    push    ebp
    mov     ebp, esp
    push    msg
    call    printf
    add     esp, 4
    xor     eax, eax
    pop     ebp
    ret
""".trimStart() else """
section .data
    msg db "Hello, World!", 10, 0

section .text
    global main
    extern printf

main:
    push    rbp
    mov     rbp, rsp
    lea     rdi, [rel msg]
    xor     eax, eax
    call    printf
    xor     eax, eax
    pop     rbp
    ret
""".trimStart()
    } else {
        if (format == "elf32") """
section .data

section .bss

section .text
    global _start

_start:
    ; exit(0) — Linux x86 (int 0x80)
    mov     eax, 1
    xor     ebx, ebx
    int     0x80
""".trimStart() else """
section .data

section .bss

section .text
    global _start

_start:
    ; exit(0) — Linux x64 (syscall)
    mov     rax, 60
    xor     rdi, rdi
    syscall
""".trimStart()
    }

    fun mipsSrc(cInterop: Boolean): String = if (cInterop) """
    .data
msg:
    .asciiz "Hello, World!\n"

    .text
    .globl main
    .extern printf

main:
    addiu   ${'$'}sp, ${'$'}sp, -8
    sw      ${'$'}ra, 4(${'$'}sp)

    la      ${'$'}a0, msg
    jal     printf

    lw      ${'$'}ra, 4(${'$'}sp)
    addiu   ${'$'}sp, ${'$'}sp, 8
    li      ${'$'}v0, 0
    jr      ${'$'}ra
""".trimStart() else """
    .data
msg:    .ascii "Hello, World!\n"
msg_len = . - msg

    .text
    # Linux/MIPS ld defaults to __start as the entry symbol when there is no C
    # runtime; using `main` would link with a warning and jump into garbage.
    .globl __start

__start:
    # write(1, msg, msg_len) — Linux/MIPS o32 ABI
    li      ${'$'}v0, 4004       # sys_write
    li      ${'$'}a0, 1          # fd = stdout
    la      ${'$'}a1, msg
    li      ${'$'}a2, msg_len
    syscall

    # exit(0)
    li      ${'$'}v0, 4001       # sys_exit
    li      ${'$'}a0, 0          # status
    syscall
""".trimStart()

    // ── WSL balloon notification ─────────────────────────────────────────────

    // [hasWslButInactive] = a WSL toolchain exists but the project's CMake build
    // isn't using it (e.g. the default/active profile is MinGW). The two cases
    // need different fixes: select the existing toolchain vs. create one.
    fun notifyWslToolchain(project: Project, hasWslButInactive: Boolean) {
        val (title, body) = if (hasWslButInactive) {
            "WSL Toolchain Not Selected" to
                "A WSL toolchain exists, but this project's CMake build is using a " +
                "non-WSL toolchain (e.g. MinGW), which can't assemble. " +
                "Make WSL the default in <b>Settings → Build, Execution, Deployment → " +
                "Toolchains</b> (move it to the top of the list with the ↑ arrow), " +
                "<i>or</i> pick it for this project under <b>Settings → … → CMake → " +
                "Toolchain</b>. Then confirm the tools are installed via " +
                "<b>Settings → Assembly → Executables → Verify Toolchain</b>. " +
                "(If you've already selected it for your CMake profile, ignore this.)"
        } else {
            "WSL Toolchain Required" to
                "CMake builds and CLion's GDB debug integration require a WSL toolchain. " +
                "Add one in <b>Settings → Build, Execution, Deployment → Toolchains</b> " +
                "(+ → WSL) and set it as the default (move it to the top of the list). " +
                "Then verify the tools are installed via <b>Settings → Assembly → " +
                "Executables → Verify Toolchain</b>. " +
                "The standalone Assembly Run Configuration works without this."
        }
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Assembly Tooling")
            .createNotification(title, body, NotificationType.INFORMATION)
            .addAction(NotificationAction.createSimple("Open Toolchain Settings") {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, "Toolchains")
            })
            .addAction(NotificationAction.createSimple("Open CMake Settings") {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, "CMake")
            })
            .notify(project)
    }

    // On macOS, gdb cannot attach to (ptrace) a process unless the gdb binary is
    // code-signed with a trusted certificate. Without it, debug sessions fail
    // with a cryptic "Unable to find Mach task port" error. Surfaced once per
    // session when a GDB debug config is created on macOS.
    fun notifyMacGdbCodesign(project: Project) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Assembly Tooling")
            .createNotification(
                "macOS: GDB Code-Signing Required",
                "On macOS, <b>gdb</b> must be code-signed to attach to processes (ptrace), " +
                "otherwise debugging fails with \"Unable to find Mach task port\". " +
                "Create a trusted certificate and sign your gdb binary before debugging. " +
                "(lldb, CLion's default, doesn't need this.)",
                NotificationType.WARNING
            )
            .addAction(NotificationAction.createSimple("How to code-sign gdb") {
                BrowserUtil.browse("https://sourceware.org/gdb/wiki/PermissionsDarwin")
            })
            .notify(project)
    }

    // Surfaced on project open when an assembly target is present but the build
    // tools it needs aren't installed — turns CLion's cryptic CMake/compiler
    // error into an actionable "install this" message with the exact apt command.
    fun notifyMissingTools(project: Project, missing: List<WslToolchainProbe.Tool>) {
        if (missing.isEmpty()) return
        val where    = if (PlatformHelper.isWindows) "in your WSL distribution" else "on your system"
        val tools    = missing.joinToString(", ") { it.bin }
        val packages = missing.map { it.aptPackage }.distinct().joinToString(" ")
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Assembly Tooling")
            .createNotification(
                "Assembly Build Tools Missing",
                "These tools aren't installed $where: <b>$tools</b>.<br>" +
                "Install them with:<br><code>sudo apt install $packages</code>",
                NotificationType.WARNING
            )
            .addAction(NotificationAction.createSimple("Open Assembly Settings") {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, "Executables")
            })
            .notify(project)
    }
}
