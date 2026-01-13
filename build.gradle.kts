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
        
        jvmArgs += listOf("-Djava.library.path=${project.projectDir}/lib/windows")
        
        nativeDistributions {
            packageName = "AURA-Terminal"
            packageVersion = "1.0.0"
            
            description = "A modern, transparent terminal emulator for Windows"
            vendor = "Xtra Manager Software"
            copyright = "Copyright © 2026 Xtra Manager Software"
            
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
    val osName = System.getProperty("os.name").lowercase()
    val sourcePath = if (osName.contains("windows")) "native/aura_core/target/release/aura_terminal_core.dll"
              else if (osName.contains("linux")) "native/aura_core/target/release/libaura_terminal_core.so"
              else "native/aura_core/target/release/libaura_terminal_core.dylib"
    
    from(sourcePath)
    into("$projectDir/lib/windows")
    
    doFirst {
        println("Copying native library from $sourcePath to $projectDir/lib")
    }
}

compose.desktop.application.nativeDistributions.appResourcesRootDir.set(project.layout.projectDirectory.dir("lib"))

afterEvaluate {
    tasks.named("run") {
        dependsOn(copyNativeLibs)
    }
    tasks.named("packageMsi") {
        dependsOn(copyNativeLibs)
    }
    tasks.named("prepareAppResources") {
        dependsOn(copyNativeLibs)
    }
}


