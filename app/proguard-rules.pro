# SafeGuard / SignalDisruptor Pro - keep Compose + reflection-accessed engine intact
-keep class com.safeguard.app.** { *; }
-dontwarn org.jetbrains.annotations.**
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}
