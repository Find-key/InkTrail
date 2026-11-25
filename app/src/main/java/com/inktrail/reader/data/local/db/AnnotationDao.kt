package com.inktrail.reader.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface AnnotationDao {
    @Query("SELECT * FROM annotations WHERE bookId = :bookId ORDER BY chapterIndex ASC, startOffset ASC, createdAt ASC")
    fun observeAnnotationsForBook(bookId: String): Flow<List<AnnotationEntity>>

    @Query(
        "SELECT * FROM annotations WHERE bookId = :bookId AND chapterIndex = :chapterIndex AND isBookmark = 0 ORDER BY startOffset ASC"
    )
    fun observeHighlightsForChapter(bookId: String, chapterIndex: Int): Flow<List<AnnotationEntity>>

    @Query(
        "SELECT * FROM annotations WHERE bookId = :bookId AND chapterIndex = :chapterIndex AND isBookmark = 0 ORDER BY startOffset ASC"
    )
    suspend fun getHighlightsForChapter(bookId: String, chapterIndex: Int): List<AnnotationEntity>

    @Query(
        "SELECT * FROM annotations WHERE bookId = :bookId AND isBookmark = 1 ORDER BY chapterIndex ASC, startOffset ASC"
    )
    fun observeBookmarksForBook(bookId: String): Flow<List<AnnotationEntity>>

    @Query(
        "SELECT * FROM annotations WHERE bookId = :bookId AND chapterIndex = :chapterIndex AND isBookmark = 1 LIMIT 1"
    )
    suspend fun getBookmarkForChapter(bookId: String, chapterIndex: Int): AnnotationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotation(annotation: AnnotationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotations(annotations: List<AnnotationEntity>)

    @Query("UPDATE annotations SET noteContent = :noteContent WHERE id = :id")
    suspend fun updateNoteContent(id: Long, noteContent: String?)

    @Query("DELETE FROM annotations WHERE id = :id")
    suspend fun deleteAnnotationById(id: Long)

    @Query("DELETE FROM annotations WHERE bookId = :bookId AND chapterIndex = :chapterIndex AND isBookmark = 0")
    suspend fun deleteHighlightsForChapter(bookId: String, chapterIndex: Int)

    @Transaction
    suspend fun replaceChapterHighlights(
        bookId: String,
        chapterIndex: Int,
        highlights: List<AnnotationEntity>
    ) {
        deleteHighlightsForChapter(bookId, chapterIndex)
        if (highlights.isNotEmpty()) {
            insertAnnotations(highlights)
        }
    }
}
