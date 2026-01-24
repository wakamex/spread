package app.spread.domain

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Timing configuration and effective WPM calculation.
 * All functions are pure - no side effects.
 */

data class TimingSettings(
    val baseWpm: Int,
    val periodDelayMs: Int,
    val commaDelayMs: Int,
    val paragraphDelayMs: Int,
    /**
     * Scale factor for word length timing (0.0 to 1.0).
     * Uses sqrt(len/5.2) formula from psycholinguistic research.
     * 0.0 = uniform timing (all words same duration)
     * 1.0 = full effect (short words faster, long words slower)
     * Default 0.8 for natural feel without being too aggressive.
     */
    val lengthTimingScale: Float,
    /**
     * Duration multiplier for split word chunks (words with hyphens from splitting).
     * 1.0 = no change, 1.3 = 30% longer display time.
     * Helps brain reconstruct split words. Multiplier scales with WPM.
     */
    val splitChunkMultiplier: Float,
    /**
     * Horizontal position of the ORP anchor as fraction of screen width.
     * 0.5 = center, 0.42 = left of center (recommended for ORP asymmetry).
     * Lower values give more room for the right side of words.
     */
    val anchorPositionPercent: Float,
    /**
     * Vertical position of text in portrait mode as fraction of screen height.
     * 0.0 = top, 0.5 = center, 1.0 = bottom.
     * Upper quarter (0.20-0.25) reduces neck strain from looking down at phone.
     */
    val verticalPositionPortrait: Float,
    /**
     * Vertical position of text in landscape mode as fraction of screen height.
     * Landscape screens are shorter, so position closer to center (0.35-0.40).
     */
    val verticalPositionLandscape: Float,
    /**
     * Maximum display characters per chunk (including hyphens).
     * Affects word splitting threshold and font size calculation.
     * Higher values = fewer splits but smaller font on narrow screens.
     * Range: 10-24, default 12.
     */
    val maxDisplayChars: Int
) {
    val baseDelayMs: Int get() = 60_000 / baseWpm

    companion object {
        /** Average word length in English (used for sqrt timing formula) */
        const val AVG_WORD_LENGTH = 5.2f

        /**
         * Bucket-based multipliers for O(1) effective WPM calculation.
         * Approximates sqrt(avgBucketLength / AVG_WORD_LENGTH).
         */
        const val SHORT_WORD_MULTIPLIER = 0.76f   // avg ~3 chars
        const val MEDIUM_WORD_MULTIPLIER = 1.12f  // avg ~6.5 chars
        const val LONG_WORD_MULTIPLIER = 1.42f    // avg ~10.5 chars
        const val VERY_LONG_WORD_MULTIPLIER = 1.70f // avg ~15 chars

        /** Default anchor position: 42% from left (left of center) */
        const val DEFAULT_ANCHOR_POSITION = 0.42f
        /** Default split chunk multiplier: 30% extra time */
        const val DEFAULT_SPLIT_CHUNK_MULTIPLIER = 1.3f
        /** Default vertical position in portrait: upper quarter */
        const val DEFAULT_VERTICAL_PORTRAIT = 0.22f
        /** Default vertical position in landscape: closer to center */
        const val DEFAULT_VERTICAL_LANDSCAPE = 0.38f
        /** Default max display chars (10 letters + 2 hyphens) */
        const val DEFAULT_MAX_DISPLAY_CHARS = 12

        val Uniform = TimingSettings(
            baseWpm = 300,
            periodDelayMs = 0,
            commaDelayMs = 0,
            paragraphDelayMs = 0,
            lengthTimingScale = 0.0f,
            splitChunkMultiplier = 1.0f,
            anchorPositionPercent = DEFAULT_ANCHOR_POSITION,
            verticalPositionPortrait = DEFAULT_VERTICAL_PORTRAIT,
            verticalPositionLandscape = DEFAULT_VERTICAL_LANDSCAPE,
            maxDisplayChars = DEFAULT_MAX_DISPLAY_CHARS
        )

        val Natural = TimingSettings(
            baseWpm = 300,
            periodDelayMs = 150,
            commaDelayMs = 75,
            paragraphDelayMs = 300,
            lengthTimingScale = 0.8f,
            splitChunkMultiplier = DEFAULT_SPLIT_CHUNK_MULTIPLIER,
            anchorPositionPercent = DEFAULT_ANCHOR_POSITION,
            verticalPositionPortrait = DEFAULT_VERTICAL_PORTRAIT,
            verticalPositionLandscape = DEFAULT_VERTICAL_LANDSCAPE,
            maxDisplayChars = DEFAULT_MAX_DISPLAY_CHARS
        )

        val Comprehension = TimingSettings(
            baseWpm = 250,
            periodDelayMs = 300,
            commaDelayMs = 150,
            paragraphDelayMs = 500,
            lengthTimingScale = 1.0f,
            splitChunkMultiplier = 1.5f,
            anchorPositionPercent = DEFAULT_ANCHOR_POSITION,
            verticalPositionPortrait = DEFAULT_VERTICAL_PORTRAIT,
            verticalPositionLandscape = DEFAULT_VERTICAL_LANDSCAPE,
            maxDisplayChars = DEFAULT_MAX_DISPLAY_CHARS
        )

        val Default = Natural
    }
}

data class EffectiveWpm(
    val wpm: Int,
    val totalMinutes: Double,
    val minutesRemaining: Double
)

data class EffectiveWpmInfo(
    val chapter: EffectiveWpm,
    val book: EffectiveWpm
)

// --- Pure calculation functions ---

/**
 * Calculate effective WPM for given stats and settings.
 * O(1) complexity - uses pre-computed statistics.
 */
fun calculateEffectiveWpm(
    stats: ChapterStats,
    settings: TimingSettings,
    wordsRead: Int = 0
): EffectiveWpm {
    if (stats.wordCount == 0) {
        return EffectiveWpm(wpm = settings.baseWpm, totalMinutes = 0.0, minutesRemaining = 0.0)
    }

    val baseDelayMs = settings.baseDelayMs.toDouble()
    val scale = settings.lengthTimingScale.toDouble()

    // Calculate weighted base time using sqrt-based multipliers (scaled by lengthTimingScale)
    // multiplier = 1 + scale * (bucketMultiplier - 1)
    // This interpolates between 1.0 (uniform) and bucketMultiplier (full effect)
    fun scaledMultiplier(bucketMultiplier: Float): Double =
        1.0 + scale * (bucketMultiplier - 1.0)

    val shortMultiplier = scaledMultiplier(TimingSettings.SHORT_WORD_MULTIPLIER)
    val mediumMultiplier = scaledMultiplier(TimingSettings.MEDIUM_WORD_MULTIPLIER)
    val longMultiplier = scaledMultiplier(TimingSettings.LONG_WORD_MULTIPLIER)
    val veryLongMultiplier = scaledMultiplier(TimingSettings.VERY_LONG_WORD_MULTIPLIER)

    val lengthWeightedMs = baseDelayMs * (
        stats.shortWords * shortMultiplier +
        stats.mediumWords * mediumMultiplier +
        stats.longWords * longMultiplier +
        stats.veryLongWords * veryLongMultiplier
    )

    val punctMs = stats.commas.toLong() * settings.commaDelayMs +
            stats.periods.toLong() * settings.periodDelayMs +
            stats.paragraphs.toLong() * settings.paragraphDelayMs

    // Split chunks use a multiplier on their length-adjusted base delay
    val splitChunkExtraMs = (stats.splitChunks * baseDelayMs * (settings.splitChunkMultiplier - 1.0)).toLong()

    val totalMs = lengthWeightedMs + punctMs + splitChunkExtraMs
    val totalMinutes = totalMs / 60_000.0
    val effectiveWpm = (stats.wordCount * 60_000.0 / totalMs).roundToInt()

    val wordsRemaining = (stats.wordCount - wordsRead).coerceAtLeast(0)
    val fractionRemaining = if (stats.wordCount > 0) wordsRemaining.toDouble() / stats.wordCount else 0.0
    val minutesRemaining = totalMinutes * fractionRemaining

    return EffectiveWpm(
        wpm = effectiveWpm,
        totalMinutes = totalMinutes,
        minutesRemaining = minutesRemaining
    )
}

/**
 * Calculate effective WPM for chapter and book at given position.
 */
fun calculateEffectiveWpmInfo(
    book: Book,
    position: Position,
    settings: TimingSettings
): EffectiveWpmInfo {
    val chapter = book.chapters.getOrNull(position.chapterIndex)
    val chapterStats = chapter?.stats ?: ChapterStats.ZERO

    val chapterWordsRead = position.wordIndex

    val previousChaptersWords = book.chapters
        .take(position.chapterIndex)
        .sumOf { it.stats.wordCount }
    val bookWordsRead = previousChaptersWords + chapterWordsRead

    return EffectiveWpmInfo(
        chapter = calculateEffectiveWpm(chapterStats, settings, chapterWordsRead),
        book = calculateEffectiveWpm(book.stats.aggregated, settings, bookWordsRead)
    )
}

/**
 * Calculate delay for a specific word using sqrt(len/avgLen) formula.
 * This gives proportionally more time to longer words in a natural way.
 */
fun Word.delayMs(settings: TimingSettings): Long {
    val baseDelayMs = settings.baseDelayMs.toDouble()
    val scale = settings.lengthTimingScale.toDouble()

    // Calculate length multiplier using sqrt formula: sqrt(len / AVG_WORD_LENGTH)
    // Then scale by lengthTimingScale: 1 + scale * (sqrtMultiplier - 1)
    val wordLength = text.length.coerceAtLeast(1)
    val sqrtMultiplier = sqrt(wordLength.toDouble() / TimingSettings.AVG_WORD_LENGTH)
    val lengthMultiplier = 1.0 + scale * (sqrtMultiplier - 1.0)

    // Apply length multiplier to base delay
    var delayMs = baseDelayMs * lengthMultiplier

    // Apply split chunk multiplier (e.g., 1.3x = 30% longer)
    if (isSplitChunk) {
        delayMs *= settings.splitChunkMultiplier
    }

    // Add punctuation delay
    val punctExtra = when (followingPunct) {
        null -> 0
        Punctuation.COMMA -> settings.commaDelayMs
        Punctuation.PERIOD -> settings.periodDelayMs
        Punctuation.PARAGRAPH -> settings.paragraphDelayMs
    }

    return (delayMs + punctExtra).toLong()
}
