package com.inktrail.reader.engine.annotation

/**
 * Half-open highlight interval [startOffset, endOffset) inside a single chapter.
 */
data class HighlightInterval(
    val id: Long = 0L,
    val startOffset: Int,
    val endOffset: Int,
    val colorHex: Long,
    val noteContent: String? = null,
    val createdAt: Long = 0L
)

/**
 * Pure Kotlin manager for chapter highlight intervals.
 * Maintains the invariant that intervals within a chapter are sorted and mutually disjoint:
 * - Same-color overlapping or touching intervals ([a, b) + [b, c)) are merged into one.
 * - Different-color additions carve out / override the overlapped segment of existing intervals.
 * - Erasing [eraseStart, eraseEnd) trims, drops, or splits existing intervals cleanly.
 */
class HighlightIntervalManager {

    fun addOrMerge(
        existing: List<HighlightInterval>,
        incoming: HighlightInterval
    ): List<HighlightInterval> {
        val cleanIncoming = sanitize(incoming) ?: return normalize(existing)
        val sanitizedExisting = existing.mapNotNull(::sanitize)

        // Step 1: Override/carve out any different-color intervals overlapped by incoming.
        val afterOverride = ArrayList<HighlightInterval>(sanitizedExisting.size + 2)
        val sameColorCandidates = ArrayList<HighlightInterval>()

        for (item in sanitizedExisting) {
            if (item.colorHex == cleanIncoming.colorHex) {
                sameColorCandidates += item
            } else {
                carveOut(item, cleanIncoming.startOffset, cleanIncoming.endOffset, afterOverride)
            }
        }

        // Step 2: Merge incoming with any overlapping or adjacent same-color intervals.
        var mergedStart = cleanIncoming.startOffset
        var mergedEnd = cleanIncoming.endOffset
        var mergedId = cleanIncoming.id
        var mergedNote = cleanIncoming.noteContent?.takeIf { it.isNotBlank() }
        var mergedCreatedAt = cleanIncoming.createdAt

        val untouchedSameColor = ArrayList<HighlightInterval>(sameColorCandidates.size)
        val sortedSameColor = sameColorCandidates.sortedBy { it.startOffset }

        // Expand merged window until fixed point so bridging across two adjacent intervals merges all.
        var changed = true
        val remaining = sortedSameColor.toMutableList()
        while (changed) {
            changed = false
            val iter = remaining.iterator()
            while (iter.hasNext()) {
                val candidate = iter.next()
                val overlapsOrTouches =
                    candidate.endOffset >= mergedStart && mergedEnd >= candidate.startOffset
                if (overlapsOrTouches) {
                    mergedStart = minOf(mergedStart, candidate.startOffset)
                    mergedEnd = maxOf(mergedEnd, candidate.endOffset)
                    if (mergedId == 0L && candidate.id != 0L) {
                        mergedId = candidate.id
                    }
                    mergedNote = mergeNotes(existingNote = candidate.noteContent, incomingNote = mergedNote)
                    mergedCreatedAt = maxOf(mergedCreatedAt, candidate.createdAt)
                    iter.remove()
                    changed = true
                }
            }
        }
        untouchedSameColor.addAll(remaining)

        val merged = HighlightInterval(
            id = mergedId,
            startOffset = mergedStart,
            endOffset = mergedEnd,
            colorHex = cleanIncoming.colorHex,
            noteContent = mergedNote,
            createdAt = mergedCreatedAt
        )

        val combined = ArrayList<HighlightInterval>(afterOverride.size + untouchedSameColor.size + 1)
        combined.addAll(afterOverride)
        combined.addAll(untouchedSameColor)
        combined.add(merged)
        return combined.sortedWith(compareBy<HighlightInterval> { it.startOffset }.thenBy { it.endOffset })
    }

    fun erase(
        existing: List<HighlightInterval>,
        eraseStart: Int,
        eraseEnd: Int
    ): List<HighlightInterval> {
        val cleanStart = eraseStart.coerceAtLeast(0)
        if (cleanStart >= eraseEnd) return normalize(existing)

        val result = ArrayList<HighlightInterval>(existing.size)
        for (item in existing) {
            val clean = sanitize(item) ?: continue
            carveOut(clean, cleanStart, eraseEnd, result)
        }
        return result.sortedWith(compareBy<HighlightInterval> { it.startOffset }.thenBy { it.endOffset })
    }

    fun normalize(intervals: List<HighlightInterval>): List<HighlightInterval> {
        var current = emptyList<HighlightInterval>()
        for (interval in intervals) {
            current = addOrMerge(current, interval)
        }
        return current
    }

    fun findOverlapping(
        intervals: List<HighlightInterval>,
        startOffset: Int,
        endOffset: Int
    ): List<HighlightInterval> {
        if (startOffset >= endOffset) return emptyList()
        return intervals.filter { it.endOffset > startOffset && endOffset > it.startOffset }
    }

    private fun carveOut(
        interval: HighlightInterval,
        cutStart: Int,
        cutEnd: Int,
        out: MutableList<HighlightInterval>
    ) {
        // No overlap
        if (interval.endOffset <= cutStart || interval.startOffset >= cutEnd) {
            out += interval
            return
        }

        // Completely covered
        if (cutStart <= interval.startOffset && cutEnd >= interval.endOffset) {
            return
        }

        // Split into left and right pieces when cut is strictly inside interval
        if (interval.startOffset < cutStart && cutEnd < interval.endOffset) {
            out += interval.copy(endOffset = cutStart)
            out += interval.copy(id = 0L, startOffset = cutEnd)
            return
        }

        // Trim right tail
        if (interval.startOffset < cutStart) {
            out += interval.copy(endOffset = cutStart)
            return
        }

        // Trim left head
        if (cutEnd < interval.endOffset) {
            out += interval.copy(startOffset = cutEnd)
        }
    }

    private fun mergeNotes(existingNote: String?, incomingNote: String?): String? {
        val a = existingNote?.trim()?.takeIf { it.isNotEmpty() }
        val b = incomingNote?.trim()?.takeIf { it.isNotEmpty() }
        return when {
            a == null && b == null -> null
            a == null -> b
            b == null -> a
            a == b -> b
            b.contains(a) -> b
            a.contains(b) -> a
            else -> "$a\n$b"
        }
    }

    private fun sanitize(interval: HighlightInterval): HighlightInterval? {
        val start = interval.startOffset.coerceAtLeast(0)
        if (start >= interval.endOffset) return null
        return if (start == interval.startOffset) interval else interval.copy(startOffset = start)
    }
}
