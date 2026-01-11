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
        currentDirectory: String = System.getenv("HOME") ?: "~",
        onSettingsClick: () -> Unit,
        modifier: Modifier = Modifier
) {
    var gitBranch by remember { mutableStateOf<String?>(null) }
    var cpuUsage by remember { mutableStateOf("0%") }
    var memoryUsage by remember { mutableStateOf("0MB") }
    var currentTime by remember { mutableStateOf("") }

    // Update git branch
    LaunchedEffect(currentDirectory) { gitBranch = getGitBranch(currentDirectory) }

    // Update time every second
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
            delay(1000)
        }
    }

    // Update system stats every 2 seconds
    LaunchedEffect(Unit) {
        while (true) {
            // Memory (Linux specific via /proc/meminfo)
            try {
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

                    // Format: "7.5GB/8GB (93%)"
                    memoryUsage = "%.1fGB/%.0fGB (%d%%)".format(usedGb, totalGb, percent)
                } else {
                    // Fallback to JVM memory if parsing fails
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
                            .height(28.dp) // Increased height from 22.dp to allow better centering
                            .background(StatusBarBackground)
                            .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
    ) {
        // Left side: directory and git
        Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            StatusText(shortenPath(currentDirectory))
            gitBranch?.let { StatusText("⎇ $it", StatusBarAccent) }
        }

        // Right side: stats and time
        Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            // Settings Button
            androidx.compose.material3.Icon(
                    imageVector = androidx.compose.material.icons.Icons.Filled.Settings,
                    contentDescription = "Settings",
                    modifier =
                            Modifier.size(20.dp)
                                    .clickable { onSettingsClick() }
                                    .padding(end = 4.dp),
                    tint = StatusBarText
            )
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
