package com.inktrail.reader.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY lastReadAt DESC, createdAt DESC")
    fun observeAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :bookId LIMIT 1")
    fun observeBookById(bookId: String): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE id = :bookId LIMIT 1")
    suspend fun getBookById(bookId: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChaptersInternal(chapters: List<ChapterEntity>)

    @Transaction
    suspend fun insertBookWithChapters(book: BookEntity, chapters: List<ChapterEntity>) {
        insertBook(book)
        if (chapters.isNotEmpty()) {
            insertChaptersInternal(chapters)
        }
    }

    @Query(
        "UPDATE books SET currentChapterIndex = :chapterIndex, currentCharOffset = :charOffset, lastReadAt = :lastReadAt WHERE id = :bookId"
    )
    suspend fun updateReadingProgress(
        bookId: String,
        chapterIndex: Int,
        charOffset: Int,
        lastReadAt: Long
    )

    @Query("DELETE FROM books WHERE id = :bookId")
    suspend fun deleteBookById(bookId: String)
}
