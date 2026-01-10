package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import java.awt.MouseInfo
import java.awt.Point

// Title bar colors
val TitleBarBackground = Color(0xFF1A1A1A)
val CloseButtonColor = Color(0xFFFF5F56)
val MinimizeButtonColor = Color(0xFFFFBD2E)
val MaximizeButtonColor = Color(0xFF27C93F)
val TitleTextColor = Color(0xFFCCCCCC)

/** Custom title bar with window controls and drag-to-move. */
@Composable
fun CustomTitleBar(
        title: String,
        windowState: WindowState,
        onClose: () -> Unit,
        onMinimize: () -> Unit,
        modifier: Modifier = Modifier
) {
    var isMaximized by remember { mutableStateOf(false) }
    var dragStartPosition by remember { mutableStateOf(Point(0, 0)) }
    var windowStartX by remember { mutableStateOf(0) }
    var windowStartY by remember { mutableStateOf(0) }

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
                                            val deltaX = currentPos.x - dragStartPosition.x
                                            val deltaY = currentPos.y - dragStartPosition.y

                                            windowState.position =
                                                    WindowPosition.Absolute(
                                                            (windowStartX + deltaX).dp,
                                                            (windowStartY + deltaY).dp
                                                    )
                                        }
                                )
                            }
                            .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Window controls (macOS style - left side)
        Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            // Close button
            WindowControlButton(color = CloseButtonColor, onClick = onClose)

            // Minimize button
            WindowControlButton(color = MinimizeButtonColor, onClick = onMinimize)

            // Maximize button
            WindowControlButton(
                    color = MaximizeButtonColor,
                    onClick = {
                        isMaximized = !isMaximized
                        windowState.placement =
                                if (isMaximized) {
                                    WindowPlacement.Maximized
                                } else {
                                    WindowPlacement.Floating
                                }
                    }
            )
        }

        // Title
        Text(text = title, color = TitleTextColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)

        // Spacer for symmetry
        Spacer(modifier = Modifier.width(76.dp))
    }
}

/** Circular window control button (macOS style). */
@Composable
private fun WindowControlButton(color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
            modifier =
                    modifier.size(12.dp).clip(CircleShape).background(color).pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.any { it.pressed }) {
                                    onClick()
                                }
                            }
                        }
                    }
    )
}
