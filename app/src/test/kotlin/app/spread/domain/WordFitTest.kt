package app.spread.domain

import app.spread.domain.WordSplitConfig.MAX_CHUNK_CHARS
import app.spread.ui.FontSizing
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests that verify words fit on screen via font size adjustment.
 *
 * These tests verify the core invariant:
 * For any supported screen width, the calculated font size will
 * fit MAX_CHUNK_CHARS (12) characters without clipping.
 */
class WordFitTest {

    // Target device widths (dp) - covers real device range
    private val narrowPhoneWidthDp = 320f   // iPhone SE, small Androids
    private val mediumPhoneWidthDp = 360f   // Common Android phones
    private val pixel8ProPortraitDp = 336f  // User's device portrait
    private val pixel8ProLandscapeDp = 730f // User's device landscape
    private val tabletPortraitDp = 600f
    private val tabletLandscapeDp = 960f

    @Test
    fun `MAX_CHUNK_CHARS matches cognitive research limit`() {
        // Research.md says visual span is 10-12 chars
        // MAX_CHUNK_CHARS = 10 letters, + 2 hyphens = 12 display chars
        assertTrue("MAX_CHUNK_CHARS should be in cognitive range 10-12",
            MAX_CHUNK_CHARS in 10..12)
        assertEquals("MAX_CHUNK_CHARS should be 10", 10, MAX_CHUNK_CHARS)
        assertEquals("MAX_DISPLAY_CHARS should be 12", 12, FontSizing.MAX_DISPLAY_CHARS)
    }

    @Test
    fun `FontSizing constants are consistent`() {
        // Verify the base metrics make sense
        // At 48sp, 12 display chars (10 letters + 2 hyphens) should need about 348dp (12 * 29)
        val baseTextWidth = FontSizing.totalWidthDp(FontSizing.MAX_DISPLAY_CHARS, FontSizing.BASE_FONT_SP)
        assertEquals("12 display chars at 48sp should be ~348dp", 348f, baseTextWidth, 1f)
    }

    @Test
    fun `text fits on all supported screen widths`() {
        val screenWidths = listOf(
            narrowPhoneWidthDp to "narrow phone (320dp)",
            mediumPhoneWidthDp to "medium phone (360dp)",
            pixel8ProPortraitDp to "Pixel 8 Pro portrait (336dp)",
            pixel8ProLandscapeDp to "Pixel 8 Pro landscape (730dp)",
            tabletPortraitDp to "tablet portrait (600dp)",
            tabletLandscapeDp to "tablet landscape (960dp)"
        )

        val failures = mutableListOf<String>()

        for ((widthDp, name) in screenWidths) {
            if (!FontSizing.verifyFit(widthDp)) {
                val fontSp = FontSizing.calculateFontSp(widthDp)
                val textWidth = FontSizing.totalWidthDp(MAX_CHUNK_CHARS, fontSp)
                val available = widthDp - FontSizing.HORIZONTAL_PADDING_DP
                failures.add("$name: text=$textWidth dp, available=$available dp, font=$fontSp sp")
            }
        }

        if (failures.isNotEmpty()) {
            fail("Text does not fit on these screens:\n${failures.joinToString("\n")}")
        }
    }

    @Test
    fun `font size stays within readable range`() {
        val screenWidths = listOf(narrowPhoneWidthDp, mediumPhoneWidthDp, pixel8ProPortraitDp)

        for (widthDp in screenWidths) {
            val fontSp = FontSizing.calculateFontSp(widthDp)
            assertTrue(
                "Font size $fontSp sp for ${widthDp}dp screen should be >= ${FontSizing.MIN_FONT_SP}sp",
                fontSp >= FontSizing.MIN_FONT_SP
            )
            assertTrue(
                "Font size $fontSp sp should be <= ${FontSizing.BASE_FONT_SP}sp",
                fontSp <= FontSizing.BASE_FONT_SP
            )
        }
    }

    @Test
    fun `landscape uses full base font size`() {
        // Wide screens should use full 48sp
        val landscapeFontSp = FontSizing.calculateFontSp(pixel8ProLandscapeDp)
        assertEquals(
            "Landscape should use full base font size",
            FontSizing.BASE_FONT_SP,
            landscapeFontSp,
            0.1f
        )
    }

    @Test
    fun `narrow portrait reduces font proportionally`() {
        val narrowFontSp = FontSizing.calculateFontSp(narrowPhoneWidthDp)
        val reduction = FontSizing.BASE_FONT_SP - narrowFontSp

        // Expected: (320-16)/12 = 25.3dp per char, ratio = 25.3/29 = 0.87
        // Font = 48 * 0.87 ≈ 42sp, reduction ≈ 6sp
        assertTrue("Font reduction should be reasonable (<10sp)", reduction < 10f)
        assertTrue("Font reduction should be positive for narrow screen", reduction > 0f)

        println("Narrow screen (320dp): ${narrowFontSp}sp (reduction: ${reduction}sp)")
    }

    @Test
    fun `all tokenized chunks fit within MAX_CHUNK_CHARS`() {
        val longWords = listOf(
            "internationalization",
            "telecommunications",
            "deinstitutionalization",
            "counterrevolutionary",
            "electroencephalography"
        )

        val failures = mutableListOf<String>()

        for (word in longWords) {
            val chunks = tokenize(word)
            for (chunk in chunks) {
                val cleanLen = chunk.text.filter { it.isLetterOrDigit() }.length
                if (cleanLen > MAX_CHUNK_CHARS) {
                    failures.add("'${chunk.text}' from '$word' is $cleanLen chars")
                }
            }
        }

        if (failures.isNotEmpty()) {
            fail("Chunks exceed MAX_CHUNK_CHARS ($MAX_CHUNK_CHARS):\n${failures.joinToString("\n")}")
        }
    }

    @Test
    fun `verify the complete chain - tokenize then fit`() {
        // This test verifies the full invariant:
        // 1. Tokenizer produces chunks <= MAX_CHUNK_CHARS
        // 2. FontSizing calculates font that fits MAX_CHUNK_CHARS on screen
        // Therefore: all tokenized chunks fit on all screens

        val word = "counterrevolutionary"
        val chunks = tokenize(word)

        println("Testing: '$word'")
        println("Chunks: ${chunks.map { it.text }}")

        for (chunk in chunks) {
            val cleanLen = chunk.text.filter { it.isLetterOrDigit() }.length
            println("  '${chunk.text}' ($cleanLen chars)")

            // Verify chunk fits the cognitive limit
            assertTrue("Chunk exceeds MAX_CHUNK_CHARS", cleanLen <= MAX_CHUNK_CHARS)
        }

        // Verify font sizing works for narrowest screen
        assertTrue(
            "MAX_CHUNK_CHARS should fit on narrow screen",
            FontSizing.verifyFit(narrowPhoneWidthDp)
        )

        println("✓ All chunks fit on narrow screen (${narrowPhoneWidthDp}dp)")
    }
}
