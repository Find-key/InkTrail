package com.inktrail.reader.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.inktrail.reader.domain.model.Book

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val filePath: String,
    val fileSizeBytes: Long,
    val charsetName: String,
    val totalChapters: Int,
    val currentChapterIndex: Int,
    val currentCharOffset: Int,
    val lastReadAt: Long,
    val createdAt: Long
)

fun BookEntity.toDomain(): Book = Book(
    id = id,
    title = title,
    filePath = filePath,
    fileSizeBytes = fileSizeBytes,
    charsetName = charsetName,
    totalChapters = totalChapters,
    currentChapterIndex = currentChapterIndex,
    currentCharOffset = currentCharOffset,
    lastReadAt = lastReadAt,
    createdAt = createdAt
)

fun Book.toEntity(): BookEntity = BookEntity(
    id = id,
    title = title,
    filePath = filePath,
    fileSizeBytes = fileSizeBytes,
    charsetName = charsetName,
    totalChapters = totalChapters,
    currentChapterIndex = currentChapterIndex,
    currentCharOffset = currentCharOffset,
    lastReadAt = lastReadAt,
    createdAt = createdAt
)
