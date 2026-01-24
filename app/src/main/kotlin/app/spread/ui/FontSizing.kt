package app.spread.ui

import app.spread.domain.WordSplitConfig

/**
 * Font size calculation for RSVP word display.
 *
 * Strategy: Calculate optimal font size to fit displayed text on screen.
 * Chunks may have MAX_CHUNK_CHARS letters plus up to 2 hyphens (leading/trailing).
 * This ensures consistent reading speed across orientations while maximizing
 * readability within screen constraints.
 */
object FontSizing {
    /** Base font size when screen is wide enough */
    const val BASE_FONT_SP = 48f

    /** Character width at base font size (48sp monospace) */
    const val BASE_CHAR_WIDTH_DP = 29f

    /** Horizontal padding from screen edges */
    const val HORIZONTAL_PADDING_DP = 16f

    /** Minimum acceptable font size for readability */
    const val MIN_FONT_SP = 36f

    /**
     * Maximum display characters: MAX_CHUNK_CHARS letters + 2 hyphens.
     * Chunks like "-revolution-" have leading and trailing hyphens.
     */
    const val MAX_DISPLAY_CHARS = WordSplitConfig.MAX_CHUNK_CHARS + 2

    /**
     * Calculate optimal font size to fit MAX_DISPLAY_CHARS on the given screen width.
     *
     * @param screenWidthDp Screen width in dp
     * @return Font size in sp, capped between MIN_FONT_SP and BASE_FONT_SP
     */
    fun calculateFontSp(screenWidthDp: Float): Float {
        val availableWidthDp = screenWidthDp - HORIZONTAL_PADDING_DP
        val requiredCharWidthDp = availableWidthDp / MAX_DISPLAY_CHARS

        // Scale font proportionally to char width
        val scaledFontSp = BASE_FONT_SP * (requiredCharWidthDp / BASE_CHAR_WIDTH_DP)

        // Cap at base size (don't make font larger than 48sp)
        // and ensure minimum readability
        return scaledFontSp.coerceIn(MIN_FONT_SP, BASE_FONT_SP)
    }

    /**
     * Calculate the actual character width at a given font size.
     * Used for verification in tests.
     */
    fun charWidthDpAtFontSp(fontSp: Float): Float {
        return BASE_CHAR_WIDTH_DP * (fontSp / BASE_FONT_SP)
    }

    /**
     * Calculate total width needed for N characters at a given font size.
     */
    fun totalWidthDp(charCount: Int, fontSp: Float): Float {
        return charCount * charWidthDpAtFontSp(fontSp)
    }

    /**
     * Verify that MAX_DISPLAY_CHARS fits on screen at calculated font size.
     * Returns true if text will fit without clipping.
     */
    fun verifyFit(screenWidthDp: Float): Boolean {
        val fontSp = calculateFontSp(screenWidthDp)
        val textWidthDp = totalWidthDp(MAX_DISPLAY_CHARS, fontSp)
        val availableWidthDp = screenWidthDp - HORIZONTAL_PADDING_DP
        return textWidthDp <= availableWidthDp
    }
}
