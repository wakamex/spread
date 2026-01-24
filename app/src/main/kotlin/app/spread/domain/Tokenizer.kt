package app.spread.domain

/**
 * Text tokenization into Words with pre-computed metadata.
 * Pure functions - no side effects.
 *
 * Includes morpheme-based word splitting for long words (≥13 chars) to ensure
 * they fit on screen. Mirrors the Rust tokenizer logic.
 */

/**
 * Word splitting constants.
 * SYNC: These values must match rust/src/tokenizer.rs
 *
 * Strategy: Split at cognitive max (12 chars per research), then adjust
 * font size per orientation so chunks always fit on screen.
 */
object WordSplitConfig {
    /** Split words at or above this length */
    const val MIN_SPLIT_LENGTH = 11

    /**
     * Maximum alphanumeric characters per chunk.
     * With hyphens (up to 2), max display is 12 chars - fits 320dp screens.
     * Within research-backed visual span of 10-12 chars.
     * SYNC: Must match rust/src/tokenizer.rs MAX_CHUNK_CHARS
     */
    const val MAX_CHUNK_CHARS = 10

    /** Minimum chunk size to avoid tiny fragments */
    const val MIN_CHUNK_CHARS = 3
}

// Common prefixes sorted by length (longest first for greedy matching)
private val PREFIXES = listOf(
    "counter", "electro", "pseudo",
    "circum", "contra", "extra", "hetero", "homeo", "infra", "inter",
    "intra", "macro", "micro", "multi", "neuro", "patho", "photo",
    "pneumo", "proto", "psycho", "super", "trans", "ultra",
    "anti", "auto", "demi", "hemi", "homo", "hyper", "hypo", "mega",
    "meta", "mini", "mono", "omni", "para", "peri", "poly", "post",
    "semi", "tele", "vice",
    "bio", "dis", "eco", "geo", "mis", "neo", "non", "out", "pre",
    "pro", "sub", "tri",
    "co", "de", "en", "em", "ex", "im", "in", "ir", "re", "un"
)

// Common suffixes sorted by length (longest first)
private val SUFFIXES = listOf(
    "ological", "isation", "ization",
    "ability", "fulness", "ibility", "isation", "ization", "ousness",
    "ation", "ence", "ible", "ical", "ious", "itis", "ling", "ment",
    "ness", "ous", "ship", "sion", "tion", "tude", "ward",
    "able", "ally", "ence", "ency", "ful", "ian", "ing", "ion",
    "ism", "ist", "ity", "ive", "ize", "less", "like", "logy",
    "ory", "ure",
    "al", "an", "ar", "ed", "en", "er", "es", "ly", "or", "th", "ty"
)

/**
 * Split a long word into readable chunks at morpheme boundaries.
 * Returns list of chunks with hyphen markers for continuation.
 */
private fun splitWord(word: String): List<String> {
    // Extract leading/trailing punctuation
    val leadingPunct = word.takeWhile { !it.isLetterOrDigit() }
    val trailingPunct = word.takeLastWhile { !it.isLetterOrDigit() }
    val clean = word.drop(leadingPunct.length).dropLast(trailingPunct.length)

    if (clean.length < WordSplitConfig.MIN_SPLIT_LENGTH) {
        return listOf(word)
    }

    val lower = clean.lowercase()
    val chunks = mutableListOf<String>()

    // Find prefix
    var prefixLen = 0
    for (prefix in PREFIXES) {
        if (lower.startsWith(prefix) && prefix.length >= WordSplitConfig.MIN_CHUNK_CHARS) {
            prefixLen = prefix.length
            break
        }
    }

    // Find suffix
    var suffixLen = 0
    for (suffix in SUFFIXES) {
        if (lower.endsWith(suffix) && suffix.length >= WordSplitConfig.MIN_CHUNK_CHARS) {
            suffixLen = suffix.length
            break
        }
    }

    // Ensure middle isn't too small
    val middleLen = clean.length - prefixLen - suffixLen
    if (middleLen < WordSplitConfig.MIN_CHUNK_CHARS && prefixLen > 0 && suffixLen > 0) {
        // Sacrifice the shorter affix
        if (prefixLen <= suffixLen) prefixLen = 0 else suffixLen = 0
    }

    // Add prefix chunk
    if (prefixLen > 0) {
        val prefixText = (if (chunks.isEmpty()) leadingPunct else "") + clean.take(prefixLen) + "-"
        chunks.add(prefixText)
    }

    // Split middle into chunks
    val middle = clean.drop(prefixLen).dropLast(suffixLen)
    if (middle.isNotEmpty()) {
        var pos = 0
        while (pos < middle.length) {
            val isFirst = pos == 0 && prefixLen == 0
            val end = (pos + WordSplitConfig.MAX_CHUNK_CHARS).coerceAtMost(middle.length)
            val chunk = middle.substring(pos, end)

            val formatted = if (isFirst && pos + WordSplitConfig.MAX_CHUNK_CHARS >= middle.length && suffixLen == 0) {
                // Only chunk, use original punctuation
                leadingPunct + chunk + trailingPunct
            } else if (isFirst) {
                leadingPunct + chunk + "-"
            } else if (pos + WordSplitConfig.MAX_CHUNK_CHARS >= middle.length && suffixLen == 0) {
                "-" + chunk + trailingPunct
            } else {
                "-" + chunk + "-"
            }
            chunks.add(formatted)
            pos = end
        }
    }

    // Add suffix chunk
    if (suffixLen > 0) {
        val suffixText = "-" + clean.takeLast(suffixLen) + trailingPunct
        chunks.add(suffixText)
    }

    return if (chunks.isEmpty()) listOf(word) else chunks
}

/**
 * Tokenize raw text into Words with length buckets and punctuation info.
 * Long words (≥13 chars) are split at morpheme boundaries.
 */
fun tokenize(text: String): List<Word> {
    val words = mutableListOf<Word>()
    val rawWords = text.split(Regex("\\s+")).filter { it.isNotBlank() }

    for (raw in rawWords) {
        // Strip punctuation from word for length calculation
        val cleaned = raw.filter { it.isLetterOrDigit() || it == '\'' || it == '-' }
        if (cleaned.isEmpty()) continue

        // Split long words
        val chunks = splitWord(raw)

        for ((chunkIndex, chunk) in chunks.withIndex()) {
            val chunkCleaned = chunk.filter { it.isLetterOrDigit() || it == '\'' }
            val isLastChunk = chunkIndex == chunks.lastIndex

            // Determine following punctuation (only for last chunk)
            val lastChar = chunk.lastOrNull()
            val followingPunct = if (isLastChunk && lastChar != null) {
                Punctuation.fromChar(lastChar)
            } else {
                null
            }

            words.add(
                Word(
                    text = chunk,
                    lengthBucket = LengthBucket.fromLength(chunkCleaned.length),
                    followingPunct = followingPunct
                )
            )
        }
    }

    return words
}

/**
 * Tokenize paragraphs, marking paragraph breaks.
 */
fun tokenizeParagraphs(paragraphs: List<String>): List<Word> {
    val allWords = mutableListOf<Word>()

    for ((pIndex, paragraph) in paragraphs.withIndex()) {
        val words = tokenize(paragraph)
        if (words.isEmpty()) continue

        // Add all words except the last one as-is
        allWords.addAll(words.dropLast(1))

        // Mark the last word with paragraph punctuation (unless it already has stronger punct)
        val lastWord = words.last()
        val markedLast = if (pIndex < paragraphs.lastIndex && lastWord.followingPunct == null) {
            lastWord.copy(followingPunct = Punctuation.PARAGRAPH)
        } else {
            lastWord
        }
        allWords.add(markedLast)
    }

    return allWords
}

/**
 * Create a Chapter from title and paragraph text.
 */
fun createChapter(index: Int, title: String, paragraphs: List<String>): Chapter {
    val words = tokenizeParagraphs(paragraphs)
    return Chapter(
        index = index,
        title = title,
        words = words,
        stats = ChapterStats.fromWords(words)
    )
}

/**
 * Create a Book from parsed content.
 */
fun createBook(
    id: String,
    title: String,
    author: String?,
    chapters: List<Chapter>,
    coverPath: String? = null
): Book {
    return Book(
        id = BookId(id),
        metadata = BookMetadata(title, author, coverPath),
        chapters = chapters,
        stats = BookStats.fromChapters(chapters)
    )
}
