package com.aura.terminal.engine

import java.io.File

object RustBridge {
    init {
        try {
            loadNativeLibrary()
        } catch (e: UnsatisfiedLinkError) {
            System.err.println("Failed to load aura_terminal_core: ${e.message}")
        }
    }

    private fun loadNativeLibrary() {
        val libName = "aura_terminal_core.dll"
        val appDir = getAppDirectory()
        val possiblePaths = listOf(
            File(appDir, libName),
            File(appDir, "resources/$libName"),
            File(appDir, "app/$libName"),
            File(appDir, "app/resources/$libName"),
            File(appDir, "../app/$libName"),
            File(appDir, "../app/resources/$libName")
        )
        
        for (path in possiblePaths) {
            if (path.exists()) {
                System.load(path.absolutePath)
                println("[RustBridge] Loaded native library from: ${path.absolutePath}")
                return
            }
        }
        

        System.loadLibrary("aura_terminal_core")
        println("[RustBridge] Loaded native library from java.library.path")
    }

    private fun getAppDirectory(): File {
        val codeSource = RustBridge::class.java.protectionDomain?.codeSource
        return if (codeSource != null) {
            File(codeSource.location.toURI()).parentFile
        } else {
            File(System.getProperty("user.dir"))
        }
    }

    @JvmStatic
    external fun pushData(data: ByteArray)

    @JvmStatic
    external fun getLine(index: Int): String

    @JvmStatic
    external fun getSize(): Int

    @JvmStatic
    external fun clear()
}
