package com.inktrail.reader.ui.bookshelf

import com.inktrail.reader.domain.model.Book
import com.inktrail.reader.domain.model.ImportProgress

data class BookshelfUiState(
    val books: List<Book> = emptyList(),
    val isLoading: Boolean = true,
    val isGridMode: Boolean = false,
    val importProgress: ImportProgress? = null,
    val pendingDeleteBook: Book? = null,
    val errorMessage: String? = null
) {
    val isImporting: Boolean
        get() = importProgress != null &&
            importProgress !is ImportProgress.Completed &&
            importProgress !is ImportProgress.Failed
}
