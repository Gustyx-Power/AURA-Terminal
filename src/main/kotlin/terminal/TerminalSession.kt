package terminal

import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Manages a terminal session using PTY (pseudo-terminal). Handles shell process lifecycle and I/O
 * operations.
 */
class TerminalSession(
        private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private var process: PtyProcess? = null
    private var outputWriter: OutputStreamWriter? = null
    private var readJob: Job? = null

    // Flow untuk mengirim output ke UI
    private val _outputFlow = MutableSharedFlow<String>(replay = 100)
    val outputFlow: SharedFlow<String> = _outputFlow.asSharedFlow()

    // Status terminal
    var isRunning: Boolean = false
        private set

    /** Starts the terminal session with the user's default shell. */
    fun start() {
        if (isRunning) {
            System.out.println("[TerminalSession] Already running")
            return
        }

        val shell = System.getenv("SHELL") ?: "/bin/bash"
        val home = System.getenv("HOME") ?: "/home"
        val user = System.getenv("USER") ?: "user"

        System.out.println("[TerminalSession] Starting shell: $shell")

        try {
            // Environment variables untuk shell
            val env =
                    mapOf(
                            "TERM" to "xterm-256color",
                            "HOME" to home,
                            "USER" to user,
                            "SHELL" to shell,
                            "PATH" to (System.getenv("PATH") ?: "/usr/bin:/bin"),
                            "LANG" to (System.getenv("LANG") ?: "en_US.UTF-8")
                    )

            // Build dan start PTY process
            process =
                    PtyProcessBuilder()
                            .setCommand(arrayOf(shell, "-l")) // Login shell
                            .setEnvironment(env)
                            .setDirectory(home)
                            .setInitialColumns(120)
                            .setInitialRows(40)
                            .start()

            outputWriter = OutputStreamWriter(process!!.outputStream, StandardCharsets.UTF_8)
            isRunning = true

            System.out.println(
                    "[TerminalSession] Shell started successfully (PID: ${process!!.pid()})"
            )

            // Start async output reading
            startOutputReader()
        } catch (e: Exception) {
            System.err.println("[TerminalSession] Failed to start shell: ${e.message}")
            e.printStackTrace()
            isRunning = false
        }
    }

    /** Reads output from the shell asynchronously using Coroutines. */
    private fun startOutputReader() {
        readJob =
                scope.launch {
                    val reader =
                            BufferedReader(
                                    InputStreamReader(process!!.inputStream, StandardCharsets.UTF_8)
                            )

                    System.out.println("[TerminalSession] Output reader started")

                    try {
                        val buffer = CharArray(4096)
                        while (isActive && isRunning) {
                            val bytesRead = reader.read(buffer)
                            if (bytesRead == -1) {
                                System.out.println("[TerminalSession] End of stream reached")
                                break
                            }

                            if (bytesRead > 0) {
                                val output = String(buffer, 0, bytesRead)

                                // Debug output ke console
                                System.out.print(output)

                                // Emit ke flow untuk UI
                                _outputFlow.emit(output)
                            }
                        }
                    } catch (e: Exception) {
                        if (isRunning) {
                            System.err.println("[TerminalSession] Read error: ${e.message}")
                        }
                    } finally {
                        System.out.println("[TerminalSession] Output reader stopped")
                    }
                }
    }

    /** Sends input/command to the shell. */
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

    /** Sends a command followed by newline. */
    fun sendCommand(command: String) {
        sendInput("$command\n")
    }

    /** Resizes the terminal. */
    fun resize(columns: Int, rows: Int) {
        process?.let {
            try {
                it.winSize = com.pty4j.WinSize(columns, rows)
                System.out.println("[TerminalSession] Resized to ${columns}x${rows}")
            } catch (e: Exception) {
                System.err.println("[TerminalSession] Resize error: ${e.message}")
            }
        }
    }

    /** Stops the terminal session and cleanup resources. */
    fun stop() {
        if (!isRunning) return

        System.out.println("[TerminalSession] Stopping terminal session...")

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

        System.out.println("[TerminalSession] Terminal session stopped")
    }
}
