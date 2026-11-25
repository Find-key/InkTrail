package com.inktrail.reader.engine.cleaner

/**
 * Configuration options for text layout cleaning.
 * When [enabled] is false, [TextCleaner.clean] returns the raw text verbatim.
 */
data class TextCleanerOptions(
    val enabled: Boolean = true,
    val mergeBlankLines: Boolean = true,
    val fixHardLineBreaks: Boolean = true,
    val addCjkSpacing: Boolean = true,
    val normalizeParagraphIndent: Boolean = false
)

/**
 * Pure Kotlin typography and layout cleaner for raw TXT chapter text.
 */
class TextCleaner {

    fun clean(rawText: String, options: TextCleanerOptions = TextCleanerOptions()): String {
        if (!options.enabled || rawText.isEmpty()) {
            return rawText
        }

        val normalizedNewlines = rawText
            .removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace('\r', '\n')

        var lines = normalizedNewlines.split('\n').map { it.trimEnd() }

        if (options.fixHardLineBreaks) {
            lines = mergeHardWrappedLines(lines)
        }

        if (options.mergeBlankLines) {
            lines = collapseBlankLines(lines)
        }

        var result = lines.joinToString("\n")

        if (options.addCjkSpacing) {
            result = insertCjkLatinSpacing(result)
        }

        if (options.normalizeParagraphIndent) {
            result = applyParagraphIndent(result)
        }

        return result
    }

    private fun mergeHardWrappedLines(lines: List<String>): List<String> {
        if (lines.size <= 1) return lines
        val merged = ArrayList<String>(lines.size)
        var i = 0
        while (i < lines.size) {
            var current = lines[i]
            while (i + 1 < lines.size && shouldJoinLines(current, lines[i + 1])) {
                val next = lines[i + 1].trimStart()
                current = joinLines(current, next)
                i++
            }
            merged += current
            i++
        }
        return merged
    }

    private fun shouldJoinLines(currentLine: String, nextLine: String): Boolean {
        val currentTrimmed = currentLine.trim()
        if (currentTrimmed.isEmpty() || nextLine.isBlank()) return false

        // Do not join into a line that explicitly begins with paragraph indentation.
        if (hasLeadingIndent(nextLine)) return false

        val nextTrimmed = nextLine.trimStart()
        // Do not merge headings, dividers, dialogue openings, or list bullets.
        if (isHeadingOrDivider(currentTrimmed) || isHeadingOrDivider(nextTrimmed)) return false
        if (startsNewParagraphConstruct(nextTrimmed)) return false

        val lastChar = currentTrimmed.last()
        if (lastChar in SENTENCE_END_CHARS) return false

        // Join when current line ends mid-sentence (CJK, ASCII letter/digit, continuation punctuation).
        return isContinuationEnding(lastChar)
    }

    private fun joinLines(left: String, rightTrimmedStart: String): String {
        val leftTrimmedEnd = left.trimEnd()
        if (leftTrimmedEnd.isEmpty()) return rightTrimmedStart
        if (rightTrimmedStart.isEmpty()) return leftTrimmedEnd

        val lastChar = leftTrimmedEnd.last()
        val firstChar = rightTrimmedStart.first()

        // Preserve word separation for ASCII/Latin wraps, avoid spurious spaces inside Chinese prose.
        val needsSpace = isAsciiWordOrPunct(lastChar) && isAsciiWordOrPunct(firstChar)
        return if (needsSpace) {
            "$leftTrimmedEnd $rightTrimmedStart"
        } else {
            "$leftTrimmedEnd$rightTrimmedStart"
        }
    }

    private fun collapseBlankLines(lines: List<String>): List<String> {
        val out = ArrayList<String>(lines.size)
        var consecutiveBlank = 0
        for (line in lines) {
            if (line.isBlank()) {
                if (out.isNotEmpty() && consecutiveBlank == 0) {
                    out += ""
                }
                consecutiveBlank++
            } else {
                consecutiveBlank = 0
                out += line
            }
        }
        while (out.isNotEmpty() && out.last().isBlank()) {
            out.removeAt(out.lastIndex)
        }
        return out
    }

    private fun insertCjkLatinSpacing(text: String): String {
        if (text.length < 2) return text
        val sb = StringBuilder(text.length + text.length / 16)
        for (i in text.indices) {
            val curr = text[i]
            if (i > 0) {
                val prev = text[i - 1]
                if ((isCjkIdeographOrKana(prev) && isLatinOrDigit(curr)) ||
                    (isLatinOrDigit(prev) && isCjkIdeographOrKana(curr))
                ) {
                    sb.append(' ')
                }
            }
            sb.append(curr)
        }
        return sb.toString()
    }

    private fun applyParagraphIndent(text: String): String {
        val lines = text.split('\n')
        return lines.joinToString("\n") { line ->
            if (line.isBlank()) {
                ""
            } else {
                val stripped = line.trimStart { it == ' ' || it == '\t' || it == '\u3000' }
                if (isHeadingOrDivider(stripped)) {
                    stripped
                } else {
                    "\u3000\u3000$stripped"
                }
            }
        }
    }

    private fun hasLeadingIndent(line: String): Boolean {
        if (line.isEmpty()) return false
        return line.startsWith('\u3000') ||
            line.startsWith('\t') ||
            line.startsWith("  ")
    }

    private fun startsNewParagraphConstruct(trimmed: String): Boolean {
        if (trimmed.isEmpty()) return false
        val first = trimmed.first()
        if (first in OPENING_DIALOGUE_OR_LIST_CHARS) return true
        return NUMBERED_LIST_REGEX.containsMatchIn(trimmed)
    }

    private fun isHeadingOrDivider(trimmed: String): Boolean {
        if (trimmed.length <= 80 && CHAPTER_HEADING_REGEX.matches(trimmed)) return true
        return DIVIDER_REGEX.matches(trimmed)
    }

    private fun isContinuationEnding(c: Char): Boolean {
        return isCjkIdeographOrKana(c) ||
            isLatinOrDigit(c) ||
            c in CONTINUATION_PUNCT_CHARS
    }

    private fun isCjkIdeographOrKana(c: Char): Boolean {
        val code = c.code
        return (code in 0x4E00..0x9FFF) || // CJK Unified Ideographs
            (code in 0x3400..0x4DBF) ||    // CJK Extension A
            (code in 0xF900..0xFAFF) ||    // CJK Compatibility Ideographs
            (code in 0x3040..0x309F) ||    // Hiragana
            (code in 0x30A0..0x30FF)       // Katakana
    }

    private fun isLatinOrDigit(c: Char): Boolean =
        (c in 'a'..'z') || (c in 'A'..'Z') || (c in '0'..'9')

    private fun isAsciiWordOrPunct(c: Char): Boolean =
        c.code in 0x21..0x7E

    companion object {
        private val SENTENCE_END_CHARS = setOf(
            '。', '！', '？', '…', '．', '”', '’', '」', '』', '】', '）',
            '.', '!', '?', '"', '\'', ')', ':', '：', ';', '；', '~', '～'
        )
        private val CONTINUATION_PUNCT_CHARS = setOf(
            '，', '、', '—', '－', ',', '-', '（', '(', '“', '「', '『', '《'
        )
        private val OPENING_DIALOGUE_OR_LIST_CHARS = setOf(
            '“', '「', '『', '•', '·', '※'
        )
        private val NUMBERED_LIST_REGEX = Regex("""^(?:\d+[.、)]|[-*]\s+)""")
        private val DIVIDER_REGEX = Regex("""^[-=*_~—·\s]{3,}$""")
        private val CHAPTER_HEADING_REGEX = Regex(
            """^(?:第[零一二三四五六七八九十百千万两0-9]+[章节回卷集部篇].*|Chapter\s+\d+.*|(?:序[章言]?|楔子|尾声|引子|后记)(?:\s+.*|[：:].*)?|番外.*)$""",
            RegexOption.IGNORE_CASE
        )
    }
}
