package com.inktrail.reader.domain.repository

import com.inktrail.reader.domain.model.ReaderPreferences
import com.inktrail.reader.domain.model.ReaderTheme
import kotlinx.coroutines.flow.Flow

interface ReaderPreferencesRepository {
    val preferences: Flow<ReaderPreferences>

    suspend fun updateFontSize(fontSizeSp: Float)

    suspend fun updateLineHeight(multiplier: Float)

    suspend fun updateHorizontalMargin(marginDp: Int)

    suspend fun updateParagraphSpacing(spacingDp: Int)

    suspend fun updateTheme(theme: ReaderTheme)

    suspend fun updateCleanerEnabled(enabled: Boolean)

    suspend fun updateMergeBlankLines(enabled: Boolean)

    suspend fun updateFixHardLineBreaks(enabled: Boolean)

    suspend fun updateAddCjkSpacing(enabled: Boolean)

    suspend fun updateNormalizeParagraphIndent(enabled: Boolean)

    suspend fun updateFocusLineMode(enabled: Boolean)

    suspend fun updateSpoilerFreeMode(enabled: Boolean)
}
