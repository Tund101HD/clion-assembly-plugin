package me.lucaperri.dev.languages.run.debug

import me.lucaperri.dev.languages.run.NasmBuildPipeline
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

// Builds the NASM binary via NasmBuildPipeline (with DWARF symbols), then starts
// `qemu-x86_64 -g <port> <binary>` and blocks waiting for a GDB connection.
// Use this as a "Before launch" step in a Remote GDB Server debug configuration.
//
// This is the cross-arch path: an x86-64 ELF can't run natively on an ARM host
// (or under WSL2's ARM64 kernel), so qemu-x86_64 user-mode emulation provides the
// gdbstub. Mirrors MipsQemuServerCommandLineState.
class NasmQemuServerCommandLineState(
    private val environment: ExecutionEnvironment,
    private val config: NasmQemuServerRunConfiguration
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
        if (!WslToolchainProbe.preflight(WslToolchainProbe.Capability.NASM_DEBUG, settings, console)) return 1

        val out = NasmBuildPipeline.build(
            config.scriptName, config.workingDirectory,
            config.nasmFormat, config.cInterop, console, settings,
            debug = true,
        ) ?: return 1

        val workDir = config.workingDirectory.ifBlank { out.parent ?: "." }
        val emulBin = settings.qemuX86_64Path.ifBlank { "qemu-x86_64-static" }
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
        // See MipsQemuServerCommandLineState for the deadlock rationale: when
        // run as a Before Launch step, foreground-waiting on qemu would block
        // GDB from ever attaching. nohup + & + disown lets bash return as soon
        // as qemu is up; qemu cleans itself up when the debugged program exits.
        val logFile = File(System.getProperty("java.io.tmpdir"),
            "clion-asm-qemu-nasm-$port.log")
        return try {
            val binEsc = (if (ph.isWindows) ph.toWslPath(out.path) else out.path)
                .replace("'", "'\\''")
            val logEsc = (if (ph.isWindows) ph.toWslPath(logFile.path) else logFile.path)
                .replace("'", "'\\''")
            // See MipsQemuServerCommandLineState for the [c]-trick rationale:
            // wrapping the basename's last character in a regex character class
            // makes the pattern match qemu's cmdline but NOT this script's own
            // cmdline (which would otherwise self-kill with SIGTERM/exit 15).
            val emulBase = emulBin.substringAfterLast('/').substringAfterLast('\\')
            val emulPattern = if (emulBase.length > 1)
                emulBase.dropLast(1) + "[" + emulBase.last() + "]"
            else emulBase
            // See MipsQemuServerCommandLineState: write the launch script to a
            // file and exec via `bash <file>`. Inline `bash -c "..."` through
            // GeneralCommandLine -> wsl.exe -> bash garbles `$!` (returns
            // empty), so kill -0 always fails and we falsely report "exited
            // prematurely" even though qemu is alive.
            val scriptFile = File.createTempFile("clion-asm-qemu-nasm-launch-", ".sh").apply {
                deleteOnExit()
                writeText(buildString {
                    append("#!/usr/bin/env bash\n")
                    append("set +e\n")
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
