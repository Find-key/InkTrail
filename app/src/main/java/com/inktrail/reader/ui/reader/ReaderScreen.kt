package com.inktrail.reader.ui.reader

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inktrail.reader.domain.model.HighlightRange
import com.inktrail.reader.domain.model.ReaderTheme
import com.inktrail.reader.engine.search.SearchHit
import com.inktrail.reader.ui.reader.components.AnnotationsListSheet
import com.inktrail.reader.ui.reader.components.BookSearchSheet
import com.inktrail.reader.ui.reader.components.ChapterDrawerSheet
import com.inktrail.reader.ui.reader.components.EditAnnotationDialog
import com.inktrail.reader.ui.reader.components.TextSelectionActionDialog
import com.inktrail.reader.ui.reader.components.TypographySettingsSheet
import com.inktrail.reader.ui.theme.ReaderPalette
import com.inktrail.reader.ui.theme.toPalette
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun ReaderRoute(
    onBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    BackHandler(
        enabled = uiState.pendingSelection != null ||
            uiState.editingAnnotation != null ||
            uiState.activePanel != ReaderPanel.NONE ||
            uiState.isMenuVisible
    ) {
        when {
            uiState.pendingSelection != null -> viewModel.dismissPendingSelection()
            uiState.editingAnnotation != null -> viewModel.dismissAnnotationEditor()
            uiState.activePanel != ReaderPanel.NONE -> viewModel.closeActivePanel()
            else -> viewModel.toggleMenu()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP || event == Lifecycle.Event.ON_PAUSE) {
                viewModel.persistProgressImmediate()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.persistProgressImmediate()
        }
    }

    ReaderScreen(
        uiState = uiState,
        onBack = {
            viewModel.persistProgressImmediate()
            onBack()
        },
        onRemoveMissingBook = {
            coroutineScope.launch {
                viewModel.removeMissingBookRecord()
                onBack()
            }
        },
        onUpdatePaginationEnv = viewModel::updatePaginationEnv,
        onUserScrollStateChanged = viewModel::onUserScrollStateChanged,
        onPageSettled = viewModel::onPageSettled,
        onToggleMenu = viewModel::toggleMenu,
        onPreviousChapter = viewModel::jumpToPreviousChapter,
        onNextChapter = viewModel::jumpToNextChapter,
        onOpenChapterDrawer = viewModel::openChapterDrawer,
        onOpenTypographySettings = viewModel::openTypographySettings,
        onOpenSearchSheet = viewModel::openSearchSheet,
        onOpenAnnotationsSheet = viewModel::openAnnotationsSheet,
        onClosePanel = viewModel::closeActivePanel,
        onSelectChapter = { idx -> viewModel.jumpToChapter(idx, 0) },
        onToggleBookmark = viewModel::toggleCurrentChapterBookmark,
        onTextRangeSelected = viewModel::onTextRangeSelected,
        onExpandSelectionToSentence = viewModel::expandPendingSelectionToSentence,
        onSaveSelectionHighlight = viewModel::savePendingSelectionHighlight,
        onEraseSelectionHighlight = viewModel::erasePendingSelectionHighlight,
        onDismissPendingSelection = viewModel::dismissPendingSelection,
        onTapHighlight = viewModel::openAnnotationEditor,
        onSaveEditedNote = viewModel::saveEditedNote,
        onDeleteAnnotation = viewModel::deleteAnnotation,
        onDismissAnnotationEditor = viewModel::dismissAnnotationEditor,
        onJumpToAnnotation = viewModel::jumpToAnnotation,
        onSearchQueryChange = viewModel::updateSearchQuery,
        onExecuteSearch = viewModel::executeSearch,
        onSelectSearchHit = viewModel::jumpToSearchResult,
        onToggleSpoilerFreeMode = viewModel::updateSpoilerFreeMode,
        onUpdateFontSize = viewModel::updateFontSize,
        onUpdateLineHeight = viewModel::updateLineHeight,
        onUpdateHorizontalMargin = viewModel::updateHorizontalMargin,
        onUpdateParagraphSpacing = viewModel::updateParagraphSpacing,
        onUpdateTheme = viewModel::updateTheme,
        onCycleQuickTheme = viewModel::cycleQuickTheme,
        onUpdateCleanerEnabled = viewModel::updateCleanerEnabled,
        onUpdateMergeBlankLines = viewModel::updateMergeBlankLines,
        onUpdateFixHardLineBreaks = viewModel::updateFixHardLineBreaks,
        onUpdateAddCjkSpacing = viewModel::updateAddCjkSpacing,
        onUpdateNormalizeParagraphIndent = viewModel::updateNormalizeParagraphIndent,
        onUpdateFocusLineMode = viewModel::updateFocusLineMode,
        onDismissOversizedLineWarning = viewModel::dismissOversizedLineWarning,
        onClearStatusMessage = viewModel::clearStatusMessage
    )
}

@Composable
fun ReaderScreen(
    uiState: ReaderUiState,
    onBack: () -> Unit,
    onRemoveMissingBook: () -> Unit,
    onUpdatePaginationEnv: (PaginationSpec, ComposeTextMeasureCallback) -> Unit,
    onUserScrollStateChanged: (Boolean) -> Unit,
    onPageSettled: (ReaderPageItem) -> Unit,
    onToggleMenu: () -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onOpenChapterDrawer: () -> Unit,
    onOpenTypographySettings: () -> Unit,
    onOpenSearchSheet: () -> Unit,
    onOpenAnnotationsSheet: () -> Unit,
    onClosePanel: () -> Unit,
    onSelectChapter: (Int) -> Unit,
    onToggleBookmark: () -> Unit,
    onTextRangeSelected: (chapterIndex: Int, startOffset: Int, endOffset: Int) -> Unit,
    onExpandSelectionToSentence: () -> Unit,
    onSaveSelectionHighlight: (colorHex: Long, noteContent: String?) -> Unit,
    onEraseSelectionHighlight: () -> Unit,
    onDismissPendingSelection: () -> Unit,
    onTapHighlight: (HighlightRange) -> Unit,
    onSaveEditedNote: (annotationId: Long, noteContent: String?) -> Unit,
    onDeleteAnnotation: (Long) -> Unit,
    onDismissAnnotationEditor: () -> Unit,
    onJumpToAnnotation: (HighlightRange) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onExecuteSearch: () -> Unit,
    onSelectSearchHit: (SearchHit) -> Unit,
    onToggleSpoilerFreeMode: (Boolean) -> Unit,
    onUpdateFontSize: (Float) -> Unit,
    onUpdateLineHeight: (Float) -> Unit,
    onUpdateHorizontalMargin: (Int) -> Unit,
    onUpdateParagraphSpacing: (Int) -> Unit,
    onUpdateTheme: (ReaderTheme) -> Unit,
    onCycleQuickTheme: () -> Unit,
    onUpdateCleanerEnabled: (Boolean) -> Unit,
    onUpdateMergeBlankLines: (Boolean) -> Unit,
    onUpdateFixHardLineBreaks: (Boolean) -> Unit,
    onUpdateAddCjkSpacing: (Boolean) -> Unit,
    onUpdateNormalizeParagraphIndent: (Boolean) -> Unit,
    onUpdateFocusLineMode: (Boolean) -> Unit,
    onDismissOversizedLineWarning: () -> Unit,
    onClearStatusMessage: () -> Unit
) {
    val palette = uiState.preferences.theme.toPalette()
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !palette.theme.isDark
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.statusMessage) {
        val msg = uiState.statusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            onClearStatusMessage()
        }
    }

    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer(cacheSize = 64)
    val prefs = uiState.preferences

    val bodyTextStyle = remember(prefs.fontSizeSp, prefs.lineHeightMultiplier, palette.primaryText) {
        TextStyle(
            fontFamily = FontFamily.Serif,
            fontSize = prefs.fontSizeSp.sp,
            lineHeight = (prefs.fontSizeSp * prefs.lineHeightMultiplier).sp,
            color = palette.primaryText
        )
    }

    val paragraphSpacingPx = with(density) { prefs.paragraphSpacingDp.dp.toPx() }
    val fontSizePx = with(density) { prefs.fontSizeSp.sp.toPx() }
    val blankLineHeightPx = remember(fontSizePx, prefs.lineHeightMultiplier, paragraphSpacingPx) {
        ComposeTextMeasureCallback.computeBlankLineHeightPx(
            fontSizePx = fontSizePx,
            lineHeightMultiplier = prefs.lineHeightMultiplier,
            paragraphSpacingPx = paragraphSpacingPx
        )
    }
    val blankLineHeightDp = with(density) { blankLineHeightPx.toDp() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            ReaderHeaderStrip(
                chapterTitle = uiState.currentChapterTitle,
                bookTitle = uiState.book?.title.orEmpty(),
                palette = palette,
                horizontalMarginDp = prefs.horizontalMarginDp.dp
            )

            AnimatedVisibility(visible = uiState.oversizedLineWarningVisible) {
                Surface(
                    color = palette.surface,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = prefs.horizontalMarginDp.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = palette.accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "检测到超长无换行单行段落，已自动流式分片以保证流畅阅读。",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.secondaryText,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onDismissOversizedLineWarning,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "关闭提示",
                                tint = palette.secondaryText,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = prefs.horizontalMarginDp.dp, vertical = 6.dp)
            ) {
                val widthPx = with(density) { maxWidth.toPx().roundToInt() }.coerceAtLeast(1)
                val heightPx = with(density) { maxHeight.toPx().roundToInt() }.coerceAtLeast(1)

                val spec = remember(
                    widthPx,
                    heightPx,
                    prefs.fontSizeSp,
                    prefs.lineHeightMultiplier,
                    prefs.horizontalMarginDp,
                    prefs.paragraphSpacingDp
                ) {
                    PaginationSpec(
                        contentWidthPx = widthPx,
                        contentHeightPx = heightPx,
                        fontSizeSp = prefs.fontSizeSp,
                        lineHeightMultiplier = prefs.lineHeightMultiplier,
                        horizontalMarginDp = prefs.horizontalMarginDp,
                        paragraphSpacingDp = prefs.paragraphSpacingDp
                    )
                }

                val callback = remember(textMeasurer, bodyTextStyle, spec, paragraphSpacingPx, blankLineHeightPx) {
                    ComposeTextMeasureCallback(
                        textMeasurer = textMeasurer,
                        bodyTextStyle = bodyTextStyle,
                        contentWidthPx = spec.contentWidthPx,
                        contentHeightPx = spec.contentHeightPx,
                        paragraphSpacingPx = paragraphSpacingPx,
                        blankLineHeightPx = blankLineHeightPx
                    )
                }

                LaunchedEffect(spec, callback) {
                    onUpdatePaginationEnv(spec, callback)
                }

                when {
                    uiState.isFileMissing || (!uiState.isLoading && uiState.errorMessage != null && uiState.pages.isEmpty()) -> {
                        ReaderErrorView(
                            message = uiState.errorMessage ?: "无法读取书籍内容",
                            isFileMissing = uiState.isFileMissing,
                            palette = palette,
                            onBack = onBack,
                            onRemoveMissingBook = onRemoveMissingBook,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    uiState.isLoading && uiState.pages.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = palette.accent)
                        }
                    }
                    else -> {
                        ReaderPagerContent(
                            uiState = uiState,
                            palette = palette,
                            bodyTextStyle = bodyTextStyle,
                            paragraphSpacingDp = prefs.paragraphSpacingDp.dp,
                            blankLineHeightDp = blankLineHeightDp,
                            focusLineMode = prefs.focusLineMode,
                            onUserScrollStateChanged = onUserScrollStateChanged,
                            onPageSettled = onPageSettled,
                            onToggleMenu = onToggleMenu,
                            onTextRangeSelected = onTextRangeSelected,
                            onTapHighlight = onTapHighlight
                        )
                    }
                }
            }

            ReaderFooterStrip(
                pageInChapter = uiState.currentPageInChapter + 1,
                totalPagesInChapter = uiState.currentChapterTotalPages,
                chapterIndex = uiState.currentChapterIndex + 1,
                totalChapters = uiState.chapters.size.coerceAtLeast(1),
                overallProgressPercent = uiState.overallProgressPercent,
                palette = palette,
                horizontalMarginDp = prefs.horizontalMarginDp.dp
            )
        }

        ReaderOverlayBars(
            visible = uiState.isMenuVisible && uiState.activePanel == ReaderPanel.NONE,
            bookTitle = uiState.book?.title.orEmpty(),
            chapterTitle = uiState.currentChapterTitle,
            chapterProgressLabel = "第 ${uiState.currentChapterIndex + 1}/${uiState.chapters.size.coerceAtLeast(1)} 章 · 本章 ${uiState.currentPageInChapter + 1}/${uiState.currentChapterTotalPages} 页",
            hasPrevChapter = uiState.currentChapterIndex > 0,
            hasNextChapter = uiState.currentChapterIndex + 1 < uiState.chapters.size,
            isBookmarked = uiState.isCurrentChapterBookmarked,
            focusLineEnabled = prefs.focusLineMode,
            activeTheme = prefs.theme,
            palette = palette,
            onBack = onBack,
            onPreviousChapter = onPreviousChapter,
            onNextChapter = onNextChapter,
            onOpenSearchSheet = onOpenSearchSheet,
            onToggleBookmark = onToggleBookmark,
            onOpenAnnotationsSheet = onOpenAnnotationsSheet,
            onOpenChapterDrawer = onOpenChapterDrawer,
            onOpenTypographySettings = onOpenTypographySettings,
            onCycleQuickTheme = onCycleQuickTheme,
            onToggleFocusLine = { onUpdateFocusLineMode(!prefs.focusLineMode) }
        )

        ChapterDrawerSheet(
            visible = uiState.activePanel == ReaderPanel.CHAPTER_DRAWER,
            bookTitle = uiState.book?.title.orEmpty(),
            chapters = uiState.chapters,
            currentChapterIndex = uiState.currentChapterIndex,
            palette = palette,
            onSelectChapter = onSelectChapter,
            onDismiss = onClosePanel
        )

        TypographySettingsSheet(
            visible = uiState.activePanel == ReaderPanel.TYPOGRAPHY_SETTINGS,
            preferences = prefs,
            palette = palette,
            onUpdateFontSize = onUpdateFontSize,
            onUpdateLineHeight = onUpdateLineHeight,
            onUpdateHorizontalMargin = onUpdateHorizontalMargin,
            onUpdateParagraphSpacing = onUpdateParagraphSpacing,
            onUpdateTheme = onUpdateTheme,
            onUpdateCleanerEnabled = onUpdateCleanerEnabled,
            onUpdateMergeBlankLines = onUpdateMergeBlankLines,
            onUpdateFixHardLineBreaks = onUpdateFixHardLineBreaks,
            onUpdateAddCjkSpacing = onUpdateAddCjkSpacing,
            onUpdateNormalizeParagraphIndent = onUpdateNormalizeParagraphIndent,
            onUpdateFocusLineMode = onUpdateFocusLineMode,
            onDismiss = onClosePanel
        )

        BookSearchSheet(
            visible = uiState.activePanel == ReaderPanel.SEARCH_SHEET,
            query = uiState.searchQuery,
            spoilerFreeMode = prefs.spoilerFreeMode,
            currentChapterNumber = uiState.currentChapterIndex + 1,
            isSearching = uiState.isSearching,
            hasExecutedSearch = uiState.hasExecutedSearch,
            results = uiState.searchResults,
            palette = palette,
            onQueryChange = onSearchQueryChange,
            onExecuteSearch = onExecuteSearch,
            onToggleSpoilerFree = onToggleSpoilerFreeMode,
            onSelectHit = onSelectSearchHit,
            onDismiss = onClosePanel
        )

        AnnotationsListSheet(
            visible = uiState.activePanel == ReaderPanel.ANNOTATIONS_SHEET,
            annotations = uiState.visibleAnnotations,
            chapters = uiState.chapters,
            spoilerFreeMode = prefs.spoilerFreeMode,
            palette = palette,
            onToggleSpoilerFree = onToggleSpoilerFreeMode,
            onJumpToAnnotation = onJumpToAnnotation,
            onEditAnnotation = onTapHighlight,
            onDeleteAnnotation = onDeleteAnnotation,
            onDismiss = onClosePanel
        )

        uiState.pendingSelection?.let { selection ->
            val chapterHighlights = uiState.highlightsByChapter[selection.chapterIndex].orEmpty()
            val hasOverlap = chapterHighlights.any {
                it.endOffset > selection.startOffset && selection.endOffset > it.startOffset
            }
            TextSelectionActionDialog(
                selection = selection,
                hasOverlapWithExisting = hasOverlap,
                palette = palette,
                onSaveHighlight = onSaveSelectionHighlight,
                onEraseSelection = onEraseSelectionHighlight,
                onExpandToSentence = onExpandSelectionToSentence,
                onDismiss = onDismissPendingSelection
            )
        }

        uiState.editingAnnotation?.let { annotation ->
            EditAnnotationDialog(
                annotation = annotation,
                palette = palette,
                onSaveNote = { newNote -> onSaveEditedNote(annotation.id, newNote) },
                onDeleteAnnotation = { onDeleteAnnotation(annotation.id) },
                onDismiss = onDismissAnnotationEditor
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 28.dp)
        )
    }
}

private data class PageLineLocation(
    val paragraphIndex: Int,
    val localLineIndex: Int,
    val startOffsetInPara: Int,
    val endOffsetInPara: Int,
    val lineTopInParent: Float,
    val lineBottomInParent: Float,
    val lineTopLocal: Float,
    val lineBottomLocal: Float
)

@Composable
private fun ReaderPagerContent(
    uiState: ReaderUiState,
    palette: ReaderPalette,
    bodyTextStyle: TextStyle,
    paragraphSpacingDp: Dp,
    blankLineHeightDp: Dp,
    focusLineMode: Boolean,
    onUserScrollStateChanged: (Boolean) -> Unit,
    onPageSettled: (ReaderPageItem) -> Unit,
    onToggleMenu: () -> Unit,
    onTextRangeSelected: (chapterIndex: Int, startOffset: Int, endOffset: Int) -> Unit,
    onTapHighlight: (HighlightRange) -> Unit
) {
    val pages = uiState.pages
    if (pages.isEmpty()) return

    val pagerState = rememberPagerState(
        initialPage = uiState.targetPagerIndex.coerceIn(0, pages.lastIndex),
        pageCount = { pages.size }
    )
    val coroutineScope = rememberCoroutineScope()
    val latestPages by rememberUpdatedState(pages)
    val latestOnPageSettled by rememberUpdatedState(onPageSettled)
    val lastSyncedGeneration = remember { mutableLongStateOf(-1L) }

    SideEffect {
        if (lastSyncedGeneration.longValue != uiState.windowGeneration) {
            lastSyncedGeneration.longValue = uiState.windowGeneration
            val target = uiState.targetPagerIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
            if (!pagerState.isScrollInProgress && pagerState.currentPage != target) {
                pagerState.requestScrollToPage(target)
            }
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                onUserScrollStateChanged(scrolling)
            }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage to pagerState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { (settledIndex, scrolling) ->
                if (!scrolling && settledIndex in latestPages.indices) {
                    latestOnPageSettled(latestPages[settledIndex])
                }
            }
    }

    HorizontalPager(
        state = pagerState,
        key = { index -> latestPages.getOrNull(index)?.stableKey ?: "page-$index" },
        modifier = Modifier.fillMaxSize()
    ) { pageIndex ->
        val pageItem = pages.getOrNull(pageIndex) ?: return@HorizontalPager
        val chapterHighlights = uiState.highlightsByChapter[pageItem.chapterIndex].orEmpty()
        val activePendingSelection = uiState.pendingSelection?.takeIf {
            it.chapterIndex == pageItem.chapterIndex
        }

        ReaderPageCanvas(
            pageItem = pageItem,
            pageIndexInPager = pageIndex,
            totalPagerCount = pages.size,
            pagerState = pagerState,
            palette = palette,
            bodyTextStyle = bodyTextStyle,
            paragraphSpacingDp = paragraphSpacingDp,
            blankLineHeightDp = blankLineHeightDp,
            focusLineMode = focusLineMode,
            chapterHighlights = chapterHighlights,
            pendingSelection = activePendingSelection,
            showFocusStepper = focusLineMode && !uiState.isMenuVisible && uiState.activePanel == ReaderPanel.NONE,
            onToggleMenu = onToggleMenu,
            onAnimateToPage = { target ->
                coroutineScope.launch { pagerState.animateScrollToPage(target) }
            },
            onTextRangeSelected = onTextRangeSelected,
            onTapHighlight = onTapHighlight
        )
    }
}

@Composable
private fun ReaderPageCanvas(
    pageItem: ReaderPageItem,
    pageIndexInPager: Int,
    totalPagerCount: Int,
    pagerState: PagerState,
    palette: ReaderPalette,
    bodyTextStyle: TextStyle,
    paragraphSpacingDp: Dp,
    blankLineHeightDp: Dp,
    focusLineMode: Boolean,
    chapterHighlights: List<HighlightRange>,
    pendingSelection: TextSelectionState?,
    showFocusStepper: Boolean,
    onToggleMenu: () -> Unit,
    onAnimateToPage: (Int) -> Unit,
    onTextRangeSelected: (chapterIndex: Int, startOffset: Int, endOffset: Int) -> Unit,
    onTapHighlight: (HighlightRange) -> Unit
) {
    val paragraphs = remember(pageItem.stableKey) {
        ComposeTextMeasureCallback.splitPageParagraphs(pageItem.slice.text)
    }
    val paragraphLayouts = remember(pageItem.stableKey) { mutableStateMapOf<Int, TextLayoutResult>() }
    val paragraphTopOffsets = remember(pageItem.stableKey) { mutableStateMapOf<Int, Float>() }
    var focusedLineIndex by remember(pageItem.stableKey) { mutableIntStateOf(0) }
    var liveDragSelection by remember(pageItem.stableKey) { mutableStateOf<Pair<Int, Int>?>(null) }
    var dragOriginChapterOffset by remember(pageItem.stableKey) { mutableIntStateOf(-1) }

    val latestHighlights by rememberUpdatedState(chapterHighlights)
    val latestOnTapHighlight by rememberUpdatedState(onTapHighlight)
    val latestOnTextRangeSelected by rememberUpdatedState(onTextRangeSelected)

    fun resolveChapterOffsetAt(pos: Offset, strictHitBox: Boolean = false): Int? {
        val nonBlankIndices = paragraphs.indices.filter { !paragraphs[it].isBlankLine && paragraphLayouts[it] != null }
        if (nonBlankIndices.isEmpty()) return null

        var targetIdx = -1
        var bestDist = Float.MAX_VALUE
        for (idx in nonBlankIndices) {
            val layout = paragraphLayouts[idx] ?: continue
            val top = paragraphTopOffsets[idx] ?: 0f
            val bottom = top + layout.size.height
            if (pos.y in top..bottom) {
                targetIdx = idx
                break
            }
            if (!strictHitBox) {
                val dist = if (pos.y < top) top - pos.y else pos.y - bottom
                if (dist < bestDist) {
                    bestDist = dist
                    targetIdx = idx
                }
            }
        }
        if (targetIdx < 0) return null

        val para = paragraphs[targetIdx]
        val layout = paragraphLayouts[targetIdx] ?: return null
        val top = paragraphTopOffsets[targetIdx] ?: 0f
        val localY = (pos.y - top).coerceIn(0f, layout.size.height.toFloat().coerceAtLeast(1f))
        if (strictHitBox) {
            val line = layout.getLineForVerticalPosition(localY)
            if (pos.x < layout.getLineLeft(line) - 12f || pos.x > layout.getLineRight(line) + 12f) {
                return null
            }
        }
        val localPos = Offset(
            x = pos.x.coerceIn(0f, layout.size.width.toFloat().coerceAtLeast(1f)),
            y = localY
        )
        val localOffset = layout.getOffsetForPosition(localPos).coerceIn(0, para.text.length)
        return pageItem.slice.startCharOffset + para.startOffsetInSlice + localOffset
    }

    val pageLines = remember(
        paragraphs,
        paragraphLayouts.toMap(),
        paragraphTopOffsets.toMap()
    ) {
        val list = ArrayList<PageLineLocation>()
        paragraphs.forEachIndexed { paraIdx, para ->
            if (!para.isBlankLine) {
                val layout = paragraphLayouts[paraIdx]
                val paraTop = paragraphTopOffsets[paraIdx] ?: 0f
                if (layout != null) {
                    for (lineIdx in 0 until layout.lineCount) {
                        val localTop = layout.getLineTop(lineIdx)
                        val localBottom = layout.getLineBottom(lineIdx)
                        list += PageLineLocation(
                            paragraphIndex = paraIdx,
                            localLineIndex = lineIdx,
                            startOffsetInPara = layout.getLineStart(lineIdx),
                            endOffsetInPara = layout.getLineEnd(lineIdx, visibleEnd = true),
                            lineTopInParent = paraTop + localTop,
                            lineBottomInParent = paraTop + localBottom,
                            lineTopLocal = localTop,
                            lineBottomLocal = localBottom
                        )
                    }
                }
            }
        }
        list
    }

    val safeFocusedLineIdx = focusedLineIndex.coerceIn(0, (pageLines.size - 1).coerceAtLeast(0))
    val activeFocusRef = pageLines.getOrNull(safeFocusedLineIdx)

    val selectionHighlightSpan = liveDragSelection
        ?: pendingSelection?.let { it.startOffset to it.endOffset }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(pagerState, totalPagerCount, pageItem.stableKey) {
                detectTapGestures { offset ->
                    val tappedChapterOffset = resolveChapterOffsetAt(offset, strictHitBox = true)
                    val hitHighlight = if (tappedChapterOffset != null) {
                        latestHighlights.firstOrNull {
                            tappedChapterOffset in it.startOffset until it.endOffset
                        }
                    } else null

                    if (hitHighlight != null) {
                        latestOnTapHighlight(hitHighlight)
                        return@detectTapGestures
                    }

                    val width = size.width.toFloat().coerceAtLeast(1f)
                    when {
                        offset.x < width * 0.25f -> {
                            val prev = pagerState.currentPage - 1
                            if (prev >= 0) onAnimateToPage(prev)
                        }
                        offset.x > width * 0.75f -> {
                            val next = pagerState.currentPage + 1
                            if (next < totalPagerCount) onAnimateToPage(next)
                        }
                        else -> onToggleMenu()
                    }
                }
            }
            .pointerInput(pageItem.stableKey) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { startPos ->
                        val chapterOffset = resolveChapterOffsetAt(startPos)
                        if (chapterOffset != null && pageItem.slice.text.isNotEmpty()) {
                            dragOriginChapterOffset = chapterOffset
                            val sliceOffset = (chapterOffset - pageItem.slice.startCharOffset)
                                .coerceIn(0, (pageItem.slice.text.length - 1).coerceAtLeast(0))
                            val (clauseStart, clauseEnd) = ReaderViewModel.findClauseBounds(
                                text = pageItem.slice.text,
                                anchorOffset = sliceOffset
                            )
                            liveDragSelection = (pageItem.slice.startCharOffset + clauseStart) to
                                (pageItem.slice.startCharOffset + clauseEnd)
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val origin = dragOriginChapterOffset
                        if (origin >= 0) {
                            val currentOffset = resolveChapterOffsetAt(change.position)
                            if (currentOffset != null) {
                                val start = minOf(origin, currentOffset)
                                val end = maxOf(origin, currentOffset)
                                    .coerceAtLeast(start + 1)
                                    .coerceAtMost(pageItem.slice.endCharOffset)
                                liveDragSelection = start to end
                            }
                        }
                    },
                    onDragEnd = {
                        val finalized = liveDragSelection
                        liveDragSelection = null
                        dragOriginChapterOffset = -1
                        if (finalized != null && finalized.first < finalized.second) {
                            latestOnTextRangeSelected(
                                pageItem.chapterIndex,
                                finalized.first,
                                finalized.second
                            )
                        }
                    },
                    onDragCancel = {
                        liveDragSelection = null
                        dragOriginChapterOffset = -1
                    }
                )
            }
            .pointerInput(focusLineMode, pageLines) {
                if (!focusLineMode) return@pointerInput
                detectVerticalDragGestures { change, _ ->
                    if (pageLines.isEmpty()) return@detectVerticalDragGestures
                    val y = change.position.y
                    val matched = pageLines.indexOfFirst { y <= it.lineBottomInParent }
                    val newIdx = if (matched >= 0) matched else pageLines.lastIndex
                    if (newIdx != focusedLineIndex) {
                        focusedLineIndex = newIdx
                    }
                }
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            paragraphs.forEachIndexed { idx, para ->
                if (para.isBlankLine) {
                    Spacer(modifier = Modifier.height(blankLineHeightDp))
                } else {
                    val isFocusPara = focusLineMode && activeFocusRef?.paragraphIndex == idx
                    val paraChapterStart = pageItem.slice.startCharOffset + para.startOffsetInSlice
                    val annotatedText = remember(
                        para.text,
                        paraChapterStart,
                        focusLineMode,
                        isFocusPara,
                        activeFocusRef,
                        chapterHighlights,
                        selectionHighlightSpan,
                        palette
                    ) {
                        buildParagraphAnnotatedString(
                            raw = para.text,
                            paraChapterStart = paraChapterStart,
                            chapterHighlights = chapterHighlights,
                            selectionSpan = selectionHighlightSpan,
                            focusLineMode = focusLineMode,
                            activeRef = if (isFocusPara) activeFocusRef else null,
                            palette = palette
                        )
                    }

                    Text(
                        text = annotatedText,
                        style = bodyTextStyle,
                        onTextLayout = { layout -> paragraphLayouts[idx] = layout },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coords ->
                                paragraphTopOffsets[idx] = coords.positionInParent().y
                            }
                            .drawBehind {
                                if (isFocusPara && activeFocusRef != null) {
                                    val padX = 6.dp.toPx()
                                    val top = activeFocusRef.lineTopLocal
                                    val height = (activeFocusRef.lineBottomLocal - top).coerceAtLeast(1f)
                                    drawRoundRect(
                                        color = palette.focusLineBackground,
                                        topLeft = Offset(-padX, top),
                                        size = Size(size.width + padX * 2f, height),
                                        cornerRadius = CornerRadius(6.dp.toPx())
                                    )
                                }
                            }
                    )
                    if (idx < paragraphs.lastIndex) {
                        Spacer(modifier = Modifier.height(paragraphSpacingDp))
                    }
                }
            }
        }

        if (showFocusStepper && pageLines.isNotEmpty()) {
            Surface(
                color = palette.surface.copy(alpha = 0.92f),
                shape = RoundedCornerShape(20.dp),
                tonalElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (safeFocusedLineIdx > 0) {
                                focusedLineIndex = safeFocusedLineIdx - 1
                            } else if (pageIndexInPager > 0) {
                                onAnimateToPage(pageIndexInPager - 1)
                            }
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.KeyboardArrowUp,
                            contentDescription = "上一行",
                            tint = palette.primaryText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = "焦点行 ${safeFocusedLineIdx + 1}/${pageLines.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.secondaryText,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                    IconButton(
                        onClick = {
                            if (safeFocusedLineIdx + 1 < pageLines.size) {
                                focusedLineIndex = safeFocusedLineIdx + 1
                            } else if (pageIndexInPager + 1 < totalPagerCount) {
                                onAnimateToPage(pageIndexInPager + 1)
                            }
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.KeyboardArrowDown,
                            contentDescription = "下一行",
                            tint = palette.primaryText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun buildParagraphAnnotatedString(
    raw: String,
    paraChapterStart: Int,
    chapterHighlights: List<HighlightRange>,
    selectionSpan: Pair<Int, Int>?,
    focusLineMode: Boolean,
    activeRef: PageLineLocation?,
    palette: ReaderPalette
): AnnotatedString {
    val paraChapterEnd = paraChapterStart + raw.length
    val highlightAlpha = if (palette.theme.isDark) 0.36f else 0.52f

    return buildAnnotatedString {
        append(raw)

        // Apply saved highlights overlapping this paragraph
        for (h in chapterHighlights) {
            if (h.endOffset <= paraChapterStart || h.startOffset >= paraChapterEnd) continue
            val localStart = (h.startOffset - paraChapterStart).coerceIn(0, raw.length)
            val localEnd = (h.endOffset - paraChapterStart).coerceIn(localStart, raw.length)
            if (localEnd > localStart) {
                addStyle(
                    SpanStyle(
                        background = Color(h.colorHex).copy(alpha = highlightAlpha),
                        textDecoration = if (!h.noteContent.isNullOrBlank()) TextDecoration.Underline else null
                    ),
                    localStart,
                    localEnd
                )
            }
        }

        // Apply active long-press drag selection span
        if (selectionSpan != null) {
            val (selStart, selEnd) = selectionSpan
            if (selEnd > paraChapterStart && selStart < paraChapterEnd) {
                val localStart = (selStart - paraChapterStart).coerceIn(0, raw.length)
                val localEnd = (selEnd - paraChapterStart).coerceIn(localStart, raw.length)
                if (localEnd > localStart) {
                    addStyle(
                        SpanStyle(background = palette.accent.copy(alpha = 0.28f)),
                        localStart,
                        localEnd
                    )
                }
            }
        }

        // Apply focus-line dimming without altering font weights
        if (focusLineMode) {
            val dimmedColor = palette.primaryText.copy(alpha = 0.35f)
            if (activeRef == null) {
                addStyle(SpanStyle(color = dimmedColor), 0, raw.length)
            } else {
                val start = activeRef.startOffsetInPara.coerceIn(0, raw.length)
                val end = activeRef.endOffsetInPara.coerceIn(start, raw.length)
                if (start > 0) {
                    addStyle(SpanStyle(color = dimmedColor), 0, start)
                }
                if (end > start) {
                    addStyle(SpanStyle(color = palette.primaryText), start, end)
                }
                if (end < raw.length) {
                    addStyle(SpanStyle(color = dimmedColor), end, raw.length)
                }
            }
        }
    }
}

@Composable
private fun ReaderHeaderStrip(
    chapterTitle: String,
    bookTitle: String,
    palette: ReaderPalette,
    horizontalMarginDp: Dp
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalMarginDp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = chapterTitle.ifEmpty { bookTitle },
            style = MaterialTheme.typography.labelMedium,
            color = palette.secondaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (chapterTitle.isNotEmpty() && bookTitle.isNotEmpty()) {
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = bookTitle,
                style = MaterialTheme.typography.labelMedium,
                color = palette.secondaryText.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ReaderFooterStrip(
    pageInChapter: Int,
    totalPagesInChapter: Int,
    chapterIndex: Int,
    totalChapters: Int,
    overallProgressPercent: Int,
    palette: ReaderPalette,
    horizontalMarginDp: Dp
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalMarginDp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "本章 $pageInChapter / $totalPagesInChapter 页",
            style = MaterialTheme.typography.labelMedium,
            color = palette.secondaryText
        )
        Text(
            text = "第 $chapterIndex/$totalChapters 章 · $overallProgressPercent%",
            style = MaterialTheme.typography.labelMedium,
            color = palette.secondaryText
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderOverlayBars(
    visible: Boolean,
    bookTitle: String,
    chapterTitle: String,
    chapterProgressLabel: String,
    hasPrevChapter: Boolean,
    hasNextChapter: Boolean,
    isBookmarked: Boolean,
    focusLineEnabled: Boolean,
    activeTheme: ReaderTheme,
    palette: ReaderPalette,
    onBack: () -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onOpenSearchSheet: () -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenAnnotationsSheet: () -> Unit,
    onOpenChapterDrawer: () -> Unit,
    onOpenTypographySettings: () -> Unit,
    onCycleQuickTheme: () -> Unit,
    onToggleFocusLine: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                color = palette.surface,
                tonalElevation = 2.dp
            ) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = bookTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = palette.primaryText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (chapterTitle.isNotEmpty()) {
                                    Text(
                                        text = chapterTitle,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = palette.secondaryText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = "返回书架",
                                    tint = palette.primaryText
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = onOpenSearchSheet) {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = "全书搜索",
                                    tint = palette.primaryText
                                )
                            }
                            IconButton(onClick = onToggleBookmark) {
                                Icon(
                                    imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                    contentDescription = if (isBookmarked) "取消书签" else "添加书签",
                                    tint = if (isBookmarked) palette.accent else palette.primaryText
                                )
                            }
                            IconButton(onClick = onOpenAnnotationsSheet) {
                                Icon(
                                    imageVector = Icons.Outlined.Bookmarks,
                                    contentDescription = "书签与笔记",
                                    tint = palette.primaryText
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = palette.surface
                        )
                    )
                    HorizontalDivider(color = palette.divider)
                }
            }
        }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = palette.surface,
                tonalElevation = 4.dp
            ) {
                Column(modifier = Modifier.navigationBarsPadding()) {
                    HorizontalDivider(color = palette.divider)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onPreviousChapter, enabled = hasPrevChapter) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                                contentDescription = "上一章",
                                tint = if (hasPrevChapter) palette.primaryText else palette.secondaryText.copy(alpha = 0.35f)
                            )
                        }
                        Text(
                            text = chapterProgressLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(onClick = onNextChapter, enabled = hasNextChapter) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = "下一章",
                                tint = if (hasNextChapter) palette.primaryText else palette.secondaryText.copy(alpha = 0.35f)
                            )
                        }
                    }

                    HorizontalDivider(color = palette.divider)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BottomControlItem(
                            icon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.FormatListBulleted,
                                    contentDescription = "目录",
                                    tint = palette.primaryText
                                )
                            },
                            label = "目录",
                            labelColor = palette.primaryText,
                            onClick = onOpenChapterDrawer
                        )
                        BottomControlItem(
                            icon = {
                                Icon(
                                    imageVector = Icons.Outlined.Tune,
                                    contentDescription = "排版",
                                    tint = palette.primaryText
                                )
                            },
                            label = "排版",
                            labelColor = palette.primaryText,
                            onClick = onOpenTypographySettings
                        )
                        BottomControlItem(
                            icon = {
                                Icon(
                                    imageVector = Icons.Outlined.CenterFocusStrong,
                                    contentDescription = "焦点行",
                                    tint = if (focusLineEnabled) palette.accent else palette.primaryText
                                )
                            },
                            label = if (focusLineEnabled) "焦点·开" else "焦点行",
                            labelColor = if (focusLineEnabled) palette.accent else palette.primaryText,
                            onClick = onToggleFocusLine
                        )
                        BottomControlItem(
                            icon = {
                                Icon(
                                    imageVector = Icons.Outlined.Palette,
                                    contentDescription = "切换主题",
                                    tint = palette.primaryText
                                )
                            },
                            label = activeTheme.displayName,
                            labelColor = palette.primaryText,
                            onClick = onCycleQuickTheme
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomControlItem(
    icon: @Composable () -> Unit,
    label: String,
    labelColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            icon()
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = labelColor
            )
        }
    }
}

@Composable
private fun ReaderErrorView(
    message: String,
    isFileMissing: Boolean,
    palette: ReaderPalette,
    onBack: () -> Unit,
    onRemoveMissingBook: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(44.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = palette.primaryText
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onBack) {
                Text("返回书架")
            }
            if (isFileMissing) {
                OutlinedButton(onClick = onRemoveMissingBook) {
                    Text("移除失效记录")
                }
            }
        }
    }
}
