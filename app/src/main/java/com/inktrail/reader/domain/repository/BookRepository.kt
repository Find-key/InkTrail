package com.inktrail.reader.domain.repository

import com.inktrail.reader.domain.model.Book
import com.inktrail.reader.domain.model.Chapter
import kotlinx.coroutines.flow.Flow

interface BookRepository {
    fun observeBooks(): Flow<List<Book>>

    fun observeBook(bookId: String): Flow<Book?>

    suspend fun getBook(bookId: String): Book?

    suspend fun getChapters(bookId: String): List<Chapter>

    fun observeChapters(bookId: String): Flow<List<Chapter>>

    suspend fun getChapter(bookId: String, chapterIndex: Int): Chapter?

    suspend fun readChapterRawText(book: Book, chapter: Chapter): String

    suspend fun saveImportedBook(book: Book, chapters: List<Chapter>)

    suspend fun updateReadingProgress(bookId: String, chapterIndex: Int, charOffset: Int)

    suspend fun deleteBook(bookId: String)
}
