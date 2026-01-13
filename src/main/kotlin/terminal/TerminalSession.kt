package terminal

import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
import com.pty4j.WinSize

import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

import kotlinx.coroutines.channels.BufferOverflow

class TerminalSession(
        private val shell: String = getDefaultShell(),
        private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    companion object {
        private val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        
        fun getDefaultShell(): String {
            return if (isWindows) {
                "C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe"
            } else {
                System.getenv("SHELL") ?: "/bin/bash"
            }
        }
        
        fun getHomeDirectory(): String {
            return if (isWindows) {
                System.getenv("USERPROFILE") ?: System.getenv("HOMEDRIVE")?.let { 
                    it + System.getenv("HOMEPATH") 
                } ?: "C:\\Users"
            } else {
                System.getenv("HOME") ?: "/home"
            }
        }
        
        fun getUsername(): String {
            return if (isWindows) {
                System.getenv("USERNAME") ?: "user"
            } else {
                System.getenv("USER") ?: "user"
            }
        }
    }
    
    private var process: PtyProcess? = null
    private var outputWriter: OutputStreamWriter? = null
    private var readJob: Job? = null

    private val _outputFlow = MutableSharedFlow<ByteArray>(
        replay = 100,
        extraBufferCapacity = 1000, 
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val outputFlow: SharedFlow<ByteArray> = _outputFlow.asSharedFlow()

    var isRunning: Boolean = false
        private set

    private val inputChannel = kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.UNLIMITED)
    private var inputJob: Job? = null

    fun start() {
        if (isRunning) {
            println("[TerminalSession] Already running")
            return
        }

        val home = getHomeDirectory()
        val user = getUsername()

        println("[TerminalSession] Starting shell: $shell (Windows: $isWindows)")

        try {
            val env = if (isWindows) {
                mutableMapOf(
                    "TERM" to "xterm-256color",
                    "COLORTERM" to "truecolor",
                    "USERPROFILE" to home,
                    "USERNAME" to user,
                    "COLUMNS" to "120",
                    "LINES" to "40"
                ).apply {
                    System.getenv("PATH")?.let { put("PATH", it) }
                    System.getenv("SYSTEMROOT")?.let { put("SYSTEMROOT", it) }
                    System.getenv("SYSTEMDRIVE")?.let { put("SYSTEMDRIVE", it) }
                    System.getenv("COMSPEC")?.let { put("COMSPEC", it) }
                    System.getenv("TEMP")?.let { put("TEMP", it) }
                    System.getenv("TMP")?.let { put("TMP", it) }
                    System.getenv("HOMEDRIVE")?.let { put("HOMEDRIVE", it) }
                    System.getenv("HOMEPATH")?.let { put("HOMEPATH", it) }
                    System.getenv("APPDATA")?.let { put("APPDATA", it) }
                    System.getenv("LOCALAPPDATA")?.let { put("LOCALAPPDATA", it) }
                    System.getenv("PROGRAMFILES")?.let { put("PROGRAMFILES", it) }
                    System.getenv("WINDIR")?.let { put("WINDIR", it) }
                }
            } else {
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
            }

            val command = if (isWindows) {
                arrayOf(shell, "-NoLogo", "-NoExit")
            } else {
                arrayOf(shell, "-l")
            }

            process =
                    PtyProcessBuilder()
                            .setCommand(command)
                            .setEnvironment(env)
                            .setDirectory(home)
                            .setInitialColumns(120)
                            .setInitialRows(40)
                            .start()

            outputWriter = OutputStreamWriter(process!!.outputStream, StandardCharsets.UTF_8)
            isRunning = true

            println("[TerminalSession] Shell started successfully (PID: ${process!!.pid()})")
            startOutputReader()
            startInputProcessor()
        } catch (e: Exception) {
            System.err.println("[TerminalSession] Failed to start shell: ${e.message}")
            e.printStackTrace()
            isRunning = false
        }
    }

    private fun startOutputReader() {
        readJob =
                scope.launch(Dispatchers.IO) {
                    val inputStream = process!!.inputStream
                    println("[TerminalSession] Output reader started")

                    try {
                        val buffer = ByteArray(8192)
                        
                        while (isActive && isRunning) {
                             val available = inputStream.available()
                             if (available > 0) {
                                 // Read whatever is available or up to buffer size
                                 val readCount = inputStream.read(buffer)
                                 if (readCount == -1) break
                                 
                                 if (readCount > 0) {
                                     // Crucial: emit only valid bytes
                                     val data = buffer.copyOfRange(0, readCount)
                                     _outputFlow.emit(data)
                                 }
                             } else {
                                 delay(10) // Small delay to prevent tight loop
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

    private fun startInputProcessor() {
        inputJob = scope.launch {
            for (input in inputChannel) {
                try {
                    outputWriter?.apply {
                        write(input)
                        flush()
                    }
                } catch (e: Exception) {
                    System.err.println("[TerminalSession] Send error: ${e.message}")
                }
            }
        }
    }

    fun sendInput(input: String) {
        inputChannel.trySend(input)
    }

    fun sendCommand(command: String) = sendInput("$command\r")

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
