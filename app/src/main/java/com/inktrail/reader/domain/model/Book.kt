package com.inktrail.reader.domain.model

/**
 * Domain entity representing an imported local book and its persistent reading anchor.
 */
data class Book(
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
) {
    /**
     * Approximate chapter-based completion ratio in [0f, 1f] for bookshelf display.
     */
    val progressFraction: Float
        get() {
            if (totalChapters <= 0) return 0f
            if (currentChapterIndex == 0 && currentCharOffset == 0) return 0f
            return ((currentChapterIndex + 1).toFloat() / totalChapters.toFloat()).coerceIn(0f, 1f)
        }
}
