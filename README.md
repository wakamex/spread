# Spread - RSVP Speed Reading App

A fast, native RSVP (Rapid Serial Visual Presentation) speed reader for Android.

## Features

- **ORP-Centered Display**: Words anchored at Optimal Recognition Point (~35%) for stable eye position
- **Adaptive Font Sizing**: Font scales per screen width to fit 12 display chars on any device (320dp+)
- **Morpheme-Based Word Splitting**: Long words split at prefix/suffix boundaries (e.g., `inter-` | `national-` | `-ization`)
- **Adaptive Timing**: Configurable delays for punctuation and word length with presets (Uniform, Natural, Comprehension)
- **Effective WPM Display**: O(1) calculation shows actual reading pace accounting for all timing adjustments
- **Progress Persistence**: Reading position saved per book using Room database
- **EPUB Support**: Native Rust parser for fast, memory-efficient parsing
- **Demo Book**: Built-in content for first-time users with restart option

## Build Requirements

- **JDK**: 17 or 21 (JDK 21 requires AGP 8.3+)
- **Android SDK**: API 34
- **Gradle**: 8.x (wrapper included)
- **Rust**: For native EPUB parser (pre-built binaries included)

## Building

```bash
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Build Notes

### JDK 21 Compatibility

AGP 8.2.x has a known incompatibility with JDK 21's jlink tool when processing Android's `core-for-system-modules.jar`. Error:

```
ModuleTarget is malformed: platformString missing delimiter: android
```

**Solution**: Use AGP 8.3.0+ which includes the fix. This is already configured in `build.gradle.kts`.

### Native Library

The Rust-based EPUB parser is pre-compiled for all 4 Android ABIs:
- `arm64-v8a`
- `armeabi-v7a`
- `x86`
- `x86_64`

To rebuild the native library:

```bash
cd rust
cargo ndk -t arm64-v8a -t armeabi-v7a -t x86 -t x86_64 -o ../app/src/main/jniLibs build --release
```

Requires: [cargo-ndk](https://github.com/aspect-build/cargo-ndk)

## Architecture

- **UI**: Kotlin + Jetpack Compose
- **State Management**: Functional core with pure reducer
- **Parsing**: Rust (EPUB) via JNI
- **Storage**: Room (library), DataStore (settings)

See [CHANGELOG.md](CHANGELOG.md) for detailed implementation notes.
