package com.inktrail.reader.ui.bookshelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inktrail.reader.domain.model.Book
import com.inktrail.reader.domain.model.ImportProgress
import com.inktrail.reader.domain.repository.BookRepository
import com.inktrail.reader.domain.usecase.ImportBookUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BookshelfViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val importBookUseCase: ImportBookUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookshelfUiState())
    val uiState: StateFlow<BookshelfUiState> = _uiState.asStateFlow()

    private var importJob: Job? = null

    init {
        viewModelScope.launch {
            bookRepository.observeBooks().collect { books ->
                _uiState.update { state ->
                    state.copy(
                        books = books,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun onImportBookUri(uriString: String) {
        if (_uiState.value.isImporting) return
        importJob?.cancel()
        importJob = viewModelScope.launch {
            importBookUseCase(uriString).collect { progress ->
                when (progress) {
                    is ImportProgress.Completed -> {
                        _uiState.update {
                            it.copy(
                                importProgress = null,
                                errorMessage = null
                            )
                        }
                    }
                    is ImportProgress.Failed -> {
                        _uiState.update {
                            it.copy(
                                importProgress = null,
                                errorMessage = progress.message
                            )
                        }
                    }
                    else -> {
                        _uiState.update { it.copy(importProgress = progress) }
                    }
                }
            }
        }
    }

    fun toggleLayoutMode() {
        _uiState.update { it.copy(isGridMode = !it.isGridMode) }
    }

    fun requestDeleteBook(book: Book) {
        _uiState.update { it.copy(pendingDeleteBook = book) }
    }

    fun dismissDeleteDialog() {
        _uiState.update { it.copy(pendingDeleteBook = null) }
    }

    fun confirmDeleteBook() {
        val target = _uiState.value.pendingDeleteBook ?: return
        _uiState.update { it.copy(pendingDeleteBook = null) }
        viewModelScope.launch {
            bookRepository.deleteBook(target.id)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
