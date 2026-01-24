package app.spread.data

import app.spread.domain.*

/**
 * Native EPUB parser using Rust via JNI.
 */
object NativeParser {

    init {
        System.loadLibrary("spread_core")
    }

    /**
     * Parse an EPUB file from raw bytes.
     * Returns null on parse failure.
     */
    external fun parseEpub(data: ByteArray): NativeBook?

    /**
     * Get the native library version.
     */
    external fun getVersion(): String
}

// --- JNI data transfer objects ---
// These mirror the Rust structures and are converted to domain types

data class NativeBook(
    val metadata: NativeBookMetadata,
    val chapters: Array<NativeChapter>,
    val stats: NativeBookStats
)

data class NativeBookMetadata(
    val title: String,
    val author: String?
)

data class NativeChapter(
    val index: Int,
    val title: String,
    val words: Array<NativeWord>,
    val stats: NativeChapterStats
)

data class NativeWord(
    val text: String,
    val lengthBucket: Int,  // 0=short, 1=medium, 2=long, 3=veryLong
    val followingPunct: Int // 0=none, 1=comma, 2=period, 3=paragraph
)

data class NativeChapterStats(
    val wordCount: Int,
    val lengthCounts: IntArray,  // [short, medium, long, veryLong]
    val punctCounts: IntArray    // [none, comma, period, paragraph]
)

data class NativeBookStats(
    val totalWords: Int,
    val aggregated: NativeChapterStats
)

// --- Conversion to domain types ---

fun NativeBook.toDomain(id: String): Book {
    val domainChapters = chapters.map { it.toDomain() }
    return Book(
        id = BookId(id),
        metadata = BookMetadata(
            title = metadata.title,
            author = metadata.author,
            coverPath = null
        ),
        chapters = domainChapters,
        stats = BookStats.fromChapters(domainChapters)
    )
}

fun NativeChapter.toDomain(): Chapter {
    val domainWords = words.map { it.toDomain() }
    return Chapter(
        index = index,
        title = title,
        words = domainWords,
        stats = ChapterStats.fromWords(domainWords)
    )
}

fun NativeWord.toDomain(): Word {
    return Word(
        text = text,
        lengthBucket = when (lengthBucket) {
            0 -> LengthBucket.SHORT
            1 -> LengthBucket.MEDIUM
            2 -> LengthBucket.LONG
            else -> LengthBucket.VERY_LONG
        },
        followingPunct = when (followingPunct) {
            1 -> Punctuation.COMMA
            2 -> Punctuation.PERIOD
            3 -> Punctuation.PARAGRAPH
            else -> null
        }
    )
}
