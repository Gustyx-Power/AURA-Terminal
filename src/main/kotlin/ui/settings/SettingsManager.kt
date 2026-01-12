package ui.settings

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SettingsData(
        val theme: String = "DARK",
        val fontSize: Int = 14,
        val fontFamily: String = "Monospace",
        val fontSource: String = "SYSTEM",
        val opacity: Float = 1.0f,
        val opacityMode: String = "SOLID",
        val cursorStyle: String = "BLOCK",
        val cursorColor: Long = 0xFF4CAF50,
        val shell: String = "",
        val showStatusBar: Boolean = true
)

object SettingsManager {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    private val configDir =
            File(System.getenv("HOME") ?: System.getProperty("user.home"), ".config/aura-terminal")
    private val configFile = File(configDir, "settings.json")

    fun load(): TerminalSettings {
        return try {
            if (configFile.exists()) {
                val data = json.decodeFromString<SettingsData>(configFile.readText())
                TerminalSettings(
                        theme = Theme.valueOf(data.theme),
                        fontSize = data.fontSize,
                        fontFamily = data.fontFamily,
                        fontSource =
                                try {
                                    FontSource.valueOf(data.fontSource)
                                } catch (e: Exception) {
                                    FontSource.SYSTEM
                                },
                        opacity = data.opacity,

                        opacityMode = OpacityMode.valueOf(data.opacityMode),
                        cursorStyle = CursorStyle.valueOf(data.cursorStyle),
                        cursorColor = data.cursorColor,
                        shell = data.shell.ifEmpty { ShellManager.getDefaultShell() },
                        showStatusBar = data.showStatusBar
                )
            } else {
                TerminalSettings()
            }
        } catch (e: Exception) {
            println("[SettingsManager] Error loading settings: ${e.message}")
            TerminalSettings()
        }
    }

    fun save(settings: TerminalSettings) {
        try {
            configDir.mkdirs()
            val data =
                    SettingsData(
                            theme = settings.theme.name,
                            fontSize = settings.fontSize,
                            fontFamily = settings.fontFamily,
                            fontSource = settings.fontSource.name,
                            opacity = settings.opacity,
                            opacityMode = settings.opacityMode.name,
                            cursorStyle = settings.cursorStyle.name,
                            cursorColor = settings.cursorColor,
                            shell = settings.shell,
                            showStatusBar = settings.showStatusBar
                    )
            configFile.writeText(json.encodeToString(data))
            println("[SettingsManager] Settings saved to ${configFile.absolutePath}")
        } catch (e: Exception) {
            println("[SettingsManager] Error saving settings: ${e.message}")
        }
    }

    fun getConfigPath(): String = configFile.absolutePath
}
