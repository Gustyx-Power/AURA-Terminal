import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.flow.collectLatest
import terminal.TerminalSession

// Neon Cyan color for terminal text
val NeonCyan = Color(0xFF00FFFF)
val NeonGreen = Color(0xFF39FF14)

fun main() = application {
    // Initialize terminal session
    val terminalSession = remember { TerminalSession() }

    LaunchedEffect(Unit) { terminalSession.start() }

    Window(
            onCloseRequest = {
                terminalSession.stop()
                exitApplication()
            },
            title = "Aura Terminal",
            state = rememberWindowState(width = 900.dp, height = 600.dp),
            transparent = true,
            undecorated = true
    ) { App(terminalSession) }
}

@Composable
fun App(terminalSession: TerminalSession) {
    // State for terminal output
    var terminalOutput by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    // Collect output from TerminalSession
    LaunchedEffect(terminalSession) {
        terminalSession.outputFlow.collectLatest { newOutput -> terminalOutput += newOutput }
    }

    // Auto-scroll to bottom when new output arrives
    LaunchedEffect(terminalOutput) { scrollState.animateScrollTo(scrollState.maxValue) }

    MaterialTheme(colorScheme = darkColorScheme()) {
        // Semi-transparent black background with alpha 0.7
        Box(
                modifier =
                        Modifier.fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.7f))
        ) {
            // Scrollable terminal output
            Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(scrollState)) {
                BasicText(
                        text = terminalOutput.ifEmpty { "Starting terminal..." },
                        style =
                                TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 14.sp,
                                        color = NeonCyan,
                                        lineHeight = 20.sp
                                )
                )
            }
        }
    }
}
