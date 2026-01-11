package ui.settings

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import java.awt.GraphicsEnvironment
import java.io.File

data class FontInfo(val name: String, val source: FontSource, val fontFamily: FontFamily? = null)

enum class FontSource {
    SYSTEM,
    BUNDLED
}

object FontManager {
    private val bundledFontsDir =
            File(
                    System.getenv("HOME") ?: System.getProperty("user.home"),
                    ".config/aura-terminal/fonts"
            )

    private val cachedFonts = mutableMapOf<String, FontFamily>()

    fun getSystemFonts(): List<FontInfo> {
        val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val fontNames = ge.availableFontFamilyNames

        // Filter for common monospace fonts used in terminals
        val monospaceFonts =
                listOf(
                        "JetBrains Mono",
                        "Fira Code",
                        "Cascadia Code",
                        "SF Mono",
                        "Source Code Pro",
                        "Consolas",
                        "Monaco",
                        "Menlo",
                        "Ubuntu Mono",
                        "DejaVu Sans Mono",
                        "Courier New",
                        "Monospace"
                )

        return fontNames
                .filter { name ->
                    monospaceFonts.any { mono -> name.contains(mono, ignoreCase = true) } ||
                            name.contains("Mono", ignoreCase = true) ||
                            name.contains("Code", ignoreCase = true)
                }
                .map { FontInfo(it, FontSource.SYSTEM) }
    }

    fun getBundledFonts(): List<FontInfo> {
        if (!bundledFontsDir.exists()) {
            bundledFontsDir.mkdirs()
            return emptyList()
        }

        return bundledFontsDir
                .listFiles()
                ?.filter { it.isFile && (it.extension == "ttf" || it.extension == "otf") }
                ?.map { file ->
                    val fontName = file.nameWithoutExtension.replace("-", " ").replace("_", " ")
                    FontInfo(fontName, FontSource.BUNDLED)
                }
                ?: emptyList()
    }

    fun getAllFonts(): List<FontInfo> {
        val systemFonts = getSystemFonts()
        val bundledFonts = getBundledFonts()

        // Bundled fonts first, then system fonts
        return bundledFonts + systemFonts
    }

    fun loadFont(fontName: String, source: FontSource): FontFamily {
        val cacheKey = "$source:$fontName"
        cachedFonts[cacheKey]?.let {
            return it
        }

        val fontFamily =
                when (source) {
                    FontSource.SYSTEM -> loadSystemFont(fontName)
                    FontSource.BUNDLED -> loadBundledFont(fontName)
                }

        cachedFonts[cacheKey] = fontFamily
        return fontFamily
    }

    @OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
    private fun loadSystemFont(fontName: String): FontFamily {
        return try {
            // Try to load using AWT font
            val awtFont = java.awt.Font(fontName, java.awt.Font.PLAIN, 12)
            if (awtFont.family == fontName || awtFont.name.contains(fontName, ignoreCase = true)) {
                FontFamily(fontName)
            } else {
                println("[FontManager] System font '$fontName' not found, using Monospace")
                FontFamily.Monospace
            }
        } catch (e: Exception) {
            println("[FontManager] Error loading system font '$fontName': ${e.message}")
            FontFamily.Monospace
        }
    }

    @OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
    private fun loadBundledFont(fontName: String): FontFamily {
        return try {
            val targetName = fontName.replace(" ", "").replace("_", "").replace("-", "")

            // Debug: List all available files
            val allFiles = bundledFontsDir.listFiles() ?: emptyArray()
            println(
                    "[FontManager] Looking for bundled font '$fontName' (normalized: '$targetName') in ${bundledFontsDir.absolutePath}"
            )
            println("[FontManager] Available files: ${allFiles.joinToString { it.name }}")

            val fontFile =
                    allFiles.find { file ->
                        if (!file.isFile) return@find false
                        val fileName =
                                file.nameWithoutExtension
                                        .replace(" ", "")
                                        .replace("_", "")
                                        .replace("-", "")

                        // Match exact name, or name with/without capitalization/spaces
                        fileName.equals(targetName, ignoreCase = true) ||
                                file.name.equals("$fontName.ttf", ignoreCase = true) ||
                                file.name.equals("$fontName.otf", ignoreCase = true)
                    }

            if (fontFile != null) {
                println("[FontManager] Found bundled font file: ${fontFile.absolutePath}")
                FontFamily(
                        Font(file = fontFile, weight = FontWeight.Normal, style = FontStyle.Normal)
                )
            } else {
                println(
                        "[FontManager] Bundled font '$fontName' not found. Checked ${allFiles.size} files."
                )
                FontFamily.Monospace
            }
        } catch (e: Exception) {
            println("[FontManager] Error loading bundled font '$fontName': ${e.message}")
            FontFamily.Monospace
        }
    }

    fun getFontsDirectory(): String = bundledFontsDir.absolutePath

    fun hasBundledFonts(): Boolean = getBundledFonts().isNotEmpty()
}
