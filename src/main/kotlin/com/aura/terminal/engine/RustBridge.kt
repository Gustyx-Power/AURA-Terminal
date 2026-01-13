package com.aura.terminal.engine

object RustBridge {
    init {
        try {
            System.loadLibrary("aura_core")
        } catch (e: UnsatisfiedLinkError) {
             System.err.println("Failed to load aura_core: ${e.message}")
        }
    }

    external fun pushLine(line: String)
    external fun getLine(index: Int): String
    external fun getSize(): Int
    external fun clear()
}
