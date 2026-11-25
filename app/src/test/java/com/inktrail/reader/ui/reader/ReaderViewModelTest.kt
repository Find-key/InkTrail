package com.inktrail.reader.ui.reader

import androidx.lifecycle.SavedStateHandle
import com.inktrail.reader.data.local.storage.BookFileMissingException
import com.inktrail.reader.domain.model.Book
import com.inktrail.reader.domain.model.Chapter
import com.inktrail.reader.domain.model.HighlightRange
import com.inktrail.reader.domain.model.ReaderPreferences
import com.inktrail.reader.domain.model.ReaderTheme
import com.inktrail.reader.domain.repository.AnnotationRepository
import com.inktrail.reader.domain.repository.BookRepository
import com.inktrail.reader.domain.repository.ReaderPreferencesRepository
import com.inktrail.reader.domain.usecase.LoadChapterPagesUseCase
import com.inktrail.reader.domain.usecase.ManageAnnotationUseCase
import com.inktrail.reader.domain.usecase.SearchInBookUseCase
import com.inktrail.reader.engine.cleaner.TextCleaner
import com.inktrail.reader.engine.paginator.MeasureCallback
import com.inktrail.reader.engine.paginator.Paginator
import com.inktrail.reader.engine.search.BookSearchEngine
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        bookId: String,
        bookRepo: BookRepository,
        prefsRepo: ReaderPreferencesRepository,
        annotationRepo: AnnotationRepository = defaultAnnotationRepo()
    ): ReaderViewModel {
        return ReaderViewModel(
            savedStateHandle = SavedStateHandle(mapOf("bookId" to bookId)),
            bookRepository = bookRepo,
            preferencesRepository = prefsRepo,
            annotationRepository = annotationRepo,
            loadChapterPagesUseCase = LoadChapterPagesUseCase(
                bookRepository = bookRepo,
                textCleaner = TextCleaner(),
                paginator = Paginator(),
                defaultDispatcher = testDispatcher
            ),
            searchInBookUseCase = SearchInBookUseCase(
                bookRepository = bookRepo,
                textCleaner = TextCleaner(),
                searchEngine = BookSearchEngine(),
                ioDispatcher = testDispatcher
            ),
            manageAnnotationUseCase = ManageAnnotationUseCase(annotationRepo),
            paginator = Paginator()
        )
    }

    private fun defaultAnnotationRepo(): AnnotationRepository {
        val repo = mockk<AnnotationRepository>(relaxed = true)
        every { repo.observeAllAnnotations(any()) } returns flowOf(emptyList())
        return repo
    }

    @Test
    fun `loads saved chapter anchor, preloads neighbors, and preserves anchor on reflow`() = runTest {
        val book = Book(
            id = "book-1",
            title = "墨辙测试",
            filePath = "/tmp/book-1.txt",
            fileSizeBytes = 2048L,
            charsetName = "UTF-8",
            totalChapters = 4,
            currentChapterIndex = 1,
            currentCharOffset = 45,
            lastReadAt = 100L,
            createdAt = 50L
        )
        val chapters = (0..3).map { idx ->
            Chapter(
                id = idx.toLong() + 1L,
                bookId = "book-1",
                chapterIndex = idx,
                title = "第${idx + 1}章",
                startByteOffset = idx * 500L,
                endByteOffset = (idx + 1) * 500L
            )
        }

        val repo = mockk<BookRepository>(relaxed = true)
        every { repo.observeBook("book-1") } returns flowOf(book)
        every { repo.observeChapters("book-1") } returns flowOf(chapters)
        coEvery { repo.readChapterRawText(book, any()) } answers {
            val ch = secondArg<Chapter>()
            "第${ch.chapterIndex + 1}章内容：".plus("墨辙流式分页验证。".repeat(12))
        }

        val prefsFlow = MutableStateFlow(ReaderPreferences())
        val prefsRepo = mockk<ReaderPreferencesRepository>(relaxed = true)
        every { prefsRepo.preferences } returns prefsFlow

        val viewModel = createViewModel("book-1", repo, prefsRepo)

        val wideCallback = MeasureCallback { text, start -> minOf(text.length, start + 40) }
        val wideSpec = PaginationSpec(900, 1400, 18f, 1.6f, 20, 10)
        viewModel.updatePaginationEnv(wideSpec, wideCallback)

        val state1 = viewModel.uiState.value
        assertFalse(state1.isLoading)
        assertEquals(1, state1.currentChapterIndex)
        assertEquals(setOf(0, 1, 2), state1.pages.map { it.chapterIndex }.toSet())
        val targetSlice1 = state1.pages[state1.targetPagerIndex].slice
        assertTrue(45 in targetSlice1.startCharOffset until targetSlice1.endCharOffset)

        val narrowCallback = MeasureCallback { text, start -> minOf(text.length, start + 18) }
        val narrowSpec = PaginationSpec(600, 1000, 24f, 1.6f, 20, 10)
        viewModel.updatePaginationEnv(narrowSpec, narrowCallback)

        val state2 = viewModel.uiState.value
        val targetSlice2 = state2.pages[state2.targetPagerIndex].slice
        assertTrue(45 in targetSlice2.startCharOffset until targetSlice2.endCharOffset)

        val ch2FirstPage = state2.pages.first { it.chapterIndex == 2 }
        viewModel.onPageSettled(ch2FirstPage)

        val state3 = viewModel.uiState.value
        assertEquals(2, state3.currentChapterIndex)
        assertEquals(setOf(1, 2, 3), state3.pages.map { it.chapterIndex }.toSet())
        coVerify(atLeast = 1) {
            repo.updateReadingProgress("book-1", 2, ch2FirstPage.slice.startCharOffset)
        }
    }

    @Test
    fun `surfaces missing file error cleanly and deletes record on recovery action`() = runTest {
        val book = Book("b2", "丢失", "/tmp/missing.txt", 100L, "UTF-8", 1, 0, 0, 0L, 0L)
        val ch0 = Chapter(1L, "b2", 0, "第一章", 0L, 100L)

        val repo = mockk<BookRepository>(relaxed = true)
        every { repo.observeBook("b2") } returns flowOf(book)
        every { repo.observeChapters("b2") } returns flowOf(listOf(ch0))
        coEvery { repo.readChapterRawText(book, ch0) } throws BookFileMissingException("/tmp/missing.txt")

        val prefsRepo = mockk<ReaderPreferencesRepository>(relaxed = true)
        every { prefsRepo.preferences } returns flowOf(ReaderPreferences())

        val viewModel = createViewModel("b2", repo, prefsRepo)
        viewModel.updatePaginationEnv(
            PaginationSpec(800, 1200, 18f, 1.6f, 20, 10),
            MeasureCallback { text, start -> minOf(text.length, start + 100) }
        )

        val state = viewModel.uiState.value
        assertTrue(state.isFileMissing)
        assertFalse(state.isLoading)
        assertTrue(state.errorMessage?.contains("missing.txt") == true)

        viewModel.removeMissingBookRecord()
        coVerify(exactly = 1) { repo.deleteBook("b2") }
    }

    @Test
    fun `toggling cleaner options remaps anchor to keep reading sentence on target page`() = runTest {
        val rawBody = buildString {
            append("第一章 序幕\n\n\n")
            append("这是一段很长的前言文字，用于拉开偏移。\n".repeat(4))
            append("关键锚点句子在这里，不能飘走。\n")
            append("后续段落文字内容。".repeat(6))
        }
        val book = Book("b3", "清洗测试", "/tmp/b3.txt", 1000L, "UTF-8", 2, 0, 0, 0L, 0L)
        val ch0 = Chapter(1L, "b3", 0, "第一章 序幕", 0L, 500L)
        val ch1 = Chapter(2L, "b3", 1, "第二章", 500L, 1000L)

        val repo = mockk<BookRepository>(relaxed = true)
        every { repo.observeBook("b3") } returns flowOf(book)
        every { repo.observeChapters("b3") } returns flowOf(listOf(ch0, ch1))
        coEvery { repo.readChapterRawText(book, ch0) } returns rawBody
        coEvery { repo.readChapterRawText(book, ch1) } returns "第二章内容。"

        val prefsFlow = MutableStateFlow(ReaderPreferences(cleanerEnabled = true, normalizeParagraphIndent = false))
        val prefsRepo = mockk<ReaderPreferencesRepository>(relaxed = true)
        every { prefsRepo.preferences } returns prefsFlow

        val viewModel = createViewModel("b3", repo, prefsRepo)
        val callback = MeasureCallback { text, start -> minOf(text.length, start + 35) }
        viewModel.updatePaginationEnv(PaginationSpec(800, 1200, 18f, 1.6f, 20, 10), callback)

        val anchorPage = viewModel.uiState.value.pages.first { it.slice.text.contains("关键锚点句子") }
        viewModel.onPageSettled(anchorPage)

        prefsFlow.value = ReaderPreferences(cleanerEnabled = false, normalizeParagraphIndent = true)

        val reflowedState = viewModel.uiState.value
        val activeSlice = reflowedState.pages[reflowedState.targetPagerIndex].slice
        assertTrue(
            "Expected active page after cleaner toggle to preserve anchor, got: ${activeSlice.text}",
            activeSlice.text.contains("关键锚点句子") || activeSlice.text.contains("不能飘走")
        )
    }

    @Test
    fun `panel state transitions and quick theme cycling update state and preferences`() = runTest {
        val book = Book("b4", "面板测试", "/tmp/b4.txt", 200L, "UTF-8", 2, 0, 0, 0L, 0L)
        val ch0 = Chapter(1L, "b4", 0, "第一章", 0L, 100L)
        val ch1 = Chapter(2L, "b4", 1, "第二章", 100L, 200L)

        val repo = mockk<BookRepository>(relaxed = true)
        every { repo.observeBook("b4") } returns flowOf(book)
        every { repo.observeChapters("b4") } returns flowOf(listOf(ch0, ch1))
        coEvery { repo.readChapterRawText(book, any()) } returns "正文测试内容。"

        val prefsFlow = MutableStateFlow(ReaderPreferences(theme = ReaderTheme.PAPER_WHITE))
        val prefsRepo = mockk<ReaderPreferencesRepository>(relaxed = true)
        every { prefsRepo.preferences } returns prefsFlow

        val viewModel = createViewModel("b4", repo, prefsRepo)

        viewModel.toggleMenu()
        assertTrue(viewModel.uiState.value.isMenuVisible)

        viewModel.openChapterDrawer()
        assertFalse(viewModel.uiState.value.isMenuVisible)
        assertEquals(ReaderPanel.CHAPTER_DRAWER, viewModel.uiState.value.activePanel)

        viewModel.openTypographySettings()
        assertEquals(ReaderPanel.TYPOGRAPHY_SETTINGS, viewModel.uiState.value.activePanel)

        viewModel.openSearchSheet()
        assertEquals(ReaderPanel.SEARCH_SHEET, viewModel.uiState.value.activePanel)

        viewModel.openAnnotationsSheet()
        assertEquals(ReaderPanel.ANNOTATIONS_SHEET, viewModel.uiState.value.activePanel)

        viewModel.closeActivePanel()
        assertEquals(ReaderPanel.NONE, viewModel.uiState.value.activePanel)

        viewModel.cycleQuickTheme()
        coVerify(exactly = 1) { prefsRepo.updateTheme(ReaderTheme.PARCHMENT) }

        viewModel.updateFocusLineMode(true)
        coVerify(exactly = 1) { prefsRepo.updateFocusLineMode(true) }
    }

    @Test
    fun `search and visible annotations respond in real time to spoiler free mode toggle`() = runTest {
        val book = Book("b5", "防剧透测试", "/tmp/b5.txt", 300L, "UTF-8", 2, 0, 0, 0L, 0L)
        val ch0 = Chapter(1L, "b5", 0, "第一章", 0L, 150L)
        val ch1 = Chapter(2L, "b5", 1, "第二章", 150L, 300L)

        val repo = mockk<BookRepository>(relaxed = true)
        every { repo.observeBook("b5") } returns flowOf(book)
        every { repo.observeChapters("b5") } returns flowOf(listOf(ch0, ch1))
        coEvery { repo.getChapters("b5") } returns listOf(ch0, ch1)
        coEvery { repo.readChapterRawText(book, ch0) } returns "第一章出现密钥线索。"
        coEvery { repo.readChapterRawText(book, ch1) } returns "第二章揭晓最终密钥真相。"

        val annotationsFlow = MutableStateFlow(
            listOf(
                HighlightRange(1L, "b5", 0, 0, 5, "第一章出现", null, 0xFFFFE082L),
                HighlightRange(2L, "b5", 1, 0, 5, "第二章揭晓", "剧透笔记", 0xFFFFE082L)
            )
        )
        val annotationRepo = mockk<AnnotationRepository>(relaxed = true)
        every { annotationRepo.observeAllAnnotations("b5") } returns annotationsFlow

        val prefsFlow = MutableStateFlow(ReaderPreferences(spoilerFreeMode = true))
        val prefsRepo = mockk<ReaderPreferencesRepository>(relaxed = true)
        every { prefsRepo.preferences } returns prefsFlow

        val viewModel = createViewModel("b5", repo, prefsRepo, annotationRepo)
        viewModel.updatePaginationEnv(
            PaginationSpec(800, 1200, 18f, 1.6f, 20, 10),
            MeasureCallback { text, start -> minOf(text.length, start + 100) }
        )

        // Under spoilerFreeMode = true at chapter 0, chapter 1 annotation is hidden
        assertEquals(1, viewModel.uiState.value.visibleAnnotations.size)

        viewModel.openSearchSheet()
        viewModel.updateSearchQuery("密钥")
        viewModel.executeSearch()
        assertEquals(1, viewModel.uiState.value.searchResults.size)
        assertEquals(0, viewModel.uiState.value.searchResults.single().chapterIndex)

        // Toggle spoilerFreeMode OFF -> both search results AND future annotations appear live
        prefsFlow.value = ReaderPreferences(spoilerFreeMode = false)
        assertEquals(2, viewModel.uiState.value.visibleAnnotations.size)
        assertEquals(2, viewModel.uiState.value.searchResults.size)
    }

    @Test
    fun `selection sentence expansion, highlight save, erase, bookmark and oversized line detection work`() = runTest {
        val oversizedLine = "墨".repeat(4200)
        val chapterText = "前面句子。这里是目标短语，点击后应该扩展。\n$oversizedLine"
        val book = Book("b6", "批注测试", "/tmp/b6.txt", 9000L, "UTF-8", 1, 0, 0, 0L, 0L)
        val ch0 = Chapter(1L, "b6", 0, "第一章", 0L, 9000L)

        val repo = mockk<BookRepository>(relaxed = true)
        every { repo.observeBook("b6") } returns flowOf(book)
        every { repo.observeChapters("b6") } returns flowOf(listOf(ch0))
        coEvery { repo.readChapterRawText(book, ch0) } returns chapterText

        val annotationRepo = mockk<AnnotationRepository>(relaxed = true)
        every { annotationRepo.observeAllAnnotations("b6") } returns flowOf(emptyList())
        coEvery { annotationRepo.toggleBookmark(any(), any(), any(), any()) } returns true

        val prefsRepo = mockk<ReaderPreferencesRepository>(relaxed = true)
        every { prefsRepo.preferences } returns flowOf(ReaderPreferences())

        val viewModel = createViewModel("b6", repo, prefsRepo, annotationRepo)
        viewModel.updatePaginationEnv(
            PaginationSpec(800, 1200, 18f, 1.6f, 20, 10),
            MeasureCallback { text, start -> minOf(text.length, start + 150) }
        )

        assertTrue(viewModel.uiState.value.oversizedLineWarningVisible)
        viewModel.dismissOversizedLineWarning()
        assertFalse(viewModel.uiState.value.oversizedLineWarningVisible)

        val start = chapterText.indexOf("目标短语")
        viewModel.onTextRangeSelected(0, start, start + 4)
        assertNotNull(viewModel.uiState.value.pendingSelection)

        viewModel.expandPendingSelectionToSentence()
        assertEquals("这里是目标短语，点击后应该扩展。", viewModel.uiState.value.pendingSelection?.selectedText)

        viewModel.savePendingSelectionHighlight(0xFFFFE082L, "重点句")
        assertNull(viewModel.uiState.value.pendingSelection)
        coVerify(exactly = 1) {
            annotationRepo.addOrMergeHighlight(
                bookId = "b6",
                chapterIndex = 0,
                startOffset = chapterText.indexOf("这里是目标短语"),
                endOffset = chapterText.indexOf("扩展。") + 3,
                selectedText = "这里是目标短语，点击后应该扩展。",
                colorHex = 0xFFFFE082L,
                noteContent = "重点句"
            )
        }

        viewModel.toggleCurrentChapterBookmark()
        coVerify(exactly = 1) { annotationRepo.toggleBookmark("b6", 0, any(), any()) }
        assertEquals("已添加书签", viewModel.uiState.value.statusMessage)
    }
}
