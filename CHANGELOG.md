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
- **Zen mode**: UI fades out during playback for distraction-free reading
  - TopBar and BottomBar animate to transparent when playing
  - Word position stays fixed (bars remain in layout, only alpha changes)
  - Tap to pause reveals controls
- Base WPM adjustable 100-1000 via slider

#### Morpheme-Based Word Splitting
- **Long words (≥11 chars) automatically split** at morpheme boundaries
- Based on research: foveal visual span is 10-12 characters
- Max 10 letters per chunk (12 display chars with hyphens) to fit 320dp screens
- Split at prefix/suffix boundaries for cognitive optimization:
  - "internationalization" → `inter-` | `national-` | `-ization`
  - "electroencephalography" → `electro-` | `encephalogr-` | `-aphy`
- **~100 common prefixes**: inter-, counter-, electro-, neuro-, psycho-, bio-, etc.
- **~100 common suffixes**: -ization, -ological, -ability, -ment, -tion, etc.
- Fallback chunking at 10 chars when no morpheme boundary found
- Hyphen markers signal continuation to the brain

#### Adaptive Font Sizing
- **Font size adapts per screen width** to fit configured max display chars
- **Runtime font measurement** using Compose `TextMeasurer` for accurate sizing
  - Measures actual device font metrics instead of hardcoded assumptions
  - Prevents text clipping across all devices regardless of font variations
  - Fallback to conservative estimate (5% safety margin) for tests/previews
- Default: 12 display chars (10 letters + 2 hyphens) based on cognitive research
- 320dp portrait: ~42sp, 730dp landscape: 48sp (base)
- Proper edge padding (16dp each side) enforced in layout
- Ensures consistent chunk display across orientations

#### Configurable Max Chunk Size
- **`maxDisplayChars` setting** controls both font sizing AND word splitting threshold (10-24, default 12)
- Higher values = fewer word splits but smaller font on narrow screens
- Lower values = more splits but larger, more readable font
- **Dynamic re-parsing**: Changing chunk size re-parses the book in real-time
  - EPUB bytes stored in memory for instant re-parsing (~200ms for large books)
  - Position preserved via character offset mapping
  - Demo book regenerated with new chunk size
- Font automatically scales to fit the configured max on any screen width
- Settings loaded before demo book to avoid visual jutter on startup
- Persisted via DataStore

#### Demo Book
- **Demo book bundled as EPUB** in assets (~5KB)
- Loaded through same code path as user EPUBs (unified parsing)
- **Restart button** appears when book finishes
- Shows "Finished!" message with option to restart from beginning

#### Left-Shifted Anchor Position
- **Anchor at 42% of screen width** (left of center) by default
- Accommodates ORP asymmetry: ORP at ~35% of word means 65% extends right
- Configurable via `anchorPositionPercent` setting (0.3 to 0.5)
- Based on psychophysical research on visual span asymmetry

#### Split Chunk Timing Multiplier
- **Duration multiplier for split word chunks** (words with hyphens from splitting)
- Uses multiplier (not constant) per Research.md Zipfian pacing formula
- Default: 1.3x in Natural mode, 1.5x in Comprehension
- Scales proportionally with WPM (consistent effect at all speeds)
- Configurable via `splitChunkMultiplier` setting (1.0-2.0)
- Counted in effective WPM calculation

#### Orientation-Adaptive Vertical Positioning
- **Vertical position adapts to screen orientation** for ergonomic comfort
- Portrait mode: 22% from top (upper quarter, reduces neck strain from looking down)
- Landscape mode: 38% from top (closer to center, screen is shorter)
- Configurable via `verticalPositionPortrait` and `verticalPositionLandscape` settings
- Based on ergonomic research for mobile device viewing angles

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

#### Why 10-letter / 12-display-char default chunk size?
- Research: foveal visual span is 10-12 characters
- DEFAULT_MAX_CHUNK_CHARS = 10 letters, plus up to 2 hyphens = 12 display chars max
- 12 display chars fit on 320dp narrow screens with adaptive font sizing
- Longer chunks would require micro-saccades, defeating RSVP benefits
- Split threshold = maxChunkChars + 1 (configurable 10-24)

#### Why re-parse on chunk size change instead of caching multiple versions?
- Memory efficient: Only store EPUB bytes once (~200KB-2MB)
- Re-parsing is fast: ~200ms for 135K word book (Pride & Prejudice benchmark)
- Position mapping via character offset preserves reading progress
- Keeps architecture simple: one code path for all chunk sizes

#### Why bundle demo book as EPUB instead of hardcoded Kotlin?
- Unified code path: Demo uses same parsing as user books
- Removes ~60 lines of hardcoded text and chapter creation
- Simplifies BookSource to single data class (no sealed interface)
- Demo content editable without recompiling (just replace asset)
- Tiny overhead: 5KB EPUB vs similar Kotlin string literals

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

#### Why runtime font measurement instead of hardcoded width?
- Different devices have different monospace font metrics
- Hardcoded `BASE_CHAR_WIDTH_DP = 29f` caused clipping on some devices
- Robolectric tests use different fonts than real devices
- Runtime `TextMeasurer.measure("M")` gets actual device font width
- Calculation adapts: `fontSize = base * (availableSpace / measuredWidth)`
- No external library needed - uses Compose's built-in `rememberTextMeasurer()`

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
- [x] ~~Zen mode (hide UI during playback, show on pause)~~ ✓
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
