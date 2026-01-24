package app.spread.domain

import kotlin.math.roundToInt

/**
 * Timing configuration and effective WPM calculation.
 * All functions are pure - no side effects.
 */

data class TimingSettings(
    val baseWpm: Int,
    val periodDelayMs: Int,
    val commaDelayMs: Int,
    val paragraphDelayMs: Int,
    val mediumWordExtraMs: Int,
    val longWordExtraMs: Int,
    val veryLongWordExtraMs: Int,
    /** Extra delay for split word chunks (words with hyphens from splitting) */
    val splitChunkExtraMs: Int,
    /**
     * Horizontal position of the ORP anchor as fraction of screen width.
     * 0.5 = center, 0.42 = left of center (recommended for ORP asymmetry).
     * Lower values give more room for the right side of words.
     */
    val anchorPositionPercent: Float
) {
    val baseDelayMs: Int get() = 60_000 / baseWpm

    companion object {
        /** Default anchor position: 42% from left (left of center) */
        const val DEFAULT_ANCHOR_POSITION = 0.42f

        val Uniform = TimingSettings(
            baseWpm = 300,
            periodDelayMs = 0,
            commaDelayMs = 0,
            paragraphDelayMs = 0,
            mediumWordExtraMs = 0,
            longWordExtraMs = 0,
            veryLongWordExtraMs = 0,
            splitChunkExtraMs = 0,
            anchorPositionPercent = DEFAULT_ANCHOR_POSITION
        )

        val Natural = TimingSettings(
            baseWpm = 300,
            periodDelayMs = 150,
            commaDelayMs = 75,
            paragraphDelayMs = 300,
            mediumWordExtraMs = 20,
            longWordExtraMs = 40,
            veryLongWordExtraMs = 60,
            splitChunkExtraMs = 50,
            anchorPositionPercent = DEFAULT_ANCHOR_POSITION
        )

        val Comprehension = TimingSettings(
            baseWpm = 250,
            periodDelayMs = 300,
            commaDelayMs = 150,
            paragraphDelayMs = 500,
            mediumWordExtraMs = 30,
            longWordExtraMs = 60,
            veryLongWordExtraMs = 100,
            splitChunkExtraMs = 80,
            anchorPositionPercent = DEFAULT_ANCHOR_POSITION
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

    val baseMs = stats.wordCount.toLong() * settings.baseDelayMs

    val lengthMs = stats.mediumWords.toLong() * settings.mediumWordExtraMs +
            stats.longWords.toLong() * settings.longWordExtraMs +
            stats.veryLongWords.toLong() * settings.veryLongWordExtraMs

    val punctMs = stats.commas.toLong() * settings.commaDelayMs +
            stats.periods.toLong() * settings.periodDelayMs +
            stats.paragraphs.toLong() * settings.paragraphDelayMs

    val splitChunkMs = stats.splitChunks.toLong() * settings.splitChunkExtraMs

    val totalMs = baseMs + lengthMs + punctMs + splitChunkMs
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
 * Calculate delay for a specific word.
 */
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

    val splitChunkExtra = if (isSplitChunk) settings.splitChunkExtraMs else 0

    return base + lengthExtra + punctExtra + splitChunkExtra
}
