package ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import java.awt.MouseInfo
import java.awt.Point

val isMac: Boolean = System.getProperty("os.name").lowercase().contains("mac")

val TitleBarBackground = Color(0xFF1A1A1A)
val TitleTextColor = Color(0xFFCCCCCC)
val CloseButtonColor = Color(0xFFFF5F56)
val MinimizeButtonColor = Color(0xFFFFBD2E)
val MaximizeButtonColor = Color(0xFF27C93F)
val WindowsButtonHoverColor = Color(0xFF666666)
val WindowsCloseHoverColor = Color(0xFFE81123)
val WindowsIconColor = Color(0xFFFFFFFF)

@Composable
fun CustomTitleBar(
        title: String,
        windowState: WindowState,
        onClose: () -> Unit,
        onMinimize: () -> Unit,
        modifier: Modifier = Modifier
) {
    var isMaximized by remember {
        mutableStateOf(windowState.placement == WindowPlacement.Maximized)
    }
    var dragStartPosition by remember { mutableStateOf(Point(0, 0)) }
    var windowStartX by remember { mutableStateOf(0) }
    var windowStartY by remember { mutableStateOf(0) }

    LaunchedEffect(windowState.placement) {
        isMaximized = windowState.placement == WindowPlacement.Maximized
    }

    val onMaximizeToggle: () -> Unit = {
        isMaximized = !isMaximized
        windowState.placement =
                if (isMaximized) WindowPlacement.Maximized else WindowPlacement.Floating
    }

    Row(
            modifier =
                    modifier.fillMaxWidth()
                            .height(36.dp)
                            .background(TitleBarBackground)
                            .pointerInput(Unit) {
                                detectDragGestures(
                                        onDragStart = {
                                            dragStartPosition = MouseInfo.getPointerInfo().location
                                            val pos = windowState.position
                                            if (pos is WindowPosition.Absolute) {
                                                windowStartX = pos.x.value.toInt()
                                                windowStartY = pos.y.value.toInt()
                                            }
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            val currentPos = MouseInfo.getPointerInfo().location
                                            windowState.position =
                                                    WindowPosition.Absolute(
                                                            (windowStartX + currentPos.x -
                                                                            dragStartPosition.x)
                                                                    .dp,
                                                            (windowStartY + currentPos.y -
                                                                            dragStartPosition.y)
                                                                    .dp
                                                    )
                                        }
                                )
                            }
                            .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (isMac) {
            MacOSWindowControls(onClose, onMinimize, onMaximizeToggle)
            Text(title, color = TitleTextColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.width(76.dp))
        } else {
            Text(title, color = TitleTextColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.weight(1f))
            WindowsLinuxWindowControls(isMaximized, onClose, onMinimize, onMaximizeToggle)
        }
    }
}

@Composable
private fun MacOSWindowControls(
        onClose: () -> Unit,
        onMinimize: () -> Unit,
        onMaximize: () -> Unit
) {
    Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        TrafficLightButton(CloseButtonColor, onClose)
        TrafficLightButton(MinimizeButtonColor, onMinimize)
        TrafficLightButton(MaximizeButtonColor, onMaximize)
    }
}

@Composable
private fun TrafficLightButton(color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    Box(
            modifier =
                    modifier.size(12.dp)
                            .clip(CircleShape)
                            .background(if (isHovered) color.copy(alpha = 0.8f) else color)
                            .hoverable(interactionSource)
                            .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    onClick = onClick
                            )
    )
}

@Composable
private fun WindowsLinuxWindowControls(
        isMaximized: Boolean,
        onClose: () -> Unit,
        onMinimize: () -> Unit,
        onMaximize: () -> Unit
) {
    Row(
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        WindowsControlButton(Icons.Filled.Minimize, "Minimize", onMinimize)
        WindowsControlButton(
                if (isMaximized) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                if (isMaximized) "Restore" else "Maximize",
                onMaximize
        )
        WindowsControlButton(Icons.Filled.Close, "Close", onClose, true)
    }
}

@Composable
private fun WindowsControlButton(
        icon: ImageVector,
        contentDescription: String,
        onClick: () -> Unit,
        isCloseButton: Boolean = false,
        modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val backgroundColor =
            when {
                isCloseButton && isHovered -> WindowsCloseHoverColor
                isHovered -> WindowsButtonHoverColor
                else -> Color.Transparent
            }
    Box(
            modifier =
                    modifier.size(width = 46.dp, height = 36.dp)
                            .background(backgroundColor)
                            .hoverable(interactionSource)
                            .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    onClick = onClick
                            ),
            contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription, tint = WindowsIconColor, modifier = Modifier.size(16.dp)) }
}
