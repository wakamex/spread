# Spread ProGuard Rules

# Keep data classes used for state
-keep class app.spread.domain.** { *; }

# Keep JNI data transfer objects (Rust finds these by name)
-keep class app.spread.data.Native* { *; }

# Kotlin
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
