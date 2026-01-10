package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import java.awt.Cursor
import java.awt.MouseInfo
import java.awt.Point

/** Invisible resize handle for window resizing. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ResizeHandle(
        windowState: WindowState,
        position: ResizePosition,
        size: Dp = 8.dp,
        modifier: Modifier = Modifier
) {
        var dragStartPosition by remember { mutableStateOf(Point(0, 0)) }
        var windowStartWidth by remember { mutableStateOf(0f) }
        var windowStartHeight by remember { mutableStateOf(0f) }
        var windowStartX by remember { mutableStateOf(0) }
        var windowStartY by remember { mutableStateOf(0) }

        val cursor =
                when (position) {
                        ResizePosition.TOP_LEFT, ResizePosition.BOTTOM_RIGHT ->
                                Cursor.NW_RESIZE_CURSOR
                        ResizePosition.TOP_RIGHT, ResizePosition.BOTTOM_LEFT ->
                                Cursor.NE_RESIZE_CURSOR
                        ResizePosition.TOP, ResizePosition.BOTTOM -> Cursor.N_RESIZE_CURSOR
                        ResizePosition.LEFT, ResizePosition.RIGHT -> Cursor.E_RESIZE_CURSOR
                }

        Box(
                modifier =
                        modifier.size(size)
                                .background(Color.Transparent)
                                .pointerHoverIcon(PointerIcon(Cursor(cursor)))
                                .pointerInput(Unit) {
                                        detectDragGestures(
                                                onDragStart = {
                                                        dragStartPosition =
                                                                MouseInfo.getPointerInfo().location
                                                        windowStartWidth =
                                                                windowState.size.width.value
                                                        windowStartHeight =
                                                                windowState.size.height.value
                                                        val pos = windowState.position
                                                        if (pos is WindowPosition.Absolute) {
                                                                windowStartX = pos.x.value.toInt()
                                                                windowStartY = pos.y.value.toInt()
                                                        }
                                                },
                                                onDrag = { change, _ ->
                                                        change.consume()
                                                        val currentPos =
                                                                MouseInfo.getPointerInfo().location
                                                        val deltaX =
                                                                currentPos.x - dragStartPosition.x
                                                        val deltaY =
                                                                currentPos.y - dragStartPosition.y

                                                        val minWidth = 400f
                                                        val minHeight = 300f

                                                        when (position) {
                                                                ResizePosition.RIGHT -> {
                                                                        val newWidth =
                                                                                (windowStartWidth +
                                                                                                deltaX)
                                                                                        .coerceAtLeast(
                                                                                                minWidth
                                                                                        )
                                                                        windowState.size =
                                                                                windowState.size
                                                                                        .copy(
                                                                                                width =
                                                                                                        newWidth.dp
                                                                                        )
                                                                }
                                                                ResizePosition.BOTTOM -> {
                                                                        val newHeight =
                                                                                (windowStartHeight +
                                                                                                deltaY)
                                                                                        .coerceAtLeast(
                                                                                                minHeight
                                                                                        )
                                                                        windowState.size =
                                                                                windowState.size
                                                                                        .copy(
                                                                                                height =
                                                                                                        newHeight
                                                                                                                .dp
                                                                                        )
                                                                }
                                                                ResizePosition.BOTTOM_RIGHT -> {
                                                                        val newWidth =
                                                                                (windowStartWidth +
                                                                                                deltaX)
                                                                                        .coerceAtLeast(
                                                                                                minWidth
                                                                                        )
                                                                        val newHeight =
                                                                                (windowStartHeight +
                                                                                                deltaY)
                                                                                        .coerceAtLeast(
                                                                                                minHeight
                                                                                        )
                                                                        windowState.size =
                                                                                windowState.size
                                                                                        .copy(
                                                                                                width =
                                                                                                        newWidth.dp,
                                                                                                height =
                                                                                                        newHeight
                                                                                                                .dp
                                                                                        )
                                                                }
                                                                ResizePosition.LEFT -> {
                                                                        val newWidth =
                                                                                (windowStartWidth -
                                                                                                deltaX)
                                                                                        .coerceAtLeast(
                                                                                                minWidth
                                                                                        )
                                                                        if (newWidth > minWidth) {
                                                                                windowState.size =
                                                                                        windowState
                                                                                                .size
                                                                                                .copy(
                                                                                                        width =
                                                                                                                newWidth.dp
                                                                                                )
                                                                                windowState
                                                                                        .position =
                                                                                        WindowPosition
                                                                                                .Absolute(
                                                                                                        (windowStartX +
                                                                                                                        deltaX)
                                                                                                                .dp,
                                                                                                        windowStartY
                                                                                                                .dp
                                                                                                )
                                                                        }
                                                                }
                                                                ResizePosition.TOP -> {
                                                                        val newHeight =
                                                                                (windowStartHeight -
                                                                                                deltaY)
                                                                                        .coerceAtLeast(
                                                                                                minHeight
                                                                                        )
                                                                        if (newHeight > minHeight) {
                                                                                windowState.size =
                                                                                        windowState
                                                                                                .size
                                                                                                .copy(
                                                                                                        height =
                                                                                                                newHeight
                                                                                                                        .dp
                                                                                                )
                                                                                windowState
                                                                                        .position =
                                                                                        WindowPosition
                                                                                                .Absolute(
                                                                                                        windowStartX
                                                                                                                .dp,
                                                                                                        (windowStartY +
                                                                                                                        deltaY)
                                                                                                                .dp
                                                                                                )
                                                                        }
                                                                }
                                                                ResizePosition.TOP_LEFT -> {
                                                                        val newWidth =
                                                                                (windowStartWidth -
                                                                                                deltaX)
                                                                                        .coerceAtLeast(
                                                                                                minWidth
                                                                                        )
                                                                        val newHeight =
                                                                                (windowStartHeight -
                                                                                                deltaY)
                                                                                        .coerceAtLeast(
                                                                                                minHeight
                                                                                        )
                                                                        if (newWidth > minWidth) {
                                                                                windowState.size =
                                                                                        windowState
                                                                                                .size
                                                                                                .copy(
                                                                                                        width =
                                                                                                                newWidth.dp
                                                                                                )
                                                                                windowState
                                                                                        .position =
                                                                                        WindowPosition
                                                                                                .Absolute(
                                                                                                        (windowStartX +
                                                                                                                        deltaX)
                                                                                                                .dp,
                                                                                                        windowStartY
                                                                                                                .dp
                                                                                                )
                                                                        }
                                                                        if (newHeight > minHeight) {
                                                                                windowState.size =
                                                                                        windowState
                                                                                                .size
                                                                                                .copy(
                                                                                                        height =
                                                                                                                newHeight
                                                                                                                        .dp
                                                                                                )
                                                                                val currPos =
                                                                                        windowState
                                                                                                .position
                                                                                if (currPos is
                                                                                                WindowPosition.Absolute
                                                                                ) {
                                                                                        windowState
                                                                                                .position =
                                                                                                WindowPosition
                                                                                                        .Absolute(
                                                                                                                currPos.x,
                                                                                                                (windowStartY +
                                                                                                                                deltaY)
                                                                                                                        .dp
                                                                                                        )
                                                                                }
                                                                        }
                                                                }
                                                                ResizePosition.TOP_RIGHT -> {
                                                                        val newWidth =
                                                                                (windowStartWidth +
                                                                                                deltaX)
                                                                                        .coerceAtLeast(
                                                                                                minWidth
                                                                                        )
                                                                        val newHeight =
                                                                                (windowStartHeight -
                                                                                                deltaY)
                                                                                        .coerceAtLeast(
                                                                                                minHeight
                                                                                        )
                                                                        windowState.size =
                                                                                windowState.size
                                                                                        .copy(
                                                                                                width =
                                                                                                        newWidth.dp
                                                                                        )
                                                                        if (newHeight > minHeight) {
                                                                                windowState.size =
                                                                                        windowState
                                                                                                .size
                                                                                                .copy(
                                                                                                        height =
                                                                                                                newHeight
                                                                                                                        .dp
                                                                                                )
                                                                                windowState
                                                                                        .position =
                                                                                        WindowPosition
                                                                                                .Absolute(
                                                                                                        windowStartX
                                                                                                                .dp,
                                                                                                        (windowStartY +
                                                                                                                        deltaY)
                                                                                                                .dp
                                                                                                )
                                                                        }
                                                                }
                                                                ResizePosition.BOTTOM_LEFT -> {
                                                                        val newWidth =
                                                                                (windowStartWidth -
                                                                                                deltaX)
                                                                                        .coerceAtLeast(
                                                                                                minWidth
                                                                                        )
                                                                        val newHeight =
                                                                                (windowStartHeight +
                                                                                                deltaY)
                                                                                        .coerceAtLeast(
                                                                                                minHeight
                                                                                        )
                                                                        windowState.size =
                                                                                windowState.size
                                                                                        .copy(
                                                                                                height =
                                                                                                        newHeight
                                                                                                                .dp
                                                                                        )
                                                                        if (newWidth > minWidth) {
                                                                                windowState.size =
                                                                                        windowState
                                                                                                .size
                                                                                                .copy(
                                                                                                        width =
                                                                                                                newWidth.dp
                                                                                                )
                                                                                windowState
                                                                                        .position =
                                                                                        WindowPosition
                                                                                                .Absolute(
                                                                                                        (windowStartX +
                                                                                                                        deltaX)
                                                                                                                .dp,
                                                                                                        windowStartY
                                                                                                                .dp
                                                                                                )
                                                                        }
                                                                }
                                                        }
                                                }
                                        )
                                }
        )
}

enum class ResizePosition {
        TOP,
        BOTTOM,
        LEFT,
        RIGHT,
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
}
