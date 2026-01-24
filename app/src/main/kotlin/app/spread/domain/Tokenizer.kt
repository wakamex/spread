package app.spread.domain

/**
 * Text tokenization into Words with pre-computed metadata.
 * Pure functions - no side effects.
 */

/**
 * Tokenize raw text into Words with length buckets and punctuation info.
 */
fun tokenize(text: String): List<Word> {
    val words = mutableListOf<Word>()
    val rawWords = text.split(Regex("\\s+")).filter { it.isNotBlank() }

    for ((index, raw) in rawWords.withIndex()) {
        // Strip punctuation from word for length calculation
        val cleaned = raw.filter { it.isLetterOrDigit() || it == '\'' || it == '-' }
        if (cleaned.isEmpty()) continue

        // Determine following punctuation
        val lastChar = raw.lastOrNull()
        val followingPunct = when {
            lastChar != null -> Punctuation.fromChar(lastChar)
            else -> null
        }

        words.add(
            Word(
                text = raw,
                lengthBucket = LengthBucket.fromLength(cleaned.length),
                followingPunct = followingPunct
            )
        )
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
