package ui

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.awt.Color
import java.awt.Window
import javax.swing.JFrame
import javax.swing.SwingUtilities

object MacTransparency {

    interface ObjC : Library {
        companion object {
            val INSTANCE: ObjC by lazy { Native.load("objc", ObjC::class.java) }
        }

        fun objc_getClass(name: String): Pointer?
        fun sel_registerName(name: String): Pointer?
        fun objc_msgSend(receiver: Pointer?, selector: Pointer?): Pointer?
        fun objc_msgSend(receiver: Pointer?, selector: Pointer?, arg1: Double): Pointer?
        fun objc_msgSend(receiver: Pointer?, selector: Pointer?, arg1: Boolean): Pointer?
        fun objc_msgSend(receiver: Pointer?, selector: Pointer?, arg1: Pointer?): Pointer?
        fun objc_msgSend(receiver: Pointer?, selector: Pointer?, arg1: Long): Pointer?
        fun objc_msgSend(receiver: Pointer?, selector: Pointer?, arg1: Int): Pointer?
    }

    private val objc: ObjC by lazy { ObjC.INSTANCE }

    private fun getMainNSWindow(): Pointer? {
        return try {
            val nsAppClass = objc.objc_getClass("NSApplication") ?: return null
            val sharedAppSel = objc.sel_registerName("sharedApplication")
            val nsApp = objc.objc_msgSend(nsAppClass, sharedAppSel) ?: return null

            val mainWindowSel = objc.sel_registerName("mainWindow")
            val mainWindow = objc.objc_msgSend(nsApp, mainWindowSel)
            if (mainWindow != null && Pointer.nativeValue(mainWindow) != 0L) {
                return mainWindow
            }

            val keyWindowSel = objc.sel_registerName("keyWindow")
            val keyWindow = objc.objc_msgSend(nsApp, keyWindowSel)
            if (keyWindow != null && Pointer.nativeValue(keyWindow) != 0L) {
                return keyWindow
            }

            null
        } catch (e: Exception) {
            null
        }
    }

    fun setTransparentBackground(window: Window, opacity: Float) {
        Thread {
            try {
                Thread.sleep(1000)

                SwingUtilities.invokeAndWait {
                    try {
                        window.background = Color(0, 0, 0, 0)

                        if (window is JFrame) {
                            window.contentPane.background = Color(0, 0, 0, 0)
                            window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
                            window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
                        }
                    } catch (_: Exception) {}

                    try {
                        val nsWindow = getMainNSWindow() ?: return@invokeAndWait
                        val setOpaqueSel = objc.sel_registerName("setOpaque:")
                        objc.objc_msgSend(nsWindow, setOpaqueSel, false)

                        val nsColorClass = objc.objc_getClass("NSColor")
                        if (nsColorClass != null) {
                            val clearColorSel = objc.sel_registerName("clearColor")
                            val clearColor = objc.objc_msgSend(nsColorClass, clearColorSel)

                            if (clearColor != null) {
                                val setBgColorSel = objc.sel_registerName("setBackgroundColor:")
                                objc.objc_msgSend(nsWindow, setBgColorSel, clearColor)
                            }
                        }

                        val contentViewSel = objc.sel_registerName("contentView")
                        val contentView = objc.objc_msgSend(nsWindow, contentViewSel)

                        if (contentView != null && Pointer.nativeValue(contentView) != 0L) {
                            val setWantsLayerSel = objc.sel_registerName("setWantsLayer:")
                            objc.objc_msgSend(contentView, setWantsLayerSel, true)

                            val layerSel = objc.sel_registerName("layer")
                            val layer = objc.objc_msgSend(contentView, layerSel)

                            if (layer != null && Pointer.nativeValue(layer) != 0L) {
                                objc.objc_msgSend(layer, setOpaqueSel, false)
                            }

                            makeViewHierarchyTransparent(contentView, 0)
                        }

                        val displaySel = objc.sel_registerName("display")
                        objc.objc_msgSend(nsWindow, displaySel)
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }.start()
    }

    private fun makeViewHierarchyTransparent(view: Pointer, depth: Int) {
        if (depth > 10) return

        val setOpaqueSel = objc.sel_registerName("setOpaque:")

        try {
            val layerSel = objc.sel_registerName("layer")
            val layer = objc.objc_msgSend(view, layerSel)

            if (layer != null && Pointer.nativeValue(layer) != 0L) {
                objc.objc_msgSend(layer, setOpaqueSel, false)
            }

            val subviewsSel = objc.sel_registerName("subviews")
            val subviews = objc.objc_msgSend(view, subviewsSel) ?: return

            val countSel = objc.sel_registerName("count")
            val countPtr = objc.objc_msgSend(subviews, countSel)
            val count = if (countPtr != null) Pointer.nativeValue(countPtr).toInt() else 0

            val objectAtIndexSel = objc.sel_registerName("objectAtIndex:")
            for (i in 0 until count) {
                val subview = objc.objc_msgSend(subviews, objectAtIndexSel, i.toLong())
                if (subview != null && Pointer.nativeValue(subview) != 0L) {
                    makeViewHierarchyTransparent(subview, depth + 1)
                }
            }
        } catch (_: Exception) {}
    }
}
