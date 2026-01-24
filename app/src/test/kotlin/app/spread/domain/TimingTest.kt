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
            paragraphs = 20
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
            paragraphs = 20
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
            paragraphs = 45
        )

        val settings = TimingSettings(
            baseWpm = 300,
            periodDelayMs = 150,
            commaDelayMs = 75,
            paragraphDelayMs = 300,
            mediumWordExtraMs = 0,
            longWordExtraMs = 40,
            veryLongWordExtraMs = 60
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
    fun `minutes remaining decreases as words are read`() {
        val stats = ChapterStats(
            wordCount = 100,
            shortWords = 100,
            mediumWords = 0,
            longWords = 0,
            veryLongWords = 0,
            periods = 5,
            commas = 10,
            paragraphs = 3
        )

        val atStart = calculateEffectiveWpm(stats, TimingSettings.Uniform, wordsRead = 0)
        val atMiddle = calculateEffectiveWpm(stats, TimingSettings.Uniform, wordsRead = 50)
        val atEnd = calculateEffectiveWpm(stats, TimingSettings.Uniform, wordsRead = 100)

        assertTrue(atStart.minutesRemaining > atMiddle.minutesRemaining)
        assertTrue(atMiddle.minutesRemaining > atEnd.minutesRemaining)
        assertEquals(0.0, atEnd.minutesRemaining, 0.001)
    }
}
