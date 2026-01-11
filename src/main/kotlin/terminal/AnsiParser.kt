package terminal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString

/** ANSI escape code parser - converts ANSI color codes to Compose styles */
object AnsiParser {
    private val ANSI_REGEX =
            Regex(
                    "(?:" +
                            "\u001B\\[([0-9;?]*)([A-Za-z@])" +
                            "|\u001B\\]([^\u0007\u001B]*)\u0007" +
                            "|\u001B\\][^\u001B]*\u001B\\\\" +
                            "|\u001B[PX^_][^\u001B]*\u001B\\\\" +
                            "|\u001B[()][AB012]" +
                            "|\u001B[=>M78NOHDE]" +
                            ")"
            )

    private val COLORS =
            mapOf(
                    30 to Color(0xFF000000),
                    31 to Color(0xFFCD0000),
                    32 to Color(0xFF00CD00),
                    33 to Color(0xFFCDCD00),
                    34 to Color(0xFF0000EE),
                    35 to Color(0xFFCD00CD),
                    36 to Color(0xFF00CDCD),
                    37 to Color(0xFFE5E5E5),
                    39 to Color(0xFF00FFFF)
            )

    private val BRIGHT_COLORS =
            mapOf(
                    90 to Color(0xFF7F7F7F),
                    91 to Color(0xFFFF0000),
                    92 to Color(0xFF00FF00),
                    93 to Color(0xFFFFFF00),
                    94 to Color(0xFF5C5CFF),
                    95 to Color(0xFFFF00FF),
                    96 to Color(0xFF00FFFF),
                    97 to Color(0xFFFFFFFF)
            )

    private val BG_COLORS =
            mapOf(
                    40 to Color(0xFF000000),
                    41 to Color(0xFFCD0000),
                    42 to Color(0xFF00CD00),
                    43 to Color(0xFFCDCD00),
                    44 to Color(0xFF0000EE),
                    45 to Color(0xFFCD00CD),
                    46 to Color(0xFF00CDCD),
                    47 to Color(0xFFE5E5E5),
                    49 to Color.Transparent
            )

    fun parse(text: String, defaultColor: Color = Color(0xFF00FFFF)): AnnotatedString {
        val cleanedText = text.replace("\u0007", "").replace("\u000F", "").replace("\u000E", "")

        return buildAnnotatedString {
            var currentFgColor = defaultColor
            var currentBgColor = Color.Transparent
            var isBold = false
            var lastEnd = 0

            ANSI_REGEX.findAll(cleanedText).forEach { match ->
                val textBefore = cleanedText.substring(lastEnd, match.range.first)
                if (textBefore.isNotEmpty()) {
                    val displayColor =
                            if (isBold && currentFgColor in COLORS.values)
                                    makeBright(currentFgColor)
                            else currentFgColor
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

                val params = match.groupValues.getOrNull(1) ?: ""
                val command = match.groupValues.getOrNull(2) ?: ""

                if (command == "m") {
                    val codes =
                            if (params.isEmpty()) listOf(0)
                            else params.split(";").mapNotNull { it.toIntOrNull() }
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
                            in 100..107 ->
                                    currentBgColor = BRIGHT_COLORS[code - 60] ?: Color.Transparent
                            38 -> {
                                if (i + 2 < codes.size && codes[i + 1] == 5) {
                                    currentFgColor = get256Color(codes[i + 2])
                                    i += 2
                                } else if (i + 4 < codes.size && codes[i + 1] == 2) {
                                    currentFgColor =
                                            Color(
                                                    codes[i + 2] / 255f,
                                                    codes[i + 3] / 255f,
                                                    codes[i + 4] / 255f
                                            )
                                    i += 4
                                }
                            }
                            48 -> {
                                if (i + 2 < codes.size && codes[i + 1] == 5) {
                                    currentBgColor = get256Color(codes[i + 2])
                                    i += 2
                                } else if (i + 4 < codes.size && codes[i + 1] == 2) {
                                    currentBgColor =
                                            Color(
                                                    codes[i + 2] / 255f,
                                                    codes[i + 3] / 255f,
                                                    codes[i + 4] / 255f
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

    private fun makeBright(color: Color) =
            when (color) {
                COLORS[30] -> BRIGHT_COLORS[90]
                COLORS[31] -> BRIGHT_COLORS[91]
                COLORS[32] -> BRIGHT_COLORS[92]
                COLORS[33] -> BRIGHT_COLORS[93]
                COLORS[34] -> BRIGHT_COLORS[94]
                COLORS[35] -> BRIGHT_COLORS[95]
                COLORS[36] -> BRIGHT_COLORS[96]
                COLORS[37] -> BRIGHT_COLORS[97]
                else -> color
            }
                    ?: color

    private fun get256Color(index: Int): Color =
            when {
                index < 16 ->
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
                                .getOrElse(index) { Color.White }
                index < 232 -> {
                    val n = index - 16
                    Color((n / 36) * 51 / 255f, ((n / 6) % 6) * 51 / 255f, (n % 6) * 51 / 255f)
                }
                else -> {
                    val gray = (index - 232) * 10 + 8
                    Color(gray / 255f, gray / 255f, gray / 255f)
                }
            }

    fun strip(text: String) =
            text.replace(ANSI_REGEX, "")
                    .replace("\u0007", "")
                    .replace("\u000F", "")
                    .replace("\u000E", "")
}
