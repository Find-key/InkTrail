package com.inktrail.reader.domain.model

/**
 * Domain entity representing a single indexed chapter or logical byte slice of a book.
 */
data class Chapter(
    val id: Long = 0L,
    val bookId: String,
    val chapterIndex: Int,
    val title: String,
    val startByteOffset: Long,
    val endByteOffset: Long
) {
    val byteLength: Long
        get() = (endByteOffset - startByteOffset).coerceAtLeast(0L)
}
