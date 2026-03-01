package tj.app.quran_todo.common.recitation

import tj.app.quran_todo.data.database.entity.quran.AyahEntity
import kotlin.math.max

enum class RecitationIssueType {
    MISSING,
    EXTRA,
    REPLACED,
}

data class RecitationIssue(
    val type: RecitationIssueType,
    val expected: String? = null,
    val actual: String? = null,
)

data class RecitationMatch(
    val ayahNumber: Int,
    val ayahNumberInSurah: Int,
    val confidence: Float,
    val transcript: String,
    val matchedTokens: Set<String>,
    val matchedWordIndexes: Set<Int>,
    val issues: List<RecitationIssue>,
)

object AyahRecitationMatcher {
    fun findBestAyah(transcript: String, ayahs: List<AyahEntity>): RecitationMatch? {
        val normalizedTranscript = normalizeArabic(transcript)
        if (normalizedTranscript.isBlank()) return null
        val transcriptTokensRaw = tokenize(normalizedTranscript)
        val transcriptTokens = if (transcriptTokensRaw.size > 24) {
            transcriptTokensRaw.takeLast(24)
        } else {
            transcriptTokensRaw
        }
        if (transcriptTokens.size < 2) return null

        var bestMatch: AyahMatchCandidate? = null

        ayahs.forEach { ayah ->
            val normalizedAyah = normalizeArabic(ayah.text)
            val ayahTokens = tokenize(normalizedAyah)
            if (ayahTokens.isEmpty()) return@forEach

            val windowMatch = findBestWindowMatch(ayahTokens, transcriptTokens)
            if (windowMatch.score > (bestMatch?.score ?: 0f)) {
                val matchedTokens = windowMatch.matchedWordIndexes
                    .mapNotNull { index -> ayahTokens.getOrNull(index) }
                    .toSet()
                bestMatch = AyahMatchCandidate(
                    ayah = ayah,
                    score = windowMatch.score,
                    issues = windowMatch.issues,
                    matchedTokens = matchedTokens,
                    matchedWordIndexes = windowMatch.matchedWordIndexes
                )
            }
        }

        val matched = bestMatch ?: return null
        val minScore = when {
            transcriptTokens.size <= 3 -> 0.50f
            transcriptTokens.size <= 5 -> 0.42f
            else -> 0.34f
        }
        if (matched.score < minScore) return null
        return RecitationMatch(
            ayahNumber = matched.ayah.number,
            ayahNumberInSurah = matched.ayah.numberInSurah,
            confidence = matched.score,
            transcript = transcript,
            matchedTokens = matched.matchedTokens,
            matchedWordIndexes = matched.matchedWordIndexes,
            issues = matched.issues
        )
    }

    fun normalizeArabic(text: String): String {
        return text
            .lowercase()
            .replace(Regex("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]"), "")
            .replace('ٱ', 'ا')
            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .replace('ى', 'ي')
            .replace('ة', 'ه')
            .replace(Regex("[^\\p{L}\\p{Nd}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun tokenize(text: String): List<String> =
        text.split(" ").map { it.trim() }.filter { it.isNotEmpty() }

    private data class AyahMatchCandidate(
        val ayah: AyahEntity,
        val score: Float,
        val issues: List<RecitationIssue>,
        val matchedTokens: Set<String>,
        val matchedWordIndexes: Set<Int>,
    )

    private data class WindowMatch(
        val score: Float,
        val issues: List<RecitationIssue>,
        val matchedWordIndexes: Set<Int>,
    )

    private fun findBestWindowMatch(
        ayahTokens: List<String>,
        transcriptTokens: List<String>,
    ): WindowMatch {
        val windows = candidateWindows(ayahTokens.size, transcriptTokens.size)
        var bestScore = 0f
        var bestIssues: List<RecitationIssue> = emptyList()
        var bestIndexes: Set<Int> = emptySet()

        windows.forEach { window ->
            val windowTokens = ayahTokens.subList(window.first, window.second)
            val distance = levenshteinWords(windowTokens, transcriptTokens)
            val maxSize = max(windowTokens.size, transcriptTokens.size).coerceAtLeast(1)
            val similarity = (1f - distance.toFloat() / maxSize.toFloat()).coerceIn(0f, 1f)

            val alignedIndexes = alignedTokenMatchIndexes(windowTokens, transcriptTokens)
            val coverage = if (windowTokens.isEmpty()) {
                0f
            } else {
                (alignedIndexes.size.toFloat() / windowTokens.size.toFloat()).coerceIn(0f, 1f)
            }
            val score = (similarity * 0.68f + coverage * 0.32f).coerceIn(0f, 1f)
            if (score > bestScore) {
                bestScore = score
                bestIssues = buildIssues(windowTokens, transcriptTokens)
                bestIndexes = alignedIndexes
                    .map { index -> index + window.first }
                    .toSet()
            }
        }

        return WindowMatch(
            score = bestScore,
            issues = bestIssues,
            matchedWordIndexes = bestIndexes
        )
    }

    private fun candidateWindows(ayahSize: Int, transcriptSize: Int): List<Pair<Int, Int>> {
        if (ayahSize <= 0) return emptyList()
        val windows = mutableListOf<Pair<Int, Int>>()
        val minWindowSize = (transcriptSize - 3).coerceAtLeast(2).coerceAtMost(ayahSize)
        val maxWindowSize = (transcriptSize + 3).coerceAtLeast(minWindowSize).coerceAtMost(ayahSize)

        for (windowSize in minWindowSize..maxWindowSize) {
            for (start in 0..(ayahSize - windowSize)) {
                windows += start to (start + windowSize)
            }
        }

        windows += 0 to ayahSize
        return windows.distinct()
    }

    private fun alignedTokenMatchIndexes(expected: List<String>, actual: List<String>): Set<Int> {
        if (expected.isEmpty() || actual.isEmpty()) return emptySet()
        var i = 0
        var j = 0
        val matches = mutableSetOf<Int>()
        while (i < expected.size && j < actual.size) {
            if (isTokenMatch(expected[i], actual[j])) {
                matches += i
                i++
                j++
            } else if (i + 1 < expected.size && isTokenMatch(expected[i + 1], actual[j])) {
                i++
            } else if (j + 1 < actual.size && isTokenMatch(expected[i], actual[j + 1])) {
                j++
            } else {
                i++
                j++
            }
        }
        return matches
    }

    private fun buildIssues(expected: List<String>, actual: List<String>): List<RecitationIssue> {
        val issues = mutableListOf<RecitationIssue>()
        var i = 0
        var j = 0
        while (i < expected.size || j < actual.size) {
            if (i < expected.size && j < actual.size && isTokenMatch(expected[i], actual[j])) {
                i++
                j++
                continue
            }

            if (i + 1 < expected.size && j < actual.size && isTokenMatch(expected[i + 1], actual[j])) {
                issues += RecitationIssue(type = RecitationIssueType.MISSING, expected = expected[i])
                i++
                continue
            }

            if (j + 1 < actual.size && i < expected.size && isTokenMatch(expected[i], actual[j + 1])) {
                issues += RecitationIssue(type = RecitationIssueType.EXTRA, actual = actual[j])
                j++
                continue
            }

            when {
                i < expected.size && j < actual.size -> {
                    issues += RecitationIssue(
                        type = RecitationIssueType.REPLACED,
                        expected = expected[i],
                        actual = actual[j]
                    )
                    i++
                    j++
                }
                i < expected.size -> {
                    issues += RecitationIssue(type = RecitationIssueType.MISSING, expected = expected[i])
                    i++
                }
                j < actual.size -> {
                    issues += RecitationIssue(type = RecitationIssueType.EXTRA, actual = actual[j])
                    j++
                }
            }
            if (issues.size >= 8) break
        }
        return issues
    }

    private fun levenshteinWords(expected: List<String>, actual: List<String>): Int {
        if (expected.isEmpty()) return actual.size
        if (actual.isEmpty()) return expected.size
        val dp = Array(expected.size + 1) { IntArray(actual.size + 1) }
        for (i in 0..expected.size) dp[i][0] = i
        for (j in 0..actual.size) dp[0][j] = j

        for (i in 1..expected.size) {
            for (j in 1..actual.size) {
                val cost = if (isTokenMatch(expected[i - 1], actual[j - 1])) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[expected.size][actual.size]
    }

    private fun isTokenMatch(expected: String, actual: String): Boolean {
        if (expected == actual) return true
        val similarity = tokenSimilarity(expected, actual)
        return similarity >= 0.74f
    }

    private fun tokenSimilarity(left: String, right: String): Float {
        if (left == right) return 1f
        if (left.isBlank() || right.isBlank()) return 0f
        val maxLength = maxOf(left.length, right.length)
        if (maxLength <= 0) return 1f
        val distance = levenshteinChars(left, right)
        return (1f - distance.toFloat() / maxLength.toFloat()).coerceIn(0f, 1f)
    }

    private fun levenshteinChars(left: String, right: String): Int {
        if (left == right) return 0
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length
        val previous = IntArray(right.length + 1) { it }
        val current = IntArray(right.length + 1)
        for (i in left.indices) {
            current[0] = i + 1
            for (j in right.indices) {
                val cost = if (left[i] == right[j]) 0 else 1
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + cost
                )
            }
            for (j in previous.indices) {
                previous[j] = current[j]
            }
        }
        return previous[right.length]
    }
}

fun RecitationIssue.describe(): String = when (type) {
    RecitationIssueType.MISSING -> "Missing: ${expected ?: "word"}"
    RecitationIssueType.EXTRA -> "Extra: ${actual ?: "word"}"
    RecitationIssueType.REPLACED -> "Expected '${expected ?: "?"}', got '${actual ?: "?"}'"
}
