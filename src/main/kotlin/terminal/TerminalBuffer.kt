package terminal

import androidx.compose.ui.graphics.Color

data class TerminalCell(
        var char: Char = ' ',
        var foreground: Color = Color(0xFF00FFFF),
        var background: Color = Color.Transparent,
        var bold: Boolean = false
)

/** Terminal screen buffer with ANSI escape sequence support */
class TerminalBuffer(var columns: Int = 120, var rows: Int = 40) {
    private var buffer: Array<Array<TerminalCell>> = createBuffer()
    private val scrollback = mutableListOf<Array<TerminalCell>>()
    private val maxScrollback = 10000

    var cursorRow: Int = 0
        private set
    var cursorCol: Int = 0
        private set

    var currentForeground: Color = Color(0xFF00FFFF)
    var currentBackground: Color = Color.Transparent
    var currentBold: Boolean = false
    val defaultForeground = Color(0xFF00FFFF)

    private val ansiColors =
            mapOf(
                    30 to Color(0xFF000000),
                    31 to Color(0xFFCD0000),
                    32 to Color(0xFF00CD00),
                    33 to Color(0xFFCDCD00),
                    34 to Color(0xFF0000EE),
                    35 to Color(0xFFCD00CD),
                    36 to Color(0xFF00CDCD),
                    37 to Color(0xFFE5E5E5),
                    39 to Color(0xFF00FFFF),
                    90 to Color(0xFF7F7F7F),
                    91 to Color(0xFFFF0000),
                    92 to Color(0xFF00FF00),
                    93 to Color(0xFFFFFF00),
                    94 to Color(0xFF5C5CFF),
                    95 to Color(0xFFFF00FF),
                    96 to Color(0xFF00FFFF),
                    97 to Color(0xFFFFFFFF)
            )

    private val bgColors =
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

    private fun createBuffer() = Array(rows) { Array(columns) { TerminalCell() } }

    fun processOutput(text: String) {
        var i = 0
        while (i < text.length) {
            when {
                text[i] == '\u001B' && i + 1 < text.length -> {
                    when (text[i + 1]) {
                        '[' -> i = parseCSI(text, i + 2)
                        ']' -> i = skipOSC(text, i + 2)
                        '(', ')' -> i += 3
                        else -> i += 2
                    }
                }
                text[i] == '\r' -> {
                    cursorCol = 0
                    i++
                }
                text[i] == '\n' -> {
                    newLine()
                    i++
                }
                text[i] == '\b' -> {
                    if (cursorCol > 0) cursorCol--
                    i++
                }
                text[i] == '\t' -> {
                    cursorCol = minOf(((cursorCol / 8) + 1) * 8, columns - 1)
                    i++
                }
                text[i] == '\u0007' -> i++
                else -> {
                    if (!text[i].isISOControl()) putChar(text[i])
                    i++
                }
            }
        }
    }

    private fun putChar(char: Char) {
        if (cursorCol >= columns) {
            cursorCol = 0
            newLine()
        }
        buffer[cursorRow][cursorCol] =
                TerminalCell(char, currentForeground, currentBackground, currentBold)
        cursorCol++
    }

    private fun newLine() {
        cursorRow++
        if (cursorRow >= rows) {
            scrollUp()
            cursorRow = rows - 1
        }
    }

    private fun scrollUp() {
        if (scrollback.size >= maxScrollback) scrollback.removeAt(0)
        scrollback.add(buffer[0].copyOf())
        for (r in 0 until rows - 1) buffer[r] = buffer[r + 1]
        buffer[rows - 1] = Array(columns) { TerminalCell() }
    }

    private fun parseCSI(text: String, start: Int): Int {
        var i = start
        val params = StringBuilder()
        while (i < text.length && (text[i].isDigit() || text[i] == ';' || text[i] == '?')) {
            params.append(text[i])
            i++
        }
        if (i >= text.length) return i

        val command = text[i]
        val paramList =
                if (params.isEmpty()) emptyList()
                else params.toString().split(";").mapNotNull { it.toIntOrNull() }

        when (command) {
            'A' -> cursorRow = maxOf(0, cursorRow - paramList.getOrElse(0) { 1 })
            'B' -> cursorRow = minOf(rows - 1, cursorRow + paramList.getOrElse(0) { 1 })
            'C' -> cursorCol = minOf(columns - 1, cursorCol + paramList.getOrElse(0) { 1 })
            'D' -> cursorCol = maxOf(0, cursorCol - paramList.getOrElse(0) { 1 })
            'E' -> {
                cursorRow = minOf(rows - 1, cursorRow + paramList.getOrElse(0) { 1 })
                cursorCol = 0
            }
            'F' -> {
                cursorRow = maxOf(0, cursorRow - paramList.getOrElse(0) { 1 })
                cursorCol = 0
            }
            'G' -> cursorCol = minOf(columns - 1, maxOf(0, paramList.getOrElse(0) { 1 } - 1))
            'H', 'f' -> {
                cursorRow = minOf(rows - 1, maxOf(0, paramList.getOrElse(0) { 1 } - 1))
                cursorCol = minOf(columns - 1, maxOf(0, paramList.getOrElse(1) { 1 } - 1))
            }
            'J' -> eraseInDisplay(paramList.getOrElse(0) { 0 })
            'K' -> eraseInLine(paramList.getOrElse(0) { 0 })
            'm' -> processSGR(paramList)
            's' -> {
                savedCursorRow = cursorRow
                savedCursorCol = cursorCol
            }
            'u' -> {
                cursorRow = savedCursorRow
                cursorCol = savedCursorCol
            }
            'd' -> cursorRow = minOf(rows - 1, maxOf(0, paramList.getOrElse(0) { 1 } - 1))
        }
        return i + 1
    }

    private var savedCursorRow = 0
    private var savedCursorCol = 0

    private fun eraseInDisplay(mode: Int) {
        when (mode) {
            0 -> {
                eraseInLine(0)
                for (r in cursorRow + 1 until rows) for (c in 0 until columns) buffer[r][c] =
                        TerminalCell()
            }
            1 -> {
                for (r in 0 until cursorRow) for (c in 0 until columns) buffer[r][c] =
                        TerminalCell()
                eraseInLine(1)
            }
            2, 3 -> {
                for (r in 0 until rows) for (c in 0 until columns) buffer[r][c] = TerminalCell()
            }
        }
    }

    private fun eraseInLine(mode: Int) {
        when (mode) {
            0 -> for (c in cursorCol until columns) buffer[cursorRow][c] = TerminalCell()
            1 -> for (c in 0..cursorCol) buffer[cursorRow][c] = TerminalCell()
            2 -> for (c in 0 until columns) buffer[cursorRow][c] = TerminalCell()
        }
    }

    private fun processSGR(params: List<Int>) {
        if (params.isEmpty()) {
            resetAttributes()
            return
        }
        var i = 0
        while (i < params.size) {
            when (val code = params[i]) {
                0 -> resetAttributes()
                1 -> currentBold = true
                22 -> currentBold = false
                in 30..37, 39 -> currentForeground = ansiColors[code] ?: defaultForeground
                in 40..47, 49 -> currentBackground = bgColors[code] ?: Color.Transparent
                in 90..97 -> currentForeground = ansiColors[code] ?: defaultForeground
                38 -> {
                    if (i + 2 < params.size && params[i + 1] == 5) {
                        currentForeground = get256Color(params[i + 2])
                        i += 2
                    } else if (i + 4 < params.size && params[i + 1] == 2) {
                        currentForeground =
                                Color(
                                        params[i + 2] / 255f,
                                        params[i + 3] / 255f,
                                        params[i + 4] / 255f
                                )
                        i += 4
                    }
                }
                48 -> {
                    if (i + 2 < params.size && params[i + 1] == 5) {
                        currentBackground = get256Color(params[i + 2])
                        i += 2
                    } else if (i + 4 < params.size && params[i + 1] == 2) {
                        currentBackground =
                                Color(
                                        params[i + 2] / 255f,
                                        params[i + 3] / 255f,
                                        params[i + 4] / 255f
                                )
                        i += 4
                    }
                }
            }
            i++
        }
    }

    private fun resetAttributes() {
        currentForeground = defaultForeground
        currentBackground = Color.Transparent
        currentBold = false
    }

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

    private fun skipOSC(text: String, start: Int): Int {
        var i = start
        while (i < text.length) {
            if (text[i] == '\u0007') return i + 1
            if (text[i] == '\u001B' && i + 1 < text.length && text[i + 1] == '\\') return i + 2
            i++
        }
        return i
    }

    fun getScreenContent(): List<List<TerminalCell>> = buffer.map { it.toList() }
    fun getScrollbackContent(): List<List<TerminalCell>> = scrollback.map { it.toList() }

    fun resize(newColumns: Int, newRows: Int) {
        val newBuffer =
                Array(newRows) { row ->
                    Array(newColumns) { col ->
                        if (row < rows && col < columns) buffer[row][col] else TerminalCell()
                    }
                }
        columns = newColumns
        rows = newRows
        buffer = newBuffer
        cursorRow = minOf(cursorRow, rows - 1)
        cursorCol = minOf(cursorCol, columns - 1)
    }

    fun clear() {
        buffer = createBuffer()
        cursorRow = 0
        cursorCol = 0
    }

    fun getTextRange(startRow: Int, startCol: Int, endRow: Int, endCol: Int): String {
        val sb = StringBuilder()
        val (sRow, sCol, eRow, eCol) =
                if (startRow < endRow || (startRow == endRow && startCol <= endCol))
                        listOf(startRow, startCol, endRow, endCol)
                else listOf(endRow, endCol, startRow, startCol)

        for (row in sRow..eRow) {
            if (row < 0 || row >= rows) continue
            val colStart = if (row == sRow) maxOf(0, sCol) else 0
            val colEnd = if (row == eRow) minOf(columns - 1, eCol) else columns - 1
            for (col in colStart..colEnd) if (col >= 0 && col < columns)
                    sb.append(buffer[row][col].char)
            if (row < eRow) sb.append('\n')
        }
        return sb.toString().trimEnd()
    }

    fun getAllText(): String {
        val sb = StringBuilder()
        for (row in 0 until rows) {
            for (col in 0 until columns) sb.append(buffer[row][col].char)
            if (row < rows - 1) sb.append('\n')
        }
        return sb.toString().trimEnd()
    }
}
