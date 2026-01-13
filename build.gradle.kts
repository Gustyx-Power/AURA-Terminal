import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.internal.os.OperatingSystem

plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
}

group = "com.aura.terminal"
version = "1.0.0"

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(compose.runtime)
    
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    implementation("org.jetbrains.pty4j:pty4j:0.12.13")

    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")
}

compose.desktop {
    application {
        mainClass = "MainKt"
        
        // Dynamic library path based on OS
        val libraryPath = if (OperatingSystem.current().isMacOsX || OperatingSystem.current().isLinux) 
            "${project.projectDir}/src/main/resources" 
        else 
            "${project.projectDir}/lib/windows"
            
        jvmArgs += listOf("-Djava.library.path=$libraryPath")
        
        nativeDistributions {
            packageName = "AURA-Terminal"
            packageVersion = "1.0.0"
            
            description = "A modern, transparent terminal emulator"
            vendor = "Xtra Manager Software"
            copyright = "Copyright © 2026 Xtra Manager Software"
            
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb, TargetFormat.Rpm)
            modules("java.instrument", "java.management", "java.naming", "java.sql", "jdk.management")

            linux {
                targetFormats(TargetFormat.Deb, TargetFormat.Rpm)                iconFile.set(project.file("src/main/resources/icon.png"))

                packageName = "aura-terminal" 
                packageVersion = "1.0.0"
                appCategory = "System"
                debMaintainer = "gustyx <gustyx@aura.terminal>" 


            }

            macOS {
                iconFile.set(project.file("icons/icon.icns"))
                bundleID = "com.aura.terminal"
                dockName = "Aura Terminal"
                signing {
                    sign.set(false)
                }
            }
            
            windows {
                iconFile.set(project.file("icons/icon.ico"))
                
                upgradeUuid = "a63e0466-cc80-4c8d-bfd3-0e9569a1d9cb"
                
                shortcut = true
                menu = true
                dirChooser = true
                appResourcesRootDir.set(project.layout.projectDirectory.dir("lib"))
            }

            buildTypes.release.proguard {
                version.set("7.3.2")
                obfuscate.set(true)
                optimize.set(true)
                configurationFiles.from(project.file("compose-desktop.pro"))
            }
        }
    }
}

kotlin {
    jvmToolchain(17)
}

val copyNativeLibs by tasks.registering(Copy::class) {
    val currentOs = OperatingSystem.current()
    
    val sourcePath = when {
        currentOs.isWindows -> "native/aura_core/target/release/aura_terminal_core.dll"
        currentOs.isMacOsX -> "native/aura_core/target/release/libaura_terminal_core.dylib"
        currentOs.isLinux -> "native/aura_core/target/release/libaura_terminal_core.so"
        else -> throw GradleException("Unsupported OS: ${currentOs.name}")
    }
    
    val destPath = when {
        currentOs.isMacOsX || currentOs.isLinux -> "src/main/resources"
        else -> "lib/windows" 
    }

    from(sourcePath)
    into("$projectDir/$destPath")
    
    doFirst {
        println("Detected OS: ${currentOs.name}")
        println("Copying native library from $sourcePath to $projectDir/$destPath")
    }
}

// Adjust resource dir setting based on OS
if (!OperatingSystem.current().isWindows) {
} else {
    compose.desktop.application.nativeDistributions.appResourcesRootDir.set(project.layout.projectDirectory.dir("lib"))
}

afterEvaluate {
    tasks.findByName("run")?.dependsOn(copyNativeLibs)
    tasks.findByName("packageMsi")?.dependsOn(copyNativeLibs)
    tasks.findByName("packageDmg")?.dependsOn(copyNativeLibs)
    tasks.findByName("prepareAppResources")?.dependsOn(copyNativeLibs)
    tasks.findByName("processResources")?.dependsOn(copyNativeLibs)
    tasks.findByName("packageDeb")?.dependsOn(copyNativeLibs)
    tasks.findByName("packageRpm")?.dependsOn(copyNativeLibs)
}


