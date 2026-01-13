import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.style.TextOverflow
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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import terminal.AnsiParser
import terminal.AutocompleteManager
import terminal.TerminalSession
import ui.components.StatusBar
import ui.settings.FontManager
import ui.settings.OpacityMode
import ui.settings.SettingsManager
import ui.settings.SettingsPanel
import ui.settings.ShellManager
import ui.settings.TerminalSettings
import ui.settings.Theme
import ui.settings.getThemeColors

val WINDOW_WIDTH = 800.dp
val WINDOW_HEIGHT = 600.dp



@OptIn(ExperimentalComposeUiApi::class)
fun main() = application {
    var settings by remember { mutableStateOf(SettingsManager.load()) }
    var showSettings by remember { mutableStateOf(false) }
    var pendingShellCommand by remember { mutableStateOf(null as String?) }

    val terminalSession =
            remember(settings.shell) { TerminalSession(shell = settings.shell).also { it.start() } }
    val autocompleteManager = remember { AutocompleteManager() }

    val windowState =
            rememberWindowState(
                    size = DpSize(WINDOW_WIDTH, WINDOW_HEIGHT),
                    position = WindowPosition.Aligned(Alignment.Center)
            )

    LaunchedEffect(pendingShellCommand) {
        pendingShellCommand?.let { cmd ->
            terminalSession.sendCommand(cmd)
            pendingShellCommand = null
        }
    }

    val osName = System.getProperty("os.name").lowercase()
    val isLinux = osName.contains("linux")
    val isMac = osName.contains("mac") || osName.contains("darwin")
    val isWindows = osName.contains("windows")

    val useUndecorated = isLinux || isMac || isWindows
    val useTransparent = isLinux || isWindows

    Window(
            onCloseRequest = {
                terminalSession.stop()
                exitApplication()
            },
            title = "AURA Terminal",
            state = windowState,
            resizable = true,
            undecorated = useUndecorated,
            transparent = useTransparent
    ) {
        LaunchedEffect(Unit) {
            if (isLinux) {
                delay(100)
                ui.LinuxTransparency.forceNativeBorders(window)
            } else if (isMac) {
                delay(500)
                ui.MacTransparency.setTransparentBackground(window, settings.opacity)
            } else if (isWindows) {
                delay(300)
                ui.WindowsTransparency.setTransparentBackground(window, settings.opacity)
            }
        }

        val themeColors = getThemeColors(settings.theme)
        val colorScheme =
                if (settings.theme == Theme.DARK) darkColorScheme() else lightColorScheme()

        val backgroundAlpha =
                if (settings.opacityMode == OpacityMode.SOLID) 1f else settings.opacity
        val windowBackground = themeColors.background.copy(alpha = backgroundAlpha)

        val macCornerRadius = if (isMac) 10.dp else 0.dp
        
        MaterialTheme(colorScheme = colorScheme) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (isMac) Modifier.clip(RoundedCornerShape(macCornerRadius))
                        else Modifier
                    )
                    .background(
                        color = windowBackground,
                        shape = if (isMac) RoundedCornerShape(macCornerRadius) else RoundedCornerShape(0.dp)
                    )
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (isMac) {
                        ui.components.MacTitleBar(
                            window = window,
                            title = "AURA Terminal",
                            onClose = {
                                terminalSession.stop()
                                exitApplication()
                            }
                        )
                    }
                    
                    if (isWindows) {
                        ui.components.WindowsTitleBar(
                            window = window,
                            title = "AURA Terminal",
                            showSettings = !settings.showStatusBar,
                            onSettingsClick = { showSettings = !showSettings },
                            onClose = {
                                terminalSession.stop()
                                exitApplication()
                            }
                        )
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.weight(1f)) {
                                App(terminalSession, autocompleteManager, settings) {
                                    terminalSession.stop()
                                    exitApplication()
                                }
                            }

                            if (settings.showStatusBar) {
                                StatusBar(onSettingsClick = { showSettings = !showSettings })
                            }
                        }

                        androidx.compose.animation.AnimatedVisibility(
                                visible = showSettings,
                                enter = slideInHorizontally(initialOffsetX = { it }),
                                exit = slideOutHorizontally(targetOffsetX = { it }),
                                modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            SettingsPanel(
                                    settings = settings,
                                    onSettingsChange = { newSettings ->
                                        if (newSettings.shell != settings.shell) {
                                            terminalSession.stop()
                                        }
                                        settings = newSettings
                                        SettingsManager.save(newSettings)
                                    },
                                    onClose = { showSettings = false },
                                    onShellInstall = { cmd -> pendingShellCommand = cmd }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun App(
        terminalSession: TerminalSession,
        autocompleteManager: AutocompleteManager,
        settings: TerminalSettings,
        onExit: () -> Unit
) {
    val viewModel = remember { com.aura.terminal.viewmodel.TerminalViewModel() }
    val listState = rememberLazyListState()

    var currentInput by remember { mutableStateOf("") }
    var ghostText by remember { mutableStateOf<String?>(null) }
    var historyIndex by remember { mutableStateOf(-1) }
    var savedInput by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    
    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuPosition by remember { mutableStateOf(Offset.Zero) }

    val showCursor = remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            showCursor.value = !showCursor.value
        }
    }

    val themeColors = getThemeColors(settings.theme)

    val fontSize = settings.fontSize
    
    val processedLinesChannel = remember { kotlinx.coroutines.channels.Channel<List<String>>(kotlinx.coroutines.channels.Channel.UNLIMITED) }
    
    LaunchedEffect(terminalSession) {
        withContext(Dispatchers.IO) {
            terminalSession.outputFlow.collect { newOutput ->
                val sanitized = newOutput.replace("\r", "")
                val parts = sanitized.split('\n')
                processedLinesChannel.send(parts)
            }
        }
    }
    
    LaunchedEffect(Unit) {
        while (true) {
            val updates = mutableListOf<List<String>>()
            var result = processedLinesChannel.tryReceive()
            while (result.isSuccess) {
                updates.add(result.getOrThrow())
                result = processedLinesChannel.tryReceive()
            }

            if (updates.isNotEmpty()) {
                val batch = mutableListOf<String>()
                
                updates.forEach { parts ->
                    parts.forEach { part ->
                         if (part.contains("\u000C") || part.contains("\u001B[2J") || part.contains("\u001Bc")) {
                             if (batch.isNotEmpty()) {
                                 viewModel.pushLines(batch)
                                 batch.clear()
                             }
                             viewModel.clear()
                             batch.add(part)
                         } else {
                             batch.add(part)
                         }
                    }
                }
                
                if (batch.isNotEmpty()) {
                    viewModel.pushLines(batch)
                }
            }

            delay(16)
        }
    }
    
    LaunchedEffect(viewModel.lineCount) {
        if (viewModel.lineCount > 0) {
            listState.scrollToItem(viewModel.lineCount - 1)
        }
    }



    LaunchedEffect(currentInput) {
        if (currentInput.isNotEmpty()) {
            delay(150)
            ghostText = autocompleteManager.getGhostText(currentInput)
        } else {
            ghostText = null
        }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
                modifier =
                        Modifier.fillMaxSize()
                                .background(Color.Transparent)
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
                                                    if (event.button == PointerButton.Secondary) {
                                                        contextMenuPosition = position
                                                        showContextMenu = true
                                                    } else if (event.button == PointerButton.Primary) {
                                                        showContextMenu = false
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                .onKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown) {
                                        handleKeyEvent(
                                                event,
                                                currentInput,
                                                ghostText,
                                                historyIndex,
                                                savedInput,
                                                autocompleteManager,
                                                terminalSession,
                                                { currentInput = it },
                                                { ghostText = it },
                                                { historyIndex = it },
                                                { savedInput = it },
                                                { viewModel.clear() },
                                                onExit
                                        )
                                    } else false
                                }
        ) {
            val fontFamily = FontManager.loadFont(settings.fontFamily, settings.fontSource)
            
            androidx.compose.foundation.text.selection.SelectionContainer {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                ) {
                    items(count = viewModel.lineCount) { index ->
                        val line = viewModel.getLine(index)
                        val isLastLine = index == viewModel.lineCount - 1
                    
                        Box(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                            if (isLastLine) {
                                val styledLine = remember(line, themeColors.foreground) {
                                    AnsiParser.parse(line, themeColors.foreground)
                                }
        
                                val finalStyledText = remember(styledLine, currentInput, ghostText, showCursor.value, settings.cursorStyle, settings.cursorColor) {
                                    buildAnnotatedString {
                                        append(styledLine)
                                        
                                        val plainLine = AnsiParser.strip(line)
                                        var remainingInput = currentInput
                                        
                                        for (i in currentInput.length downTo 1) {
                                            val sub = currentInput.substring(0, i)
                                            if (plainLine.endsWith(sub)) {
                                                remainingInput = currentInput.substring(i)
                                                break
                                            }
                                        }
                                        
                                        if (remainingInput.isNotEmpty()) {
                                            withStyle(SpanStyle(color = themeColors.foreground)) {
                                                append(remainingInput)
                                            }
                                        }
                                        
                                        if (ghostText != null && currentInput.isNotEmpty()) {
                                            withStyle(SpanStyle(color = themeColors.foreground.copy(alpha = 0.5f))) {
                                                append(ghostText)
                                            }
                                        }
    
                                        if (showCursor.value) {
                                            val cursorChar = when (settings.cursorStyle) {
                                                ui.settings.CursorStyle.BLOCK -> "\u2588"
                                                ui.settings.CursorStyle.LINE -> "|"
                                                ui.settings.CursorStyle.UNDERLINE -> "_"
                                            }
                                            
                                            val customColor = Color(settings.cursorColor)
                                            
                                            withStyle(SpanStyle(color = customColor)) {
                                                append(cursorChar)
                                            }
                                        }
                                    }
                                }
        
                                Text(
                                    text = finalStyledText,
                                    fontFamily = fontFamily,
                                    fontSize = fontSize.sp,
                                    color = themeColors.foreground,
                                    lineHeight = (fontSize + 4).sp,
                                    softWrap = false,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip
                                )
                            } else {
                                // Baris history normal
                                val styledText = remember(line, themeColors.foreground) {
                                    AnsiParser.parse(line, themeColors.foreground)
                                }
        
                                Text(
                                    text = styledText,
                                    fontFamily = fontFamily,
                                    fontSize = fontSize.sp,
                                    color = themeColors.foreground,
                                    lineHeight = (fontSize + 4).sp,
                                    softWrap = false,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip
                                )
                            }
                        }
                    }
                }
            }
    }

        DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = { showContextMenu = false },
                offset = DpOffset((contextMenuPosition.x / 2).dp, (contextMenuPosition.y / 2).dp)
        ) {
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
                        viewModel.clear()
                        showContextMenu = false
                    }
            )
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
        onInputChange: (String) -> Unit,
        onGhostTextChange: (String?) -> Unit,
        onHistoryIndexChange: (Int) -> Unit,
        onSavedInputChange: (String) -> Unit,
        onClearOutput: () -> Unit,
        onExit: () -> Unit
): Boolean {
    if (isModifierOnlyKey(event.key)) return false
    return when {
        event.key == Key.Enter -> {
            if (currentInput.isNotEmpty()) {
                val trimmedInput = currentInput.trim()
                if (trimmedInput == "exit" || trimmedInput == "logout") {
                    onExit()
                    return true
                }
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
                    onHistoryIndexChange(historyIndex + 1)
                    autocompleteManager.getHistoryAt(historyIndex + 1)?.let { onInputChange(it) }
                }
            }
            true
        }
        event.key == Key.DirectionDown -> {
            if (historyIndex > 0) {
                onHistoryIndexChange(historyIndex - 1)
                autocompleteManager.getHistoryAt(historyIndex - 1)?.let { onInputChange(it) }
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
                (if (ShellManager.isMac) event.isMetaPressed else event.isCtrlPressed) -> {
            getClipboardText()?.let { onInputChange(currentInput + it) }
            true
        }
        event.key == Key.C &&
                event.isShiftPressed &&
                (if (ShellManager.isMac) event.isMetaPressed else event.isCtrlPressed) -> {
             if (currentInput.isNotEmpty()) setClipboardText(currentInput)
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

