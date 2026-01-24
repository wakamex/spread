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
     * Calculate optimal font size to fit maxDisplayChars on the given screen width,
     * accounting for anchor position and ORP offset.
     *
     * @param screenWidthDp Screen width in dp
     * @param maxDisplayChars Maximum characters to fit (from settings)
     * @param anchorPosition Anchor position as fraction of screen width (default 0.42)
     * @return Font size in sp, scaled to fit configured chars
     */
    fun calculateFontSp(
        screenWidthDp: Float,
        maxDisplayChars: Int = TimingSettings.DEFAULT_MAX_DISPLAY_CHARS,
        anchorPosition: Float = TimingSettings.DEFAULT_ANCHOR_POSITION
    ): Float {
        // Calculate available space on each side of anchor
        val leftOfAnchorDp = (screenWidthDp * anchorPosition) - EDGE_PADDING_DP
        val rightOfAnchorDp = (screenWidthDp * (1 - anchorPosition)) - EDGE_PADDING_DP

        // Word extends: ORP_FRACTION left, (1 - ORP_FRACTION) right
        // Constraint: leftChars must fit in leftSpace, rightChars must fit in rightSpace
        // maxCharWidth = min(leftSpace / leftChars, rightSpace / rightChars)
        val leftChars = maxDisplayChars * ORP_FRACTION
        val rightChars = maxDisplayChars * (1 - ORP_FRACTION)

        val maxCharWidthFromLeft = if (leftChars > 0) leftOfAnchorDp / leftChars else Float.MAX_VALUE
        val maxCharWidthFromRight = if (rightChars > 0) rightOfAnchorDp / rightChars else Float.MAX_VALUE

        val requiredCharWidthDp = minOf(maxCharWidthFromLeft, maxCharWidthFromRight)

        // Scale font proportionally to char width
        val scaledFontSp = BASE_FONT_SP * (requiredCharWidthDp / BASE_CHAR_WIDTH_DP)

        // Cap at base size (don't make font larger than 48sp)
        return scaledFontSp.coerceAtMost(BASE_FONT_SP)
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

        val leftOfAnchorDp = (screenWidthDp * anchorPosition) - EDGE_PADDING_DP
        val rightOfAnchorDp = (screenWidthDp * (1 - anchorPosition)) - EDGE_PADDING_DP

        val leftFits = leftChars * charWidthDp <= leftOfAnchorDp
        val rightFits = rightChars * charWidthDp <= rightOfAnchorDp

        return leftFits && rightFits
    }
}
