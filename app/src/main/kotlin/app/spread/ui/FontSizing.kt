package app.spread.ui

import app.spread.domain.TimingSettings

/**
 * Font size calculation for RSVP word display.
 *
 * Strategy: Calculate optimal font size to fit displayed text on screen,
 * accounting for off-center anchor position. With ORP at ~25% of word
 * and anchor at 42% of screen, the right side of the word extends further
 * than center-aligned text would.
 *
 * No minimum font size - user's maxDisplayChars setting takes priority.
 */
object FontSizing {
    /** Base font size when screen is wide enough */
    const val BASE_FONT_SP = 48f

    /** Character width at base font size (48sp monospace) */
    const val BASE_CHAR_WIDTH_DP = 29f

    /**
     * Edge padding buffer from screen edges.
     * Accounts for system insets and font metric variations.
     */
    const val EDGE_PADDING_DP = 16f

    /**
     * ORP position as fraction of word length.
     * For 12-char words, ORP is at index 3 = 25% into word.
     * This means 75% of the word extends RIGHT of the ORP.
     */
    private const val ORP_FRACTION = 0.25f

    /**
     * Calculate optimal font size using runtime-measured character width.
     * This provides accurate sizing across all devices regardless of font metrics.
     *
     * @param screenWidthDp Screen width in dp
     * @param measuredCharWidthDp Actual measured character width at BASE_FONT_SP
     * @param maxDisplayChars Maximum characters to fit (from settings)
     * @param anchorPosition Anchor position as fraction of content width (default 0.42)
     * @return Font size in sp, scaled to fit configured chars
     */
    fun calculateFontSpFromMeasured(
        screenWidthDp: Float,
        measuredCharWidthDp: Float,
        maxDisplayChars: Int = TimingSettings.DEFAULT_MAX_DISPLAY_CHARS,
        anchorPosition: Float = TimingSettings.DEFAULT_ANCHOR_POSITION
    ): Float {
        // Content width is screen minus edge padding on both sides
        val contentWidthDp = screenWidthDp - (EDGE_PADDING_DP * 2)

        // Anchor position is relative to content width
        val anchorFromLeftDp = contentWidthDp * anchorPosition
        val anchorFromRightDp = contentWidthDp * (1 - anchorPosition)

        // Word extends: ORP_FRACTION left, (1 - ORP_FRACTION) right
        val leftChars = maxDisplayChars * ORP_FRACTION
        val rightChars = maxDisplayChars * (1 - ORP_FRACTION)

        val maxCharWidthFromLeft = if (leftChars > 0) anchorFromLeftDp / leftChars else Float.MAX_VALUE
        val maxCharWidthFromRight = if (rightChars > 0) anchorFromRightDp / rightChars else Float.MAX_VALUE

        val requiredCharWidthDp = minOf(maxCharWidthFromLeft, maxCharWidthFromRight)

        // Scale font proportionally: if we need X dp per char but measured Y dp at base font,
        // then fontSize = baseFontSp * (X / Y)
        val scaledFontSp = BASE_FONT_SP * (requiredCharWidthDp / measuredCharWidthDp)

        // Cap at base size (don't make font larger than 48sp)
        return scaledFontSp.coerceAtMost(BASE_FONT_SP)
    }

    /**
     * Calculate optimal font size to fit maxDisplayChars on the given screen width,
     * using hardcoded BASE_CHAR_WIDTH_DP assumption. Used for tests and previews.
     *
     * @param screenWidthDp Screen width in dp
     * @param maxDisplayChars Maximum characters to fit (from settings)
     * @param anchorPosition Anchor position as fraction of content width (default 0.42)
     * @return Font size in sp, scaled to fit configured chars
     */
    fun calculateFontSp(
        screenWidthDp: Float,
        maxDisplayChars: Int = TimingSettings.DEFAULT_MAX_DISPLAY_CHARS,
        anchorPosition: Float = TimingSettings.DEFAULT_ANCHOR_POSITION
    ): Float {
        // Use hardcoded char width with safety margin for tests/previews
        return calculateFontSpFromMeasured(
            screenWidthDp = screenWidthDp,
            measuredCharWidthDp = BASE_CHAR_WIDTH_DP * 1.05f,  // 5% safety margin
            maxDisplayChars = maxDisplayChars,
            anchorPosition = anchorPosition
        )
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
     * Verify that maxDisplayChars fits on screen at calculated font size.
     * Returns true if text will fit without clipping.
     */
    fun verifyFit(
        screenWidthDp: Float,
        maxDisplayChars: Int = TimingSettings.DEFAULT_MAX_DISPLAY_CHARS,
        anchorPosition: Float = TimingSettings.DEFAULT_ANCHOR_POSITION
    ): Boolean {
        val fontSp = calculateFontSp(screenWidthDp, maxDisplayChars, anchorPosition)
        val charWidthDp = charWidthDpAtFontSp(fontSp)

        // Check both sides fit
        val leftChars = maxDisplayChars * ORP_FRACTION
        val rightChars = maxDisplayChars * (1 - ORP_FRACTION)

        // Content width is screen minus padding on both sides
        val contentWidthDp = screenWidthDp - (EDGE_PADDING_DP * 2)
        val anchorFromLeftDp = contentWidthDp * anchorPosition
        val anchorFromRightDp = contentWidthDp * (1 - anchorPosition)

        val leftFits = leftChars * charWidthDp <= anchorFromLeftDp
        val rightFits = rightChars * charWidthDp <= anchorFromRightDp

        return leftFits && rightFits
    }
}
