import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import terminal.AutocompleteManager
import terminal.TerminalBuffer
import terminal.TerminalSession
import ui.components.CustomTitleBar
import ui.components.isMac

// Colors
val NeonCyan = Color(0xFF00FFFF)
val GhostGray = Color(0xFF666666)
val CursorColor = Color(0xFFFFFFFF)
val TerminalBackground = Color(0xFF0D0D0D)

// Terminal config
const val TERMINAL_COLUMNS = 80
const val TERMINAL_ROWS = 60
const val CHAR_WIDTH = 9
const val CHAR_HEIGHT = 18
val WINDOW_WIDTH = 749.dp
val WINDOW_HEIGHT = 601.dp

private val OSC_TITLE_REGEX = Regex("\u001B\\]0;[^\u0007]*\u0007")

fun stripOscTitleSequences(text: String) = text.replace(OSC_TITLE_REGEX, "")

fun main() = application {
    val terminalSession = remember { TerminalSession() }
    val autocompleteManager = remember { AutocompleteManager() }
    val windowState =
            rememberWindowState(
                    size = DpSize(WINDOW_WIDTH, WINDOW_HEIGHT),
                    position = WindowPosition.Aligned(androidx.compose.ui.Alignment.Center)
            )

    LaunchedEffect(Unit) { terminalSession.start() }

    Window(
            onCloseRequest = {
                terminalSession.stop()
                exitApplication()
            },
            title = "Aura Terminal",
            state = windowState,
            resizable = true,
            undecorated = true
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            CustomTitleBar(
                    title = "Aura Terminal",
                    windowState = windowState,
                    onClose = {
                        terminalSession.stop()
                        exitApplication()
                    },
                    onMinimize = { windowState.isMinimized = true }
            )
            App(terminalSession, autocompleteManager)
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun App(terminalSession: TerminalSession, autocompleteManager: AutocompleteManager) {
    val terminalBuffer = remember { TerminalBuffer(columns = 120, rows = 40) }
    var bufferVersion by remember { mutableStateOf(0) }
    val scrollState = rememberScrollState()
    var currentInput by remember { mutableStateOf("") }
    var ghostText by remember { mutableStateOf<String?>(null) }
    var historyIndex by remember { mutableStateOf(-1) }
    var savedInput by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    var showCursor by remember { mutableStateOf(true) }

    // Selection state
    var selectionStartRow by remember { mutableStateOf(-1) }
    var selectionStartCol by remember { mutableStateOf(-1) }
    var selectionEndRow by remember { mutableStateOf(-1) }
    var selectionEndCol by remember { mutableStateOf(-1) }
    var isSelecting by remember { mutableStateOf(false) }

    // Context menu state
    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuPosition by remember { mutableStateOf(Offset.Zero) }

    val selectionColor = Color(0xFF3367D6)
    val charWidth = 8.5f
    val charHeight = 18f
    val paddingOffset = 16f

    fun positionToCell(x: Float, y: Float): Pair<Int, Int> {
        val col = ((x - paddingOffset) / charWidth).toInt().coerceIn(0, terminalBuffer.columns - 1)
        val row =
                ((y - paddingOffset) / charHeight + scrollState.value / charHeight)
                        .toInt()
                        .coerceIn(0, terminalBuffer.rows - 1)
        return Pair(row, col)
    }

    fun isInSelection(row: Int, col: Int): Boolean {
        if (selectionStartRow < 0 || selectionEndRow < 0) return false
        val (sRow, sCol, eRow, eCol) =
                if (selectionStartRow < selectionEndRow ||
                                (selectionStartRow == selectionEndRow &&
                                        selectionStartCol <= selectionEndCol)
                )
                        listOf(
                                selectionStartRow,
                                selectionStartCol,
                                selectionEndRow,
                                selectionEndCol
                        )
                else listOf(selectionEndRow, selectionEndCol, selectionStartRow, selectionStartCol)

        return when {
            row < sRow || row > eRow -> false
            row == sRow && row == eRow -> col >= sCol && col <= eCol
            row == sRow -> col >= sCol
            row == eRow -> col <= eCol
            else -> true
        }
    }

    fun getSelectedText(): String {
        if (selectionStartRow < 0 || selectionEndRow < 0) return ""
        return terminalBuffer.getTextRange(
                selectionStartRow,
                selectionStartCol,
                selectionEndRow,
                selectionEndCol
        )
    }

    fun clearSelection() {
        selectionStartRow = -1
        selectionStartCol = -1
        selectionEndRow = -1
        selectionEndCol = -1
        isSelecting = false
    }

    // Cursor blink
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            showCursor = !showCursor
        }
    }

    // Process terminal output
    LaunchedEffect(terminalSession) {
        terminalSession.outputFlow.collectLatest { newOutput ->
            terminalBuffer.processOutput(newOutput)
            bufferVersion++
        }
    }

    // Auto-scroll
    LaunchedEffect(bufferVersion, currentInput) {
        delay(16)
        scrollState.scrollTo(scrollState.maxValue)
    }

    // Ghost text
    LaunchedEffect(currentInput) {
        ghostText =
                if (currentInput.isNotEmpty()) autocompleteManager.getGhostText(currentInput)
                else null
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                    modifier =
                            Modifier.fillMaxSize()
                                    .background(TerminalBackground)
                                    .focusRequester(focusRequester)
                                    .focusable()
                                    .pointerInput(Unit) {
                                        awaitPointerEventScope {
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val position =
                                                        event.changes.firstOrNull()?.position
                                                                ?: Offset.Zero

                                                when (event.type) {
                                                    PointerEventType.Press -> {
                                                        focusRequester.requestFocus()
                                                        if (event.button == PointerButton.Secondary
                                                        ) {
                                                            contextMenuPosition = position
                                                            showContextMenu = true
                                                        } else if (event.button ==
                                                                        PointerButton.Primary
                                                        ) {
                                                            showContextMenu = false
                                                            val (row, col) =
                                                                    positionToCell(
                                                                            position.x,
                                                                            position.y
                                                                    )
                                                            selectionStartRow = row
                                                            selectionStartCol = col
                                                            selectionEndRow = row
                                                            selectionEndCol = col
                                                            isSelecting = true
                                                        }
                                                    }
                                                    PointerEventType.Move -> {
                                                        if (isSelecting &&
                                                                        event.changes.any {
                                                                            it.pressed
                                                                        }
                                                        ) {
                                                            val (row, col) =
                                                                    positionToCell(
                                                                            position.x,
                                                                            position.y
                                                                    )
                                                            selectionEndRow = row
                                                            selectionEndCol = col
                                                        }
                                                    }
                                                    PointerEventType.Release -> {
                                                        if (isSelecting) {
                                                            isSelecting = false
                                                            if (selectionStartRow ==
                                                                            selectionEndRow &&
                                                                            selectionStartCol ==
                                                                                    selectionEndCol
                                                            )
                                                                    clearSelection()
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    .onKeyEvent { event ->
                                        if (event.type == KeyEventType.KeyDown) {
                                            if (!isModifierOnlyKey(event.key) &&
                                                            !(event.key == Key.C &&
                                                                    event.isShiftPressed &&
                                                                    (if (isMac) event.isMetaPressed
                                                                    else event.isCtrlPressed))
                                            ) {
                                                clearSelection()
                                            }
                                            handleKeyEvent(
                                                    event,
                                                    currentInput,
                                                    ghostText,
                                                    historyIndex,
                                                    savedInput,
                                                    autocompleteManager,
                                                    terminalSession,
                                                    terminalBuffer,
                                                    selectionStartRow,
                                                    selectionStartCol,
                                                    selectionEndRow,
                                                    selectionEndCol,
                                                    { currentInput = it },
                                                    { ghostText = it },
                                                    { historyIndex = it },
                                                    { savedInput = it },
                                                    {
                                                        terminalBuffer.clear()
                                                        bufferVersion++
                                                    }
                                            )
                                        } else false
                                    }
            ) {
                Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(scrollState)
                ) {
                    @Suppress("UNUSED_VARIABLE") val unused = bufferVersion
                    val screenContent = terminalBuffer.getScreenContent()
                    val cursorRow = terminalBuffer.cursorRow
                    val cursorCol = terminalBuffer.cursorCol

                    val screenText = buildAnnotatedString {
                        val maxRow = minOf(cursorRow + 1, screenContent.size)
                        for (rowIndex in 0 until maxRow) {
                            val row = screenContent[rowIndex]
                            for ((colIndex, cell) in row.withIndex()) {
                                var char = cell.char
                                var fg = cell.foreground
                                var bg = cell.background

                                val visualCursorCol =
                                        if (rowIndex == cursorRow) cursorCol + currentInput.length
                                        else -1

                                if (rowIndex == cursorRow &&
                                                colIndex >= cursorCol &&
                                                colIndex < cursorCol + currentInput.length
                                ) {
                                    char = currentInput[colIndex - cursorCol]
                                    fg = NeonCyan
                                }

                                val isCursorPos =
                                        rowIndex == cursorRow && colIndex == visualCursorCol
                                val isSelected = isInSelection(rowIndex, colIndex)

                                if (isCursorPos && showCursor) {
                                    withStyle(
                                            SpanStyle(
                                                    color = CursorColor,
                                                    background =
                                                            if (isSelected) selectionColor
                                                            else Color.Unspecified
                                            )
                                    ) { append("\u2588") }
                                } else {
                                    val finalBg =
                                            when {
                                                isSelected -> selectionColor
                                                bg != Color.Transparent -> bg
                                                else -> Color.Unspecified
                                            }
                                    withStyle(SpanStyle(color = fg, background = finalBg)) {
                                        append(char)
                                    }
                                }
                            }
                            if (rowIndex < maxRow - 1) append("\n")
                        }
                    }

                    BasicText(
                            text = screenText,
                            style =
                                    TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 14.sp,
                                            lineHeight = 18.sp
                                    )
                    )
                }
            }

            // Context menu
            DropdownMenu(
                    expanded = showContextMenu,
                    onDismissRequest = { showContextMenu = false },
                    offset =
                            DpOffset((contextMenuPosition.x / 2).dp, (contextMenuPosition.y / 2).dp)
            ) {
                DropdownMenuItem(
                        text = { Text("Copy") },
                        onClick = {
                            val text = getSelectedText().ifEmpty { terminalBuffer.getAllText() }
                            if (text.isNotEmpty()) setClipboardText(text)
                            showContextMenu = false
                        }
                )
                DropdownMenuItem(
                        text = { Text("Paste") },
                        onClick = {
                            getClipboardText()?.let { currentInput += it }
                            showContextMenu = false
                        }
                )
                DropdownMenuItem(
                        text = { Text("Clear") },
                        onClick = {
                            terminalBuffer.clear()
                            bufferVersion++
                            showContextMenu = false
                        }
                )
            }
        }
    }
}

private fun handleKeyEvent(
        event: KeyEvent,
        currentInput: String,
        ghostText: String?,
        historyIndex: Int,
        savedInput: String,
        autocompleteManager: AutocompleteManager,
        terminalSession: TerminalSession,
        terminalBuffer: TerminalBuffer,
        selectionStartRow: Int,
        selectionStartCol: Int,
        selectionEndRow: Int,
        selectionEndCol: Int,
        onInputChange: (String) -> Unit,
        onGhostTextChange: (String?) -> Unit,
        onHistoryIndexChange: (Int) -> Unit,
        onSavedInputChange: (String) -> Unit,
        onClearOutput: () -> Unit
): Boolean {
    if (isModifierOnlyKey(event.key)) return false

    return when {
        event.key == Key.Enter -> {
            if (currentInput.isNotEmpty()) {
                autocompleteManager.addToHistory(currentInput)
                terminalSession.sendCommand(currentInput)
                onInputChange("")
                onGhostTextChange(null)
                onHistoryIndexChange(-1)
            } else terminalSession.sendInput("\n")
            true
        }
        event.key == Key.Tab || (event.key == Key.DirectionRight && ghostText != null) -> {
            ghostText?.let {
                onInputChange(currentInput + it)
                onGhostTextChange(null)
            }
            true
        }
        event.key == Key.Backspace -> {
            if (currentInput.isNotEmpty()) onInputChange(currentInput.dropLast(1))
            true
        }
        event.key == Key.DirectionUp -> {
            val size = autocompleteManager.historySize()
            if (size > 0) {
                if (historyIndex == -1) onSavedInputChange(currentInput)
                if (historyIndex < size - 1) {
                    val idx = historyIndex + 1
                    onHistoryIndexChange(idx)
                    autocompleteManager.getHistoryAt(idx)?.let { onInputChange(it) }
                }
            }
            true
        }
        event.key == Key.DirectionDown -> {
            if (historyIndex > 0) {
                val idx = historyIndex - 1
                onHistoryIndexChange(idx)
                autocompleteManager.getHistoryAt(idx)?.let { onInputChange(it) }
            } else if (historyIndex == 0) {
                onHistoryIndexChange(-1)
                onInputChange(savedInput)
            }
            true
        }
        event.key == Key.C && event.isCtrlPressed && !event.isShiftPressed -> {
            terminalSession.sendInput("\u0003")
            onInputChange("")
            onGhostTextChange(null)
            true
        }
        event.key == Key.L && event.isCtrlPressed -> {
            onClearOutput()
            terminalSession.sendInput("\u000C")
            true
        }
        event.key == Key.D && event.isCtrlPressed -> {
            terminalSession.sendInput("\u0004")
            true
        }
        event.key == Key.V &&
                event.isShiftPressed &&
                (if (isMac) event.isMetaPressed else event.isCtrlPressed) -> {
            getClipboardText()?.let { onInputChange(currentInput + it) }
            true
        }
        event.key == Key.C &&
                event.isShiftPressed &&
                (if (isMac) event.isMetaPressed else event.isCtrlPressed) -> {
            if (selectionStartRow >= 0 && selectionEndRow >= 0) {
                val text =
                        terminalBuffer.getTextRange(
                                selectionStartRow,
                                selectionStartCol,
                                selectionEndRow,
                                selectionEndCol
                        )
                if (text.isNotEmpty()) setClipboardText(text)
            } else if (currentInput.isNotEmpty()) setClipboardText(currentInput)
            true
        }
        else -> {
            val codePoint = event.utf16CodePoint
            if (codePoint > 0 && isPrintableChar(codePoint)) {
                onInputChange(currentInput + codePoint.toChar())
                onHistoryIndexChange(-1)
                true
            } else false
        }
    }
}

private fun isModifierOnlyKey(key: Key) =
        key in
                listOf(
                        Key.ShiftLeft,
                        Key.ShiftRight,
                        Key.CtrlLeft,
                        Key.CtrlRight,
                        Key.AltLeft,
                        Key.AltRight,
                        Key.CapsLock,
                        Key.NumLock,
                        Key.ScrollLock,
                        Key.Function,
                        Key.MetaLeft,
                        Key.MetaRight,
                        Key.Escape,
                        Key.Insert,
                        Key.Delete,
                        Key.MoveHome,
                        Key.MoveEnd,
                        Key.PageUp,
                        Key.PageDown,
                        Key.F1,
                        Key.F2,
                        Key.F3,
                        Key.F4,
                        Key.F5,
                        Key.F6,
                        Key.F7,
                        Key.F8,
                        Key.F9,
                        Key.F10,
                        Key.F11,
                        Key.F12
                )

private fun isPrintableChar(codePoint: Int): Boolean {
    val type = Character.getType(codePoint)
    return type != Character.CONTROL.toInt() &&
            type != Character.UNASSIGNED.toInt() &&
            type != Character.PRIVATE_USE.toInt() &&
            type != Character.SURROGATE.toInt() &&
            type != Character.FORMAT.toInt()
}

private fun getClipboardText(): String? =
        try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            val contents = clipboard.getContents(null)
            if (contents?.isDataFlavorSupported(DataFlavor.stringFlavor) == true)
                    contents.getTransferData(DataFlavor.stringFlavor) as String
            else null
        } catch (e: Exception) {
            null
        }

private fun setClipboardText(text: String) {
    try {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    } catch (e: Exception) {}
}
