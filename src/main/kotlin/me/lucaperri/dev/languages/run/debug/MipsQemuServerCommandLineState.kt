package me.lucaperri.dev.languages.run.debug

import me.lucaperri.dev.languages.run.MipsBuildPipeline
import me.lucaperri.dev.languages.run.toolchain.PlatformHelper
import me.lucaperri.dev.languages.run.toolchain.WslToolchainProbe

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.application.ApplicationManager
import me.lucaperri.dev.languages.settings.AsmExecutableSettings
import java.io.File
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicReference

// Builds the MIPS binary via MipsBuildPipeline, then starts
// `qemu-mips-static -g <port> <binary>` and blocks waiting for a GDB connection.
// Use this as a "Before launch" step in a Remote GDB Server debug configuration.
class MipsQemuServerCommandLineState(
    private val environment: ExecutionEnvironment,
    private val config: MipsQemuServerRunConfiguration
) : RunProfileState {

    private val currentProcess = AtomicReference<Process?>(null)

    override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult {
        val console = TextConsoleBuilderFactory.getInstance()
            .createBuilder(environment.project).console
        val handler = makeHandler(console)
        console.attachToProcess(handler)
        handler.startNotify()
        return DefaultExecutionResult(console, handler)
    }

    private fun makeHandler(console: ConsoleView): ProcessHandler = object : ProcessHandler() {
        override fun startNotify() {
            super.startNotify()
            ApplicationManager.getApplication().executeOnPooledThread {
                notifyProcessTerminated(runPipeline(console))
            }
        }
        override fun destroyProcessImpl() { currentProcess.get()?.destroy() }
        override fun detachProcessImpl()  { notifyProcessDetached() }
        override fun detachIsDefault()    = false
        override fun getProcessInput(): OutputStream? = null
    }

    private fun runPipeline(console: ConsoleView): Int {
        val ph       = PlatformHelper
        val settings = AsmExecutableSettings.getInstance()

        // Preflight: fail fast with an actionable message if the WSL/native
        // toolchain is missing the tools this debug path needs.
        if (!WslToolchainProbe.preflight(
                WslToolchainProbe.mipsDebugTools(config.cInterop),
                "MIPS debug", settings, console)) return 1

        val out = MipsBuildPipeline.build(
            config.scriptName, config.workingDirectory,
            config.assemblerArgs, config.cInterop, console, settings,
            debug = true,
            marchOverride = config.marchOverride,
        ) ?: return 1

        val workDir = config.workingDirectory.ifBlank { out.parent ?: "." }
        val emulBin = settings.mipsEmulatorPath.ifBlank { "qemu-mips-static" }
        val port    = config.gdbPort

        // The "(GDB Debug)" sibling — auto-created alongside this config — runs
        // this server as its Before Launch step and then attaches gdb-multiarch.
        // Surface that here so a user who runs the server standalone knows what
        // to launch instead.
        val gdbConfigName = config.name.removeSuffix(" (QEMU Server)") + " (GDB Debug)"
        sys(console, "\nStarting QEMU GDB stub on port $port...\n")
        sys(console, "─────────────────────────────────────────────────\n")
        sys(console, "This is the QEMU SERVER side. To actually debug, launch\n")
        sys(console, "\"$gdbConfigName\" — it starts this server as a Before Launch\n")
        sys(console, "step and attaches gdb-multiarch automatically.\n")
        sys(console, "─────────────────────────────────────────────────\n\n")

        // Detach qemu so this run-config "completes" once qemu is listening.
        // Critical: when this state runs as a Before Launch step for the GDB
        // Debug config, that step must RETURN before the debugger attaches —
        // foreground-waiting on qemu would deadlock (qemu only exits after the
        // GDB session completes, but the GDB session never starts because the
        // before-launch never finishes). nohup + & + disown detaches qemu from
        // bash, and bash returns immediately after the sleep grace period.
        // qemu cleans itself up when the debugged program exits.
        val logFile = File(System.getProperty("java.io.tmpdir"),
            "clion-asm-qemu-mips-$port.log")
        return try {
            // Path / log path inside the target environment (WSL on Windows,
            // native otherwise) — used both by the cleanup step and the launch.
            val binEsc = (if (ph.isWindows) ph.toWslPath(out.path) else out.path)
                .replace("'", "'\\''")
            val logEsc = (if (ph.isWindows) ph.toWslPath(logFile.path) else logFile.path)
                .replace("'", "'\\''")
            // Use the binary's basename for the pkill pattern so user-configured
            // absolute paths still match, and so we don't accidentally kill a
            // user's own process that happens to listen on the same port.
            //
            // The basename's last character is wrapped in a one-element regex
            // character class — `qemu-mips-stati[c]` — so the pattern matches
            // qemu's actual cmdline (ERE: any of "c") but does NOT match this
            // bash script's own cmdline (which contains the literal brackets).
            // Without this trick `pgrep -f` finds itself and `pkill -f` kills
            // the parent bash with SIGTERM.
            val emulBase = emulBin.substringAfterLast('/').substringAfterLast('\\')
            val emulPattern = if (emulBase.length > 1)
                emulBase.dropLast(1) + "[" + emulBase.last() + "]"
            else emulBase
            // Write the launch script to a file and run it via `bash <file>`,
            // not via `bash -c "<inline script>"`. The inline form was tripping
            // over a quoting/&-handling edge case in the
            // `Java GeneralCommandLine -> CreateProcess -> wsl.exe -> bash -c`
            // chain — bash received the script garbled (`$!` came back empty,
            // so kill -0 of an empty PID always failed and we reported "qemu
            // exited prematurely" even though qemu was perfectly alive).
            // Verified empirically: the same script in a file works; via
            // `bash -c` it dies. This also mirrors the user-tutorial's
            // Shell Script run-config approach, which has never had this issue.
            val scriptFile = File.createTempFile("clion-asm-qemu-mips-launch-", ".sh").apply {
                deleteOnExit()
                writeText(buildString {
                    append("#!/usr/bin/env bash\n")
                    append("set +e\n")
                    // Auto-cleanup: a prior debug session that was killed
                    // mid-flight can leave qemu still bound to this port. Nuke
                    // any stale instance of *our* qemu binary that's listening
                    // on \$port before we try to launch a fresh one —
                    // otherwise the new qemu fails with
                    //   bind: Address already in use
                    //   qemu: could not open gdbserver on \$port
                    append("if pgrep -f '$emulPattern -g $port' >/dev/null 2>&1; then\n")
                    append("    echo '(cleaning up stale qemu on port $port)'\n")
                    append("    pkill -f '$emulPattern -g $port' 2>/dev/null || true\n")
                    append("    sleep 0.3\n")
                    append("fi\n")
                    if (ph.isWindows) append("chmod +x '$binEsc'\n")
                    append("nohup '$emulBin' -g $port '$binEsc' > '$logEsc' 2>&1 &\n")
                    append("QPID=\$!\n")
                    append("disown \$QPID 2>/dev/null || true\n")
                    append("sleep 0.5\n")
                    append("if ! kill -0 \$QPID 2>/dev/null; then\n")
                    append("    echo 'qemu exited prematurely, log follows:' 1>&2\n")
                    append("    cat '$logEsc' 1>&2\n")
                    append("    exit 1\n")
                    append("fi\n")
                    append("echo \"qemu listening on port $port (pid \$QPID)\"\n")
                })
            }
            val scriptPath = if (ph.isWindows) ph.toWslPath(scriptFile.path) else scriptFile.path
            // `--` separates wsl.exe's own flags from the command it runs inside
            // WSL. Without it wsl.exe parses some bash args as its own.
            val cmd = if (ph.isWindows)
                ph.wslExe() + listOf("--", "bash", scriptPath)
            else
                listOf("bash", scriptPath)

            val process = GeneralCommandLine(cmd)
                .withWorkDirectory(File(workDir))
                .createProcess()
            currentProcess.set(process)

            val errThread = Thread {
                process.errorStream.bufferedReader()
                    .forEachLine { err(console, it + "\n") }
            }.also { it.isDaemon = true; it.start() }
            val outThread = Thread {
                process.inputStream.bufferedReader()
                    .forEachLine { sys(console, it + "\n") }
            }.also { it.isDaemon = true; it.start() }

            // This waitFor returns once bash exits (≈ 0.5s after launch);
            // qemu itself keeps running in WSL/system because it was disowned.
            val code = process.waitFor()
            errThread.join(2000)
            outThread.join(2000)
            currentProcess.set(null)
            if (code == 0)
                sys(console, "Ready for GDB to connect at localhost:$port.\n")
            else
                err(console, "Failed to start QEMU (exit $code). See log: ${logFile.path}\n")
            code
        } catch (e: Exception) { err(console, "QEMU error: ${e.message}\n"); 1 }
    }

    private fun err(c: ConsoleView, t: String) = c.print(t, ConsoleViewContentType.ERROR_OUTPUT)
    private fun sys(c: ConsoleView, t: String) = c.print(t, ConsoleViewContentType.SYSTEM_OUTPUT)
}
