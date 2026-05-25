package me.lucaperri.dev.languages.run

import me.lucaperri.dev.languages.run.debug.MipsCmakeConfigSuppressor
import me.lucaperri.dev.languages.run.debug.MipsQemuServerRunConfiguration
import me.lucaperri.dev.languages.run.debug.MipsQemuServerRunConfigurationType
import me.lucaperri.dev.languages.run.debug.NasmQemuServerRunConfiguration
import me.lucaperri.dev.languages.run.debug.NasmQemuServerRunConfigurationType
import me.lucaperri.dev.languages.run.project.AsmProjectTemplates
import me.lucaperri.dev.languages.run.toolchain.PlatformHelper
import me.lucaperri.dev.languages.run.toolchain.WslToolchainDetector
import me.lucaperri.dev.languages.run.toolchain.WslToolchainProbe

import com.intellij.execution.BeforeRunTaskProvider
import com.intellij.execution.DefaultExecutionTarget
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.impl.RunConfigurationBeforeRunProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.jetbrains.cidr.cpp.cmake.workspace.CMakeWorkspace
import com.jetbrains.cidr.cpp.execution.debugger.CLionDebuggerKind
import com.jetbrains.cidr.cpp.execution.remote.CLionRemoteRunConfiguration
import com.jetbrains.cidr.cpp.execution.remote.CLionRemoteRunConfigurationType
import com.jetbrains.cidr.cpp.execution.remote.DebuggerData
import com.jetbrains.cidr.execution.debugger.remote.CidrRemoteDebugParameters
import com.jetbrains.cidr.execution.debugger.remote.CidrRemotePathMapping
import kotlinx.coroutines.launch
import java.io.File

// On project open: scan all CMakeLists.txt files for NASM / MIPS targets.
// For each one, delegate build/run to CLion's CMake workspace (which creates
// CMake Application run configurations automatically), and additionally create
// a MIPS QEMU Debug Server config + a GDB Remote Debug config for MIPS targets.
// The GDB config has the QEMU server pre-wired as its "Before launch" step.
//
// The companion exposes linkAndConfigureCmakeFile() so NewAsmProjectAction and
// the project generators can call it immediately after writing files.
class AsmRunConfigurationCreator : StartupActivity.DumbAware {

    override fun runActivity(project: Project) {
        DumbService.getInstance(project).runWhenSmart {
            val scope      = GlobalSearchScope.projectScope(project)
            val runManager = RunManager.getInstance(project)

            val cmakeFiles = runReadActionBlocking {
                FilenameIndex.getVirtualFilesByName("CMakeLists.txt", scope)
            }

            val types = mutableSetOf<AsmType>()
            for (file in cmakeFiles) {
                linkAndConfigureCmakeFile(project, file, runManager)?.let { types += it }
            }

            // Guide setup: ensure an active WSL toolchain (Windows), then that the
            // build tools each detected language needs are actually installed.
            if (types.isNotEmpty()) maybeGuide(project, types)
        }
    }

    companion object {

        private val GUIDE_SHOWN = Key.create<Boolean>("me.lucaperri.dev.assembly.guideShown")
        private val MAC_GDB_NOTIFIED = Key.create<Boolean>("me.lucaperri.dev.assembly.macGdbNotified")

        // Single source of setup guidance, fired at most once per session for a
        // project that contains assembly targets:
        //   1. On Windows, if the project's CMake build isn't wired to a WSL
        //      toolchain, prompt to select/add one — CMake can't assemble otherwise.
        //   2. Otherwise probe (off the EDT) that the build tools each detected
        //      language needs are installed, surfacing an apt command if not.
        // Shared by the startup scan and the project-creation entry points.
        fun maybeGuide(project: Project, types: Set<AsmType>) {
            if (types.isEmpty()) return
            if (project.getUserData(GUIDE_SHOWN) == true) return

            // Step 1 — toolchain selection (fast, in-memory; safe on any thread).
            if (PlatformHelper.isWindows && !WslToolchainDetector.projectUsesWslToolchain(project)) {
                WslToolchainDetector.logDiagnostics(project)
                project.putUserData(GUIDE_SHOWN, true)
                AsmProjectTemplates.notifyWslToolchain(project, WslToolchainDetector.hasWslToolchain())
                return
            }

            // Step 2 — build tools installed? Probing spawns WSL, so do it off the
            // EDT and show the balloon back on the UI thread. Probe the *debug*
            // capability when GDB auto-launch is on, so missing gdb-multiarch /
            // qemu-user-static is surfaced at project open instead of on first
            // debug attempt (where a missing debugger fails with an opaque error).
            project.putUserData(GUIDE_SHOWN, true)
            val debugProbe = pluginSettings().autoLaunchGdbOnQemu
            val tools = buildSet {
                if (AsmType.NASM in types) {
                    addAll(WslToolchainProbe.Capability.NASM_BUILD.tools)
                    if (debugProbe) addAll(WslToolchainProbe.Capability.NASM_DEBUG.tools)
                }
                if (AsmType.MIPS in types) {
                    addAll(WslToolchainProbe.Capability.MIPS_BUILD.tools)
                    if (debugProbe) addAll(WslToolchainProbe.Capability.MIPS_DEBUG.tools)
                }
            }.toList()
            val settings = pluginSettings()
            ApplicationManager.getApplication().executeOnPooledThread {
                val missing = WslToolchainProbe.probe(tools, settings)
                    .filterValues { !it }.keys.toList()
                if (missing.isNotEmpty()) {
                    ApplicationManager.getApplication().invokeLater {
                        if (!project.isDisposed) AsmProjectTemplates.notifyMissingTools(project, missing)
                    }
                }
            }
        }

        // Once per project session, warn macOS users that gdb needs code-signing
        // to attach. Called when a GDB debug config is created.
        private fun maybeNotifyMacCodesign(project: Project) {
            if (!PlatformHelper.isMac) return
            if (project.getUserData(MAC_GDB_NOTIFIED) == true) return
            project.putUserData(MAC_GDB_NOTIFIED, true)
            AsmProjectTemplates.notifyMacGdbCodesign(project)
        }

        // ── Public entry point ───────────────────────────────────────────────
        // Links the CMakeLists.txt directory into CLion's CMake workspace
        // (triggering automatic CMake Application run config creation) and,
        // for MIPS targets, also creates a QEMU Debug Server run config.
        // Returns the detected assembly target's type, or null when the file has
        // no assembly target (callers use this to drive setup guidance).
        fun linkAndConfigureCmakeFile(
            project: Project,
            cmakeFile: VirtualFile,
            runManager: RunManager = RunManager.getInstance(project)
        ): AsmType? {
            val dir    = cmakeFile.parent ?: return null
            val target = runReadActionBlocking {
                parseCmake(cmakeFile)
            } ?: return null

            // Hand build/run to CLion's CMake integration, and force a reload so
            // the project's CMake model is (re)built on open, not just creation.
            val workspace = CMakeWorkspace.getInstance(project)
            workspace.coroutineScope.launch {
                runCatching {
                    workspace.linkCMakeProject(VfsUtilCore.virtualToIoFile(dir))
                    workspace.scheduleReload()
                }
            }

            // QEMU + GDB Remote Debug configs have no CMake equivalent — create ours.
            // MIPS always needs emulation; NASM only on hosts that can't run an
            // x86-64 ELF natively (ARM), or when the user forces it.
            when {
                target.type == AsmType.MIPS -> {
                    // Tell the suppressor this is a MIPS target so CLion's broken
                    // auto-created CMake Application config gets removed. Since that
                    // config (the only "Run" entry) is removed, provide our own
                    // qemu-backed Run config so MIPS isn't debug-only.
                    MipsCmakeConfigSuppressor.recordMipsTarget(project, target.name)
                    addMipsRunConfig(project, runManager, target)
                    val qemuSettings = addQemuConfig(project, runManager, target)
                    if (qemuSettings != null && pluginSettings().autoLaunchGdbOnQemu) {
                        addGdbRemoteConfig(project, runManager, target, qemuSettings)
                    }
                }
                target.type == AsmType.NASM && shouldCreateNasmQemu() -> {
                    val qemuSettings = addNasmQemuConfig(project, runManager, target)
                    if (qemuSettings != null && pluginSettings().autoLaunchGdbOnQemu) {
                        addNasmGdbRemoteConfig(project, runManager, target, qemuSettings)
                    }
                }
            }
            return target.type
        }

        private fun pluginSettings() = me.lucaperri.dev.languages.settings.AsmExecutableSettings.getInstance()

        // Always prefer CLion's bundled GDB. From CLion 2026.1 it ships a
        // multiarch build (verified: supports mips, arm, riscv, ...), so it
        // can decode the remote target's foreign architecture without relying
        // on the user having `gdb-multiarch` installed in their WSL toolchain.
        // Critically, the default WSL toolchain's debugger is plain `/usr/bin/gdb`
        // (x86-only), which silently fails to attach to a MIPS gdbstub —
        // that's the trap we kept hitting before this switch.
        // Path translation for the Windows host case is handled via the
        // CidrRemoteDebugParameters.pathMappings field below, not here.
        private fun debuggerDataForHost(): DebuggerData =
            DebuggerData(CLionDebuggerKind.Bundled.GDB)

        // ── Cross-arch NASM debug eligibility ────────────────────────────────
        // An x86-64 ELF can't execute on an ARM host (or under WSL2's ARM64
        // kernel), so it needs qemu-x86_64 + a gdbstub. macOS is deferred
        // (Mach-O vs ELF, Rosetta os.arch misreport) — relaxing the !isMac
        // guard here is the single change that enables it later.
        private fun shouldCreateNasmQemu(): Boolean = nasmQemuDecision(
            prefer = pluginSettings().preferQemuForNasmDebug,
            isMac  = PlatformHelper.isMac,
            isArm  = isArmArch(System.getProperty("os.arch")),
        )

        // Pure decision, split out for unit testing.
        fun nasmQemuDecision(prefer: Boolean, isMac: Boolean, isArm: Boolean): Boolean =
            prefer || (isArm && !isMac)

        fun isArmArch(arch: String?): Boolean {
            val a = arch?.lowercase() ?: return false
            return a.contains("aarch64") || a.contains("arm")
        }

        // ── MIPS Run config creation ─────────────────────────────────────────
        // MIPS can't run natively; this builds and runs the binary under
        // qemu-mips-static (via MipsCommandLineState). Replaces the suppressed
        // CMake Application config so MIPS targets have a usable "Run" entry.
        private fun addMipsRunConfig(
            project: Project,
            runManager: RunManager,
            target: DetectedTarget
        ) {
            val type = MipsRunConfigurationType.getInstance()
            val existing = runManager.getConfigurationsList(type)
                .filterIsInstance<MipsRunConfiguration>()
                .map { it.scriptName }.toSet()
            if (target.sourceFile in existing) return

            val s = runManager.createConfiguration(
                "${target.name} (Run)", type.configurationFactories.first())
            (s.configuration as MipsRunConfiguration).apply {
                scriptName       = target.sourceFile
                workingDirectory = target.workDir
                cInterop         = target.cInterop
                assemblerArgs    = ""
            }
            s.isTemporary = false
            runManager.addConfiguration(s)
        }

        // ── QEMU config creation ─────────────────────────────────────────────

        private fun addQemuConfig(
            project: Project,
            runManager: RunManager,
            target: DetectedTarget
        ): com.intellij.execution.RunnerAndConfigurationSettings? {
            val qemuType = MipsQemuServerRunConfigurationType.getInstance()
            val existing = runManager.getConfigurationsList(qemuType)
                .filterIsInstance<MipsQemuServerRunConfiguration>()
                .map { it.scriptName }.toSet()
            if (target.sourceFile in existing) return null

            val q = runManager.createConfiguration(
                "${target.name} (QEMU Server)", qemuType.configurationFactories.first())
            (q.configuration as MipsQemuServerRunConfiguration).apply {
                scriptName       = target.sourceFile
                workingDirectory = target.workDir
                cInterop         = target.cInterop
                assemblerArgs    = ""
                gdbPort          = 1234
            }
            q.isTemporary = false
            runManager.addConfiguration(q)
            return q
        }

        // ── GDB Remote Debug config creation ────────────────────────────────

        private fun addGdbRemoteConfig(
            project: Project,
            runManager: RunManager,
            target: DetectedTarget,
            qemuSettings: com.intellij.execution.RunnerAndConfigurationSettings
        ) {
            val remoteType = ConfigurationTypeUtil.findConfigurationType(CLionRemoteRunConfigurationType::class.java)
            val configName = "${target.name} (GDB Debug)"
            // Auto-migrate: older plugin builds created a Remote GDB Server
            // config under the same name (wrong type — meant to upload + run
            // gdbserver, not attach to an existing stub). Remove it so the new
            // Remote Debug config below takes its place.
            evictOutdatedGdbDebugConfig(runManager, configName, CLionRemoteRunConfiguration::class.java)
            if (runManager.findConfigurationByName(configName) != null) return

            val qemuConfig = qemuSettings.configuration as MipsQemuServerRunConfiguration
            val s = runManager.createConfiguration(configName, remoteType.configurationFactories.first())
            val gdbConfig = s.configuration as CLionRemoteRunConfiguration
            // "Remote Debug" type just attaches gdb to a running target — no
            // upload, no gdbserver launch. Matches the user-tutorial flow where
            // QEMU is already the gdb stub.
            gdbConfig.params = CidrRemoteDebugParameters(
                /* remoteCommand = */ "tcp:localhost:${qemuConfig.gdbPort}",
                /* symbolFile    = */ symbolFilePathForTarget(target),
                /* sysroot       = */ if (target.cInterop) defaultMipsSysroot() else "",
                /* pathMappings  = */ wslSourcePathMappings(),
            )
            gdbConfig.debuggerData = debuggerDataForHost()

            // Wire QEMU server as "Before launch" step
            val provider = BeforeRunTaskProvider.getProvider(project, RunConfigurationBeforeRunProvider.ID)
            val task = provider?.createTask(gdbConfig)
            if (task is RunConfigurationBeforeRunProvider.RunConfigurableBeforeRunTask) {
                task.isEnabled = true
                task.setSettingsWithTarget(qemuSettings, DefaultExecutionTarget.INSTANCE)
                gdbConfig.setBeforeRunTasks(listOf(task))
            }

            s.isTemporary = false
            runManager.addConfiguration(s)
            maybeNotifyMacCodesign(project)
        }

        // The compiled ELF's path as the *host-side* gdb sees it. CLion's
        // Bundled GDB runs natively on the host (Windows/Linux/macOS), not
        // inside WSL, so the symbol-file path must be a native path — Windows
        // `C:\…`, not `/mnt/c/…`. The path mapping below handles the inverse
        // direction (translating /mnt/c source refs *inside* the DWARF back to
        // the Windows source files at debug time).
        private fun symbolFilePathForTarget(target: DetectedTarget): String {
            val base = File(target.sourceFile).nameWithoutExtension
            return File(target.workDir, ".asm-build/$base").path
        }

        // DWARF info emitted by the WSL-side assembler records source paths in
        // /mnt/c/… form. When the host-side bundled GDB encounters those, it
        // can't open them — Windows has no /mnt/c. The mapping tells gdb to
        // substitute /mnt/c → C:\ so source-level stepping resolves files
        // correctly. Returns an empty list on non-Windows where no mapping is
        // needed (both the assembler and gdb agree on path syntax).
        private fun wslSourcePathMappings(): List<CidrRemotePathMapping> {
            if (!PlatformHelper.isWindows) return emptyList()
            // Cover the common drive letters; users on D:\, etc. just add more
            // if needed (this list is a minimum-viable default).
            return listOf("c", "d").map {
                CidrRemotePathMapping("/mnt/$it", "${it.uppercase()}:\\")
            }
        }

        // Default sysroot for the MIPS cross-toolchain on Debian/Ubuntu. Only
        // used for C-interop builds where gdb needs libc symbols; pure-asm
        // builds link with -nostdlib so the sysroot is unused.
        private fun defaultMipsSysroot(): String = "/usr/mips-linux-gnu"

        // Removes any existing run config named [configName] whose configuration
        // is NOT an instance of [expectedClass]. Used to evict the old
        // RemoteGdbServerRunConfiguration auto-created by previous plugin
        // builds so the new CLionRemoteRunConfiguration replaces it in place.
        private fun evictOutdatedGdbDebugConfig(
            runManager: RunManager,
            configName: String,
            expectedClass: Class<*>,
        ) {
            val existing = runManager.findConfigurationByName(configName) ?: return
            if (!expectedClass.isInstance(existing.configuration)) {
                runCatching { runManager.removeConfiguration(existing) }
            }
        }

        // ── NASM cross-arch QEMU + GDB config creation ──────────────────────
        // Parallel to the MIPS pair above; kept separate (rather than
        // generalised) to avoid disturbing the shipping MIPS path.

        private fun addNasmQemuConfig(
            project: Project,
            runManager: RunManager,
            target: DetectedTarget
        ): com.intellij.execution.RunnerAndConfigurationSettings? {
            val qemuType = NasmQemuServerRunConfigurationType.getInstance()
            val existing = runManager.getConfigurationsList(qemuType)
                .filterIsInstance<NasmQemuServerRunConfiguration>()
                .map { it.scriptName }.toSet()
            if (target.sourceFile in existing) return null

            val q = runManager.createConfiguration(
                "${target.name} (QEMU Server)", qemuType.configurationFactories.first())
            (q.configuration as NasmQemuServerRunConfiguration).apply {
                scriptName       = target.sourceFile
                workingDirectory = target.workDir
                nasmFormat       = target.nasmFormat
                cInterop         = target.cInterop
                gdbPort          = 1234
            }
            q.isTemporary = false
            runManager.addConfiguration(q)
            return q
        }

        private fun addNasmGdbRemoteConfig(
            project: Project,
            runManager: RunManager,
            target: DetectedTarget,
            qemuSettings: com.intellij.execution.RunnerAndConfigurationSettings
        ) {
            val remoteType = ConfigurationTypeUtil.findConfigurationType(CLionRemoteRunConfigurationType::class.java)
            val configName = "${target.name} (GDB Debug)"
            evictOutdatedGdbDebugConfig(runManager, configName, CLionRemoteRunConfiguration::class.java)
            if (runManager.findConfigurationByName(configName) != null) return

            val qemuConfig = qemuSettings.configuration as NasmQemuServerRunConfiguration
            val s = runManager.createConfiguration(configName, remoteType.configurationFactories.first())
            val gdbConfig = s.configuration as CLionRemoteRunConfiguration
            gdbConfig.params = CidrRemoteDebugParameters(
                /* remoteCommand = */ "tcp:localhost:${qemuConfig.gdbPort}",
                /* symbolFile    = */ symbolFilePathForTarget(target),
                /* sysroot       = */ "",
                /* pathMappings  = */ wslSourcePathMappings(),
            )
            gdbConfig.debuggerData = debuggerDataForHost()

            // Wire QEMU server as "Before launch" step
            val provider = BeforeRunTaskProvider.getProvider(project, RunConfigurationBeforeRunProvider.ID)
            val task = provider?.createTask(gdbConfig)
            if (task is RunConfigurationBeforeRunProvider.RunConfigurableBeforeRunTask) {
                task.isEnabled = true
                task.setSettingsWithTarget(qemuSettings, DefaultExecutionTarget.INSTANCE)
                gdbConfig.setBeforeRunTasks(listOf(task))
            }

            s.isTemporary = false
            runManager.addConfiguration(s)
            maybeNotifyMacCodesign(project)
        }

        // ── CMakeLists.txt parser ────────────────────────────────────────────

        // Best-effort source lookup for GLOB-based projects: the conventional
        // main.<ext>, else the first file with that extension in the directory.
        private fun findSourceFile(dir: VirtualFile, ext: String): String? {
            val children = dir.children ?: return null
            return (children.firstOrNull { it.name.equals("main.$ext", ignoreCase = true) }
                ?: children.firstOrNull { it.name.endsWith(".$ext", ignoreCase = true) })?.path
        }

        private fun parseCmake(file: VirtualFile): DetectedTarget? {
            val dir = file.parent ?: return null
            val raw = runCatching { file.contentsToByteArray().toString(Charsets.UTF_8) }
                .getOrNull() ?: return null

            val text = raw.lines()
                .joinToString("\n") { it.substringBefore('#').trimEnd() }

            val isNasm = RE_PROJECT_NASM.containsMatchIn(text)
            val isMips = !isNasm && text.contains("mips-linux-gnu", ignoreCase = true)
            if (!isNasm && !isMips) return null

            val projectName = RE_PROJECT_NAME.find(text)?.groupValues?.getOrNull(1) ?: return null

            // Source file: prefer a literal name in add_executable(); fall back to
            // the conventional main.<ext> (or first matching file) when the project
            // uses a GLOB variable — e.g. file(GLOB SRC ...) + add_executable(... ${SRC}).
            val ext        = if (isNasm) "nasm" else "mips"
            val sourceName = RE_ADD_EXECUTABLE.find(text)?.groupValues?.getOrNull(1)
            val sourceFile = if (sourceName != null)
                dir.findFileByRelativePath(sourceName)?.path ?: "${dir.path}/$sourceName"
            else
                findSourceFile(dir, ext) ?: "${dir.path}/main.$ext"

            val nasmFormat = if (isNasm)
                RE_NASM_FORMAT.find(text)?.groupValues?.getOrNull(1) ?: defaultNasmFormat()
            else "elf64"

            // Pure-asm templates link with -nostdlib; C-interop ones don't. Works
            // for both NASM and MIPS regardless of which assembler driver is used.
            val cInterop = !text.contains("-nostdlib", ignoreCase = true)

            return DetectedTarget(
                name       = projectName,
                sourceFile = sourceFile,
                workDir    = dir.path,
                type       = if (isNasm) AsmType.NASM else AsmType.MIPS,
                nasmFormat = nasmFormat,
                cInterop   = cInterop
            )
        }

        // ── Data types ───────────────────────────────────────────────────────

        enum class AsmType { NASM, MIPS }

        data class DetectedTarget(
            val name: String,
            val sourceFile: String,
            val workDir: String,
            val type: AsmType,
            val nasmFormat: String,
            val cInterop: Boolean
        )

        // ── Regexes ──────────────────────────────────────────────────────────

        private val RE_PROJECT_NASM = Regex(
            """project\s*\(\s*[\w\-]+\s+ASM_NASM""", RegexOption.IGNORE_CASE)
        private val RE_PROJECT_NAME = Regex(
            """project\s*\(\s*([\w\-]+)""", RegexOption.IGNORE_CASE)
        private val RE_ADD_EXECUTABLE = Regex(
            """add_executable\s*\(\s*(?:\$\{PROJECT_NAME\}|[\w\-]+)\s+([\w./\-]+\.(?:nasm|mips|asm|s))\b""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
        private val RE_NASM_FORMAT = Regex(
            """-f\s+(elf64|elf32|macho64|win64|win32)""", RegexOption.IGNORE_CASE)

        private fun defaultNasmFormat(): String =
            runCatching { me.lucaperri.dev.languages.settings.AsmExecutableSettings.getInstance().defaultNasmArch.nasmFormat }
                .getOrDefault("elf64")
    }
}
