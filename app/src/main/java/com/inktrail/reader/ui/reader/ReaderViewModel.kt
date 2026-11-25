package com.inktrail.reader.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inktrail.reader.data.local.storage.BookFileMissingException
import com.inktrail.reader.domain.model.Book
import com.inktrail.reader.domain.model.Chapter
import com.inktrail.reader.domain.model.HighlightRange
import com.inktrail.reader.domain.model.ReaderTheme
import com.inktrail.reader.domain.repository.AnnotationRepository
import com.inktrail.reader.domain.repository.BookRepository
import com.inktrail.reader.domain.repository.ReaderPreferencesRepository
import com.inktrail.reader.domain.usecase.LoadChapterPagesUseCase
import com.inktrail.reader.domain.usecase.ManageAnnotationUseCase
import com.inktrail.reader.domain.usecase.SearchInBookUseCase
import com.inktrail.reader.engine.cleaner.TextCleanerOptions
import com.inktrail.reader.engine.paginator.MeasureCallback
import com.inktrail.reader.engine.paginator.Paginator
import com.inktrail.reader.engine.search.ReadingAnchor
import com.inktrail.reader.engine.search.SearchHit
import com.inktrail.reader.engine.search.SpoilerFreeFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.abs

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookRepository: BookRepository,
    private val preferencesRepository: ReaderPreferencesRepository,
    private val annotationRepository: AnnotationRepository,
    private val loadChapterPagesUseCase: LoadChapterPagesUseCase,
    private val searchInBookUseCase: SearchInBookUseCase,
    private val manageAnnotationUseCase: ManageAnnotationUseCase,
    private val paginator: Paginator
) : ViewModel() {

    private val bookId: String = checkNotNull(savedStateHandle["bookId"]) {
        "bookId navigation argument is required"
    }

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private val lruCache = ChapterLruCache(maxCapacity = 3)
    private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var paginationSpec: PaginationSpec? = null
    private var measureCallback: MeasureCallback? = null

    private var anchorInitialized = false
    private var anchorChapterIndex = 0
    private var anchorCharOffset = 0
    private var currentPageEndOffset = 0

    private var isUserScrolling = false
    private var pendingWindowRebuild = false
    private var loadWindowJob: Job? = null
    private var searchJob: Job? = null
    private var lastPersistedAnchor: Pair<Int, Int>? = null
    private var allBookAnnotations: List<HighlightRange> = emptyList()
    private var oversizedWarningDismissed = false

    init {
        observeBookAndPreferences()
        observeAnnotations()
    }

    private fun observeBookAndPreferences() {
        viewModelScope.launch {
            combine(
                bookRepository.observeBook(bookId),
                bookRepository.observeChapters(bookId),
                preferencesRepository.preferences
            ) { book, chapters, prefs ->
                Triple(book, chapters, prefs)
            }.collect { (book, chapters, prefs) ->
                if (book == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "未找到该书籍记录"
                        )
                    }
                    return@collect
                }

                if (!anchorInitialized && chapters.isNotEmpty()) {
                    anchorInitialized = true
                    anchorChapterIndex = book.currentChapterIndex.coerceIn(0, chapters.lastIndex)
                    anchorCharOffset = book.currentCharOffset.coerceAtLeast(0)
                } else if (chapters.isNotEmpty()) {
                    anchorChapterIndex = anchorChapterIndex.coerceIn(0, chapters.lastIndex)
                }

                val prevPrefs = _uiState.value.preferences
                val cleanerChanged = prevPrefs.toTextCleanerOptions() != prefs.toTextCleanerOptions()
                val paginationInputsChanged =
                    prevPrefs.fontSizeSp != prefs.fontSizeSp ||
                        prevPrefs.lineHeightMultiplier != prefs.lineHeightMultiplier ||
                        prevPrefs.horizontalMarginDp != prefs.horizontalMarginDp ||
                        prevPrefs.paragraphSpacingDp != prefs.paragraphSpacingDp
                val spoilerChanged = prevPrefs.spoilerFreeMode != prefs.spoilerFreeMode

                _uiState.update {
                    it.copy(
                        book = book,
                        chapters = chapters,
                        currentChapterIndex = anchorChapterIndex,
                        currentCharOffset = anchorCharOffset,
                        preferences = prefs
                    )
                }

                if (spoilerChanged) {
                    refreshAnnotationProjection()
                    if (_uiState.value.activePanel == ReaderPanel.SEARCH_SHEET &&
                        _uiState.value.hasExecutedSearch &&
                        _uiState.value.searchQuery.isNotBlank()
                    ) {
                        executeSearch()
                    }
                }

                if (chapters.isNotEmpty() && paginationSpec?.isValid == true && measureCallback != null) {
                    if (!paginationInputsChanged && (cleanerChanged || _uiState.value.pages.isEmpty())) {
                        scheduleWindowRefresh()
                    }
                } else if (chapters.isEmpty()) {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "书籍不包含有效章节")
                    }
                }
            }
        }
    }

    private fun observeAnnotations() {
        viewModelScope.launch {
            annotationRepository.observeAllAnnotations(bookId).collect { list ->
                allBookAnnotations = list
                refreshAnnotationProjection()
            }
        }
    }

    private fun refreshAnnotationProjection() {
        val spoilerAnchor = currentSpoilerAnchor()
        val spoilerFree = _uiState.value.preferences.spoilerFreeMode
        val groupedHighlights = allBookAnnotations
            .filter { !it.isBookmark }
            .groupBy { it.chapterIndex }
        val isBookmarked = allBookAnnotations.any {
            it.isBookmark && it.chapterIndex == anchorChapterIndex
        }
        val visibleList = SpoilerFreeFilter.filter(
            items = allBookAnnotations,
            anchor = spoilerAnchor,
            spoilerFreeMode = spoilerFree,
            chapterIndexOf = { it.chapterIndex },
            endOffsetOf = { it.endOffset }
        )

        _uiState.update {
            it.copy(
                highlightsByChapter = groupedHighlights,
                isCurrentChapterBookmarked = isBookmarked,
                visibleAnnotations = visibleList
            )
        }
    }

    private fun currentSpoilerAnchor(): ReadingAnchor {
        val boundaryOffset = maxOf(anchorCharOffset, currentPageEndOffset)
        return ReadingAnchor(
            chapterIndex = anchorChapterIndex,
            charOffset = boundaryOffset
        )
    }

    fun updatePaginationEnv(spec: PaginationSpec, callback: MeasureCallback) {
        if (!spec.isValid) return
        val specChanged = paginationSpec != spec
        paginationSpec = spec
        measureCallback = callback

        if ((specChanged || _uiState.value.pages.isEmpty()) &&
            _uiState.value.book != null &&
            _uiState.value.chapters.isNotEmpty()
        ) {
            scheduleWindowRefresh()
        }
    }

    fun onUserScrollStateChanged(scrolling: Boolean) {
        isUserScrolling = scrolling
        if (!scrolling && pendingWindowRebuild) {
            pendingWindowRebuild = false
            emitWindowFromCache()
        }
    }

    fun onPageSettled(item: ReaderPageItem) {
        val chapterChanged = item.chapterIndex != anchorChapterIndex
        anchorChapterIndex = item.chapterIndex
        anchorCharOffset = item.slice.startCharOffset
        currentPageEndOffset = item.slice.endCharOffset

        _uiState.update {
            it.copy(
                currentChapterIndex = item.chapterIndex,
                currentCharOffset = item.slice.startCharOffset,
                currentPageEndOffset = item.slice.endCharOffset,
                currentPageInChapter = item.pageInChapter,
                currentChapterTotalPages = item.totalPagesInChapter
            )
        }

        refreshAnnotationProjection()
        persistProgressAsync(anchorChapterIndex, anchorCharOffset)

        if (chapterChanged) {
            scheduleWindowRefresh()
        } else if (pendingWindowRebuild && !isUserScrolling) {
            pendingWindowRebuild = false
            emitWindowFromCache()
        }
    }

    fun toggleMenu() {
        _uiState.update {
            if (it.activePanel != ReaderPanel.NONE) {
                it.copy(activePanel = ReaderPanel.NONE, isMenuVisible = false)
            } else {
                it.copy(isMenuVisible = !it.isMenuVisible)
            }
        }
    }

    fun openChapterDrawer() {
        _uiState.update {
            it.copy(isMenuVisible = false, activePanel = ReaderPanel.CHAPTER_DRAWER)
        }
    }

    fun openTypographySettings() {
        _uiState.update {
            it.copy(isMenuVisible = false, activePanel = ReaderPanel.TYPOGRAPHY_SETTINGS)
        }
    }

    fun openSearchSheet() {
        _uiState.update {
            it.copy(isMenuVisible = false, activePanel = ReaderPanel.SEARCH_SHEET)
        }
    }

    fun openAnnotationsSheet() {
        refreshAnnotationProjection()
        _uiState.update {
            it.copy(isMenuVisible = false, activePanel = ReaderPanel.ANNOTATIONS_SHEET)
        }
    }

    fun closeActivePanel() {
        _uiState.update {
            it.copy(activePanel = ReaderPanel.NONE)
        }
    }

    fun jumpToChapter(chapterIndex: Int, targetCharOffset: Int = 0) {
        val chapters = _uiState.value.chapters
        if (chapters.isEmpty()) return
        val safeIndex = chapterIndex.coerceIn(0, chapters.lastIndex)
        anchorChapterIndex = safeIndex
        anchorCharOffset = targetCharOffset.coerceAtLeast(0)
        currentPageEndOffset = anchorCharOffset
        _uiState.update {
            it.copy(
                currentChapterIndex = safeIndex,
                currentCharOffset = anchorCharOffset,
                currentPageEndOffset = anchorCharOffset,
                isMenuVisible = false,
                activePanel = ReaderPanel.NONE,
                isLoading = true
            )
        }
        refreshAnnotationProjection()
        persistProgressAsync(anchorChapterIndex, anchorCharOffset)
        scheduleWindowRefresh()
    }

    fun jumpToPreviousChapter() {
        if (anchorChapterIndex > 0) {
            jumpToChapter(anchorChapterIndex - 1, targetCharOffset = 0)
        }
    }

    fun jumpToNextChapter() {
        val last = _uiState.value.chapters.lastIndex
        if (anchorChapterIndex < last) {
            jumpToChapter(anchorChapterIndex + 1, targetCharOffset = 0)
        }
    }

    // --- Highlight, Note & Bookmark Operations ---

    fun onTextRangeSelected(chapterIndex: Int, startOffset: Int, endOffset: Int) {
        if (startOffset >= endOffset) return
        val chapterText = lruCache.peek(chapterIndex)?.cleanedText ?: return
        val safeStart = startOffset.coerceIn(0, chapterText.length)
        val safeEnd = endOffset.coerceIn(safeStart, chapterText.length)
        if (safeStart >= safeEnd) return

        val snippet = chapterText.substring(safeStart, safeEnd).trim()
        if (snippet.isEmpty()) return

        _uiState.update {
            it.copy(
                pendingSelection = TextSelectionState(
                    chapterIndex = chapterIndex,
                    startOffset = safeStart,
                    endOffset = safeEnd,
                    selectedText = chapterText.substring(safeStart, safeEnd)
                ),
                isMenuVisible = false
            )
        }
    }

    fun expandPendingSelectionToSentence() {
        val current = _uiState.value.pendingSelection ?: return
        val chapterText = lruCache.peek(current.chapterIndex)?.cleanedText ?: return
        val (expandedStart, expandedEnd) = findSentenceBounds(
            text = chapterText,
            startOffset = current.startOffset,
            endOffset = current.endOffset
        )
        if (expandedStart >= expandedEnd) return
        _uiState.update {
            it.copy(
                pendingSelection = current.copy(
                    startOffset = expandedStart,
                    endOffset = expandedEnd,
                    selectedText = chapterText.substring(expandedStart, expandedEnd)
                )
            )
        }
    }

    fun dismissPendingSelection() {
        _uiState.update { it.copy(pendingSelection = null) }
    }

    fun savePendingSelectionHighlight(colorHex: Long, noteContent: String?) {
        val sel = _uiState.value.pendingSelection ?: return
        _uiState.update { it.copy(pendingSelection = null) }
        viewModelScope.launch {
            manageAnnotationUseCase.addOrMergeHighlight(
                bookId = bookId,
                chapterIndex = sel.chapterIndex,
                startOffset = sel.startOffset,
                endOffset = sel.endOffset,
                selectedText = sel.selectedText,
                colorHex = colorHex,
                noteContent = noteContent
            )
        }
    }

    fun erasePendingSelectionHighlight() {
        val sel = _uiState.value.pendingSelection ?: return
        _uiState.update { it.copy(pendingSelection = null) }
        viewModelScope.launch {
            manageAnnotationUseCase.eraseHighlightRange(
                bookId = bookId,
                chapterIndex = sel.chapterIndex,
                eraseStart = sel.startOffset,
                eraseEnd = sel.endOffset
            )
        }
    }

    fun openAnnotationEditor(annotation: HighlightRange) {
        _uiState.update { it.copy(editingAnnotation = annotation) }
    }

    fun dismissAnnotationEditor() {
        _uiState.update { it.copy(editingAnnotation = null) }
    }

    fun saveEditedNote(annotationId: Long, noteContent: String?) {
        _uiState.update { it.copy(editingAnnotation = null) }
        viewModelScope.launch {
            manageAnnotationUseCase.updateNote(annotationId, noteContent)
        }
    }

    fun deleteAnnotation(annotationId: Long) {
        if (_uiState.value.editingAnnotation?.id == annotationId) {
            _uiState.update { it.copy(editingAnnotation = null) }
        }
        viewModelScope.launch {
            manageAnnotationUseCase.deleteAnnotation(annotationId)
        }
    }

    fun toggleCurrentChapterBookmark() {
        val state = _uiState.value
        val currentSlice = state.pages.getOrNull(state.targetPagerIndex)?.slice
        val preview = currentSlice?.text
            ?.replace('\n', ' ')
            ?.trim()
            ?.take(60)
            ?.ifEmpty { state.currentChapterTitle }
            ?: state.currentChapterTitle

        viewModelScope.launch {
            val added = manageAnnotationUseCase.toggleBookmark(
                bookId = bookId,
                chapterIndex = anchorChapterIndex,
                charOffset = anchorCharOffset,
                previewText = preview
            )
            _uiState.update {
                it.copy(statusMessage = if (added) "已添加书签" else "已取消书签")
            }
        }
    }

    fun jumpToAnnotation(annotation: HighlightRange) {
        jumpToChapter(annotation.chapterIndex, annotation.startOffset)
    }

    // --- Book Search & Anti-Spoiler ---

    fun updateSearchQuery(query: String) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                hasExecutedSearch = if (query.isBlank()) false else it.hasExecutedSearch,
                searchResults = if (query.isBlank()) emptyList() else it.searchResults
            )
        }
    }

    fun executeSearch() {
        val book = _uiState.value.book ?: return
        val query = _uiState.value.searchQuery.trim()
        if (query.isEmpty()) return

        val spoilerFree = _uiState.value.preferences.spoilerFreeMode
        val cleanerOptions = _uiState.value.preferences.toTextCleanerOptions()
        val anchor = currentSpoilerAnchor()

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, hasExecutedSearch = true) }
            try {
                val hits = searchInBookUseCase(
                    book = book,
                    query = query,
                    anchor = anchor,
                    spoilerFreeMode = spoilerFree,
                    cleanerOptions = cleanerOptions
                )
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        searchResults = hits
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        statusMessage = t.message ?: "检索过程发生异常"
                    )
                }
            }
        }
    }

    fun jumpToSearchResult(hit: SearchHit) {
        jumpToChapter(hit.chapterIndex, hit.matchStartOffset)
    }

    fun updateSpoilerFreeMode(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateSpoilerFreeMode(enabled)
        }
    }

    // --- Typography & Cleaner Preferences ---

    fun updateFontSize(fontSizeSp: Float) {
        viewModelScope.launch { preferencesRepository.updateFontSize(fontSizeSp) }
    }

    fun updateLineHeight(multiplier: Float) {
        viewModelScope.launch { preferencesRepository.updateLineHeight(multiplier) }
    }

    fun updateHorizontalMargin(marginDp: Int) {
        viewModelScope.launch { preferencesRepository.updateHorizontalMargin(marginDp) }
    }

    fun updateParagraphSpacing(spacingDp: Int) {
        viewModelScope.launch { preferencesRepository.updateParagraphSpacing(spacingDp) }
    }

    fun updateTheme(theme: ReaderTheme) {
        viewModelScope.launch { preferencesRepository.updateTheme(theme) }
    }

    fun cycleQuickTheme() {
        val current = _uiState.value.preferences.theme
        val entries = ReaderTheme.entries
        val next = entries[(entries.indexOf(current) + 1) % entries.size]
        updateTheme(next)
    }

    fun updateCleanerEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.updateCleanerEnabled(enabled) }
    }

    fun updateMergeBlankLines(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.updateMergeBlankLines(enabled) }
    }

    fun updateFixHardLineBreaks(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.updateFixHardLineBreaks(enabled) }
    }

    fun updateAddCjkSpacing(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.updateAddCjkSpacing(enabled) }
    }

    fun updateNormalizeParagraphIndent(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.updateNormalizeParagraphIndent(enabled) }
    }

    fun updateFocusLineMode(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.updateFocusLineMode(enabled) }
    }

    fun dismissOversizedLineWarning() {
        oversizedWarningDismissed = true
        _uiState.update { it.copy(oversizedLineWarningVisible = false) }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    suspend fun removeMissingBookRecord() {
        bookRepository.deleteBook(bookId)
    }

    fun persistProgressImmediate() {
        if (!anchorInitialized) return
        persistProgressAsync(anchorChapterIndex, anchorCharOffset, force = true)
    }

    private fun persistProgressAsync(chapterIndex: Int, charOffset: Int, force: Boolean = false) {
        val candidate = chapterIndex to charOffset
        if (!force && lastPersistedAnchor == candidate) return
        lastPersistedAnchor = candidate
        persistScope.launch {
            withContext(NonCancellable) {
                bookRepository.updateReadingProgress(bookId, chapterIndex, charOffset)
            }
        }
    }

    private fun scheduleWindowRefresh() {
        val book = _uiState.value.book ?: return
        val chapters = _uiState.value.chapters
        val spec = paginationSpec ?: return
        val callback = measureCallback ?: return
        if (!spec.isValid || chapters.isEmpty()) return

        val cleanerOptions = _uiState.value.preferences.toTextCleanerOptions()
        val targetChapterIdx = anchorChapterIndex.coerceIn(0, chapters.lastIndex)

        loadWindowJob?.cancel()
        loadWindowJob = viewModelScope.launch {
            try {
                ensureChapterReady(
                    book = book,
                    chapter = chapters[targetChapterIdx],
                    cleanerOptions = cleanerOptions,
                    spec = spec,
                    callback = callback,
                    isActiveChapter = true
                )
                emitWindowFromCache()

                val neighborIndices = listOf(targetChapterIdx + 1, targetChapterIdx - 1)
                    .filter { it in chapters.indices }
                for (neighborIdx in neighborIndices) {
                    ensureChapterReady(
                        book = book,
                        chapter = chapters[neighborIdx],
                        cleanerOptions = cleanerOptions,
                        spec = spec,
                        callback = callback,
                        isActiveChapter = false
                    )
                }

                if (isUserScrolling) {
                    pendingWindowRebuild = true
                } else {
                    emitWindowFromCache()
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (missing: BookFileMissingException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isFileMissing = true,
                        errorMessage = missing.message ?: "本地书籍文件丢失"
                    )
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = t.message ?: "加载章节失败"
                    )
                }
            }
        }
    }

    private suspend fun ensureChapterReady(
        book: Book,
        chapter: Chapter,
        cleanerOptions: TextCleanerOptions,
        spec: PaginationSpec,
        callback: MeasureCallback,
        isActiveChapter: Boolean
    ) {
        val cached = lruCache.get(chapter.chapterIndex)
        if (cached != null && cached.cleanerOptions == cleanerOptions && cached.paginationSpec == spec) {
            return
        }

        val (rawText, cleanedText) = if (cached != null) {
            val updatedCleaned = if (cached.cleanerOptions == cleanerOptions) {
                cached.cleanedText
            } else {
                val newClean = loadChapterPagesUseCase.cleanText(cached.rawText, cleanerOptions)
                if (isActiveChapter) {
                    val remapped = remapAnchorForTextChange(
                        oldText = cached.cleanedText,
                        oldOffset = anchorCharOffset,
                        newText = newClean
                    )
                    if (remapped != anchorCharOffset) {
                        anchorCharOffset = remapped
                        _uiState.update { it.copy(currentCharOffset = remapped) }
                        persistProgressAsync(anchorChapterIndex, remapped)
                    }
                }
                newClean
            }
            cached.rawText to updatedCleaned
        } else {
            loadChapterPagesUseCase.loadCleanChapterText(book, chapter, cleanerOptions)
        }

        if (isActiveChapter && !oversizedWarningDismissed && hasOversizedSingleLine(cleanedText)) {
            _uiState.update { it.copy(oversizedLineWarningVisible = true) }
        }

        val (pages, _) = loadChapterPagesUseCase.paginate(cleanedText, 0, callback)
        lruCache.put(
            CachedChapter(
                chapterIndex = chapter.chapterIndex,
                title = chapter.title,
                rawText = rawText,
                cleanedText = cleanedText,
                cleanerOptions = cleanerOptions,
                paginationSpec = spec,
                pages = pages
            )
        )
    }

    private fun emitWindowFromCache() {
        val chapters = _uiState.value.chapters
        val spec = paginationSpec ?: return
        val cleanerOptions = _uiState.value.preferences.toTextCleanerOptions()
        if (chapters.isEmpty()) return

        val currIdx = anchorChapterIndex.coerceIn(0, chapters.lastIndex)
        val currEntry = lruCache.peek(currIdx)?.takeIf {
            it.cleanerOptions == cleanerOptions && it.paginationSpec == spec
        } ?: return

        val prevEntry = if (currIdx > 0) {
            lruCache.peek(currIdx - 1)?.takeIf {
                it.cleanerOptions == cleanerOptions && it.paginationSpec == spec
            }
        } else null

        val nextEntry = if (currIdx + 1 < chapters.size) {
            lruCache.peek(currIdx + 1)?.takeIf {
                it.cleanerOptions == cleanerOptions && it.paginationSpec == spec
            }
        } else null

        val windowItems = ArrayList<ReaderPageItem>()
        prevEntry?.let { appendChapterPages(windowItems, it) }
        val currentStartOffsetInWindow = windowItems.size
        appendChapterPages(windowItems, currEntry)
        nextEntry?.let { appendChapterPages(windowItems, it) }

        val targetPageInChapter = paginator.findPageIndexForOffset(currEntry.pages, anchorCharOffset)
        val targetPagerIdx = (currentStartOffsetInWindow + targetPageInChapter)
            .coerceIn(0, (windowItems.size - 1).coerceAtLeast(0))
        val targetSlice = currEntry.pages.getOrNull(targetPageInChapter)
        if (targetSlice != null) {
            currentPageEndOffset = targetSlice.endCharOffset
        }

        _uiState.update { state ->
            state.copy(
                currentChapterIndex = currIdx,
                currentPageEndOffset = currentPageEndOffset,
                currentPageInChapter = targetPageInChapter,
                currentChapterTotalPages = currEntry.pages.size.coerceAtLeast(1),
                pages = windowItems,
                targetPagerIndex = targetPagerIdx,
                windowGeneration = state.windowGeneration + 1L,
                isLoading = false,
                isFileMissing = false,
                errorMessage = null
            )
        }
        refreshAnnotationProjection()
    }

    private fun appendChapterPages(out: MutableList<ReaderPageItem>, entry: CachedChapter) {
        val total = entry.pages.size.coerceAtLeast(1)
        if (entry.pages.isEmpty()) {
            out += ReaderPageItem(
                chapterIndex = entry.chapterIndex,
                chapterTitle = entry.title,
                pageInChapter = 0,
                totalPagesInChapter = 1,
                slice = com.inktrail.reader.domain.model.PageSlice(0, 0, 0, "")
            )
            return
        }
        for (slice in entry.pages) {
            out += ReaderPageItem(
                chapterIndex = entry.chapterIndex,
                chapterTitle = entry.title,
                pageInChapter = slice.pageIndex,
                totalPagesInChapter = total,
                slice = slice
            )
        }
    }

    override fun onCleared() {
        persistProgressImmediate()
        super.onCleared()
    }

    companion object {
        private const val OVERSIZED_LINE_CHAR_THRESHOLD = 4000
        private val SENTENCE_BOUNDARY_CHARS = setOf('。', '！', '？', '；', '\n', '.', '!', '?', ';')
        private val CLAUSE_BOUNDARY_CHARS = setOf('，', ',', '、', '：', ':', '”', '」')

        internal fun hasOversizedSingleLine(text: String): Boolean {
            if (text.length <= OVERSIZED_LINE_CHAR_THRESHOLD) return false
            var lineStart = 0
            for (i in text.indices) {
                if (text[i] == '\n') {
                    if (i - lineStart > OVERSIZED_LINE_CHAR_THRESHOLD) return true
                    lineStart = i + 1
                }
            }
            return (text.length - lineStart) > OVERSIZED_LINE_CHAR_THRESHOLD
        }

        internal fun findSentenceBounds(
            text: String,
            startOffset: Int,
            endOffset: Int
        ): Pair<Int, Int> {
            if (text.isEmpty()) return 0 to 0
            var left = startOffset.coerceIn(0, text.lastIndex)
            while (left > 0 && text[left - 1] !in SENTENCE_BOUNDARY_CHARS) {
                left--
            }
            while (left < text.length && text[left].isWhitespace()) {
                left++
            }

            var right = endOffset.coerceIn(left, text.length)
            while (right < text.length && text[right] !in SENTENCE_BOUNDARY_CHARS) {
                right++
            }
            if (right < text.length && text[right] != '\n') {
                right++
            }
            return left to right.coerceAtLeast(left)
        }

        internal fun findClauseBounds(text: String, anchorOffset: Int): Pair<Int, Int> {
            if (text.isEmpty()) return 0 to 0
            val clamped = anchorOffset.coerceIn(0, text.lastIndex)
            var left = clamped
            while (left > 0 &&
                text[left - 1] !in SENTENCE_BOUNDARY_CHARS &&
                text[left - 1] !in CLAUSE_BOUNDARY_CHARS
            ) {
                left--
            }
            while (left < text.length && text[left].isWhitespace()) {
                left++
            }

            var right = clamped + 1
            while (right < text.length &&
                text[right] !in SENTENCE_BOUNDARY_CHARS &&
                text[right] !in CLAUSE_BOUNDARY_CHARS
            ) {
                right++
            }
            if (right <= left) {
                right = (left + 1).coerceAtMost(text.length)
            }
            return left to right
        }

        internal fun remapAnchorForTextChange(
            oldText: String,
            oldOffset: Int,
            newText: String
        ): Int {
            if (newText.isEmpty()) return 0
            if (oldText.isEmpty() || oldOffset <= 0) return 0
            val clampedOld = oldOffset.coerceIn(0, (oldText.length - 1).coerceAtLeast(0))
            val ratio = clampedOld.toFloat() / oldText.length.toFloat()
            val expectedNew = (ratio * newText.length).toInt().coerceIn(0, newText.lastIndex)

            val rawFragment = oldText.substring(clampedOld, minOf(oldText.length, clampedOld + 24))
                .replace(Regex("""[\s\u3000]+"""), "")
            val needle = rawFragment.take(6)
            if (needle.length >= 2) {
                var bestMatch: Int? = null
                var bestDist = Int.MAX_VALUE
                var searchFrom = 0
                while (searchFrom < newText.length) {
                    val idx = newText.indexOf(needle.take(3), startIndex = searchFrom)
                    if (idx < 0) break
                    val dist = abs(idx - expectedNew)
                    if (dist < bestDist) {
                        bestDist = dist
                        bestMatch = idx
                    }
                    searchFrom = idx + 1
                }
                if (bestMatch != null) return bestMatch
            }
            return expectedNew
        }
    }
}
