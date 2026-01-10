import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Aura Terminal",
        state = rememberWindowState(width = 900.dp, height = 600.dp),
        transparent = true,
        undecorated = true  // Required for transparent windows
    ) {
        App()
    }
}

@Composable
fun App() {
    MaterialTheme(
        colorScheme = darkColorScheme()
    ) {
        // Semi-transparent black background with alpha 0.7
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Aura Terminal",
                color = Color.White,
                style = MaterialTheme.typography.headlineLarge
            )
        }
    }
}
