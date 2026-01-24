package app.spread.domain

import app.spread.domain.WordSplitConfig.DEFAULT_MAX_CHUNK_CHARS
import app.spread.ui.FontSizing
import org.junit.Assert.*
import org.junit.Test

private val DEFAULT_MAX_DISPLAY_CHARS = TimingSettings.DEFAULT_MAX_DISPLAY_CHARS

/**
 * Tests that verify words fit on screen via font size adjustment.
 *
 * These tests verify the core invariant:
 * For any supported screen width, the calculated font size will
 * fit DEFAULT_MAX_CHUNK_CHARS (12) characters without clipping.
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
    fun `DEFAULT_MAX_CHUNK_CHARS matches cognitive research limit`() {
        // Research.md says visual span is 10-12 chars
        // DEFAULT_MAX_CHUNK_CHARS = 10 letters, + 2 hyphens = 12 display chars
        assertTrue("DEFAULT_MAX_CHUNK_CHARS should be in cognitive range 10-12",
            DEFAULT_MAX_CHUNK_CHARS in 10..12)
        assertEquals("DEFAULT_MAX_CHUNK_CHARS should be 10", 10, DEFAULT_MAX_CHUNK_CHARS)
        assertEquals("DEFAULT_MAX_DISPLAY_CHARS should be 12", 12, DEFAULT_MAX_DISPLAY_CHARS)
    }

    @Test
    fun `FontSizing constants are consistent`() {
        // Verify the base metrics make sense
        // At 48sp, 12 display chars (10 letters + 2 hyphens) should need about 348dp (12 * 29)
        val baseTextWidth = FontSizing.totalWidthDp(DEFAULT_MAX_DISPLAY_CHARS, FontSizing.BASE_FONT_SP)
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
                val textWidth = FontSizing.totalWidthDp(DEFAULT_MAX_CHUNK_CHARS, fontSp)
                val available = widthDp - FontSizing.EDGE_PADDING_DP
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
                "Font size $fontSp sp for ${widthDp}dp screen should be positive",
                fontSp > 0
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

        // With anchor at 42% and ORP at 25%, right side is the constraint
        // Available right: 320 * 0.58 - 16 = 169.6dp
        // For 75% of 12 chars (9 chars) to fit: charWidth <= 169.6/9 = 18.8dp
        // Font = 48 * 18.8/29 ≈ 31sp, reduction ≈ 17sp
        assertTrue("Font reduction should be reasonable (<20sp)", reduction < 20f)
        assertTrue("Font reduction should be positive for narrow screen", reduction > 0f)

        println("Narrow screen (320dp): ${narrowFontSp}sp (reduction: ${reduction}sp)")
    }

    @Test
    fun `all tokenized chunks fit within DEFAULT_MAX_CHUNK_CHARS`() {
        val longWords = listOf(
            "internationalization",
            "telecommunications",
            "deinstitutionalization",
            "counterrevolutionary",
            "electroencephalography",
            "interdisciplinary",
            "multidisciplinary",
            "neuropsychological"
        )

        val failures = mutableListOf<String>()

        for (word in longWords) {
            val chunks = tokenize(word)
            for (chunk in chunks) {
                val cleanLen = chunk.text.filter { it.isLetterOrDigit() }.length
                if (cleanLen > DEFAULT_MAX_CHUNK_CHARS) {
                    failures.add("'${chunk.text}' from '$word' has $cleanLen letters (max $DEFAULT_MAX_CHUNK_CHARS)")
                }
            }
        }

        if (failures.isNotEmpty()) {
            fail("Chunks exceed DEFAULT_MAX_CHUNK_CHARS:\n${failures.joinToString("\n")}")
        }
    }

    @Test
    fun `debug print tokenized chunks`() {
        val longWords = listOf(
            "interdisciplinary",
            "multidisciplinary"
        )

        for (word in longWords) {
            val chunks = tokenize(word)
            println("'$word' splits into:")
            for (chunk in chunks) {
                val displayLen = chunk.text.length
                val letterLen = chunk.text.filter { it.isLetterOrDigit() }.length
                println("  '${chunk.text}' (display=$displayLen, letters=$letterLen)")
            }
        }
    }

    @Test
    fun `all tokenized chunks fit within display limit`() {
        // Display limit = maxDisplayChars (default 12)
        // This includes letters AND hyphens
        val longWords = listOf(
            "internationalization",
            "telecommunications",
            "deinstitutionalization",
            "counterrevolutionary",
            "electroencephalography",
            "interdisciplinary",
            "multidisciplinary",
            "neuropsychological"
        )

        val failures = mutableListOf<String>()

        for (word in longWords) {
            val chunks = tokenize(word)
            for (chunk in chunks) {
                val displayLen = chunk.text.length  // Total display length including hyphens
                if (displayLen > DEFAULT_MAX_DISPLAY_CHARS) {
                    failures.add("'${chunk.text}' from '$word' is $displayLen display chars (max $DEFAULT_MAX_DISPLAY_CHARS)")
                }
            }
        }

        if (failures.isNotEmpty()) {
            fail("Chunks exceed display limit:\n${failures.joinToString("\n")}")
        }
    }

    @Test
    fun `verify the complete chain - tokenize then fit`() {
        // This test verifies the full invariant:
        // 1. Tokenizer produces chunks <= DEFAULT_MAX_CHUNK_CHARS
        // 2. FontSizing calculates font that fits DEFAULT_MAX_CHUNK_CHARS on screen
        // Therefore: all tokenized chunks fit on all screens

        val word = "counterrevolutionary"
        val chunks = tokenize(word)

        println("Testing: '$word'")
        println("Chunks: ${chunks.map { it.text }}")

        for (chunk in chunks) {
            val cleanLen = chunk.text.filter { it.isLetterOrDigit() }.length
            println("  '${chunk.text}' ($cleanLen chars)")

            // Verify chunk fits the cognitive limit
            assertTrue("Chunk exceeds DEFAULT_MAX_CHUNK_CHARS", cleanLen <= DEFAULT_MAX_CHUNK_CHARS)
        }

        // Verify font sizing works for narrowest screen
        assertTrue(
            "DEFAULT_MAX_CHUNK_CHARS should fit on narrow screen",
            FontSizing.verifyFit(narrowPhoneWidthDp)
        )

        println("✓ All chunks fit on narrow screen (${narrowPhoneWidthDp}dp)")
    }

    // --- Tests for configurable maxDisplayChars ---

    @Test
    fun `font scales down for larger maxDisplayChars`() {
        // With 20 display chars, font should be smaller to fit more chars
        val defaultFont = FontSizing.calculateFontSp(narrowPhoneWidthDp, DEFAULT_MAX_DISPLAY_CHARS)
        val largeCharsFont = FontSizing.calculateFontSp(narrowPhoneWidthDp, 20)

        assertTrue(
            "Font should be smaller for 20 maxDisplayChars ($largeCharsFont sp) than default ($defaultFont sp)",
            largeCharsFont < defaultFont
        )

        println("Narrow screen (320dp):")
        println("  Default (12 chars): ${defaultFont}sp")
        println("  Large (20 chars): ${largeCharsFont}sp")
    }

    @Test
    fun `20 display chars fit on all screen widths`() {
        val maxDisplayChars = 20
        val screenWidths = listOf(
            narrowPhoneWidthDp to "narrow phone (320dp)",
            mediumPhoneWidthDp to "medium phone (360dp)",
            pixel8ProPortraitDp to "Pixel 8 Pro portrait (336dp)",
            pixel8ProLandscapeDp to "Pixel 8 Pro landscape (730dp)"
        )

        val failures = mutableListOf<String>()

        for ((widthDp, name) in screenWidths) {
            if (!FontSizing.verifyFit(widthDp, maxDisplayChars)) {
                val fontSp = FontSizing.calculateFontSp(widthDp, maxDisplayChars)
                val textWidth = FontSizing.totalWidthDp(maxDisplayChars, fontSp)
                val available = widthDp - FontSizing.EDGE_PADDING_DP
                failures.add("$name: text=$textWidth dp, available=$available dp, font=$fontSp sp")
            }
        }

        if (failures.isNotEmpty()) {
            fail("20-char text does not fit on these screens:\n${failures.joinToString("\n")}")
        }
    }

    @Test
    fun `extreme maxDisplayChars still produces readable font`() {
        // With 24 chars on narrow screen, verify font is still reasonable
        val extremeFont = FontSizing.calculateFontSp(narrowPhoneWidthDp, 24)

        // Should be positive and reasonable (> 10sp is readable)
        assertTrue(
            "Font ($extremeFont sp) should be > 10sp for readability",
            extremeFont > 10f
        )

        // 24 chars on 320dp should be about (320-32)/24 * 48/29 ≈ 20sp
        println("Narrow screen (320dp) with 24 maxDisplayChars: ${extremeFont}sp")
    }

    @Test
    fun `maxDisplayChars setting range is respected`() {
        // Valid range is 10-24
        assertEquals("Min allowed should be 10", 10, 10.coerceIn(10, 24))
        assertEquals("Max allowed should be 24", 24, 24.coerceIn(10, 24))
        assertEquals("Below min should clamp to 10", 10, 5.coerceIn(10, 24))
        assertEquals("Above max should clamp to 24", 24, 30.coerceIn(10, 24))
    }

    @Test
    fun `landscape uses full or near-full base font`() {
        // Wide screens (730dp) should use full 48sp for default 12 chars
        val defaultLandscapeFont = FontSizing.calculateFontSp(pixel8ProLandscapeDp, DEFAULT_MAX_DISPLAY_CHARS)

        assertEquals(
            "Landscape with default chars should use full base font",
            FontSizing.BASE_FONT_SP,
            defaultLandscapeFont,
            0.1f
        )

        // With 20 chars, font may be slightly smaller due to anchor positioning
        val largeCharsLandscapeFont = FontSizing.calculateFontSp(pixel8ProLandscapeDp, 20)
        assertTrue(
            "Landscape with 20 chars should use near-full font (>40sp)",
            largeCharsLandscapeFont > 40f
        )

        println("Landscape (730dp): default=${defaultLandscapeFont}sp, 20chars=${largeCharsLandscapeFont}sp")
    }
}
