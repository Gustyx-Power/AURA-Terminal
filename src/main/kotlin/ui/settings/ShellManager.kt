package ui.settings

import java.io.File

data class ShellInfo(
        val name: String,
        val path: String,
        val isInstalled: Boolean,
        val installCommand: String
)

object ShellManager {
    private val osName = System.getProperty("os.name").lowercase()

    val isWindows = osName.contains("windows")
    val isMac = osName.contains("mac")
    val isArch = isLinuxDistro("arch") || isLinuxDistro("manjaro") || isLinuxDistro("endeavouros")
    val isFedora = isLinuxDistro("fedora") || isLinuxDistro("rhel") || isLinuxDistro("centos")
    val isDebian =
            isLinuxDistro("debian") ||
                    isLinuxDistro("ubuntu") ||
                    isLinuxDistro("mint") ||
                    isLinuxDistro("pop")

    private fun isLinuxDistro(name: String): Boolean {
        return try {
            val osRelease = File("/etc/os-release")
            if (osRelease.exists()) {
                osRelease.readText().lowercase().contains(name)
            } else false
        } catch (e: Exception) {
            false
        }
    }

    private fun getInstallCommand(shell: String): String {
        val sudoPrefix = if (!isWindows) "sudo " else ""
        return when {
            isWindows ->
                    when (shell) {
                        "bash" -> "winget install Git.Git"
                        "zsh" -> "winget install zsh"
                        "fish" -> "winget install fish"
                        "powershell" -> "Already installed"
                        else -> ""
                    }
            isMac -> "${sudoPrefix}brew install $shell"
            isArch -> "${sudoPrefix}pacman -S --noconfirm $shell"
            isFedora -> "${sudoPrefix}dnf install -y $shell"
            isDebian -> "${sudoPrefix}apt install -y $shell"
            else -> "${sudoPrefix}apt install -y $shell" // Default to apt
        }
    }

    private fun getShellPath(shell: String): String {
        return when {
            isWindows ->
                    when (shell) {
                        "bash" -> "C:\\Program Files\\Git\\bin\\bash.exe"
                        "powershell" -> "powershell.exe"
                        else -> ""
                    }
            else -> "/bin/$shell"
        }
    }

    fun checkShellInstalled(shell: String): Boolean {
        val path = getShellPath(shell)
        return if (isWindows) {
            try {
                val process = ProcessBuilder("where", shell).start()
                process.waitFor() == 0
            } catch (e: Exception) {
                false
            }
        } else {
            File(path).exists() || File("/usr/bin/$shell").exists()
        }
    }

    fun getAvailableShells(): List<ShellInfo> {
        val shells =
                if (isWindows) {
                    listOf("powershell", "bash")
                } else {
                    listOf("bash", "zsh", "fish", "sh")
                }

        return shells.map { shell ->
            ShellInfo(
                    name = shell,
                    path = getShellPath(shell),
                    isInstalled = checkShellInstalled(shell),
                    installCommand = getInstallCommand(shell)
            )
        }
    }

    fun getDefaultShell(): String {
        return System.getenv("SHELL") ?: if (isWindows) "powershell" else "/bin/bash"
    }

    fun installShell(shell: String, onOutput: (String) -> Unit, onComplete: (Boolean) -> Unit) {
        val command = getInstallCommand(shell)
        if (command.isEmpty() || command == "Already installed") {
            onComplete(true)
            return
        }

        Thread {
                    try {
                        val processBuilder =
                                if (isWindows) {
                                    ProcessBuilder("cmd", "/c", command)
                                } else {
                                    // For Linux/macOS, we need to run in a terminal that can handle
                                    // sudo
                                    ProcessBuilder("bash", "-c", command)
                                }

                        processBuilder.redirectErrorStream(true)
                        val process = processBuilder.start()

                        process.inputStream.bufferedReader().useLines { lines ->
                            lines.forEach { line -> onOutput(line) }
                        }

                        val exitCode = process.waitFor()
                        onComplete(exitCode == 0)
                    } catch (e: Exception) {
                        onOutput("Error: ${e.message}")
                        onComplete(false)
                    }
                }
                .start()
    }

    fun getOsDisplayName(): String {
        return when {
            isWindows -> "Windows"
            isMac -> "macOS"
            isArch -> "Arch Linux"
            isFedora -> "Fedora/RHEL"
            isDebian -> "Debian/Ubuntu"
            else -> "Linux"
        }
    }
}
