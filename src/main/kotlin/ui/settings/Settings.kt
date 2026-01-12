package ui.settings

import androidx.compose.ui.graphics.Color

data class TerminalSettings(
        val theme: Theme = Theme.DARK,
        val fontSize: Int = 14,
        val fontFamily: String = "Monospace",
        val fontSource: FontSource = FontSource.SYSTEM,
        val opacity: Float = 1.0f,
        val opacityMode: OpacityMode = OpacityMode.SOLID,
        val cursorStyle: CursorStyle = CursorStyle.BLOCK,
        val cursorColor: Long = 0xFF4CAF50, // Hacker Green
        val shell: String = ShellManager.getDefaultShell(),
        val showStatusBar: Boolean = true
)

enum class Theme {
        DARK,
        LIGHT
}

enum class CursorStyle {
        BLOCK,
        LINE,
        UNDERLINE
}

enum class OpacityMode {
        SOLID,
        TRANSPARENT
}

fun getThemeColors(theme: Theme) =
        when (theme) {
                Theme.DARK ->
                        ThemeColors(
                                background = Color(0xFF1E1E1E),
                                foreground = Color(0xFFD4D4D4),
                                cursor = Color(0xFFAEAFAD),
                                selection = Color(0xFF264F78)
                        )
                Theme.LIGHT ->
                        ThemeColors(
                                background = Color(0xFFF5F5F5),
                                foreground = Color(0xFF1E1E1E),
                                cursor = Color(0xFF000000),
                                selection = Color(0xFFADD6FF)
                        )
        }

data class ThemeColors(
        val background: Color,
        val foreground: Color,
        val cursor: Color,
        val selection: Color
)
