package app.spread.domain

/**
 * Core domain types for Spread.
 * All types are immutable. No dependencies.
 */

@JvmInline
value class BookId(val value: String)

// --- Book Structure ---

data class Book(
    val id: BookId,
    val metadata: BookMetadata,
    val chapters: List<Chapter>,
    val stats: BookStats
)

data class BookMetadata(
    val title: String,
    val author: String?,
    val coverPath: String?
)

data class Chapter(
    val index: Int,
    val title: String,
    val words: List<Word>,
    val stats: ChapterStats
)

data class Word(
    val text: String,
    val lengthBucket: LengthBucket,
    val followingPunct: Punctuation?
) {
    /**
     * True if this word is a split chunk (has leading or trailing hyphen).
     * Split chunks get extra display time to help with word reconstruction.
     */
    val isSplitChunk: Boolean
        get() = text.startsWith("-") || (text.endsWith("-") && followingPunct == null)
}

enum class LengthBucket {
    SHORT,      // 1-4 chars
    MEDIUM,     // 5-8 chars
    LONG,       // 9-12 chars
    VERY_LONG;  // 13+ chars

    companion object {
        fun fromLength(len: Int): LengthBucket = when {
            len <= 4 -> SHORT
            len <= 8 -> MEDIUM
            len <= 12 -> LONG
            else -> VERY_LONG
        }
    }
}

enum class Punctuation {
    PERIOD,     // . ! ?
    COMMA,      // , ; :
    PARAGRAPH;  // paragraph break

    companion object {
        fun fromChar(c: Char): Punctuation? = when (c) {
            '.', '!', '?' -> PERIOD
            ',', ';', ':' -> COMMA
            else -> null
        }
    }
}

// --- Statistics (pre-computed for O(1) effective WPM) ---

data class ChapterStats(
    val wordCount: Int,
    val shortWords: Int,
    val mediumWords: Int,
    val longWords: Int,
    val veryLongWords: Int,
    val periods: Int,
    val commas: Int,
    val paragraphs: Int,
    val splitChunks: Int
) {
    operator fun plus(other: ChapterStats) = ChapterStats(
        wordCount = wordCount + other.wordCount,
        shortWords = shortWords + other.shortWords,
        mediumWords = mediumWords + other.mediumWords,
        longWords = longWords + other.longWords,
        veryLongWords = veryLongWords + other.veryLongWords,
        periods = periods + other.periods,
        commas = commas + other.commas,
        paragraphs = paragraphs + other.paragraphs,
        splitChunks = splitChunks + other.splitChunks
    )

    companion object {
        val ZERO = ChapterStats(0, 0, 0, 0, 0, 0, 0, 0, 0)

        fun fromWords(words: List<Word>): ChapterStats {
            var short = 0
            var medium = 0
            var long = 0
            var veryLong = 0
            var periods = 0
            var commas = 0
            var paragraphs = 0
            var splitChunks = 0

            for (word in words) {
                when (word.lengthBucket) {
                    LengthBucket.SHORT -> short++
                    LengthBucket.MEDIUM -> medium++
                    LengthBucket.LONG -> long++
                    LengthBucket.VERY_LONG -> veryLong++
                }
                when (word.followingPunct) {
                    Punctuation.PERIOD -> periods++
                    Punctuation.COMMA -> commas++
                    Punctuation.PARAGRAPH -> paragraphs++
                    null -> {}
                }
                if (word.isSplitChunk) splitChunks++
            }

            return ChapterStats(
                wordCount = words.size,
                shortWords = short,
                mediumWords = medium,
                longWords = long,
                veryLongWords = veryLong,
                periods = periods,
                commas = commas,
                paragraphs = paragraphs,
                splitChunks = splitChunks
            )
        }
    }
}

data class BookStats(
    val totalWords: Int,
    val chapterStats: List<ChapterStats>,
    val aggregated: ChapterStats
) {
    companion object {
        fun fromChapters(chapters: List<Chapter>): BookStats {
            val stats = chapters.map { it.stats }
            val aggregated = stats.fold(ChapterStats.ZERO) { acc, s -> acc + s }
            return BookStats(
                totalWords = aggregated.wordCount,
                chapterStats = stats,
                aggregated = aggregated
            )
        }
    }
}

// --- Position ---

data class Position(
    val chapterIndex: Int,
    val wordIndex: Int
) {
    companion object {
        val START = Position(0, 0)
    }
}
