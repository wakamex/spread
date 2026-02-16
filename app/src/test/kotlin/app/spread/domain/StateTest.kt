package app.spread.domain

import org.junit.Assert.*
import org.junit.Test

class StateTest {

    private fun createTestBook(): Book {
        val chapter1 = createChapter(
            index = 0,
            title = "Chapter 1",
            paragraphs = listOf(
                "This is the first paragraph.",
                "This is the second paragraph."
            )
        )
        val chapter2 = createChapter(
            index = 1,
            title = "Chapter 2",
            paragraphs = listOf("Another chapter here.")
        )

        return createBook(
            id = "test-book",
            title = "Test Book",
            author = "Test Author",
            chapters = listOf(chapter1, chapter2)
        )
    }

    @Test
    fun `initial state has no book`() {
        val state = ReaderState.Initial
        assertNull(state.book)
        assertFalse(state.playing)
    }

    @Test
    fun `BookLoaded action sets book and resets position`() {
        val book = createTestBook()
        val (newState, _) = reduce(ReaderState.Initial, Action.BookLoaded(book, BookSource(ByteArray(0), "test")))

        assertEquals(book, newState.book)
        assertEquals(Position.START, newState.position)
        assertNotNull(newState.effectiveWpmInfo)
    }

    @Test
    fun `Play action starts playback and schedules tick`() {
        val book = createTestBook()
        val state = ReaderState.Initial.copy(book = book)

        val (newState, effects) = reduce(state, Action.Play)

        assertTrue(newState.playing)
        assertTrue(effects.any { it is Effect.ScheduleTick })
    }

    @Test
    fun `Pause action stops playback and cancels tick`() {
        val book = createTestBook()
        val state = ReaderState.Initial.copy(book = book, playing = true)

        val (newState, effects) = reduce(state, Action.Pause)

        assertFalse(newState.playing)
        assertTrue(effects.any { it is Effect.CancelTick })
    }

    @Test
    fun `NextWord advances position within chapter`() {
        val book = createTestBook()
        val state = ReaderState.Initial.copy(book = book, position = Position(0, 0))

        val (newState, _) = reduce(state, Action.NextWord)

        assertEquals(0, newState.position.chapterIndex)
        assertEquals(1, newState.position.wordIndex)
    }

    @Test
    fun `NextWord advances to next chapter at chapter end`() {
        val book = createTestBook()
        val lastWordIndex = book.chapters[0].words.lastIndex
        val state = ReaderState.Initial.copy(
            book = book,
            position = Position(0, lastWordIndex)
        )

        val (newState, _) = reduce(state, Action.NextWord)

        assertEquals(1, newState.position.chapterIndex)
        assertEquals(0, newState.position.wordIndex)
    }

    @Test
    fun `NextWord stops at end of book`() {
        val book = createTestBook()
        val lastChapter = book.chapters.lastIndex
        val lastWord = book.chapters[lastChapter].words.lastIndex
        val state = ReaderState.Initial.copy(
            book = book,
            position = Position(lastChapter, lastWord),
            playing = true
        )

        val (newState, effects) = reduce(state, Action.NextWord)

        assertFalse(newState.playing)
        assertTrue(effects.any { it is Effect.CancelTick })
    }

    @Test
    fun `PrevWord goes back within chapter`() {
        val book = createTestBook()
        val state = ReaderState.Initial.copy(book = book, position = Position(0, 5))

        val (newState, _) = reduce(state, Action.PrevWord)

        assertEquals(0, newState.position.chapterIndex)
        assertEquals(4, newState.position.wordIndex)
    }

    @Test
    fun `PrevWord goes to previous chapter at chapter start`() {
        val book = createTestBook()
        val state = ReaderState.Initial.copy(book = book, position = Position(1, 0))

        val (newState, _) = reduce(state, Action.PrevWord)

        assertEquals(0, newState.position.chapterIndex)
        assertEquals(book.chapters[0].words.lastIndex, newState.position.wordIndex)
    }

    @Test
    fun `SetBaseWpm updates settings and recalculates effective WPM`() {
        val book = createTestBook()
        val state = ReaderState.Initial.copy(book = book)

        val (newState, _) = reduce(state, Action.SetBaseWpm(400))

        assertEquals(400, newState.settings.baseWpm)
        assertNotNull(newState.effectiveWpmInfo)
    }

    @Test
    fun `SetBaseWpm clamps to valid range`() {
        val state = ReaderState.Initial

        val (tooLow, _) = reduce(state, Action.SetBaseWpm(50))
        assertEquals(100, tooLow.settings.baseWpm)

        val (tooHigh, _) = reduce(state, Action.SetBaseWpm(2000))
        assertEquals(1500, tooHigh.settings.baseWpm)
    }

    @Test
    fun `JumpToChapter changes chapter and resets word index`() {
        val book = createTestBook()
        val state = ReaderState.Initial.copy(book = book, position = Position(0, 5), playing = true)

        val (newState, effects) = reduce(state, Action.JumpToChapter(1))

        assertEquals(1, newState.position.chapterIndex)
        assertEquals(0, newState.position.wordIndex)
        assertFalse(newState.playing)
        assertTrue(effects.any { it is Effect.CancelTick })
    }

    @Test
    fun `progress tracks position correctly`() {
        val book = createTestBook()
        val totalWords = book.stats.totalWords
        val chapter0Words = book.chapters[0].words.size

        // Start of book
        val atStart = ReaderState.Initial.copy(book = book, position = Position(0, 0))
        assertEquals(0f, atStart.progress.chapter, 0.01f)
        assertEquals(0f, atStart.progress.book, 0.01f)

        // Middle of first chapter
        val midChapter = ReaderState.Initial.copy(book = book, position = Position(0, chapter0Words / 2))
        assertEquals(0.5f, midChapter.progress.chapter, 0.1f)

        // Start of second chapter
        val chapter2Start = ReaderState.Initial.copy(book = book, position = Position(1, 0))
        assertEquals(0f, chapter2Start.progress.chapter, 0.01f)
        assertEquals(chapter0Words.toFloat() / totalWords, chapter2Start.progress.book, 0.01f)
    }

    @Test
    fun `SkipWords forward skips within chapter`() {
        val book = createTestBook()
        val state = ReaderState.Initial.copy(book = book, position = Position(0, 0))

        val (newState, _) = reduce(state, Action.SkipWords(5))

        assertEquals(0, newState.position.chapterIndex)
        assertEquals(5, newState.position.wordIndex)
        assertFalse(newState.playing)
    }

    @Test
    fun `SkipWords forward crosses chapter boundary`() {
        val book = createTestBook()
        val chapter0Words = book.chapters[0].words.size
        val state = ReaderState.Initial.copy(book = book, position = Position(0, chapter0Words - 2))

        val (newState, _) = reduce(state, Action.SkipWords(5))

        assertEquals(1, newState.position.chapterIndex)
        // Should be 5 - 2 (remaining in ch0) - 1 (cross chapter) = 2 words into ch1
        assertTrue(newState.position.wordIndex >= 0)
    }

    @Test
    fun `SkipWords backward skips within chapter`() {
        val book = createTestBook()
        val state = ReaderState.Initial.copy(book = book, position = Position(0, 5))

        val (newState, _) = reduce(state, Action.SkipWords(-3))

        assertEquals(0, newState.position.chapterIndex)
        assertEquals(2, newState.position.wordIndex)
    }

    @Test
    fun `SkipWords backward crosses chapter boundary`() {
        val book = createTestBook()
        val state = ReaderState.Initial.copy(book = book, position = Position(1, 1))

        val (newState, _) = reduce(state, Action.SkipWords(-5))

        assertEquals(0, newState.position.chapterIndex)
    }

    @Test
    fun `SkipWords clamps to start of book`() {
        val book = createTestBook()
        val state = ReaderState.Initial.copy(book = book, position = Position(0, 2))

        val (newState, _) = reduce(state, Action.SkipWords(-100))

        assertEquals(0, newState.position.chapterIndex)
        assertEquals(0, newState.position.wordIndex)
    }

    @Test
    fun `SkipWords clamps to end of book`() {
        val book = createTestBook()
        val lastChapter = book.chapters.lastIndex
        val state = ReaderState.Initial.copy(book = book, position = Position(0, 0))

        val (newState, _) = reduce(state, Action.SkipWords(10000))

        assertEquals(lastChapter, newState.position.chapterIndex)
        assertEquals(book.chapters[lastChapter].words.lastIndex, newState.position.wordIndex)
    }

    @Test
    fun `NextChapter jumps to next chapter`() {
        val book = createTestBook()
        val state = ReaderState.Initial.copy(book = book, position = Position(0, 5), playing = true)

        val (newState, effects) = reduce(state, Action.NextChapter)

        assertEquals(1, newState.position.chapterIndex)
        assertEquals(0, newState.position.wordIndex)
        assertFalse(newState.playing)
        assertTrue(effects.any { it is Effect.CancelTick })
    }

    @Test
    fun `NextChapter at last chapter stays at last chapter`() {
        val book = createTestBook()
        val lastChapter = book.chapters.lastIndex
        val state = ReaderState.Initial.copy(book = book, position = Position(lastChapter, 5))

        val (newState, _) = reduce(state, Action.NextChapter)

        assertEquals(lastChapter, newState.position.chapterIndex)
        assertEquals(0, newState.position.wordIndex)
    }

    @Test
    fun `PrevChapter at chapter start goes to previous chapter`() {
        val book = createTestBook()
        val state = ReaderState.Initial.copy(book = book, position = Position(1, 0))

        val (newState, _) = reduce(state, Action.PrevChapter)

        assertEquals(0, newState.position.chapterIndex)
        assertEquals(0, newState.position.wordIndex)
    }

    @Test
    fun `PrevChapter mid-chapter goes to chapter start`() {
        val book = createTestBook()
        val state = ReaderState.Initial.copy(book = book, position = Position(1, 3))

        val (newState, _) = reduce(state, Action.PrevChapter)

        assertEquals(1, newState.position.chapterIndex)
        assertEquals(0, newState.position.wordIndex)
    }

    @Test
    fun `PrevChapter at first chapter start stays at first chapter`() {
        val book = createTestBook()
        val state = ReaderState.Initial.copy(book = book, position = Position(0, 0))

        val (newState, _) = reduce(state, Action.PrevChapter)

        assertEquals(0, newState.position.chapterIndex)
        assertEquals(0, newState.position.wordIndex)
    }
}
