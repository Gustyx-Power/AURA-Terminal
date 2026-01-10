package terminal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString

/**
 * ANSI escape code parser for terminal output. Converts ANSI color codes to Compose text styles.
 */
object AnsiParser {

    // ANSI escape sequence regex - handles multiple formats
    // ESC can be \u001B (0x1B) or \u009B (CSI)
    // Also handles escape sequences with ? and other characters
    private val ANSI_REGEX =
            Regex(
                    "(?:" +
                            "\u001B\\[([0-9;?]*)([A-Za-z@])" + // Standard CSI: ESC [ params command
                            "|\u001B\\]([^\u0007\u001B]*)\u0007" + // OSC: ESC ] ... BEL
                            "|\u001B\\][^\u001B]*\u001B\\\\" + // OSC: ESC ] ... ST
                            "|\u001B[PX^_][^\u001B]*\u001B\\\\" + // DCS, SOS, PM, APC
                            "|\u001B[()][AB012]" + // Character set selection
                            "|\u001B[=>M78NOHDE]" + // Other escape codes
                            ")"
            )

    // Standard ANSI colors (normal)
    private val COLORS =
            mapOf(
                    30 to Color(0xFF000000), // Black
                    31 to Color(0xFFCD0000), // Red
                    32 to Color(0xFF00CD00), // Green
                    33 to Color(0xFFCDCD00), // Yellow
                    34 to Color(0xFF0000EE), // Blue
                    35 to Color(0xFFCD00CD), // Magenta
                    36 to Color(0xFF00CDCD), // Cyan
                    37 to Color(0xFFE5E5E5), // White
                    39 to Color(0xFF00FFFF), // Default (Neon Cyan)
            )

    // Bright ANSI colors
    private val BRIGHT_COLORS =
            mapOf(
                    90 to Color(0xFF7F7F7F), // Bright Black (Gray)
                    91 to Color(0xFFFF0000), // Bright Red
                    92 to Color(0xFF00FF00), // Bright Green
                    93 to Color(0xFFFFFF00), // Bright Yellow
                    94 to Color(0xFF5C5CFF), // Bright Blue
                    95 to Color(0xFFFF00FF), // Bright Magenta
                    96 to Color(0xFF00FFFF), // Bright Cyan
                    97 to Color(0xFFFFFFFF), // Bright White
            )

    // Background colors
    private val BG_COLORS =
            mapOf(
                    40 to Color(0xFF000000), // Black
                    41 to Color(0xFFCD0000), // Red
                    42 to Color(0xFF00CD00), // Green
                    43 to Color(0xFFCDCD00), // Yellow
                    44 to Color(0xFF0000EE), // Blue
                    45 to Color(0xFFCD00CD), // Magenta
                    46 to Color(0xFF00CDCD), // Cyan
                    47 to Color(0xFFE5E5E5), // White
                    49 to Color.Transparent, // Default
            )

    /** Parse ANSI-encoded text and return AnnotatedString with proper styling. */
    fun parse(text: String, defaultColor: Color = Color(0xFF00FFFF)): AnnotatedString {
        // First, clean up any non-printable control characters that aren't part of escape sequences
        val cleanedText =
                text.replace("\u0007", "") // Bell
                        .replace("\u000F", "") // SI
                        .replace("\u000E", "") // SO

        return buildAnnotatedString {
            var currentFgColor = defaultColor
            var currentBgColor = Color.Transparent
            var isBold = false

            var lastEnd = 0

            ANSI_REGEX.findAll(cleanedText).forEach { match ->
                // Append text before this escape sequence
                val textBefore = cleanedText.substring(lastEnd, match.range.first)
                if (textBefore.isNotEmpty()) {
                    val displayColor =
                            if (isBold && currentFgColor in COLORS.values) {
                                // Make bright if bold
                                makeBright(currentFgColor)
                            } else {
                                currentFgColor
                            }
                    pushStyle(
                            SpanStyle(
                                    color = displayColor,
                                    background =
                                            if (currentBgColor != Color.Transparent) currentBgColor
                                            else Color.Unspecified
                            )
                    )
                    append(textBefore)
                    pop()
                }

                // Try to parse SGR command (the first capture group)
                val params = match.groupValues.getOrNull(1) ?: ""
                val command = match.groupValues.getOrNull(2) ?: ""

                // Only handle SGR (Select Graphic Rendition) commands - 'm'
                if (command == "m") {
                    val codes =
                            if (params.isEmpty()) {
                                listOf(0)
                            } else {
                                params.split(";").mapNotNull { it.toIntOrNull() }
                            }

                    var i = 0
                    while (i < codes.size) {
                        when (val code = codes[i]) {
                            0 -> {
                                currentFgColor = defaultColor
                                currentBgColor = Color.Transparent
                                isBold = false
                            }
                            1 -> isBold = true
                            22 -> isBold = false
                            in 30..37, 39 -> currentFgColor = COLORS[code] ?: defaultColor
                            in 40..47, 49 -> currentBgColor = BG_COLORS[code] ?: Color.Transparent
                            in 90..97 -> currentFgColor = BRIGHT_COLORS[code] ?: defaultColor
                            in 100..107 -> {
                                // Bright background colors
                                currentBgColor = BRIGHT_COLORS[code - 60] ?: Color.Transparent
                            }
                            38 -> {
                                // Extended foreground color
                                if (i + 2 < codes.size && codes[i + 1] == 5) {
                                    currentFgColor = get256Color(codes[i + 2])
                                    i += 2
                                } else if (i + 4 < codes.size && codes[i + 1] == 2) {
                                    currentFgColor =
                                            Color(
                                                    red = codes[i + 2] / 255f,
                                                    green = codes[i + 3] / 255f,
                                                    blue = codes[i + 4] / 255f
                                            )
                                    i += 4
                                }
                            }
                            48 -> {
                                // Extended background color
                                if (i + 2 < codes.size && codes[i + 1] == 5) {
                                    currentBgColor = get256Color(codes[i + 2])
                                    i += 2
                                } else if (i + 4 < codes.size && codes[i + 1] == 2) {
                                    currentBgColor =
                                            Color(
                                                    red = codes[i + 2] / 255f,
                                                    green = codes[i + 3] / 255f,
                                                    blue = codes[i + 4] / 255f
                                            )
                                    i += 4
                                }
                            }
                        }
                        i++
                    }
                }

                lastEnd = match.range.last + 1
            }

            // Append remaining text
            val remaining = cleanedText.substring(lastEnd)
            if (remaining.isNotEmpty()) {
                pushStyle(
                        SpanStyle(
                                color = currentFgColor,
                                background =
                                        if (currentBgColor != Color.Transparent) currentBgColor
                                        else Color.Unspecified
                        )
                )
                append(remaining)
                pop()
            }
        }
    }

    /** Make a color brighter (for bold text). */
    private fun makeBright(color: Color): Color {
        return when (color) {
            COLORS[30] -> BRIGHT_COLORS[90] ?: color
            COLORS[31] -> BRIGHT_COLORS[91] ?: color
            COLORS[32] -> BRIGHT_COLORS[92] ?: color
            COLORS[33] -> BRIGHT_COLORS[93] ?: color
            COLORS[34] -> BRIGHT_COLORS[94] ?: color
            COLORS[35] -> BRIGHT_COLORS[95] ?: color
            COLORS[36] -> BRIGHT_COLORS[96] ?: color
            COLORS[37] -> BRIGHT_COLORS[97] ?: color
            else -> color
        }
    }

    /** Get color from 256-color palette. */
    private fun get256Color(index: Int): Color {
        return when {
            index < 16 -> {
                val colors =
                        listOf(
                                Color(0xFF000000),
                                Color(0xFFCD0000),
                                Color(0xFF00CD00),
                                Color(0xFFCDCD00),
                                Color(0xFF0000EE),
                                Color(0xFFCD00CD),
                                Color(0xFF00CDCD),
                                Color(0xFFE5E5E5),
                                Color(0xFF7F7F7F),
                                Color(0xFFFF0000),
                                Color(0xFF00FF00),
                                Color(0xFFFFFF00),
                                Color(0xFF5C5CFF),
                                Color(0xFFFF00FF),
                                Color(0xFF00FFFF),
                                Color(0xFFFFFFFF)
                        )
                colors.getOrElse(index) { Color.White }
            }
            index < 232 -> {
                val n = index - 16
                val r = (n / 36) * 51
                val g = ((n / 6) % 6) * 51
                val b = (n % 6) * 51
                Color(r / 255f, g / 255f, b / 255f)
            }
            else -> {
                val gray = (index - 232) * 10 + 8
                Color(gray / 255f, gray / 255f, gray / 255f)
            }
        }
    }

    /** Strip all ANSI escape codes from text. */
    fun strip(text: String): String {
        return text.replace(ANSI_REGEX, "")
                .replace("\u0007", "")
                .replace("\u000F", "")
                .replace("\u000E", "")
    }
}
