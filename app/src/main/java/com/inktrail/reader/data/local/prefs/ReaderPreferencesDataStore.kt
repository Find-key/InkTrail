package com.inktrail.reader.data.local.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.inktrail.reader.domain.model.ReaderPreferences
import com.inktrail.reader.domain.model.ReaderTheme
import com.inktrail.reader.domain.repository.ReaderPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReaderPreferencesDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : ReaderPreferencesRepository {

    override val preferences: Flow<ReaderPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            ReaderPreferences(
                fontSizeSp = (prefs[Keys.FONT_SIZE_SP] ?: ReaderPreferences.DEFAULT_FONT_SIZE_SP)
                    .coerceIn(ReaderPreferences.MIN_FONT_SIZE_SP, ReaderPreferences.MAX_FONT_SIZE_SP),
                lineHeightMultiplier = (prefs[Keys.LINE_HEIGHT_MULTIPLIER]
                    ?: ReaderPreferences.DEFAULT_LINE_HEIGHT_MULTIPLIER)
                    .coerceIn(
                        ReaderPreferences.MIN_LINE_HEIGHT_MULTIPLIER,
                        ReaderPreferences.MAX_LINE_HEIGHT_MULTIPLIER
                    ),
                horizontalMarginDp = (prefs[Keys.HORIZONTAL_MARGIN_DP]
                    ?: ReaderPreferences.DEFAULT_HORIZONTAL_MARGIN_DP)
                    .coerceIn(
                        ReaderPreferences.MIN_HORIZONTAL_MARGIN_DP,
                        ReaderPreferences.MAX_HORIZONTAL_MARGIN_DP
                    ),
                paragraphSpacingDp = (prefs[Keys.PARAGRAPH_SPACING_DP]
                    ?: ReaderPreferences.DEFAULT_PARAGRAPH_SPACING_DP)
                    .coerceIn(
                        ReaderPreferences.MIN_PARAGRAPH_SPACING_DP,
                        ReaderPreferences.MAX_PARAGRAPH_SPACING_DP
                    ),
                theme = ReaderTheme.fromName(prefs[Keys.THEME_NAME]),
                cleanerEnabled = prefs[Keys.CLEANER_ENABLED] ?: true,
                mergeBlankLines = prefs[Keys.MERGE_BLANK_LINES] ?: true,
                fixHardLineBreaks = prefs[Keys.FIX_HARD_LINE_BREAKS] ?: true,
                addCjkSpacing = prefs[Keys.ADD_CJK_SPACING] ?: true,
                normalizeParagraphIndent = prefs[Keys.NORMALIZE_PARAGRAPH_INDENT] ?: false,
                focusLineMode = prefs[Keys.FOCUS_LINE_MODE] ?: false,
                spoilerFreeMode = prefs[Keys.SPOILER_FREE_MODE] ?: false
            )
        }

    override suspend fun updateFontSize(fontSizeSp: Float) {
        val clamped = fontSizeSp.coerceIn(
            ReaderPreferences.MIN_FONT_SIZE_SP,
            ReaderPreferences.MAX_FONT_SIZE_SP
        )
        dataStore.edit { it[Keys.FONT_SIZE_SP] = clamped }
    }

    override suspend fun updateLineHeight(multiplier: Float) {
        val clamped = multiplier.coerceIn(
            ReaderPreferences.MIN_LINE_HEIGHT_MULTIPLIER,
            ReaderPreferences.MAX_LINE_HEIGHT_MULTIPLIER
        )
        dataStore.edit { it[Keys.LINE_HEIGHT_MULTIPLIER] = clamped }
    }

    override suspend fun updateHorizontalMargin(marginDp: Int) {
        val clamped = marginDp.coerceIn(
            ReaderPreferences.MIN_HORIZONTAL_MARGIN_DP,
            ReaderPreferences.MAX_HORIZONTAL_MARGIN_DP
        )
        dataStore.edit { it[Keys.HORIZONTAL_MARGIN_DP] = clamped }
    }

    override suspend fun updateParagraphSpacing(spacingDp: Int) {
        val clamped = spacingDp.coerceIn(
            ReaderPreferences.MIN_PARAGRAPH_SPACING_DP,
            ReaderPreferences.MAX_PARAGRAPH_SPACING_DP
        )
        dataStore.edit { it[Keys.PARAGRAPH_SPACING_DP] = clamped }
    }

    override suspend fun updateTheme(theme: ReaderTheme) {
        dataStore.edit { it[Keys.THEME_NAME] = theme.name }
    }

    override suspend fun updateCleanerEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.CLEANER_ENABLED] = enabled }
    }

    override suspend fun updateMergeBlankLines(enabled: Boolean) {
        dataStore.edit { it[Keys.MERGE_BLANK_LINES] = enabled }
    }

    override suspend fun updateFixHardLineBreaks(enabled: Boolean) {
        dataStore.edit { it[Keys.FIX_HARD_LINE_BREAKS] = enabled }
    }

    override suspend fun updateAddCjkSpacing(enabled: Boolean) {
        dataStore.edit { it[Keys.ADD_CJK_SPACING] = enabled }
    }

    override suspend fun updateNormalizeParagraphIndent(enabled: Boolean) {
        dataStore.edit { it[Keys.NORMALIZE_PARAGRAPH_INDENT] = enabled }
    }

    override suspend fun updateFocusLineMode(enabled: Boolean) {
        dataStore.edit { it[Keys.FOCUS_LINE_MODE] = enabled }
    }

    override suspend fun updateSpoilerFreeMode(enabled: Boolean) {
        dataStore.edit { it[Keys.SPOILER_FREE_MODE] = enabled }
    }

    private object Keys {
        val FONT_SIZE_SP = floatPreferencesKey("font_size_sp")
        val LINE_HEIGHT_MULTIPLIER = floatPreferencesKey("line_height_multiplier")
        val HORIZONTAL_MARGIN_DP = intPreferencesKey("horizontal_margin_dp")
        val PARAGRAPH_SPACING_DP = intPreferencesKey("paragraph_spacing_dp")
        val THEME_NAME = stringPreferencesKey("theme_name")
        val CLEANER_ENABLED = booleanPreferencesKey("cleaner_enabled")
        val MERGE_BLANK_LINES = booleanPreferencesKey("merge_blank_lines")
        val FIX_HARD_LINE_BREAKS = booleanPreferencesKey("fix_hard_line_breaks")
        val ADD_CJK_SPACING = booleanPreferencesKey("add_cjk_spacing")
        val NORMALIZE_PARAGRAPH_INDENT = booleanPreferencesKey("normalize_paragraph_indent")
        val FOCUS_LINE_MODE = booleanPreferencesKey("focus_line_mode")
        val SPOILER_FREE_MODE = booleanPreferencesKey("spoiler_free_mode")
    }
}
