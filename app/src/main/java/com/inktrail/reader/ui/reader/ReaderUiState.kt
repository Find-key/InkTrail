package com.inktrail.reader.ui.reader

import com.inktrail.reader.domain.model.Book
import com.inktrail.reader.domain.model.Chapter
import com.inktrail.reader.domain.model.HighlightRange
import com.inktrail.reader.domain.model.PageSlice
import com.inktrail.reader.domain.model.ReaderPreferences
import com.inktrail.reader.engine.search.SearchHit

enum class ReaderPanel {
    NONE,
    CHAPTER_DRAWER,
    TYPOGRAPHY_SETTINGS,
    SEARCH_SHEET,
    ANNOTATIONS_SHEET
}

data class TextSelectionState(
    val chapterIndex: Int,
    val startOffset: Int,
    val endOffset: Int,
    val selectedText: String
)

data class ReaderPageItem(
    val chapterIndex: Int,
    val chapterTitle: String,
    val pageInChapter: Int,
    val totalPagesInChapter: Int,
    val slice: PageSlice
) {
    val stableKey: String
        get() = "$chapterIndex:${slice.startCharOffset}-${slice.endCharOffset}"
}

data class ReaderUiState(
    val book: Book? = null,
    val chapters: List<Chapter> = emptyList(),
    val currentChapterIndex: Int = 0,
    val currentCharOffset: Int = 0,
    val currentPageEndOffset: Int = 0,
    val currentPageInChapter: Int = 0,
    val currentChapterTotalPages: Int = 1,
    val pages: List<ReaderPageItem> = emptyList(),
    val targetPagerIndex: Int = 0,
    val windowGeneration: Long = 0L,
    val preferences: ReaderPreferences = ReaderPreferences(),
    val highlightsByChapter: Map<Int, List<HighlightRange>> = emptyMap(),
    val visibleAnnotations: List<HighlightRange> = emptyList(),
    val isCurrentChapterBookmarked: Boolean = false,
    val pendingSelection: TextSelectionState? = null,
    val editingAnnotation: HighlightRange? = null,
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val hasExecutedSearch: Boolean = false,
    val searchResults: List<SearchHit> = emptyList(),
    val isLoading: Boolean = true,
    val isMenuVisible: Boolean = false,
    val activePanel: ReaderPanel = ReaderPanel.NONE,
    val isFileMissing: Boolean = false,
    val oversizedLineWarningVisible: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null
) {
    val currentChapterTitle: String
        get() = chapters.getOrNull(currentChapterIndex)?.title
            ?: pages.getOrNull(targetPagerIndex)?.chapterTitle
            ?: ""

    val overallProgressPercent: Int
        get() {
            val total = chapters.size
            if (total <= 0) return 0
            val chapterRatio = (currentPageInChapter + 1).toFloat() /
                currentChapterTotalPages.coerceAtLeast(1).toFloat()
            val overall = ((currentChapterIndex + chapterRatio) / total.toFloat()).coerceIn(0f, 1f)
            return (overall * 100f).toInt()
        }
}
