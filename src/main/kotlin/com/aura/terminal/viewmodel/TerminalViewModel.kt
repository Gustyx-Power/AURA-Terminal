package com.aura.terminal.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.aura.terminal.engine.RustBridge

class TerminalViewModel {
    var lineCount by mutableStateOf(0)
        private set

    // Added to force recomposition even if line count doesn't change
    var updateTrigger by mutableStateOf(0L)
        private set

    fun pushData(data: ByteArray) {
        RustBridge.pushData(data)
        lineCount = RustBridge.getSize()
        updateTrigger++
    }

    fun getLine(index: Int): String {
        return RustBridge.getLine(index)
    }

    fun clear() {
        RustBridge.clear()
        lineCount = 0
        updateTrigger++
    }
}
