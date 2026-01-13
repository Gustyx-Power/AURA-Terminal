package com.aura.terminal.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.aura.terminal.engine.RustBridge

class TerminalViewModel {
    var lineCount by mutableStateOf(0)
        private set

    fun pushLine(line: String) {
        RustBridge.pushLine(line)
        lineCount = RustBridge.getSize()
    }

    fun pushLines(lines: List<String>) {
        lines.forEach { RustBridge.pushLine(it) }
        lineCount = RustBridge.getSize()
    }

    fun getLine(index: Int): String {
        return RustBridge.getLine(index)
    }

    fun clear() {
        RustBridge.clear()
        lineCount = 0
    }
}
