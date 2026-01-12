import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
}

group = "com.aura.terminal"
version = "1.0.0"

dependencies {
    // Compose Desktop
    implementation(compose.desktop.currentOs)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(compose.runtime)
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    
    // Serialization for config
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    
    // Terminal PTY
    implementation("org.jetbrains.pty4j:pty4j:0.12.13")

    // JNA for Native Access (X11 Transparency)
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")
}

compose.desktop {
    application {
        mainClass = "MainKt"
        
        nativeDistributions {
            packageName = "AURA-Terminal"
            packageVersion = "1.0.0"
            
            description = "A modern, transparent terminal emulator for Windows"
            vendor = "Xtra Manager Software"
            copyright = "Copyright © 2026 Xtra Manager Software"
            
            // Fix for "NoClassDefFoundError: java/lang/management/ManagementFactory" and "com/sun/management/OperatingSystemMXBean"
            modules("java.instrument", "java.management", "java.naming", "java.sql", "jdk.management")

            linux {
                targetFormats(TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.AppImage)
                iconFile.set(project.file("icons/icon.png"))
            }

            macOS {
                targetFormats(TargetFormat.Dmg)
                iconFile.set(project.file("icons/icon.png"))
            }
            
            windows {
                targetFormats(TargetFormat.Exe, TargetFormat.Msi)
                // Use .ico for Windows for better compatibility
                iconFile.set(project.file("icons/icon.ico"))
                
                // Fixed UUID ensures that different versions are recognized as updates/upgrades
                // rather than separate applications.
                upgradeUuid = "a63e0466-cc80-4c8d-bfd3-0e9569a1d9cb"
                
                // Allow creation of shortcuts
                shortcut = true
                menu = true
                
                // Allow user to choose install directory
                dirChooser = true
            }
        }
    }
}

kotlin {
    jvmToolchain(17)
}
