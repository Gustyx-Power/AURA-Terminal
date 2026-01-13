# 1. Keep the Entry Point
-keep class MainKt { *; }

# 2. CRITICAL: Keep JNI Bridge (Rust Connection)
# If this is renamed, aura_terminal_core.dll will fail to load!
-keep class com.aura.terminal.engine.RustBridge { *; }
-keepclassmembers class com.aura.terminal.engine.RustBridge { native <methods>; }

# 3. Keep PTY4J and JNA (Terminal Backend)
# These use reflection, so they must not be obfuscated.
-keep class com.pty4j.** { *; }
-keep class com.sun.jna.** { *; }

# 4. Keep Compose & Coroutines (Standard Safety)
-keep class androidx.compose.** { *; }
-keep class org.jetbrains.skiko.** { *; }
-keep class kotlinx.coroutines.** { *; }

# 5. Suppress Warnings (Clean Build)
-dontwarn java.awt.**
-dontwarn javax.swing.**
-dontwarn sun.misc.**
