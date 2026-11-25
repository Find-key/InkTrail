package com.inktrail.reader.domain.model

/**
 * Four eye-care reading palettes supported by InkTrail.
 */
enum class ReaderTheme(
    val displayName: String,
    val backgroundHex: Long,
    val surfaceHex: Long,
    val primaryTextHex: Long,
    val secondaryTextHex: Long,
    val accentHex: Long,
    val isDark: Boolean
) {
    PAPER_WHITE(
        displayName = "纸白",
        backgroundHex = 0xFFFAF9F6L,
        surfaceHex = 0xFFF2F0EBL,
        primaryTextHex = 0xFF1F2328L,
        secondaryTextHex = 0xFF656D76L,
        accentHex = 0xFF3E6899L,
        isDark = false
    ),
    PARCHMENT(
        displayName = "羊皮纸",
        backgroundHex = 0xFFF4ECD8L,
        surfaceHex = 0xFFEAE0C8L,
        primaryTextHex = 0xFF3B2E1EL,
        secondaryTextHex = 0xFF73624CL,
        accentHex = 0xFF8C5327L,
        isDark = false
    ),
    EYE_CARE_GREEN(
        displayName = "护眼绿",
        backgroundHex = 0xFFCDE5D0L,
        surfaceHex = 0xFFBEDAC2L,
        primaryTextHex = 0xFF1B3322L,
        secondaryTextHex = 0xFF4B6952L,
        accentHex = 0xFF2B6E44L,
        isDark = false
    ),
    DEEP_NIGHT(
        displayName = "深邃夜",
        backgroundHex = 0xFF14181FL,
        surfaceHex = 0xFF1D232EL,
        primaryTextHex = 0xFFD5DCE6L,
        secondaryTextHex = 0xFF8A95A5L,
        accentHex = 0xFF74A5FFL,
        isDark = true
    );

    companion object {
        fun fromName(name: String?): ReaderTheme =
            entries.firstOrNull { it.name == name } ?: PAPER_WHITE
    }
}
