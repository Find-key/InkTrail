package com.inktrail.reader.ui.bookshelf

import com.inktrail.reader.domain.model.Book
import com.inktrail.reader.domain.model.ImportProgress
import com.inktrail.reader.domain.repository.BookRepository
import com.inktrail.reader.domain.usecase.ImportBookUseCase
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BookshelfViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `updates books state, handles import lifecycle, and confirms deletion`() = runTest {
        val sampleBook = Book(
            id = "id-1",
            title = "示例书籍",
            filePath = "/tmp/id-1.txt",
            fileSizeBytes = 1024L,
            charsetName = "UTF-8",
            totalChapters = 10,
            currentChapterIndex = 2,
            currentCharOffset = 50,
            lastReadAt = 1000L,
            createdAt = 500L
        )
        val booksFlow = MutableStateFlow(listOf(sampleBook))
        val repo = mockk<BookRepository>(relaxed = true)
        every { repo.observeBooks() } returns booksFlow

        val importUseCase = mockk<ImportBookUseCase>()
        every { importUseCase("content://ok.txt") } returns flowOf(
            ImportProgress.Copying(100L, 100L),
            ImportProgress.Completed(sampleBook)
        )
        every { importUseCase("content://fail.txt") } returns flowOf(
            ImportProgress.Failed("空文件")
        )

        val vm = BookshelfViewModel(repo, importUseCase)

        assertFalse(vm.uiState.value.isLoading)
        assertEquals(1, vm.uiState.value.books.size)

        vm.onImportBookUri("content://ok.txt")
        assertNull(vm.uiState.value.importProgress)
        assertNull(vm.uiState.value.errorMessage)

        vm.onImportBookUri("content://fail.txt")
        assertEquals("空文件", vm.uiState.value.errorMessage)
        vm.clearError()
        assertNull(vm.uiState.value.errorMessage)

        vm.toggleLayoutMode()
        assertTrue(vm.uiState.value.isGridMode)

        vm.requestDeleteBook(sampleBook)
        assertEquals(sampleBook, vm.uiState.value.pendingDeleteBook)
        vm.confirmDeleteBook()
        assertNull(vm.uiState.value.pendingDeleteBook)
        coVerify(exactly = 1) { repo.deleteBook("id-1") }
    }
}
