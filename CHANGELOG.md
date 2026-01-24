# Spread - Development Changelog

## [Unreleased]

### Added

#### Core Architecture
- **Kotlin + Jetpack Compose UI** with Material3
- **Rust EPUB parser** with JNI bindings for all 4 Android ABIs (arm64-v8a, armeabi-v7a, x86, x86_64)
- **Functional core architecture** with pure reducer pattern (State + Action → Update with Effects)
- **minSdk 21** (Android 5.0 Lollipop) for broad device compatibility

#### RSVP Reader
- Word display with **ORP (Optimal Recognition Point) highlighting**
- **ORP center-lock**: The highlighted letter stays at exact screen center regardless of word length
  - Uses dynamic measurement via Compose `layout` modifier
  - Zero-width anchor point technique for pixel-perfect alignment
  - Verified with automated Paparazzi screenshot tests
- **Orangered (#FF4500)** for ORP color instead of pure red
  - Better perceived luminance on OLED dark backgrounds
  - Reduces eye strain vs pure red
  - Sharper edge contrast at high WPM
- **Upper-third vertical positioning** for ergonomic focus
- **Subtle guide lines** above/below text to prevent eye drift
- Tap to play/pause
- Base WPM adjustable 100-1000 via slider

#### Adaptive Timing
- Configurable extra delay for punctuation (period, comma, paragraph)
- Configurable extra delay by word length (medium, long, very long)
- **Presets**: Uniform, Natural, Comprehension
- Per-word delay calculated from pre-classified metadata (no lookahead needed)

#### Effective WPM Display
- **O(1) calculation** using pre-computed statistics at parse time
- Shows effective WPM for current chapter and entire book
- Shows estimated time remaining
- Updates in real-time as settings change (<1ms recalculation)

#### UI Polish
- Dark theme with true black (#000000) background
- Rounded progress bar ends matching slider thumb
- Centered WPM label above slider
- Settings sheet with scrollable content and proper navigation bar padding
- Edge-to-edge display with proper status bar handling

#### Testing Infrastructure
- **Paparazzi screenshot testing** for visual regression testing
- WordDisplayTestable component with debug center line
- Screenshot tests for various word lengths including problem cases
- Kotlin unit tests for timing calculations and state reducer

### Technical Decisions

#### Why Rust for EPUB parsing?
- Performance: Native code for CPU-intensive XML/ZIP parsing
- Memory safety: No GC pauses during parsing
- Cross-platform: Same parser can be used for iOS later
- Pre-computation: Statistics computed during parse for O(1) effective WPM

#### Why Orangered instead of Red?
- Pure red (#FF0000) only triggers long-wavelength cones, appearing "dim" on black
- Orangered (#FF4500) adds yellow/green component for higher perceived luminance
- Reduces OLED "smearing" at high WPM due to faster pixel transitions
- Based on color science research for reading applications

#### Why ORP center-lock with zero-width anchor?
- Initial attempts used offset calculations that drifted for long words
- The `layout(placeable.width, ...)` approach centered the word, not the ORP
- Solution: `layout(0, placeable.height)` creates a zero-width anchor at Box center
- The Row is then placed with negative offset to align ORP with anchor
- Verified pixel-perfect with Paparazzi screenshot tests

#### Why minSdk 21?
- Covers 99%+ of active Android devices
- Compose works well with desugaring for older APIs
- Rust/JNI has no Android version restrictions

### Known Issues
- Demo book is hardcoded; file picker not yet implemented
- Reading progress not persisted (Room database not implemented)
- No library screen yet

---

## Planned

### Next Priority (P0)
- [ ] File picker to import EPUB files
- [ ] Room database for book library and progress persistence
- [ ] Remember reading position per book

### Medium Priority (P1)
- [ ] Library screen with imported books
- [ ] Swipe left/right for prev/next word
- [ ] Volume buttons to adjust WPM
- [ ] Chapter list with tap to jump
- [ ] Zen mode (hide UI during playback, show on pause)
- [ ] Scrub progress bar to seek

### Lower Priority (P2)
- [ ] PDF parser
- [ ] MOBI parser
- [ ] Themes (light/dark/sepia)
- [ ] Search within book

### Deferred to v2.1
- Text-to-Speech integration
- Cloud sync
- Monetization
