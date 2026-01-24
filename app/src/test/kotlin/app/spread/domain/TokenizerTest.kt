package app.spread.domain

import org.junit.Assert.*
import org.junit.Test

class TokenizerTest {

    @Test
    fun `short words are not split`() {
        val words = tokenize("presentation") // 12 chars - under threshold
        assertEquals(1, words.size)
        assertEquals("presentation", words[0].text)
    }

    @Test
    fun `13 char words are split`() {
        val words = tokenize("comprehension") // 13 chars - at threshold
        assertTrue("Word should be split", words.size > 1)
        // Verify all chunks are under max length
        words.forEach { word ->
            val cleanLen = word.text.filter { it.isLetterOrDigit() }.length
            assertTrue("Chunk '${word.text}' too long: $cleanLen chars", cleanLen <= 12)
        }
    }

    @Test
    fun `internationalization is split correctly`() {
        val words = tokenize("internationalization")
        assertTrue("Word should be split into multiple chunks", words.size > 1)

        // Reconstruct word from chunks (remove hyphens)
        val reconstructed = words.joinToString("") {
            it.text.replace("-", "")
        }
        assertEquals("internationalization", reconstructed)

        // First chunk should be prefix "inter-"
        assertTrue("First chunk should be 'inter-'", words[0].text == "inter-")
    }

    @Test
    fun `electroencephalography is split correctly`() {
        val words = tokenize("electroencephalography")
        assertTrue("Word should be split", words.size > 1)

        // Verify first chunk is "electro-" prefix
        assertEquals("electro-", words[0].text)

        // Verify all chunks fit on screen (≤12 letters)
        words.forEach { word ->
            val cleanLen = word.text.filter { it.isLetterOrDigit() }.length
            assertTrue("Chunk '${word.text}' too long", cleanLen <= 12)
        }
    }

    @Test
    fun `word with suffix is split correctly`() {
        val words = tokenize("professionalization")
        assertTrue("Word should be split", words.size > 1)

        // Last chunk should end with "-ization" suffix
        assertTrue("Last chunk should have -ization suffix",
            words.last().text.endsWith("ization"))
    }

    @Test
    fun `punctuation is preserved`() {
        val words = tokenize("internationalization.")
        assertTrue("Word should be split", words.size > 1)

        // Last chunk should preserve the period
        assertTrue("Period should be preserved", words.last().text.endsWith("."))
        assertEquals(Punctuation.PERIOD, words.last().followingPunct)
    }

    @Test
    fun `mixed sentence tokenizes correctly`() {
        val words = tokenize("The quick internationalization works")

        // "The" and "quick" and "works" should be single words
        // "internationalization" should be split
        assertTrue("Should have more than 4 tokens due to splitting", words.size > 4)

        // First word should be "The"
        assertEquals("The", words[0].text)
    }

    @Test
    fun `all chunks have valid length buckets`() {
        val words = tokenize("electroencephalography neuropsychological")

        words.forEach { word ->
            assertNotNull("Length bucket should not be null", word.lengthBucket)
        }
    }

    @Test
    fun `counter prefix is recognized`() {
        val words = tokenize("counterrevolutionary")
        assertTrue(words.size > 1)
        assertEquals("counter-", words[0].text)
    }

    @Test
    fun `deinstitutionalization is split correctly`() {
        // "de" prefix is only 2 chars, below MIN_CHUNK_CHARS (3)
        // Word gets chunked at 12 chars instead
        val words = tokenize("deinstitutionalization")
        assertTrue("Word should be split", words.size > 1)

        // All chunks should be ≤12 chars
        words.forEach { word ->
            val cleanLen = word.text.filter { it.isLetterOrDigit() }.length
            assertTrue("Chunk '${word.text}' too long", cleanLen <= 12)
        }

        // Verify it reconstructs correctly
        val reconstructed = words.joinToString("") { it.text.replace("-", "") }
        assertEquals("deinstitutionalization", reconstructed)
    }

    @Test
    fun `words without affixes are chunked at 12 chars`() {
        // Made-up long word with no recognizable affixes
        val words = tokenize("abcdefghijklmnopqrst") // 20 chars, no affixes
        assertTrue("Word should be split", words.size > 1)

        // All chunks should be ≤12 chars
        words.forEach { word ->
            val cleanLen = word.text.filter { it.isLetterOrDigit() }.length
            assertTrue("Chunk '${word.text}' exceeds 12 chars: $cleanLen", cleanLen <= 12)
        }
    }
}
