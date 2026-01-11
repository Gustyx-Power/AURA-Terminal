package ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
 * Custom macOS titlebar with traffic light buttons
 */
@Composable
fun MacTitleBar(
    window: Window,
    title: String = "AURA Terminal",
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragStartX by remember { mutableStateOf(0) }
    var dragStartY by remember { mutableStateOf(0) }
    
    val buttonGroupHovered = remember { MutableInteractionSource() }
    val isButtonGroupHovered by buttonGroupHovered.collectIsHoveredAsState()
    val titleBarShape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(titleBarShape)
            .background(Color(0xFF1E1E1E).copy(alpha = 0.95f))
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
        Spacer(modifier = Modifier.width(8.dp))

        Row(
            modifier = Modifier
                .hoverable(buttonGroupHovered)
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Close button (red)
            TrafficLightButton(
                color = Color(0xFFFF5F57),
                hoverColor = Color(0xFFFF5F57),
                isGroupHovered = isButtonGroupHovered,
                symbol = "×",
                onClick = onClose
            )

            TrafficLightButton(
                color = Color(0xFFFFBD2E),
                hoverColor = Color(0xFFFFBD2E),
                isGroupHovered = isButtonGroupHovered,
                symbol = "−",
                onClick = {
                    if (window is Frame) {
                        window.extendedState = Frame.ICONIFIED
                    }
                }
            )

            TrafficLightButton(
                color = Color(0xFF28C840),
                hoverColor = Color(0xFF28C840),
                isGroupHovered = isButtonGroupHovered,
                symbol = "+",
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
        }
        
        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = title,
            color = Color(0xFFCCCCCC),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        
        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(72.dp))
    }
}

@Composable
private fun TrafficLightButton(
    color: Color,
    hoverColor: Color,
    isGroupHovered: Boolean,
    symbol: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(if (isGroupHovered) color else Color(0xFF555555))
            .hoverable(interactionSource)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isGroupHovered) {
            Text(
                text = symbol,
                color = Color(0xFF4A0000),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
