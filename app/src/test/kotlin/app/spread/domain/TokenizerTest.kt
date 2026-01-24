package app.spread.domain

import app.spread.domain.WordSplitConfig.MAX_CHUNK_CHARS
import app.spread.domain.WordSplitConfig.MIN_SPLIT_LENGTH
import org.junit.Assert.*
import org.junit.Test

class TokenizerTest {

    @Test
    fun `words under MIN_SPLIT_LENGTH are not split`() {
        // "understand" is 10 chars, MIN_SPLIT_LENGTH is 11
        val words = tokenize("understand")
        assertEquals(
            "Word under MIN_SPLIT_LENGTH ($MIN_SPLIT_LENGTH) should not be split",
            1, words.size
        )
        assertEquals("understand", words[0].text)
    }

    @Test
    fun `words at MIN_SPLIT_LENGTH are split`() {
        // "programming" is 11 chars, exactly at MIN_SPLIT_LENGTH
        val words = tokenize("programming")
        assertTrue("Word at MIN_SPLIT_LENGTH ($MIN_SPLIT_LENGTH) should be split", words.size > 1)
        // Verify all chunks are under MAX_CHUNK_CHARS
        words.forEach { word ->
            val cleanLen = word.text.filter { it.isLetterOrDigit() }.length
            assertTrue(
                "Chunk '${word.text}' ($cleanLen chars) exceeds MAX_CHUNK_CHARS ($MAX_CHUNK_CHARS)",
                cleanLen <= MAX_CHUNK_CHARS
            )
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
        assertEquals("inter-", words[0].text)
    }

    @Test
    fun `electroencephalography is split correctly`() {
        val words = tokenize("electroencephalography")
        assertTrue("Word should be split", words.size > 1)

        // Verify first chunk is "electro-" prefix
        assertEquals("electro-", words[0].text)

        // Verify all chunks fit on screen
        words.forEach { word ->
            val cleanLen = word.text.filter { it.isLetterOrDigit() }.length
            assertTrue(
                "Chunk '${word.text}' ($cleanLen chars) exceeds MAX_CHUNK_CHARS ($MAX_CHUNK_CHARS)",
                cleanLen <= MAX_CHUNK_CHARS
            )
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
        // "de" prefix is only 2 chars, below MIN_CHUNK_CHARS
        // Word gets chunked at MAX_CHUNK_CHARS instead
        val words = tokenize("deinstitutionalization")
        assertTrue("Word should be split", words.size > 1)

        words.forEach { word ->
            val cleanLen = word.text.filter { it.isLetterOrDigit() }.length
            assertTrue(
                "Chunk '${word.text}' ($cleanLen chars) exceeds MAX_CHUNK_CHARS ($MAX_CHUNK_CHARS)",
                cleanLen <= MAX_CHUNK_CHARS
            )
        }

        // Verify it reconstructs correctly
        val reconstructed = words.joinToString("") { it.text.replace("-", "") }
        assertEquals("deinstitutionalization", reconstructed)
    }

    @Test
    fun `words without affixes are chunked at MAX_CHUNK_CHARS`() {
        // Made-up long word with no recognizable affixes
        val words = tokenize("abcdefghijklmnopqrst") // 20 chars, no affixes
        assertTrue("Word should be split", words.size > 1)

        words.forEach { word ->
            val cleanLen = word.text.filter { it.isLetterOrDigit() }.length
            assertTrue(
                "Chunk '${word.text}' ($cleanLen chars) exceeds MAX_CHUNK_CHARS ($MAX_CHUNK_CHARS)",
                cleanLen <= MAX_CHUNK_CHARS
            )
        }
    }
}
