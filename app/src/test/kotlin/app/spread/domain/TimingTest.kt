package app.spread.domain

import org.junit.Assert.*
import org.junit.Test

class TimingTest {

    @Test
    fun `effective WPM equals base WPM with uniform settings`() {
        val stats = ChapterStats(
            wordCount = 1000,
            shortWords = 400,
            mediumWords = 300,
            longWords = 200,
            veryLongWords = 100,
            periods = 50,
            commas = 80,
            paragraphs = 20,
            splitChunks = 0
        )

        val result = calculateEffectiveWpm(stats, TimingSettings.Uniform)

        assertEquals(300, result.wpm)
    }

    @Test
    fun `effective WPM is lower with natural timing`() {
        val stats = ChapterStats(
            wordCount = 1000,
            shortWords = 400,
            mediumWords = 300,
            longWords = 200,
            veryLongWords = 100,
            periods = 50,
            commas = 80,
            paragraphs = 20,
            splitChunks = 50
        )

        val result = calculateEffectiveWpm(stats, TimingSettings.Natural)

        assertTrue("Effective WPM should be less than base WPM", result.wpm < 300)
        assertTrue("Effective WPM should be reasonable", result.wpm > 200)
    }

    @Test
    fun `effective WPM calculation is consistent`() {
        val stats = ChapterStats(
            wordCount = 5000,
            shortWords = 3200,
            mediumWords = 1200,
            longWords = 500,
            veryLongWords = 100,
            periods = 180,
            commas = 220,
            paragraphs = 45,
            splitChunks = 0
        )

        val settings = TimingSettings(
            baseWpm = 300,
            periodDelayMs = 150,
            commaDelayMs = 75,
            paragraphDelayMs = 300,
            mediumWordExtraMs = 0,
            longWordExtraMs = 40,
            veryLongWordExtraMs = 60,
            splitChunkMultiplier = 1.0f,
            anchorPositionPercent = 0.5f,
            verticalPositionPortrait = 0.22f,
            verticalPositionLandscape = 0.38f,
            maxDisplayChars = TimingSettings.DEFAULT_MAX_DISPLAY_CHARS
        )

        val result = calculateEffectiveWpm(stats, settings)

        // Expected calculation from PRD:
        // Base: 5000 * 200ms = 1,000,000ms
        // Length: 500 * 40 + 100 * 60 = 26,000ms
        // Punct: 180 * 150 + 220 * 75 + 45 * 300 = 57,000ms
        // Total: 1,083,000ms
        // WPM: 5000 / (1,083,000 / 60,000) = 277
        assertEquals(277, result.wpm)
    }

    @Test
    fun `word delay includes all components`() {
        val word = Word(
            text = "extraordinary!",
            lengthBucket = LengthBucket.VERY_LONG,
            followingPunct = Punctuation.PERIOD
        )

        val settings = TimingSettings.Natural
        val delay = word.delayMs(settings)

        val expected = settings.baseDelayMs +
                settings.veryLongWordExtraMs +
                settings.periodDelayMs

        assertEquals(expected.toLong(), delay)
    }

    @Test
    fun `split chunk uses multiplier for hyphenated words`() {
        val normalWord = Word(
            text = "hello",
            lengthBucket = LengthBucket.MEDIUM,
            followingPunct = null
        )

        val splitChunk = Word(
            text = "inter-",
            lengthBucket = LengthBucket.MEDIUM,
            followingPunct = null
        )

        assertTrue("Word ending with hyphen should be split chunk", splitChunk.isSplitChunk)
        assertFalse("Normal word should not be split chunk", normalWord.isSplitChunk)

        val settings = TimingSettings.Natural  // splitChunkMultiplier = 1.3
        val normalDelay = normalWord.delayMs(settings)
        val chunkDelay = splitChunk.delayMs(settings)

        // Split chunk base delay should be multiplied by 1.3
        // Both words have same length bucket, so difference is only the multiplier on base
        val expectedChunkBase = (settings.baseDelayMs * settings.splitChunkMultiplier).toLong()
        val expectedNormalBase = settings.baseDelayMs.toLong()

        assertEquals(
            "Split chunk should have multiplied base delay",
            expectedChunkBase - expectedNormalBase,
            chunkDelay - normalDelay
        )
    }

    @Test
    fun `split chunk multiplier scales with WPM`() {
        val splitChunk = Word(
            text = "inter-",
            lengthBucket = LengthBucket.SHORT,
            followingPunct = null
        )

        val slowSettings = TimingSettings.Natural.copy(baseWpm = 200)  // 300ms base
        val fastSettings = TimingSettings.Natural.copy(baseWpm = 600)  // 100ms base

        val slowDelay = splitChunk.delayMs(slowSettings)
        val fastDelay = splitChunk.delayMs(fastSettings)

        // At 200 WPM (300ms base), 1.3x = 390ms
        // At 600 WPM (100ms base), 1.3x = 130ms
        // Ratio should be 3:1 (same as base WPM ratio)
        val ratio = slowDelay.toDouble() / fastDelay.toDouble()
        assertEquals("Multiplier should scale proportionally with WPM", 3.0, ratio, 0.1)
    }

    @Test
    fun `split chunks are counted in effective WPM`() {
        val statsNoChunks = ChapterStats(
            wordCount = 100,
            shortWords = 100,
            mediumWords = 0,
            longWords = 0,
            veryLongWords = 0,
            periods = 0,
            commas = 0,
            paragraphs = 0,
            splitChunks = 0
        )

        val statsWithChunks = ChapterStats(
            wordCount = 100,
            shortWords = 100,
            mediumWords = 0,
            longWords = 0,
            veryLongWords = 0,
            periods = 0,
            commas = 0,
            paragraphs = 0,
            splitChunks = 20  // 20% of words are split chunks
        )

        val settings = TimingSettings.Natural  // splitChunkMultiplier = 1.3

        val wpmNoChunks = calculateEffectiveWpm(statsNoChunks, settings)
        val wpmWithChunks = calculateEffectiveWpm(statsWithChunks, settings)

        assertTrue(
            "WPM with split chunks (${wpmWithChunks.wpm}) should be lower than without (${wpmNoChunks.wpm})",
            wpmWithChunks.wpm < wpmNoChunks.wpm
        )
    }

    @Test
    fun `minutes remaining decreases as words are read`() {
        val stats = ChapterStats(
            wordCount = 100,
            shortWords = 100,
            mediumWords = 0,
            longWords = 0,
            veryLongWords = 0,
            periods = 5,
            commas = 10,
            paragraphs = 3,
            splitChunks = 0
        )

        val atStart = calculateEffectiveWpm(stats, TimingSettings.Uniform, wordsRead = 0)
        val atMiddle = calculateEffectiveWpm(stats, TimingSettings.Uniform, wordsRead = 50)
        val atEnd = calculateEffectiveWpm(stats, TimingSettings.Uniform, wordsRead = 100)

        assertTrue(atStart.minutesRemaining > atMiddle.minutesRemaining)
        assertTrue(atMiddle.minutesRemaining > atEnd.minutesRemaining)
        assertEquals(0.0, atEnd.minutesRemaining, 0.001)
    }
}
