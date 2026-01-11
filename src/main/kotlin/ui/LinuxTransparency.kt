package ui

import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Structure
import com.sun.jna.platform.unix.X11
import java.awt.Window

object LinuxTransparency {
    interface X11Extended : X11 {}

    private val x11 = X11.INSTANCE

    @Structure.FieldOrder("flags", "functions", "decorations", "input_mode", "status")
    class MotifWmHints : Structure() {
        @JvmField var flags: NativeLong = NativeLong(0)
        @JvmField var functions: NativeLong = NativeLong(0)
        @JvmField var decorations: NativeLong = NativeLong(0)
        @JvmField var input_mode: NativeLong = NativeLong(0)
        @JvmField var status: NativeLong = NativeLong(0)
    }

    fun setOpacity(window: Window, opacity: Float) {
        try {
            val display = x11.XOpenDisplay(null) ?: return
            val windowId = Native.getComponentID(window)

            if (windowId != 0L) {

                val opacityValue = (opacity.coerceIn(0f, 1f) * 0xFFFFFFFFL).toLong()
                val atomName = "_NET_WM_WINDOW_OPACITY"
                val atom = x11.XInternAtom(display, atomName, false)
                val cardinalAtom = x11.XInternAtom(display, "CARDINAL", false)

                val data = com.sun.jna.Memory(4)
                data.setInt(0, opacityValue.toInt())

                x11.XChangeProperty(
                        display,
                        X11.Window(windowId),
                        atom,
                        cardinalAtom,
                        32,
                        X11.PropModeReplace,
                        data,
                        1
                )
                x11.XFlush(display)
            }
            x11.XCloseDisplay(display)
        } catch (e: Exception) {
            println("[LinuxTransparency] Error setting opacity: ${e.message}")
        }
    }

    fun forceNativeBorders(window: Window) {
        try {
            val display = x11.XOpenDisplay(null) ?: return
            val windowId = Native.getComponentID(window)

            if (windowId != 0L) {
                // _MOTIF_WM_HINTS
                val atomName = "_MOTIF_WM_HINTS"
                val atom = x11.XInternAtom(display, atomName, false)
                val atomType = x11.XInternAtom(display, "_MOTIF_WM_HINTS", false)

                // Configure Hints using Structure
                val hints = MotifWmHints()
                // MWM_HINTS_DECORATIONS = (1L << 1) = 2
                hints.flags = NativeLong(2)
                // MWM_DECOR_ALL = 1
                hints.decorations = NativeLong(1)

                hints.write()

                x11.XChangeProperty(
                        display,
                        X11.Window(windowId),
                        atom,
                        atomType,
                        32, // Format 32 items
                        X11.PropModeReplace,
                        hints.pointer,
                        5 // 5 items
                )
                x11.XFlush(display)
            }
            x11.XCloseDisplay(display)
        } catch (e: Exception) {
            println("[LinuxTransparency] Error forcing borders: ${e.message}")
        }
    }

    fun startSystemMove(window: Window) {
        try {
            val display = x11.XOpenDisplay(null) ?: return
            val windowId = Native.getComponentID(window)

            if (windowId != 0L) {
                val atomName = "_NET_WM_MOVERESIZE"
                val atom = x11.XInternAtom(display, atomName, false)
                val mouseInfo = java.awt.MouseInfo.getPointerInfo().location

                val event = X11.XEvent()
                event.type = X11.ClientMessage
                event.xclient.type = X11.ClientMessage
                event.xclient.serial = NativeLong(0L)
                event.xclient.send_event = 1
                event.xclient.display = display
                event.xclient.window = X11.Window(windowId)
                event.xclient.message_type = atom
                event.xclient.format = 32

                event.xclient.data.l[0] = NativeLong(mouseInfo.x.toLong())
                event.xclient.data.l[1] = NativeLong(mouseInfo.y.toLong())
                event.xclient.data.l[2] = NativeLong(8L)
                event.xclient.data.l[3] = NativeLong(1L)
                event.xclient.data.l[4] = NativeLong(1L)

                val root = x11.XDefaultRootWindow(display)
                x11.XSendEvent(display, root, 0, NativeLong(0x00100000L or 0x00020000L), event)
                x11.XFlush(display)
            }
            x11.XCloseDisplay(display)
        } catch (e: Exception) {
            println("[LinuxTransparency] Error starting system move: ${e.message}")
        }
    }
}
