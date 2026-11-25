package com.inktrail.reader.data.local.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.inktrail.reader.domain.model.Chapter

@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["bookId"]),
        Index(value = ["bookId", "chapterIndex"], unique = true)
    ]
)
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val bookId: String,
    val chapterIndex: Int,
    val title: String,
    val startByteOffset: Long,
    val endByteOffset: Long
)

fun ChapterEntity.toDomain(): Chapter = Chapter(
    id = id,
    bookId = bookId,
    chapterIndex = chapterIndex,
    title = title,
    startByteOffset = startByteOffset,
    endByteOffset = endByteOffset
)

fun Chapter.toEntity(): ChapterEntity = ChapterEntity(
    id = id,
    bookId = bookId,
    chapterIndex = chapterIndex,
    title = title,
    startByteOffset = startByteOffset,
    endByteOffset = endByteOffset
)
