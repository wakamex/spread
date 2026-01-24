package app.spread.domain

import app.spread.domain.WordSplitConfig.MAX_CHUNK_CHARS
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests that verify words fit on screen without clipping.
 *
 * The core constraint: at 48sp font size with monospace font,
 * each character is approximately 28-30dp wide. The word is
 * center-locked on the ORP, so half extends left and half right.
 *
 * For a word to fit, its total width must be less than screen width.
 */
class WordFitTest {

    // Font metrics (approximate for 48sp monospace)
    private val charWidthDp = 29f  // Conservative estimate

    // Target device widths (dp)
    // Pixel 8 Pro: 1008px at ~3x density = 336dp
    private val pixel8ProWidthDp = 336f     // User's device
    private val narrowPhoneWidthDp = 320f   // iPhone SE, small Androids
    private val mediumPhoneWidthDp = 360f   // Common Android phones
    private val widePhoneWidthDp = 393f     // Pixel 5

    // Padding from edges (status bar safe area, etc)
    private val horizontalPaddingDp = 16f   // 8dp each side

    private fun maxCharsForWidth(screenWidthDp: Float): Int {
        val availableWidth = screenWidthDp - horizontalPaddingDp
        return (availableWidth / charWidthDp).toInt()
    }

    @Test
    fun `calculate max chars for different screen widths`() {
        println("Max chars for narrow phone (320dp): ${maxCharsForWidth(narrowPhoneWidthDp)}")
        println("Max chars for medium phone (360dp): ${maxCharsForWidth(mediumPhoneWidthDp)}")
        println("Max chars for wide phone (393dp): ${maxCharsForWidth(widePhoneWidthDp)}")

        // These are the actual limits:
        // 320dp -> 10 chars
        // 360dp -> 11 chars
        // 393dp -> 12 chars
    }

    @Test
    fun `MAX_CHUNK_CHARS fits on target devices`() {
        // Find actual MAX_CHUNK_CHARS by tokenizing a very long word
        val longWord = "abcdefghijklmnopqrstuvwxyz" // 26 chars
        val chunks = tokenize(longWord)

        val actualMaxChunk = chunks.maxOf { word ->
            word.text.filter { it.isLetterOrDigit() }.length
        }

        // Calculate what fits on each device
        val fitsPixel8Pro = maxCharsForWidth(pixel8ProWidthDp)
        val fitsNarrow = maxCharsForWidth(narrowPhoneWidthDp)

        // This assertion shows actual values when it fails
        assertEquals(
            "MAX_CHUNK_CHARS should fit on Pixel 8 Pro (336dp = $fitsPixel8Pro chars max) " +
            "and narrow phones (320dp = $fitsNarrow chars max)",
            fitsNarrow,  // expected: what fits on narrowest device
            actualMaxChunk  // actual: what tokenizer produces
        )
    }

    @Test
    fun `all demo book chunks fit on narrow phones`() {
        val maxCharsNarrow = maxCharsForWidth(narrowPhoneWidthDp)

        // These are the long words from the demo book
        val demoBookLongWords = listOf(
            "internationalization",
            "telecommunications",
            "infrastructure",
            "professionalization",
            "environmentalism",
            "conceptualization",
            "misunderstandings",
            "responsibilities",
            "deinstitutionalization",
            "counterrevolutionary",
            "incomprehensibilities",
            "compartmentalization",
            "interdisciplinary",
            "electroencephalography",
            "psychophysiological",
            "neuropsychological",
            "experimentation",
            "Characteristically",
            "overcompensation",
            "misrepresentation",
            "disproportionate",
            "counterproductivity",
            "organizational",
            "restructuring"
        )

        val failures = mutableListOf<String>()

        for (longWord in demoBookLongWords) {
            val chunks = tokenize(longWord)
            for (word in chunks) {
                val cleanLen = word.text.filter { it.isLetterOrDigit() }.length
                if (cleanLen > maxCharsNarrow) {
                    failures.add("'${word.text}' from '$longWord' is $cleanLen chars (max: $maxCharsNarrow)")
                }
            }
        }

        if (failures.isNotEmpty()) {
            fail("The following chunks exceed narrow screen width:\n${failures.joinToString("\n")}")
        }
    }

    @Test
    fun `tokenizer splits words to fit screen`() {
        // The word from the screenshot that was clipped: "counterrevolutionary"
        // The chunk "-revolution-" was 12 chars and got clipped

        val words = tokenize("counterrevolutionary")

        println("counterrevolutionary splits into:")
        words.forEach { println("  '${it.text}' (${it.text.filter { c -> c.isLetterOrDigit() }.length} chars)") }

        val maxCharsNarrow = maxCharsForWidth(narrowPhoneWidthDp)

        words.forEach { word ->
            val cleanLen = word.text.filter { it.isLetterOrDigit() }.length
            assertTrue(
                "Chunk '${word.text}' is $cleanLen chars but max for narrow screen is $maxCharsNarrow",
                cleanLen <= maxCharsNarrow
            )
        }
    }
}
