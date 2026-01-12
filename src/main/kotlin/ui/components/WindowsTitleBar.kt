package ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import java.awt.MouseInfo
import java.awt.Window
import java.awt.Frame

/**
 * Custom Windows-style titlebar with minimize, maximize, close buttons on the right
 */
@Composable
fun WindowsTitleBar(
    window: Window,
    title: String = "AURA Terminal",
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragStartX by remember { mutableStateOf(0) }
    var dragStartY by remember { mutableStateOf(0) }
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(Color(0xFF1E1E1E))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        val mousePos = MouseInfo.getPointerInfo().location
                        dragStartX = mousePos.x - window.x
                        dragStartY = mousePos.y - window.y
                    },
                    onDragEnd = {
                        isDragging = false
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val mousePos = MouseInfo.getPointerInfo().location
                        window.setLocation(
                            mousePos.x - dragStartX,
                            mousePos.y - dragStartY
                        )
                    }
                )
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(12.dp))

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = title,
            color = Color(0xFFCCCCCC),
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal
        )
        
        Spacer(modifier = Modifier.weight(1f))

        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            WindowsControlButton(
                symbol = "─",
                hoverColor = Color(0xFF404040),
                onClick = {
                    if (window is Frame) {
                        window.extendedState = Frame.ICONIFIED
                    }
                }
            )

            WindowsControlButton(
                symbol = if (window is Frame && window.extendedState == Frame.MAXIMIZED_BOTH) "❐" else "□",
                hoverColor = Color(0xFF404040),
                onClick = {
                    if (window is Frame) {
                        window.extendedState = if (window.extendedState == Frame.MAXIMIZED_BOTH) {
                            Frame.NORMAL
                        } else {
                            Frame.MAXIMIZED_BOTH
                        }
                    }
                }
            )

            WindowsControlButton(
                symbol = "✕",
                hoverColor = Color(0xFFE81123),
                textHoverColor = Color.White,
                onClick = onClose
            )
        }
    }
}

@Composable
private fun WindowsControlButton(
    symbol: String,
    hoverColor: Color,
    textHoverColor: Color = Color(0xFFCCCCCC),
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    Box(
        modifier = Modifier
            .width(46.dp)
            .fillMaxHeight()
            .background(if (isHovered) hoverColor else Color.Transparent)
            .hoverable(interactionSource)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = symbol,
            color = if (isHovered) textHoverColor else Color(0xFFCCCCCC),
            fontSize = 10.sp
        )
    }
}
