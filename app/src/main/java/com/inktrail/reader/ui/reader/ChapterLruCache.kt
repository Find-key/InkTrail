package com.inktrail.reader.ui.reader

import com.inktrail.reader.domain.model.PageSlice
import com.inktrail.reader.engine.cleaner.TextCleanerOptions

/**
 * Immutable specification determining how chapter text is paginated into screen-sized slices.
 */
data class PaginationSpec(
    val contentWidthPx: Int,
    val contentHeightPx: Int,
    val fontSizeSp: Float,
    val lineHeightMultiplier: Float,
    val horizontalMarginDp: Int,
    val paragraphSpacingDp: Int
) {
    val isValid: Boolean
        get() = contentWidthPx > 0 && contentHeightPx > 0
}

/**
 * Cached chapter payload held inside [ChapterLruCache].
 */
data class CachedChapter(
    val chapterIndex: Int,
    val title: String,
    val rawText: String,
    val cleanedText: String,
    val cleanerOptions: TextCleanerOptions,
    val paginationSpec: PaginationSpec?,
    val pages: List<PageSlice>
)

/**
 * Bounded 3-chapter LRU cache (current + previous + next) to enable seamless cross-chapter
 * HorizontalPager swipes while strictly bounding memory footprint for 50MB+ files.
 */
class ChapterLruCache(
    private val maxCapacity: Int = DEFAULT_CAPACITY
) {
    init {
        require(maxCapacity >= 1) { "maxCapacity must be at least 1" }
    }

    private val map = object : LinkedHashMap<Int, CachedChapter>(maxCapacity + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, CachedChapter>?): Boolean {
            return size > maxCapacity
        }
    }

    @Synchronized
    fun get(chapterIndex: Int): CachedChapter? = map[chapterIndex]

    @Synchronized
    fun peek(chapterIndex: Int): CachedChapter? {
        // Read without altering LRU order when scanning cached entries.
        return map.entries.firstOrNull { it.key == chapterIndex }?.value
    }

    @Synchronized
    fun put(entry: CachedChapter) {
        map[entry.chapterIndex] = entry
    }

    @Synchronized
    fun size(): Int = map.size

    @Synchronized
    fun cachedIndices(): Set<Int> = map.keys.toSet()

    @Synchronized
    fun clear() {
        map.clear()
    }

    companion object {
        const val DEFAULT_CAPACITY = 3
    }
}
