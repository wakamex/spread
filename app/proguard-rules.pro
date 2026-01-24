# Spread ProGuard Rules

# Keep data classes used for state
-keep class app.spread.domain.** { *; }

# Kotlin
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
