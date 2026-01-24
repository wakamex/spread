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

#### Morpheme-Based Word Splitting
- **Long words (≥13 chars) automatically split** at morpheme boundaries
- Based on research: foveal visual span is 10-12 characters
- Split at prefix/suffix boundaries for cognitive optimization:
  - "internationalization" → `inter-` | `national-` | `-ization`
  - "electroencephalography" → `electro-` | `encephalogr-` | `-aphy`
- **~100 common prefixes**: inter-, counter-, electro-, neuro-, psycho-, bio-, etc.
- **~100 common suffixes**: -ization, -ological, -ability, -ment, -tion, etc.
- Fallback chunking at 12 chars when no morpheme boundary found
- Hyphen markers signal continuation to the brain

#### File Import
- **EPUB file picker** using Android Storage Access Framework
- Reads directly from content URI (no file copying needed)
- Demo book preserved for first-time users
- Open book via 📖 button in header

#### Settings Persistence
- **All timing settings persisted** using Jetpack DataStore
- Settings survive app restart
- 500ms debounce prevents excessive writes during slider drag
- Loaded on startup via `SettingsLoaded` action

#### Book Library & Progress Persistence
- **Room database** for book library and reading progress
- `BookEntity` stores book metadata (id, title, author, fileUri, timestamps)
- `ReadingProgressEntity` stores reading position per book
- `BookRepository` unified interface for:
  - Library management (getAllBooks, saveBookToLibrary, deleteBook)
  - Progress persistence (getProgress, saveProgress)
  - EPUB parsing (loadBook, loadBookFromBytes)

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
- Screenshot tests for split word chunks with hyphens
- Tests for scientific/technical terms (neuropsychological, electroencephalography)
- Kotlin unit tests for timing calculations and state reducer
- Rust unit tests for morpheme splitting

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

#### Why morpheme splitting instead of font scaling?
- Research: "Standard RSVP fails when encountering long, polysyllabic words"
- Font scaling degrades readability and breaks the RSVP paradigm
- Morphological splits preserve semantic structure (meaning units)
- Brain processes "un-" + "believ" + "-able" faster than arbitrary chunks
- Consistent chunk sizes maintain reading rhythm

#### Why 12-char max chunk size?
- Research: foveal visual span is 10-12 characters
- Words up to 12 chars can be recognized in a single fixation
- Longer chunks would require micro-saccades, defeating RSVP benefits
- Matches the MIN_SPLIT_LENGTH of 13 (words ≥13 chars get split)

#### Why ~100 affixes instead of ML-based morpheme detection?
- Simple prefix/suffix matching covers ~80% of long English words
- O(1) lookup vs ML inference overhead
- No external dependencies (Morfessor requires Python)
- Predictable, consistent splits for user experience
- Expanded list covers scientific/technical vocabulary

#### Why minSdk 21?
- Covers 99%+ of active Android devices
- Compose works well with desugaring for older APIs
- Rust/JNI has no Android version restrictions

### Known Issues
- No library screen yet (books can be opened but not browsed)

---

## Planned

### Next Priority (P0)
- [x] ~~Room database for book library and progress persistence~~ ✓
- [x] ~~Wire up progress persistence in ViewModel (SaveProgress effect handler)~~ ✓
- [x] ~~Restore reading position when opening a book~~ ✓
- [x] ~~Persist settings via DataStore~~ ✓

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
