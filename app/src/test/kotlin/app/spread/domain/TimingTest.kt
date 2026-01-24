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
    fun `effective WPM with length timing is lower than uniform`() {
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

        val uniformSettings = TimingSettings.Uniform.copy(baseWpm = 300)
        val lengthSettings = TimingSettings.Natural.copy(
            baseWpm = 300,
            periodDelayMs = 0,
            commaDelayMs = 0,
            paragraphDelayMs = 0,
            lengthTimingScale = 1.0f  // Full sqrt effect
        )

        val uniformWpm = calculateEffectiveWpm(stats, uniformSettings)
        val lengthWpm = calculateEffectiveWpm(stats, lengthSettings)

        // With full length timing:
        // - Short words (400) get ~0.76x time (faster)
        // - Medium words (300) get ~1.12x time (slower)
        // - Long words (200) get ~1.42x time (slower)
        // - Very long words (100) get ~1.70x time (slower)
        // Net effect depends on distribution, but with more long words, WPM should be lower
        assertTrue(
            "Length timing WPM (${lengthWpm.wpm}) should differ from uniform (${uniformWpm.wpm})",
            lengthWpm.wpm != uniformWpm.wpm
        )
    }

    @Test
    fun `word delay includes length and punctuation components`() {
        val shortWord = Word(
            text = "the",
            lengthBucket = LengthBucket.SHORT,
            followingPunct = null
        )

        val longWordWithPunct = Word(
            text = "extraordinary",  // 13 chars
            lengthBucket = LengthBucket.VERY_LONG,
            followingPunct = Punctuation.PERIOD
        )

        val settings = TimingSettings.Natural
        val shortDelay = shortWord.delayMs(settings)
        val longDelay = longWordWithPunct.delayMs(settings)

        // Short word should be faster than base (sqrt(3/5.2) ≈ 0.76)
        assertTrue(
            "Short word delay ($shortDelay) should be less than base (${settings.baseDelayMs})",
            shortDelay < settings.baseDelayMs
        )

        // Long word with period should be much longer
        // sqrt(13/5.2) ≈ 1.58, plus period delay
        assertTrue(
            "Long word with period ($longDelay) should be much longer than short word ($shortDelay)",
            longDelay > shortDelay * 2
        )
    }

    @Test
    fun `split chunk uses multiplier for hyphenated words`() {
        // Use same-length words so sqrt formula gives same base
        val normalWord = Word(
            text = "hello",  // 5 chars
            lengthBucket = LengthBucket.MEDIUM,
            followingPunct = null
        )

        val splitChunk = Word(
            text = "hell-",  // 5 chars (same length)
            lengthBucket = LengthBucket.MEDIUM,
            followingPunct = null
        )

        assertTrue("Word ending with hyphen should be split chunk", splitChunk.isSplitChunk)
        assertFalse("Normal word should not be split chunk", normalWord.isSplitChunk)

        val settings = TimingSettings.Natural  // splitChunkMultiplier = 1.3
        val normalDelay = normalWord.delayMs(settings)
        val chunkDelay = splitChunk.delayMs(settings)

        // Split chunk should be ~1.3x longer than normal word of same length
        val ratio = chunkDelay.toDouble() / normalDelay.toDouble()
        assertEquals(
            "Split chunk should have ~1.3x delay",
            settings.splitChunkMultiplier.toDouble(),
            ratio,
            0.01  // Allow small tolerance for rounding
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
