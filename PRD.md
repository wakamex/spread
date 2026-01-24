Product Requirements Document: SpeedRead 2.0

Overview

Product: SpeedRead - RSVP Speed Reading App
Version: 2.0 (Complete Rewrite)
Platform: Android (initial), iOS (future)
Architecture: Kotlin/Compose + Rust core

---
1. Problem Statement

The existing SpeedRead app suffers from:
- Laggy, unresponsive UI due to WebView-based rendering
- Blocking I/O causing freezes during file operations
- No visibility into actual reading pace when using adaptive timing
- Monolithic codebase preventing iteration

Users want a speed reading app that:
- Feels instantaneous
- Gives them control and visibility over their reading pace
- Handles their ebook library reliably
- Works offline

---
2. Goals & Non-Goals

Goals

- Sub-16ms frame times (60fps) during reading
- Book parsing completes in <2 seconds for typical EPUBs
- Real-time "Effective WPM" display reflecting actual pace
- 100% offline - zero network permissions required
- <10MB APK size
- Zero ads, zero tracking

Non-Goals (v2.0)

- Social features
- Cloud sync
- Audiobook support
- Web version
- Text-to-Speech (deferred to v2.1)
- Monetization/IAP (deferred to v2.1)
- Translation (removed - adds complexity, requires network)

---
3. Core Features

3.1 RSVP Reader

Description: Display words sequentially at user-defined pace with Optimal Recognition Point (ORP) highlighting.

Requirements:
┌──────┬────────────────────────────────────────┬──────────┐
│  ID  │              Requirement               │ Priority │
├──────┼────────────────────────────────────────┼──────────┤
│ R1.1 │ Display single word centered on screen │ P0       │
├──────┼────────────────────────────────────────┼──────────┤
│ R1.2 │ Highlight ORP letter in accent color   │ P0       │
├──────┼────────────────────────────────────────┼──────────┤
│ R1.3 │ Base WPM adjustable 100-1500           │ P0       │
├──────┼────────────────────────────────────────┼──────────┤
│ R1.4 │ Tap to play/pause                      │ P0       │
├──────┼────────────────────────────────────────┼──────────┤
│ R1.5 │ Swipe left/right for prev/next word    │ P1       │
├──────┼────────────────────────────────────────┼──────────┤
│ R1.6 │ Volume buttons adjust WPM              │ P1       │
└──────┴────────────────────────────────────────┴──────────┘
3.2 Adaptive Timing

Description: Adjust per-word display duration based on word characteristics.

Requirements:
┌──────┬────────────────────────────────────────────────────────┬──────────┐
│  ID  │                      Requirement                       │ Priority │
├──────┼────────────────────────────────────────────────────────┼──────────┤
│ R2.1 │ Configurable extra delay for punctuation (. , ; : ! ?) │ P0       │
├──────┼────────────────────────────────────────────────────────┼──────────┤
│ R2.2 │ Configurable extra delay by word length thresholds     │ P0       │
├──────┼────────────────────────────────────────────────────────┼──────────┤
│ R2.3 │ Configurable pause on paragraph breaks                 │ P1       │
├──────┼────────────────────────────────────────────────────────┼──────────┤
│ R2.4 │ Presets: "Uniform", "Natural", "Comprehension"         │ P1       │
└──────┴────────────────────────────────────────────────────────┴──────────┘
Timing Model:

word_delay_ms = base_delay_ms
              + punctuation_delay(following_punct)
              + length_delay(word_length)
              + paragraph_delay(is_paragraph_end)

3.3 Effective WPM Display ⭐

Description: Show the user their actual reading pace accounting for all timing adjustments.

Requirements:
┌──────┬──────────────────────────────────────────────────────────────────┬──────────┐
│  ID  │                           Requirement                            │ Priority │
├──────┼──────────────────────────────────────────────────────────────────┼──────────┤
│ R3.1 │ Display "Effective WPM" updating in real-time as settings change │ P0       │
├──────┼──────────────────────────────────────────────────────────────────┼──────────┤
│ R3.2 │ Show effective WPM for current chapter                           │ P0       │
├──────┼──────────────────────────────────────────────────────────────────┼──────────┤
│ R3.3 │ Show effective WPM for entire book                               │ P1       │
├──────┼──────────────────────────────────────────────────────────────────┼──────────┤
│ R3.4 │ Show estimated time remaining (chapter/book)                     │ P1       │
├──────┼──────────────────────────────────────────────────────────────────┼──────────┤
│ R3.5 │ Calculation must be <1ms even for large books                    │ P0       │
└──────┴──────────────────────────────────────────────────────────────────┴──────────┘
Implementation Approach (answering your question):

No, you don't need to parse ahead at runtime. Pre-compute statistics at parse time:

data class ChapterStats(
    val wordCount: Int,
    val punctuationCounts: PunctuationCounts,
    val lengthDistribution: LengthDistribution
)

data class PunctuationCounts(
    val periods: Int,      // . ! ?
    val commas: Int,       // , ; :
    val paragraphs: Int    // double newline / </p>
)

data class LengthDistribution(
    val short: Int,        // 1-4 chars
    val medium: Int,       // 5-8 chars
    val long: Int,         // 9-12 chars
    val veryLong: Int      // 13+ chars
)

Then effective WPM is O(1):

fun calculateEffectiveWpm(
    stats: ChapterStats,
    settings: TimingSettings
): EffectiveWpm {
    val baseTimeMs = stats.wordCount * settings.baseDelayMs

    val punctTimeMs = with(stats.punctuationCounts) {
        periods * settings.periodDelayMs +
        commas * settings.commaDelayMs +
        paragraphs * settings.paragraphDelayMs
    }

    val lengthTimeMs = with(stats.lengthDistribution) {
        // short words: no extra delay
        medium * settings.mediumWordExtraMs +
        long * settings.longWordExtraMs +
        veryLong * settings.veryLongWordExtraMs
    }

    val totalTimeMs = baseTimeMs + punctTimeMs + lengthTimeMs
    val effectiveWpm = (stats.wordCount * 60_000.0 / totalTimeMs).roundToInt()
    val estimatedMinutes = totalTimeMs / 60_000.0

    return EffectiveWpm(
        wpm = effectiveWpm,
        estimatedMinutes = estimatedMinutes
    )
}

Why this is optimal:
- Statistics computed once during parse (O(n), but already iterating words)
- UI slider adjustment → instant recalculation (O(1))
- No buffering, no "parsing ahead", no prediction lag
- Works for any scope: chapter, book, selection

3.4 Book Library

Requirements:
┌──────┬────────────────────────────────┬──────────┐
│  ID  │          Requirement           │ Priority │
├──────┼────────────────────────────────┼──────────┤
│ R4.1 │ Import EPUB files              │ P0       │
├──────┼────────────────────────────────┼──────────┤
│ R4.2 │ Import PDF files               │ P0       │
├──────┼────────────────────────────────┼──────────┤
│ R4.3 │ Import MOBI/AZW files          │ P1       │
├──────┼────────────────────────────────┼──────────┤
│ R4.4 │ Display cover art and metadata │ P1       │
├──────┼────────────────────────────────┼──────────┤
│ R4.5 │ Remember position per book     │ P0       │
├──────┼────────────────────────────────┼──────────┤
│ R4.6 │ Delete books                   │ P0       │
└──────┴────────────────────────────────┴──────────┘
3.5 Navigation

Requirements:
┌──────┬──────────────────────────────────────────┬──────────┐
│  ID  │               Requirement                │ Priority │
├──────┼──────────────────────────────────────────┼──────────┤
│ R5.1 │ Chapter list with tap to jump            │ P0       │
├──────┼──────────────────────────────────────────┼──────────┤
│ R5.2 │ Progress bar showing position in chapter │ P0       │
├──────┼──────────────────────────────────────────┼──────────┤
│ R5.3 │ Scrub progress bar to seek               │ P1       │
├──────┼──────────────────────────────────────────┼──────────┤
│ R5.4 │ Search within book                       │ P2       │
└──────┴──────────────────────────────────────────┴──────────┘
---
4. Data Model

4.1 Core Types

// Immutable book representation
data class Book(
    val id: BookId,
    val metadata: BookMetadata,
    val chapters: List<Chapter>,
    val stats: BookStats              // Pre-computed
)

data class BookMetadata(
    val title: String,
    val author: String?,
    val coverPath: String?
)

data class Chapter(
    val index: Int,
    val title: String,
    val words: List<Word>,            // Tokenized
    val stats: ChapterStats           // Pre-computed
)

data class Word(
    val text: String,
    val lengthBucket: LengthBucket,   // Pre-classified
    val followingPunct: Punctuation?  // Pre-parsed
)

enum class LengthBucket { SHORT, MEDIUM, LONG, VERY_LONG }

enum class Punctuation {
    PERIOD,      // . ! ?
    COMMA,       // , ; :
    PARAGRAPH    // </p> or \n\n
}

// Aggregated for O(1) effective WPM calculation
data class ChapterStats(
    val wordCount: Int,
    val shortWords: Int,
    val mediumWords: Int,
    val longWords: Int,
    val veryLongWords: Int,
    val periods: Int,
    val commas: Int,
    val paragraphs: Int
)

data class BookStats(
    val totalWords: Int,
    val chapterStats: List<ChapterStats>,
    val aggregated: ChapterStats       // Sum of all chapters
)

4.2 Settings

data class TimingSettings(
    val baseWpm: Int,                  // 100-1500

    // Punctuation delays (ms)
    val periodDelayMs: Int,            // Default: 150
    val commaDelayMs: Int,             // Default: 75
    val paragraphDelayMs: Int,         // Default: 300

    // Word length extra delays (ms)
    val mediumWordExtraMs: Int,        // Default: 20 (5-8 chars)
    val longWordExtraMs: Int,          // Default: 40 (9-12 chars)
    val veryLongWordExtraMs: Int       // Default: 60 (13+ chars)
) {
    val baseDelayMs: Int get() = 60_000 / baseWpm

    companion object {
        val Uniform = TimingSettings(
            baseWpm = 300,
            periodDelayMs = 0, commaDelayMs = 0, paragraphDelayMs = 0,
            mediumWordExtraMs = 0, longWordExtraMs = 0, veryLongWordExtraMs = 0
        )

        val Natural = TimingSettings(
            baseWpm = 300,
            periodDelayMs = 150, commaDelayMs = 75, paragraphDelayMs = 300,
            mediumWordExtraMs = 20, longWordExtraMs = 40, veryLongWordExtraMs = 60
        )

        val Comprehension = TimingSettings(
            baseWpm = 250,
            periodDelayMs = 300, commaDelayMs = 150, paragraphDelayMs = 500,
            mediumWordExtraMs = 30, longWordExtraMs = 60, veryLongWordExtraMs = 100
        )
    }
}

4.3 Reader State

data class ReaderState(
    val book: Book?,
    val position: Position,
    val settings: TimingSettings,
    val playing: Boolean,
    val effectiveWpm: EffectiveWpm?    // Derived, but cached for UI
)

data class Position(
    val chapterIndex: Int,
    val wordIndex: Int
)

data class EffectiveWpm(
    val chapterWpm: Int,
    val chapterMinutesRemaining: Double,
    val bookWpm: Int,
    val bookMinutesRemaining: Double
)

---
5. Architecture

5.1 Layer Diagram

┌─────────────────────────────────────────────────────────────────┐
│                         UI Layer                                 │
│                    (Jetpack Compose)                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐  │
│  │ ReaderScreen│  │LibraryScreen│  │ SettingsScreen          │  │
│  └─────────────┘  └─────────────┘  └─────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      ViewModel Layer                             │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ ReaderViewModel                                          │    │
│  │  - state: StateFlow<ReaderState>                        │    │
│  │  - dispatch(action: Action)                             │    │
│  └─────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Pure Domain Layer                             │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────┐   │
│  │ ReaderReducer    │  │ WpmCalculator    │  │ Navigation   │   │
│  │ (state machine)  │  │ (pure functions) │  │ (pure)       │   │
│  └──────────────────┘  └──────────────────┘  └──────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Infrastructure Layer                          │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────┐   │
│  │ BookRepository   │  │ SettingsStore    │  │ Ticker       │   │
│  │ (Room + Rust)    │  │ (DataStore)      │  │ (Coroutines) │   │
│  └──────────────────┘  └──────────────────┘  └──────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                       Rust Core (JNI)                            │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────┐   │
│  │ EPUB Parser      │  │ PDF Parser       │  │ MOBI Parser  │   │
│  └──────────────────┘  └──────────────────┘  └──────────────┘   │
└─────────────────────────────────────────────────────────────────┘

5.2 Data Flow

User adjusts "Period Delay" slider
           │
           ▼
┌─────────────────────────────┐
│ dispatch(SetPeriodDelay(150))│
└─────────────────────────────┘
           │
           ▼
┌─────────────────────────────┐
│ reduce(state, action)        │  ◄── Pure function
│   → newSettings              │
│   → recalcEffectiveWpm()     │  ◄── O(1) using pre-computed stats
│   → Update(newState, [])     │
└─────────────────────────────┘
           │
           ▼
┌─────────────────────────────┐
│ StateFlow emits new state    │
└─────────────────────────────┘
           │
           ▼
┌─────────────────────────────┐
│ Compose recomposes UI        │
│  - Slider shows 150ms        │
│  - Effective WPM shows 267   │
│  - ETA shows "12 min left"   │
└─────────────────────────────┘

Total latency: <1 frame (16ms)

---
6. UI Specifications

6.1 Reader Screen

┌─────────────────────────────────────────┐
│ ≡  Chapter 3: The Discovery      ⚙️    │  ← Header (tap to show/hide)
├─────────────────────────────────────────┤
│                                         │
│                                         │
│                                         │
│           pho·to·syn·the·sis            │  ← Word with ORP highlight
│              ▲                          │     (red letter)
│                                         │
│                                         │
│                                         │
├─────────────────────────────────────────┤
│  ▶  267 effective WPM   12 min left     │  ← Status bar
├─────────────────────────────────────────┤
│ ═══════════●════════════════════════    │  ← Chapter progress
│ ════●══════════════════════════════     │  ← Book progress
└─────────────────────────────────────────┘

6.2 Settings Panel (Bottom Sheet)

┌─────────────────────────────────────────┐
│  Reading Speed                          │
│  ──────────────────●────────── 300 WPM  │
│                                         │
│  ─────────────────────────────────────  │
│                                         │
│  Preset: ● Uniform ○ Natural ○ Custom   │
│                                         │
│  ─────────────────────────────────────  │
│                                         │
│  Period pause        ────●───── 150ms   │
│  Comma pause         ──●─────── 75ms    │
│  Paragraph pause     ─────●──── 300ms   │
│                                         │
│  Long word delay     ───●────── 40ms    │
│  (9+ characters)                        │
│                                         │
│  ─────────────────────────────────────  │
│                                         │
│  📊 Effective WPM                       │
│     Chapter: 267 WPM (12 min)           │
│     Book:    271 WPM (3.2 hrs)          │
│                                         │
└─────────────────────────────────────────┘

6.3 Real-Time Feedback

When user drags any timing slider:
- Effective WPM updates immediately (<16ms)
- Visual indicator shows the impact: 300 → 267 WPM (-11%)
- Time estimate updates: 10 min → 12 min

---
7. Performance Requirements
┌─────────────────────────────┬───────────────┬──────────────────┐
│           Metric            │    Target     │   Measurement    │
├─────────────────────────────┼───────────────┼──────────────────┤
│ Frame time during reading   │ <16ms (60fps) │ Systrace         │
├─────────────────────────────┼───────────────┼──────────────────┤
│ Effective WPM recalculation │ <1ms          │ Benchmark        │
├─────────────────────────────┼───────────────┼──────────────────┤
│ EPUB parse time (avg 100KB) │ <500ms        │ Instrumentation  │
├─────────────────────────────┼───────────────┼──────────────────┤
│ EPUB parse time (large 2MB) │ <2s           │ Instrumentation  │
├─────────────────────────────┼───────────────┼──────────────────┤
│ PDF parse time (100 pages)  │ <3s           │ Instrumentation  │
├─────────────────────────────┼───────────────┼──────────────────┤
│ Memory usage (idle)         │ <50MB         │ Android Profiler │
├─────────────────────────────┼───────────────┼──────────────────┤
│ Memory usage (reading)      │ <80MB         │ Android Profiler │
├─────────────────────────────┼───────────────┼──────────────────┤
│ APK size                    │ <10MB         │ Build output     │
├─────────────────────────────┼───────────────┼──────────────────┤
│ Cold start to library       │ <500ms        │ Macrobenchmark   │
└─────────────────────────────┴───────────────┴──────────────────┘
---
8. Technical Implementation Notes

8.1 Effective WPM - Detailed Algorithm

At parse time (Rust):

pub struct WordToken {
    pub text: String,
    pub length_bucket: u8,      // 0=short, 1=med, 2=long, 3=verylong
    pub following_punct: u8,    // 0=none, 1=comma, 2=period, 3=paragraph
}

pub struct ChapterStats {
    pub word_count: u32,
    pub length_counts: [u32; 4],    // [short, med, long, verylong]
    pub punct_counts: [u32; 4],     // [none, comma, period, paragraph]
}

fn compute_stats(words: &[WordToken]) -> ChapterStats {
    let mut stats = ChapterStats::default();
    stats.word_count = words.len() as u32;

    for word in words {
        stats.length_counts[word.length_bucket as usize] += 1;
        stats.punct_counts[word.following_punct as usize] += 1;
    }

    stats
}

At runtime (Kotlin):

fun TimingSettings.effectiveWpmFor(stats: ChapterStats): EffectiveWpm {
    // Base time
    val baseMs = stats.wordCount * baseDelayMs

    // Length adjustments (short words = index 0, no extra delay)
    val lengthMs = stats.lengthCounts[1] * mediumWordExtraMs +
                   stats.lengthCounts[2] * longWordExtraMs +
                   stats.lengthCounts[3] * veryLongWordExtraMs

    // Punctuation adjustments (none = index 0, no extra delay)
    val punctMs = stats.punctCounts[1] * commaDelayMs +
                  stats.punctCounts[2] * periodDelayMs +
                  stats.punctCounts[3] * paragraphDelayMs

    val totalMs = baseMs + lengthMs + punctMs
    val wpm = (stats.wordCount * 60_000.0 / totalMs).roundToInt()
    val minutes = totalMs / 60_000.0

    return EffectiveWpm(wpm, minutes)
}

Complexity: O(1) - just arithmetic on pre-computed counts.

8.2 Word Timing During Playback

fun Word.delayMs(settings: TimingSettings): Long {
    val base = settings.baseDelayMs.toLong()

    val lengthExtra = when (lengthBucket) {
        LengthBucket.SHORT -> 0
        LengthBucket.MEDIUM -> settings.mediumWordExtraMs
        LengthBucket.LONG -> settings.longWordExtraMs
        LengthBucket.VERY_LONG -> settings.veryLongWordExtraMs
    }

    val punctExtra = when (followingPunct) {
        null -> 0
        Punctuation.COMMA -> settings.commaDelayMs
        Punctuation.PERIOD -> settings.periodDelayMs
        Punctuation.PARAGRAPH -> settings.paragraphDelayMs
    }

    return base + lengthExtra + punctExtra
}

No lookahead needed - each word carries its own timing metadata.

8.3 Ticker Implementation

class Ticker(private val scope: CoroutineScope) {
    private var job: Job? = null

    fun start(getDelay: () -> Long, onTick: () -> Unit) {
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                delay(getDelay())
                onTick()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}

// Usage in ViewModel
ticker.start(
    getDelay = { state.value.currentWord?.delayMs(state.value.settings) ?: 200L },
    onTick = { dispatch(Action.NextWord) }
)

---
9. File Format Support
┌──────────┬──────────────────┬──────────┬────────────────────────────┐
│  Format  │      Parser      │ Priority │           Notes            │
├──────────┼──────────────────┼──────────┼────────────────────────────┤
│ EPUB 2/3 │ Rust (zip + xml) │ P0       │ Primary format             │
├──────────┼──────────────────┼──────────┼────────────────────────────┤
│ PDF      │ Rust (pdf-rs)    │ P0       │ Text extraction only       │
├──────────┼──────────────────┼──────────┼────────────────────────────┤
│ MOBI     │ Rust (mobi-rs)   │ P1       │ Convert to internal format │
├──────────┼──────────────────┼──────────┼────────────────────────────┤
│ AZW3/KF8 │ Rust             │ P2       │ Kindle format              │
├──────────┼──────────────────┼──────────┼────────────────────────────┤
│ TXT      │ Kotlin           │ P1       │ Simple, no Rust needed     │
├──────────┼──────────────────┼──────────┼────────────────────────────┤
│ FB2      │ Rust             │ P2       │ Russian ebook format       │
└──────────┴──────────────────┴──────────┴────────────────────────────┘
---
10. Storage Schema

10.1 Room Database

@Entity
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String?,
    val coverPath: String?,
    val filePath: String,
    val format: String,
    val addedAt: Long,
    val lastOpenedAt: Long?
)

@Entity
data class ReadingProgressEntity(
    @PrimaryKey val bookId: String,
    val chapterIndex: Int,
    val wordIndex: Int,
    val updatedAt: Long
)

@Entity
data class ChapterStatsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val chapterIndex: Int,
    val wordCount: Int,
    val shortWords: Int,
    val mediumWords: Int,
    val longWords: Int,
    val veryLongWords: Int,
    val periods: Int,
    val commas: Int,
    val paragraphs: Int
)

10.2 DataStore (Settings)

val Context.settingsDataStore by preferencesDataStore(name = "settings")

object SettingsKeys {
    val BASE_WPM = intPreferencesKey("base_wpm")
    val PERIOD_DELAY = intPreferencesKey("period_delay_ms")
    val COMMA_DELAY = intPreferencesKey("comma_delay_ms")
    val PARAGRAPH_DELAY = intPreferencesKey("paragraph_delay_ms")
    val MEDIUM_WORD_EXTRA = intPreferencesKey("medium_word_extra_ms")
    val LONG_WORD_EXTRA = intPreferencesKey("long_word_extra_ms")
    val VERY_LONG_WORD_EXTRA = intPreferencesKey("very_long_word_extra_ms")
    val THEME = stringPreferencesKey("theme")
}

---
11. Milestones

M1: Core Reader (4 weeks)

- Rust EPUB parser with stats computation
- Kotlin domain layer (state, reducer, WPM calculator)
- Basic Compose UI (word display, play/pause)
- Effective WPM display

M2: Full Reader (3 weeks)

- Chapter navigation
- Progress persistence
- Settings panel with all timing controls
- Timing presets

M3: Library (2 weeks)

- Book import flow
- Library grid view
- Cover art extraction
- Delete functionality

M4: Additional Formats (2 weeks)

- PDF parser
- MOBI parser
- TXT support

M5: Polish (2 weeks)

- Themes (light/dark/sepia)
- Animations and transitions
- Performance optimization
- Accessibility
- Final testing and release prep

---
12. Success Metrics
┌───────────────────────────────┬───────────────┬─────────────────────┐
│            Metric             │    Target     │  Measurement Method │
├───────────────────────────────┼───────────────┼─────────────────────┤
│ Play Store rating             │ ≥4.5          │ Play Console        │
├───────────────────────────────┼───────────────┼─────────────────────┤
│ Crash-free rate               │ ≥99.5%        │ Play Console Vitals │
├───────────────────────────────┼───────────────┼─────────────────────┤
│ ANR rate                      │ <0.1%         │ Play Console Vitals │
├───────────────────────────────┼───────────────┼─────────────────────┤
│ Uninstall rate (30-day)       │ <40%          │ Play Console        │
├───────────────────────────────┼───────────────┼─────────────────────┤
│ App size                      │ <10MB         │ Build output        │
├───────────────────────────────┼───────────────┼─────────────────────┤
│ Cold start time               │ <500ms        │ Manual testing      │
└───────────────────────────────┴───────────────┴─────────────────────┘

Note: No in-app analytics. User privacy is paramount. Metrics come only from
Play Console aggregate data which requires no tracking code in the app.
---
13. Deferred Features (v2.1+)

The following features are explicitly out of scope for v2.0 to maintain focus on core reading experience:

13.1 Text-to-Speech
- Integrate with Android TTS engine
- Open question: Should TTS use the same adaptive timing settings?

13.2 Cloud Sync
- Sync reading progress across devices
- Requires account system

13.3 Monetization
- Options: one-time purchase, subscription, or freemium
- Decision deferred until user base established

---
Appendix A: Effective WPM Examples

Given these settings:
- Base WPM: 300 (200ms per word)
- Period delay: 150ms
- Comma delay: 75ms
- Paragraph delay: 300ms
- Long word (9-12 chars) extra: 40ms
- Very long word (13+ chars) extra: 60ms

For a chapter with:
- 5,000 words
- 3,200 short, 1,200 medium, 500 long, 100 very long
- 180 periods, 220 commas, 45 paragraphs

Base time:       5,000 × 200ms = 1,000,000ms
Length extra:    500 × 40ms + 100 × 60ms = 26,000ms
Punctuation:     180 × 150ms + 220 × 75ms + 45 × 300ms = 57,000ms
────────────────────────────────────────────────────────
Total:           1,083,000ms = 18.05 minutes

Effective WPM:   5,000 / 18.05 = 277 WPM

The user set 300 WPM but will actually read at 277 WPM (-7.7%) due to adaptive timing. This is the number we display.