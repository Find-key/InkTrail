package com.inktrail.reader.data.repository

import com.inktrail.reader.data.local.db.BookDao
import com.inktrail.reader.data.local.db.ChapterDao
import com.inktrail.reader.data.local.db.toDomain
import com.inktrail.reader.data.local.db.toEntity
import com.inktrail.reader.data.local.storage.BookFileStorage
import com.inktrail.reader.data.local.storage.ChapterByteReader
import com.inktrail.reader.di.IoDispatcher
import com.inktrail.reader.domain.model.Book
import com.inktrail.reader.domain.model.Chapter
import com.inktrail.reader.domain.repository.BookRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookRepositoryImpl @Inject constructor(
    private val bookDao: BookDao,
    private val chapterDao: ChapterDao,
    private val bookFileStorage: BookFileStorage,
    private val chapterByteReader: ChapterByteReader,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : BookRepository {

    override fun observeBooks(): Flow<List<Book>> =
        bookDao.observeAllBooks().map { list -> list.map { it.toDomain() } }

    override fun observeBook(bookId: String): Flow<Book?> =
        bookDao.observeBookById(bookId).map { it?.toDomain() }

    override suspend fun getBook(bookId: String): Book? = withContext(ioDispatcher) {
        bookDao.getBookById(bookId)?.toDomain()
    }

    override suspend fun getChapters(bookId: String): List<Chapter> = withContext(ioDispatcher) {
        chapterDao.getChaptersForBook(bookId).map { it.toDomain() }
    }

    override fun observeChapters(bookId: String): Flow<List<Chapter>> =
        chapterDao.observeChaptersForBook(bookId).map { list -> list.map { it.toDomain() } }

    override suspend fun getChapter(bookId: String, chapterIndex: Int): Chapter? =
        withContext(ioDispatcher) {
            chapterDao.getChapter(bookId, chapterIndex)?.toDomain()
        }

    override suspend fun readChapterRawText(book: Book, chapter: Chapter): String =
        withContext(ioDispatcher) {
            chapterByteReader.readChapterText(
                filePath = book.filePath,
                charsetName = book.charsetName,
                startByteOffset = chapter.startByteOffset,
                endByteOffset = chapter.endByteOffset,
                stripLeadingBom = chapter.chapterIndex == 0
            )
        }

    override suspend fun saveImportedBook(book: Book, chapters: List<Chapter>) =
        withContext(ioDispatcher) {
            bookDao.insertBookWithChapters(
                book = book.toEntity(),
                chapters = chapters.map { it.toEntity() }
            )
        }

    override suspend fun updateReadingProgress(
        bookId: String,
        chapterIndex: Int,
        charOffset: Int
    ) = withContext(ioDispatcher) {
        bookDao.updateReadingProgress(
            bookId = bookId,
            chapterIndex = chapterIndex.coerceAtLeast(0),
            charOffset = charOffset.coerceAtLeast(0),
            lastReadAt = System.currentTimeMillis()
        )
    }

    override suspend fun deleteBook(bookId: String) = withContext(ioDispatcher) {
        val existing = bookDao.getBookById(bookId)
        bookDao.deleteBookById(bookId)
        if (existing != null) {
            bookFileStorage.deleteBookFile(existing.filePath)
        }
    }
}
