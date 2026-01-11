package terminal

import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
import com.pty4j.WinSize
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class TerminalSession(
        private val shell: String = System.getenv("SHELL") ?: "/bin/bash",
        private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private var process: PtyProcess? = null
    private var outputWriter: OutputStreamWriter? = null
    private var readJob: Job? = null

    private val _outputFlow = MutableSharedFlow<String>(replay = 100)
    val outputFlow: SharedFlow<String> = _outputFlow.asSharedFlow()

    var isRunning: Boolean = false
        private set

    fun start() {
        if (isRunning) {
            println("[TerminalSession] Already running")
            return
        }

        val home = System.getenv("HOME") ?: "/home"
        val user = System.getenv("USER") ?: "user"

        println("[TerminalSession] Starting shell: $shell")

        try {
            val env =
                    mapOf(
                            "TERM" to "xterm-256color",
                            "COLORTERM" to "truecolor",
                            "HOME" to home,
                            "USER" to user,
                            "SHELL" to shell,
                            "PATH" to (System.getenv("PATH") ?: "/usr/bin:/bin"),
                            "LANG" to (System.getenv("LANG") ?: "en_US.UTF-8"),
                            "LC_ALL" to (System.getenv("LC_ALL") ?: "en_US.UTF-8"),
                            "COLUMNS" to "120",
                            "LINES" to "40"
                    )

            process =
                    PtyProcessBuilder()
                            .setCommand(arrayOf(shell, "-l"))
                            .setEnvironment(env)
                            .setDirectory(home)
                            .setInitialColumns(120)
                            .setInitialRows(40)
                            .start()

            outputWriter = OutputStreamWriter(process!!.outputStream, StandardCharsets.UTF_8)
            isRunning = true

            println("[TerminalSession] Shell started successfully (PID: ${process!!.pid()})")
            startOutputReader()
        } catch (e: Exception) {
            System.err.println("[TerminalSession] Failed to start shell: ${e.message}")
            e.printStackTrace()
            isRunning = false
        }
    }

    private fun startOutputReader() {
        readJob =
                scope.launch {
                    val reader =
                            BufferedReader(
                                    InputStreamReader(process!!.inputStream, StandardCharsets.UTF_8)
                            )
                    println("[TerminalSession] Output reader started")

                    try {
                        val buffer = CharArray(4096)
                        while (isActive && isRunning) {
                            val bytesRead = reader.read(buffer)
                            if (bytesRead == -1) {
                                println("[TerminalSession] End of stream reached")
                                break
                            }
                            if (bytesRead > 0) {
                                val output = String(buffer, 0, bytesRead)
                                print(output)
                                _outputFlow.emit(output)
                            }
                        }
                    } catch (e: Exception) {
                        if (isRunning)
                                System.err.println("[TerminalSession] Read error: ${e.message}")
                    } finally {
                        println("[TerminalSession] Output reader stopped")
                    }
                }
    }

    fun sendInput(input: String) {
        if (!isRunning || outputWriter == null) {
            System.err.println("[TerminalSession] Cannot send input: terminal not running")
            return
        }
        try {
            outputWriter?.apply {
                write(input)
                flush()
            }
        } catch (e: Exception) {
            System.err.println("[TerminalSession] Send error: ${e.message}")
        }
    }

    fun sendCommand(command: String) = sendInput("$command\n")

    fun resize(columns: Int, rows: Int) {
        process?.let {
            try {
                it.winSize = WinSize(columns, rows)
                println("[TerminalSession] Resized to ${columns}x${rows}")
            } catch (e: Exception) {
                System.err.println("[TerminalSession] Resize error: ${e.message}")
            }
        }
    }

    fun stop() {
        if (!isRunning) return
        println("[TerminalSession] Stopping terminal session...")

        isRunning = false
        readJob?.cancel()

        try {
            outputWriter?.close()
            process?.destroyForcibly()
        } catch (e: Exception) {
            System.err.println("[TerminalSession] Stop error: ${e.message}")
        }

        process = null
        outputWriter = null
        println("[TerminalSession] Terminal session stopped")
    }
}
