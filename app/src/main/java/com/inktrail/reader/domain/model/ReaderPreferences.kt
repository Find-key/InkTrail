package com.inktrail.reader.domain.model

import com.inktrail.reader.engine.cleaner.TextCleanerOptions

/**
 * Persistent reading, typography, cleaner, and anti-spoiler preferences.
 */
data class ReaderPreferences(
    val fontSizeSp: Float = DEFAULT_FONT_SIZE_SP,
    val lineHeightMultiplier: Float = DEFAULT_LINE_HEIGHT_MULTIPLIER,
    val horizontalMarginDp: Int = DEFAULT_HORIZONTAL_MARGIN_DP,
    val paragraphSpacingDp: Int = DEFAULT_PARAGRAPH_SPACING_DP,
    val theme: ReaderTheme = ReaderTheme.PAPER_WHITE,
    val cleanerEnabled: Boolean = true,
    val mergeBlankLines: Boolean = true,
    val fixHardLineBreaks: Boolean = true,
    val addCjkSpacing: Boolean = true,
    val normalizeParagraphIndent: Boolean = false,
    val focusLineMode: Boolean = false,
    val spoilerFreeMode: Boolean = false
) {
    fun toTextCleanerOptions(): TextCleanerOptions = TextCleanerOptions(
        enabled = cleanerEnabled,
        mergeBlankLines = mergeBlankLines,
        fixHardLineBreaks = fixHardLineBreaks,
        addCjkSpacing = addCjkSpacing,
        normalizeParagraphIndent = normalizeParagraphIndent
    )

    companion object {
        const val DEFAULT_FONT_SIZE_SP = 18f
        const val MIN_FONT_SIZE_SP = 14f
        const val MAX_FONT_SIZE_SP = 30f

        const val DEFAULT_LINE_HEIGHT_MULTIPLIER = 1.6f
        const val MIN_LINE_HEIGHT_MULTIPLIER = 1.2f
        const val MAX_LINE_HEIGHT_MULTIPLIER = 2.4f

        const val DEFAULT_HORIZONTAL_MARGIN_DP = 20
        const val MIN_HORIZONTAL_MARGIN_DP = 8
        const val MAX_HORIZONTAL_MARGIN_DP = 40

        const val DEFAULT_PARAGRAPH_SPACING_DP = 10
        const val MIN_PARAGRAPH_SPACING_DP = 0
        const val MAX_PARAGRAPH_SPACING_DP = 24
    }
}
