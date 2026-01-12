package ui

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.win32.StdCallLibrary
import java.awt.Window
import javax.swing.JFrame
import javax.swing.SwingUtilities

object WindowsTransparency {

    interface User32 : StdCallLibrary {
        companion object {
            val INSTANCE: User32 = Native.load("user32", User32::class.java)
        }

        fun GetWindowLongA(hWnd: HWND, nIndex: Int): Int
        fun SetWindowLongA(hWnd: HWND, nIndex: Int, dwNewLong: Int): Int
        fun SetLayeredWindowAttributes(hWnd: HWND, crKey: Int, bAlpha: Byte, dwFlags: Int): Boolean
    }

    interface Dwmapi : StdCallLibrary {
        companion object {
            val INSTANCE: Dwmapi = Native.load("dwmapi", Dwmapi::class.java)
        }

        fun DwmExtendFrameIntoClientArea(hWnd: HWND, pMargins: MARGINS): Int
        fun DwmEnableBlurBehindWindow(hWnd: HWND, pBlurBehind: DWM_BLURBEHIND): Int
        fun DwmSetWindowAttribute(hWnd: HWND, dwAttribute: Int, pvAttribute: Pointer, cbAttribute: Int): Int
    }

    @Structure.FieldOrder("left", "right", "top", "bottom")
    class MARGINS : Structure() {
        @JvmField var left: Int = 0
        @JvmField var right: Int = 0
        @JvmField var top: Int = 0
        @JvmField var bottom: Int = 0

        companion object {
            fun createFullWindow(): MARGINS {
                return MARGINS().apply {
                    left = -1
                    right = -1
                    top = -1
                    bottom = -1
                }
            }
        }
    }

    @Structure.FieldOrder("dwFlags", "fEnable", "hRgnBlur", "fTransitionOnMaximized")
    class DWM_BLURBEHIND : Structure() {
        @JvmField var dwFlags: Int = 0
        @JvmField var fEnable: Boolean = false
        @JvmField var hRgnBlur: Pointer? = null
        @JvmField var fTransitionOnMaximized: Boolean = false

        companion object {
            const val DWM_BB_ENABLE = 0x00000001
            const val DWM_BB_BLURREGION = 0x00000002
            const val DWM_BB_TRANSITIONONMAXIMIZED = 0x00000004
        }
    }

    private const val GWL_EXSTYLE = -20
    private const val WS_EX_LAYERED = 0x00080000
    private const val LWA_ALPHA = 0x00000002
    
    // DWM window attributes for Windows 11 Mica/Acrylic
    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
    private const val DWMWA_SYSTEMBACKDROP_TYPE = 38

    private fun getHWND(window: Window): HWND? {
        return try {
            val peer = window.javaClass.getMethod("getPeer").invoke(window)
            if (peer != null) {
                val hwndMethod = peer.javaClass.getMethod("getHWnd")
                val hwndValue = hwndMethod.invoke(peer) as Long
                HWND(Pointer(hwndValue))
            } else {
                // Fallback
                val hwndValue = Native.getComponentID(window)
                if (hwndValue != 0L) HWND(Pointer(hwndValue)) else null
            }
        } catch (e: Exception) {
            try {
                val hwndValue = Native.getComponentID(window)
                if (hwndValue != 0L) HWND(Pointer(hwndValue)) else null
            } catch (e2: Exception) {
                println("[WindowsTransparency] Error getting HWND: ${e2.message}")
                null
            }
        }
    }

    /**
     * Set window opacity using layered window attributes
     */
    fun setOpacity(window: Window, opacity: Float) {
        SwingUtilities.invokeLater {
            try {
                val hwnd = getHWND(window) ?: return@invokeLater

                // Add WS_EX_LAYERED style
                val user32 = User32.INSTANCE
                val currentStyle = user32.GetWindowLongA(hwnd, GWL_EXSTYLE)
                user32.SetWindowLongA(hwnd, GWL_EXSTYLE, currentStyle or WS_EX_LAYERED)

                // Set opacity (0-255)
                val alpha = (opacity.coerceIn(0f, 1f) * 255).toInt().toByte()
                user32.SetLayeredWindowAttributes(hwnd, 0, alpha, LWA_ALPHA)

                println("[WindowsTransparency] Set opacity to $opacity (alpha: $alpha)")
            } catch (e: Exception) {
                println("[WindowsTransparency] Error setting opacity: ${e.message}")
            }
        }
    }

    /**
     * Enable blur behind effect (Aero Glass / Acrylic)
     */
    fun enableBlurBehind(window: Window) {
        SwingUtilities.invokeLater {
            try {
                val hwnd = getHWND(window) ?: return@invokeLater
                val dwm = Dwmapi.INSTANCE

                // Try to extend glass frame
                val margins = MARGINS.createFullWindow()
                dwm.DwmExtendFrameIntoClientArea(hwnd, margins)

                // Enable blur behind
                val blur = DWM_BLURBEHIND().apply {
                    dwFlags = DWM_BLURBEHIND.DWM_BB_ENABLE
                    fEnable = true
                    hRgnBlur = null
                    fTransitionOnMaximized = false
                }
                dwm.DwmEnableBlurBehindWindow(hwnd, blur)

                println("[WindowsTransparency] Enabled blur behind effect")
            } catch (e: Exception) {
                println("[WindowsTransparency] Error enabling blur: ${e.message}")
            }
        }
    }

    /**
     * Apply Windows transparency using per-pixel alpha.
     */
    fun setTransparentBackground(window: Window, opacity: Float) {
        Thread {
            try {
                Thread.sleep(300) // Wait for window to be fully initialized

                SwingUtilities.invokeAndWait {
                    try {
                        window.background = java.awt.Color(0, 0, 0, 0)
                        
                        if (window is JFrame) {
                            window.contentPane.background = java.awt.Color(0, 0, 0, 0)
                        }
                    } catch (e: Exception) {
                        println("[WindowsTransparency] Error setting AWT background: ${e.message}")
                    }
                }

                enableBlurBehind(window)

                println("[WindowsTransparency] Configured per-pixel transparency")
            } catch (e: Exception) {
                println("[WindowsTransparency] Error in setTransparentBackground: ${e.message}")
            }
        }.start()
    }
}
