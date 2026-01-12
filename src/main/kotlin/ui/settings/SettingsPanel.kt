package ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val PanelBackground = Color(0xFF2D2D2D)
val TextPrimary = Color(0xFFE0E0E0)
val TextSecondary = Color(0xFF999999)
val AccentColor = Color(0xFF0078D4)
val SuccessColor = Color(0xFF4CAF50)
val WarningColor = Color(0xFFFF9800)
val ErrorColor = Color(0xFFE53935)

@Composable
fun SettingsPanel(
        settings: TerminalSettings,
        onSettingsChange: (TerminalSettings) -> Unit,
        onClose: () -> Unit,
        onShellInstall: ((String) -> Unit)? = null,
        modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    var shellsInfo by remember { mutableStateOf(ShellManager.getAvailableShells()) }
    var installingShell by remember { mutableStateOf<String?>(null) }
    var installOutput by remember { mutableStateOf("") }

    Surface(
            modifier = modifier.width(300.dp).fillMaxHeight(),
            shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp),
            color = PanelBackground,
            shadowElevation = 16.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                        "SETTINGS",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                )
                IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                    Icon(
                            Icons.Filled.Close,
                            "Close",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF404040))

            Column(modifier = Modifier.weight(1f).verticalScroll(scrollState).padding(16.dp)) {
                // Theme
                SettingSection("APPEARANCE") {
                    SettingLabel("Theme")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OptionChip("Dark", settings.theme == Theme.DARK) {
                            onSettingsChange(settings.copy(theme = Theme.DARK))
                        }
                        OptionChip("Light", settings.theme == Theme.LIGHT) {
                            onSettingsChange(settings.copy(theme = Theme.LIGHT))
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Opacity Mode
                    SettingLabel("Window Style")
                    var opacityExpanded by remember { mutableStateOf(false) }
                    Box {
                        DropdownButton(
                                text =
                                        settings.opacityMode.name.lowercase().replaceFirstChar {
                                            it.uppercase()
                                        },
                                onClick = { opacityExpanded = true }
                        )
                        DropdownMenu(
                                expanded = opacityExpanded,
                                onDismissRequest = { opacityExpanded = false }
                        ) {
                            OpacityMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                        text = {
                                            Text(
                                                    mode.name.lowercase().replaceFirstChar {
                                                        it.uppercase()
                                                    }
                                            )
                                        },
                                        onClick = {
                                            onSettingsChange(settings.copy(opacityMode = mode))
                                            opacityExpanded = false
                                        }
                                )
                            }
                        }
                    }

                    // Transparency Slider (only for transparent/glass)
                    if (settings.opacityMode != OpacityMode.SOLID) {
                        Spacer(Modifier.height(12.dp))
                        SettingLabel("Transparency")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Slider(
                                    value = settings.opacity,
                                    onValueChange = {
                                        onSettingsChange(settings.copy(opacity = it))
                                    },
                                    valueRange = 0.5f..1f,
                                    modifier = Modifier.weight(1f),
                                    colors =
                                            SliderDefaults.colors(
                                                    thumbColor = AccentColor,
                                                    activeTrackColor = AccentColor
                                            )
                            )
                            // Display as transparency: 100% opacity = 0% transparency, 50% opacity = 50% transparency
                            Text(
                                    "${((1f - settings.opacity) * 100).toInt()}%",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.width(40.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Font Settings
                SettingSection("FONT") {
                    // Font Source Tabs
                    SettingLabel("Source")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OptionChip("System", settings.fontSource == FontSource.SYSTEM) {
                            onSettingsChange(
                                    settings.copy(
                                            fontSource = FontSource.SYSTEM,
                                            fontFamily = "Monospace"
                                    )
                            )
                        }
                        OptionChip("Bundled", settings.fontSource == FontSource.BUNDLED) {
                            val bundledFonts = FontManager.getBundledFonts()
                            val defaultFont = bundledFonts.firstOrNull()?.name ?: "Monospace"
                            onSettingsChange(
                                    settings.copy(
                                            fontSource = FontSource.BUNDLED,
                                            fontFamily = defaultFont
                                    )
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Font Family Dropdown
                    SettingLabel("Font Family")
                    var fontExpanded by remember { mutableStateOf(false) }
                    val availableFonts =
                            remember(settings.fontSource) {
                                when (settings.fontSource) {
                                    FontSource.SYSTEM -> FontManager.getSystemFonts()
                                    FontSource.BUNDLED -> FontManager.getBundledFonts()
                                }
                            }

                    if (settings.fontSource == FontSource.BUNDLED && availableFonts.isEmpty()) {
                        Text(
                                "No fonts found. Add .ttf/.otf files to:\n${FontManager.getFontsDirectory()}",
                                color = WarningColor,
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                        )
                    } else {
                        Box {
                            DropdownButton(
                                    text = settings.fontFamily,
                                    onClick = { fontExpanded = true }
                            )
                            DropdownMenu(
                                    expanded = fontExpanded,
                                    onDismissRequest = { fontExpanded = false }
                            ) {
                                availableFonts.forEach { fontInfo ->
                                    DropdownMenuItem(
                                            text = { Text(fontInfo.name) },
                                            onClick = {
                                                onSettingsChange(
                                                        settings.copy(fontFamily = fontInfo.name)
                                                )
                                                fontExpanded = false
                                            }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    SettingLabel("Font Size")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                                value = settings.fontSize.toFloat(),
                                onValueChange = {
                                    onSettingsChange(settings.copy(fontSize = it.toInt()))
                                },
                                valueRange = 10f..24f,
                                steps = 13,
                                modifier = Modifier.weight(1f),
                                colors =
                                        SliderDefaults.colors(
                                                thumbColor = AccentColor,
                                                activeTrackColor = AccentColor
                                        )
                        )
                        Text(
                                "${settings.fontSize}px",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.width(40.dp)
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Cursor
                SettingSection("CURSOR") {
                    SettingLabel("Style")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CursorStyle.entries.forEach { style ->
                            OptionChip(
                                    style.name.lowercase().replaceFirstChar { it.uppercase() },
                                    settings.cursorStyle == style
                            ) { onSettingsChange(settings.copy(cursorStyle = style)) }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Shell Settings
                SettingSection("SHELL") {
                    Text(
                            "Detected OS: ${ShellManager.getOsDisplayName()}",
                            color = TextSecondary,
                            fontSize = 11.sp
                    )
                    Spacer(Modifier.height(12.dp))

                    shellsInfo.forEach { shellInfo ->
                        ShellOption(
                                shellInfo = shellInfo,
                                isSelected = settings.shell.contains(shellInfo.name),
                                isInstalling = installingShell == shellInfo.name,
                                onSelect = {
                                    if (shellInfo.isInstalled) {
                                        val shellPath =
                                                if (ShellManager.isWindows) shellInfo.name
                                                else "/bin/${shellInfo.name}"
                                        onSettingsChange(settings.copy(shell = shellPath))
                                    }
                                },
                                onInstall = {
                                    installingShell = shellInfo.name
                                    installOutput = "Installing ${shellInfo.name}...\n"
                                    installOutput += "Command: ${shellInfo.installCommand}\n\n"
                                    installOutput += "⚠️ This requires sudo password.\n"
                                    installOutput +=
                                            "Please run in terminal:\n${shellInfo.installCommand}\n"

                                    // Notify parent to run install in terminal
                                    onShellInstall?.invoke(shellInfo.installCommand)

                                    // Refresh shell list after a delay
                                    scope.launch {
                                        delay(2000)
                                        shellsInfo = ShellManager.getAvailableShells()
                                        installingShell = null
                                    }
                                }
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    if (installOutput.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                                installOutput,
                                color = WarningColor,
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Status Bar
                SettingSection("STATUS BAR") {
                    Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                                if (settings.showStatusBar) "Visible" else "Hidden",
                                color = TextSecondary,
                                fontSize = 13.sp
                        )
                        Switch(
                                checked = settings.showStatusBar,
                                onCheckedChange = {
                                    onSettingsChange(settings.copy(showStatusBar = it))
                                },
                                colors = SwitchDefaults.colors(checkedTrackColor = AccentColor)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
                title,
                color = AccentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
        )
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun SettingLabel(text: String) {
    Text(text, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun OptionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
            modifier =
                    Modifier.background(
                                    if (selected) AccentColor else Color(0xFF3C3C3C),
                                    RoundedCornerShape(6.dp)
                            )
                            .clickable(onClick = onClick)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) { Text(label, color = if (selected) Color.White else TextSecondary, fontSize = 12.sp) }
}

@Composable
private fun DropdownButton(text: String, onClick: () -> Unit) {
    Box(
            modifier =
                    Modifier.fillMaxWidth()
                            .background(Color(0xFF3C3C3C), RoundedCornerShape(6.dp))
                            .border(1.dp, Color(0xFF505050), RoundedCornerShape(6.dp))
                            .clickable(onClick = onClick)
                            .padding(12.dp)
    ) {
        Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text, color = TextPrimary, fontSize = 13.sp)
            Text("▼", color = TextSecondary, fontSize = 10.sp)
        }
    }
}

@Composable
private fun ShellOption(
        shellInfo: ShellInfo,
        isSelected: Boolean,
        isInstalling: Boolean,
        onSelect: () -> Unit,
        onInstall: () -> Unit
) {
    Row(
            modifier =
                    Modifier.fillMaxWidth()
                            .background(
                                    if (isSelected) AccentColor.copy(alpha = 0.2f)
                                    else Color(0xFF3C3C3C),
                                    RoundedCornerShape(8.dp)
                            )
                            .border(
                                    1.dp,
                                    if (isSelected) AccentColor else Color(0xFF505050),
                                    RoundedCornerShape(8.dp)
                            )
                            .clickable(
                                    enabled = shellInfo.isInstalled && !isInstalling,
                                    onClick = onSelect
                            )
                            .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                    shellInfo.name.uppercase(),
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.width(8.dp))
            if (shellInfo.isInstalled) {
                Icon(
                        Icons.Filled.Check,
                        "Installed",
                        tint = SuccessColor,
                        modifier = Modifier.size(16.dp)
                )
            } else {
                Icon(
                        Icons.Filled.Warning,
                        "Not installed",
                        tint = WarningColor,
                        modifier = Modifier.size(16.dp)
                )
            }
        }

        if (!shellInfo.isInstalled) {
            Button(
                    onClick = onInstall,
                    enabled = !isInstalling,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(28.dp)
            ) { Text(if (isInstalling) "..." else "Install", fontSize = 11.sp) }
        } else if (isSelected) {
            Text("Active", color = AccentColor, fontSize = 11.sp)
        }
    }
}
