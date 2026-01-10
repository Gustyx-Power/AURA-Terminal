import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.flow.collectLatest
import terminal.AnsiParser
import terminal.AutocompleteManager
import terminal.TerminalSession

// Terminal colors
val NeonCyan = Color(0xFF00FFFF)
val GhostGray = Color(0xFF666666)
val CursorColor = Color(0xFFFFFFFF)
val TerminalBackground = Color(0xFF0D0D0D)

// Terminal dimensions (80 columns x 60 rows)
// Character width ~9px, character height ~18px
const val TERMINAL_COLUMNS = 80
const val TERMINAL_ROWS = 60
const val CHAR_WIDTH = 9
const val CHAR_HEIGHT = 18
val WINDOW_WIDTH = (TERMINAL_COLUMNS * CHAR_WIDTH + 24).dp // +24 for padding
val WINDOW_HEIGHT = (TERMINAL_ROWS * CHAR_HEIGHT + 60).dp // +60 for title bar + padding

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
            resizable = true
    ) { App(terminalSession = terminalSession, autocompleteManager = autocompleteManager) }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun App(terminalSession: TerminalSession, autocompleteManager: AutocompleteManager) {
    // Terminal output state
    var terminalOutput by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    // Current input buffer
    var currentInput by remember { mutableStateOf("") }

    // Ghost text suggestion
    var ghostText by remember { mutableStateOf<String?>(null) }

    // History navigation
    var historyIndex by remember { mutableStateOf(-1) }
    var savedInput by remember { mutableStateOf("") }

    // Focus management
    val focusRequester = remember { FocusRequester() }

    // Collect output from TerminalSession
    LaunchedEffect(terminalSession) {
        terminalSession.outputFlow.collectLatest { newOutput -> terminalOutput += newOutput }
    }

    // Auto-scroll to bottom
    LaunchedEffect(terminalOutput, currentInput) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    // Update ghost text when input changes
    LaunchedEffect(currentInput) {
        ghostText =
                if (currentInput.isNotEmpty()) {
                    autocompleteManager.getGhostText(currentInput)
                } else {
                    null
                }
    }

    // Request focus on start
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    MaterialTheme(colorScheme = darkColorScheme()) {
        // Terminal content area with dark background
        Box(
                modifier =
                        Modifier.fillMaxSize()
                                .background(TerminalBackground)
                                .focusRequester(focusRequester)
                                .focusable()
                                .onKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown) {
                                        handleKeyEvent(
                                                event = event,
                                                currentInput = currentInput,
                                                ghostText = ghostText,
                                                historyIndex = historyIndex,
                                                savedInput = savedInput,
                                                autocompleteManager = autocompleteManager,
                                                terminalSession = terminalSession,
                                                onInputChange = { currentInput = it },
                                                onGhostTextChange = { ghostText = it },
                                                onHistoryIndexChange = { historyIndex = it },
                                                onSavedInputChange = { savedInput = it },
                                                onClearOutput = { terminalOutput = "" }
                                        )
                                    } else {
                                        false
                                    }
                                }
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(12.dp).verticalScroll(scrollState)) {
                // Terminal output with ANSI color parsing
                BasicText(
                        text = AnsiParser.parse(terminalOutput, NeonCyan),
                        style =
                                TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp
                                )
                )

                // Current input line with ghost text
                BasicText(
                        text =
                                buildAnnotatedString {
                                    withStyle(SpanStyle(color = NeonCyan)) { append(currentInput) }
                                    withStyle(
                                            SpanStyle(color = CursorColor, background = CursorColor)
                                    ) { append(" ") }
                                    ghostText?.let { ghost ->
                                        withStyle(SpanStyle(color = GhostGray)) { append(ghost) }
                                    }
                                },
                        style =
                                TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp
                                )
                )
            }
        }
    }
}

/** Handles keyboard events for terminal input. */
private fun handleKeyEvent(
        event: KeyEvent,
        currentInput: String,
        ghostText: String?,
        historyIndex: Int,
        savedInput: String,
        autocompleteManager: AutocompleteManager,
        terminalSession: TerminalSession,
        onInputChange: (String) -> Unit,
        onGhostTextChange: (String?) -> Unit,
        onHistoryIndexChange: (Int) -> Unit,
        onSavedInputChange: (String) -> Unit,
        onClearOutput: () -> Unit
): Boolean {
    return when {
        // Enter - send command
        event.key == Key.Enter -> {
            if (currentInput.isNotEmpty()) {
                autocompleteManager.addToHistory(currentInput)
                terminalSession.sendCommand(currentInput)
                onInputChange("")
                onGhostTextChange(null)
                onHistoryIndexChange(-1)
            } else {
                terminalSession.sendInput("\n")
            }
            true
        }

        // Tab or ArrowRight - accept autocomplete
        event.key == Key.Tab || (event.key == Key.DirectionRight && ghostText != null) -> {
            ghostText?.let { ghost ->
                onInputChange(currentInput + ghost)
                onGhostTextChange(null)
            }
            true
        }

        // Backspace - delete character
        event.key == Key.Backspace -> {
            if (currentInput.isNotEmpty()) {
                onInputChange(currentInput.dropLast(1))
            }
            true
        }

        // Arrow Up - history navigation
        event.key == Key.DirectionUp -> {
            val historySize = autocompleteManager.historySize()
            if (historySize > 0) {
                if (historyIndex == -1) {
                    onSavedInputChange(currentInput)
                }
                if (historyIndex < historySize - 1) {
                    val newIndex = historyIndex + 1
                    onHistoryIndexChange(newIndex)
                    autocompleteManager.getHistoryAt(newIndex)?.let { onInputChange(it) }
                }
            }
            true
        }

        // Arrow Down - history navigation
        event.key == Key.DirectionDown -> {
            if (historyIndex > 0) {
                val newIndex = historyIndex - 1
                onHistoryIndexChange(newIndex)
                autocompleteManager.getHistoryAt(newIndex)?.let { onInputChange(it) }
            } else if (historyIndex == 0) {
                onHistoryIndexChange(-1)
                onInputChange(savedInput)
            }
            true
        }

        // Ctrl+C - send interrupt
        event.key == Key.C && event.isCtrlPressed -> {
            terminalSession.sendInput("\u0003")
            onInputChange("")
            onGhostTextChange(null)
            true
        }

        // Ctrl+L - clear screen
        event.key == Key.L && event.isCtrlPressed -> {
            onClearOutput()
            terminalSession.sendInput("\u000C")
            true
        }

        // Ctrl+D - EOF
        event.key == Key.D && event.isCtrlPressed -> {
            terminalSession.sendInput("\u0004")
            true
        }

        // Regular character input
        else -> {
            val char = event.utf16CodePoint.toChar()
            if (!char.isISOControl() && char != Char.MIN_VALUE) {
                onInputChange(currentInput + char)
                onHistoryIndexChange(-1)
                true
            } else {
                false
            }
        }
    }
}
