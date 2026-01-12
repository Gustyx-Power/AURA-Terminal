package ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.lang.management.ManagementFactory
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

val StatusBarBackground = Color(0xFF252526)
val StatusBarText = Color(0xFF858585)
val StatusBarAccent = Color(0xFF0078D4)

@Composable
fun StatusBar(
        onSettingsClick: () -> Unit,
        modifier: Modifier = Modifier
) {
    var currentDirectory by remember { mutableStateOf(System.getenv("HOME") ?: "~") }
    var gitBranch by remember { mutableStateOf<String?>(null) }
    var cpuUsage by remember { mutableStateOf("0%") }
    var memoryUsage by remember { mutableStateOf("0MB") }
    var currentTime by remember { mutableStateOf("") }

    // Update time every second
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
            delay(1000)
        }
    }

    // Update system stats and current directory every 2 seconds
    LaunchedEffect(Unit) {
        while (true) {
            // Get current working directory
            try {
                val osName = System.getProperty("os.name").lowercase()
                val pwd = when {
                    osName.contains("mac") || osName.contains("darwin") -> {
                        // macOS: Use lsof to get cwd of shell process
                        try {
                            val result = Runtime.getRuntime().exec(arrayOf("bash", "-c", "lsof -p \$(pgrep -n zsh 2>/dev/null || pgrep -n bash 2>/dev/null) 2>/dev/null | grep cwd | awk '{print \$9}'"))
                                .inputStream.bufferedReader().readText().trim()
                            if (result.isNotEmpty() && File(result).exists()) result else null
                        } catch (e: Exception) { null }
                    }
                    osName.contains("linux") -> {
                        // Linux: Read from /proc
                        try {
                            val shellPid = ProcessHandle.current().children()
                                .filter { it.info().command().orElse("").contains("sh") }
                                .findFirst().orElse(null)?.pid()
                            if (shellPid != null) {
                                File("/proc/$shellPid/cwd").canonicalPath
                            } else null
                        } catch (e: Exception) { null }
                    }
                    osName.contains("windows") -> {
                        // Windows: Use wmic to get current directory
                        try {
                            val shellPid = ProcessHandle.current().children()
                                .filter { 
                                    val cmd = it.info().command().orElse("").lowercase()
                                    cmd.contains("cmd") || cmd.contains("powershell")
                                }
                                .findFirst().orElse(null)?.pid()
                            if (shellPid != null) {
                                val result = Runtime.getRuntime().exec(arrayOf("cmd", "/c", "wmic process where processid=$shellPid get ExecutablePath 2>nul"))
                                    .inputStream.bufferedReader().readText()
                                // Fallback to user profile if wmic doesn't work well
                                System.getenv("USERPROFILE") ?: "C:\\"
                            } else {
                                System.getenv("USERPROFILE") ?: "C:\\"
                            }
                        } catch (e: Exception) { 
                            System.getenv("USERPROFILE") ?: "C:\\"
                        }
                    }
                    else -> null
                }
                
                if (pwd != null && pwd != currentDirectory) {
                    currentDirectory = pwd
                    gitBranch = getGitBranch(pwd)
                }
            } catch (e: Exception) {
                // Keep current values
            }
            try {
                val osName = System.getProperty("os.name").lowercase()
                
                if (osName.contains("linux")) {
                    // Linux: Read from /proc/meminfo
                    val memInfo = File("/proc/meminfo").readLines()
                    val totalLine = memInfo.find { it.startsWith("MemTotal:") }
                    val availableLine = memInfo.find { it.startsWith("MemAvailable:") }

                    if (totalLine != null && availableLine != null) {
                        val totalKb = totalLine.filter { it.isDigit() }.toLong()
                        val availableKb = availableLine.filter { it.isDigit() }.toLong()
                        val usedKb = totalKb - availableKb

                        val totalGb = totalKb / (1024.0 * 1024.0)
                        val usedGb = usedKb / (1024.0 * 1024.0)
                        val percent = (usedKb.toDouble() / totalKb.toDouble() * 100).toInt()

                        memoryUsage = "%.1fGB/%.0fGB (%d%%)".format(usedGb, totalGb, percent)
                    }
                } else if (osName.contains("mac") || osName.contains("darwin")) {
                    // macOS: Use sysctl and vm_stat
                    val totalBytes = Runtime.getRuntime().exec(arrayOf("sysctl", "-n", "hw.memsize"))
                        .inputStream.bufferedReader().readText().trim().toLongOrNull() ?: 0L
                    
                    val vmStat = Runtime.getRuntime().exec("vm_stat")
                        .inputStream.bufferedReader().readText()
                    
                    val pageSize = 16384L // macOS typically uses 16KB pages
                    var freePages = 0L
                    var inactivePages = 0L
                    var speculativePages = 0L
                    
                    vmStat.lines().forEach { line ->
                        when {
                            line.contains("Pages free:") -> 
                                freePages = line.filter { it.isDigit() }.toLongOrNull() ?: 0L
                            line.contains("Pages inactive:") -> 
                                inactivePages = line.filter { it.isDigit() }.toLongOrNull() ?: 0L
                            line.contains("Pages speculative:") -> 
                                speculativePages = line.filter { it.isDigit() }.toLongOrNull() ?: 0L
                        }
                    }
                    
                    val availableBytes = (freePages + inactivePages + speculativePages) * pageSize
                    val usedBytes = totalBytes - availableBytes
                    
                    val totalGb = totalBytes / (1024.0 * 1024.0 * 1024.0)
                    val usedGb = usedBytes / (1024.0 * 1024.0 * 1024.0)
                    val percent = if (totalBytes > 0) (usedBytes.toDouble() / totalBytes * 100).toInt() else 0
                    
                    memoryUsage = "%.1fGB/%.0fGB (%d%%)".format(usedGb, totalGb, percent)
                } else if (osName.contains("windows")) {
                    // Windows: Use PowerShell to get system memory
                    try {
                        // Get total physical memory using PowerShell
                        val totalMemResult = Runtime.getRuntime().exec(arrayOf(
                            "powershell", "-NoProfile", "-Command",
                            "(Get-CimInstance -ClassName Win32_ComputerSystem).TotalPhysicalMemory"
                        )).inputStream.bufferedReader().readText()
                        val totalBytes = totalMemResult.trim().toLongOrNull() ?: 0L
                        
                        // Get available memory using PowerShell (returns KB)
                        val availMemResult = Runtime.getRuntime().exec(arrayOf(
                            "powershell", "-NoProfile", "-Command",
                            "(Get-CimInstance -ClassName Win32_OperatingSystem).FreePhysicalMemory"
                        )).inputStream.bufferedReader().readText()
                        val availableKb = availMemResult.trim().toLongOrNull() ?: 0L
                        
                        val availableBytes = availableKb * 1024
                        val usedBytes = totalBytes - availableBytes
                        
                        val totalGb = totalBytes / (1024.0 * 1024.0 * 1024.0)
                        val usedGb = usedBytes / (1024.0 * 1024.0 * 1024.0)
                        val percent = if (totalBytes > 0) (usedBytes.toDouble() / totalBytes * 100).toInt() else 0
                        
                        memoryUsage = "%.1fGB/%.0fGB (%d%%)".format(usedGb, totalGb, percent)
                    } catch (e: Exception) {
                        // Fallback to JVM memory
                        val runtime = Runtime.getRuntime()
                        val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
                        memoryUsage = "JVM: ${usedMem}MB"
                    }
                } else {
                    // Other OS: Use JVM memory as fallback
                    val runtime = Runtime.getRuntime()
                    val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
                    memoryUsage = "JVM: ${usedMem}MB"
                }
            } catch (e: Exception) {
                memoryUsage = "N/A"
            }

            // CPU Load (Percentage)
            try {
                val osBean =
                        ManagementFactory.getOperatingSystemMXBean() as
                                com.sun.management.OperatingSystemMXBean
                val cpuLoad = osBean.systemCpuLoad
                // cpuLoad is between 0.0 and 1.0. Convert to percentage.
                // Note: First call might return NaN or -1, need to handle that.
                cpuUsage =
                        if (!cpuLoad.isNaN() && cpuLoad >= 0) "%.0f%%".format(cpuLoad * 100)
                        else "0%"
            } catch (e: Exception) {
                // Fallback for non-Sun JVMs
                try {
                    val osBean = ManagementFactory.getOperatingSystemMXBean()
                    val load = osBean.systemLoadAverage
                    cpuUsage = if (load >= 0) "%.0f%%".format(load * 10) else "N/A"
                } catch (ex: Exception) {
                    cpuUsage = "N/A"
                }
            }

            delay(2000)
        }
    }

    Row(
            modifier =
                    modifier.fillMaxWidth()
                            .height(28.dp)
                            .background(StatusBarBackground)
                            .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
    ) {
        // Left side: Settings, directory and git
        Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                    modifier = Modifier.clickable { onSettingsClick() },
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        modifier = Modifier.size(14.dp),
                        tint = StatusBarText
                )
                StatusText("Settings")
            }
            StatusText(shortenPath(currentDirectory))
            gitBranch?.let { StatusText("⎇ $it", StatusBarAccent) }
        }

        // Right side: stats and time
        Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            StatusText("CPU: $cpuUsage")
            StatusText("RAM: $memoryUsage")
            StatusText(currentTime)
        }
    }
}

@Composable
private fun StatusText(text: String, color: Color = StatusBarText) {
    Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 14.sp // Setting explicit line height helps centering
    )
}

private fun shortenPath(path: String): String {
    val home = System.getenv("HOME") ?: ""
    return if (path.startsWith(home)) "~${path.removePrefix(home)}" else path
}

private fun getGitBranch(directory: String): String? {
    return try {
        var dir = File(directory)
        while (dir.exists()) {
            val gitDir = File(dir, ".git")
            if (gitDir.exists()) {
                val headFile = File(gitDir, "HEAD")
                if (headFile.exists()) {
                    val content = headFile.readText().trim()
                    return if (content.startsWith("ref: refs/heads/"))
                            content.removePrefix("ref: refs/heads/")
                    else content.take(7)
                }
            }
            dir = dir.parentFile ?: break
        }
        null
    } catch (e: Exception) {
        null
    }
}
