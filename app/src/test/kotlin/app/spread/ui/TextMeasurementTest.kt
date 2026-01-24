package app.spread.ui

import android.graphics.Typeface
import android.text.TextPaint
import app.spread.domain.TimingSettings
import app.spread.domain.WordSplitConfig.DEFAULT_MAX_CHUNK_CHARS
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val DEFAULT_MAX_DISPLAY_CHARS = TimingSettings.DEFAULT_MAX_DISPLAY_CHARS

/**
 * Tests that verify text actually fits on screen using real Android text measurement.
 *
 * Uses Robolectric with native graphics to run Android's TextPaint.measureText() in unit tests.
 * This catches errors in our BASE_CHAR_WIDTH_DP assumption.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class TextMeasurementTest {

    private lateinit var paint: TextPaint

    // Screen configurations to test (width in dp)
    private val screenConfigs = listOf(
        320f to "narrow phone (320dp)",
        336f to "Pixel 8 Pro portrait (336dp)",
        360f to "medium phone (360dp)",
        393f to "Pixel 5 (393dp)",
        730f to "Pixel 8 Pro landscape (730dp)"
    )

    @Before
    fun setup() {
        paint = TextPaint().apply {
            typeface = Typeface.MONOSPACE
            isAntiAlias = true
        }
    }

    @Test
    fun `verify BASE_CHAR_WIDTH_DP assumption at 48sp`() {
        // This test validates our hardcoded constant
        paint.textSize = 48f * ROBOLECTRIC_DENSITY  // Convert sp to px

        val charWidth = paint.measureText("M")  // Measure single char
        val charWidthDp = charWidth / ROBOLECTRIC_DENSITY

        println("Measured char width at 48sp: ${charWidthDp}dp (assumed: ${FontSizing.BASE_CHAR_WIDTH_DP}dp)")

        // Allow 20% tolerance - fonts vary slightly
        val tolerance = FontSizing.BASE_CHAR_WIDTH_DP * 0.20f
        assertEquals(
            "Measured char width should be close to assumed BASE_CHAR_WIDTH_DP",
            FontSizing.BASE_CHAR_WIDTH_DP.toDouble(),
            charWidthDp.toDouble(),
            tolerance.toDouble()
        )
    }

    @Test
    fun `12 display chars fit on all screen widths at calculated font size`() {
        // Worst case: 10 letters + 2 hyphens = 12 display chars
        val testString = "-revolution-"  // 12 chars - DEFAULT_MAX_DISPLAY_CHARS
        assertEquals("Test string should be DEFAULT_MAX_DISPLAY_CHARS", DEFAULT_MAX_DISPLAY_CHARS, testString.length)

        val failures = mutableListOf<String>()

        for ((screenWidthDp, name) in screenConfigs) {
            val fontSp = FontSizing.calculateFontSp(screenWidthDp)
            paint.textSize = fontSp * ROBOLECTRIC_DENSITY  // Convert sp to px

            val textWidthPx = paint.measureText(testString)
            val textWidthDp = textWidthPx / ROBOLECTRIC_DENSITY

            val availableDp = screenWidthDp - FontSizing.EDGE_PADDING_DP

            println("$name: font=${fontSp}sp, text=${textWidthDp}dp, available=${availableDp}dp")

            if (textWidthDp > availableDp) {
                failures.add("$name: text (${textWidthDp}dp) > available (${availableDp}dp) at ${fontSp}sp")
            }
        }

        if (failures.isNotEmpty()) {
            fail("Text clips on these screens:\n${failures.joinToString("\n")}")
        }
    }

    @Test
    fun `worst case chunk fits on narrow screen`() {
        // Test with actual worst-case chunk from tokenizer
        // MAX_CHUNK_CHARS = 10, so max display = 12 with hyphens
        val worstCaseChunks = listOf(
            "understand",    // 10 chars, no hyphens (max letters)
            "-revolution-",  // 10 chars + 2 hyphens (max display)
            "counter-",      // 7 chars + hyphen
            "-ization"       // 7 chars + hyphen
        )

        val narrowScreenDp = 320f
        val fontSp = FontSizing.calculateFontSp(narrowScreenDp)
        paint.textSize = fontSp * ROBOLECTRIC_DENSITY

        val availableDp = narrowScreenDp - FontSizing.EDGE_PADDING_DP
        val failures = mutableListOf<String>()

        for (chunk in worstCaseChunks) {
            val textWidthPx = paint.measureText(chunk)
            val textWidthDp = textWidthPx / ROBOLECTRIC_DENSITY

            println("Chunk '$chunk': ${textWidthDp}dp (available: ${availableDp}dp)")

            if (textWidthDp > availableDp) {
                failures.add("'$chunk' (${textWidthDp}dp) > available (${availableDp}dp)")
            }
        }

        if (failures.isNotEmpty()) {
            fail("Chunks clip on narrow screen:\n${failures.joinToString("\n")}")
        }
    }

    @Test
    fun `font scaling is proportional`() {
        // Verify our assumption that font size scales linearly with char width
        val fontSizes = listOf(36f, 42f, 48f)
        val charWidths = mutableListOf<Pair<Float, Float>>()

        for (fontSp in fontSizes) {
            paint.textSize = fontSp * ROBOLECTRIC_DENSITY
            val charWidthPx = paint.measureText("M")
            val charWidthDp = charWidthPx / ROBOLECTRIC_DENSITY
            charWidths.add(fontSp to charWidthDp)
        }

        println("Font size vs char width:")
        charWidths.forEach { (font, width) ->
            println("  ${font}sp -> ${width}dp")
        }

        // Check proportionality: width should scale linearly with font size
        val ratio1 = charWidths[1].second / charWidths[0].second
        val expectedRatio1 = charWidths[1].first / charWidths[0].first

        val ratio2 = charWidths[2].second / charWidths[1].second
        val expectedRatio2 = charWidths[2].first / charWidths[1].first

        assertEquals("Font scaling should be linear (36->42)", expectedRatio1.toDouble(), ratio1.toDouble(), 0.05)
        assertEquals("Font scaling should be linear (42->48)", expectedRatio2.toDouble(), ratio2.toDouble(), 0.05)
    }

    @Test
    fun `measure actual tokenizer output`() {
        // Test real tokenizer output for the problematic word
        val chunks = app.spread.domain.tokenize("counterrevolutionary")

        val narrowScreenDp = 320f
        val fontSp = FontSizing.calculateFontSp(narrowScreenDp)
        paint.textSize = fontSp * ROBOLECTRIC_DENSITY

        val availableDp = narrowScreenDp - FontSizing.EDGE_PADDING_DP
        val failures = mutableListOf<String>()

        println("'counterrevolutionary' tokenized at ${fontSp}sp for ${narrowScreenDp}dp screen:")

        for (word in chunks) {
            val textWidthPx = paint.measureText(word.text)
            val textWidthDp = textWidthPx / ROBOLECTRIC_DENSITY
            val fits = if (textWidthDp <= availableDp) "✓" else "✗"

            println("  $fits '${word.text}' -> ${textWidthDp}dp (available: ${availableDp}dp)")

            if (textWidthDp > availableDp) {
                failures.add("'${word.text}' (${textWidthDp}dp) > available (${availableDp}dp)")
            }
        }

        if (failures.isNotEmpty()) {
            fail("Tokenizer output clips:\n${failures.joinToString("\n")}")
        }
    }

    companion object {
        // Robolectric default density (mdpi = 1.0)
        // We use this to convert between sp/dp and px
        const val ROBOLECTRIC_DENSITY = 1.0f
    }
}
