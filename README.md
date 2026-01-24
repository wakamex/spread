# Spread

A distraction-free RSVP speed reader for Android.

## What is RSVP?

RSVP (Rapid Serial Visual Presentation) displays one word at a time in a fixed position, eliminating eye movement and letting you read faster with better focus. Spread uses research-backed techniques to make this comfortable at high speeds.

## Features

**Smart Word Display**
- Words anchored at the Optimal Recognition Point (ORP) — the natural focus point ~35% into each word
- ORP letter highlighted in orange for instant recognition

**Handles Long Words Gracefully**
- Words over 10 characters split at meaningful boundaries (prefixes/suffixes)
- "internationalization" becomes `inter-` → `national-` → `-ization`
- Each chunk stays readable; no tiny fonts or horizontal scrolling

**Adaptive Timing**
- Short common words flash quickly; long words get proportionally more time
- Extra pauses at punctuation for natural pacing
- Three presets: Uniform, Natural, Comprehension — or customize everything

**Clean Reading Experience**
- True black background for OLED screens
- Zen mode: UI fades away during reading, reappears on tap
- Adjustable speed from 100-1000 WPM

**Works Offline**
- All data stays on your device
- No accounts, no tracking, no ads
- Supports EPUB files

## Screenshots

*Coming soon*

## Building from Source

Requires JDK 17+ and Android SDK 34.

```bash
./gradlew assembleDebug
```

The Rust native library is pre-compiled for all Android ABIs. To rebuild it, see [rust/README.md](rust/README.md).

## License

MIT
