package app.mytasks.core

/**
 * Rank keys for drag-and-drop ordering. Kotlin twin of core/src/rank.ts: both
 * must produce keys that sort the same way under plain character comparison.
 *
 * A rank is a base-62 fraction between 0 and 1. Moving an item writes one new
 * rank for that item only. Keys never end in the lowest digit, so there is
 * always room between two keys.
 */
object Rank {
    private const val DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    private const val BASE = 62
    private const val ZERO = '0'
    private const val HEAD = 3
    private const val HEAD_LIMIT = BASE * BASE * BASE
    private val START = DIGITS[BASE / 2].toString()

    private fun digit(ch: Char): Int {
        val i = DIGITS.indexOf(ch)
        require(i >= 0) { "invalid rank character: $ch" }
        return i
    }

    private fun assertValid(rank: String) {
        require(rank.isNotEmpty()) { "rank must not be empty" }
        rank.forEach { digit(it) }
        require(!rank.endsWith(ZERO)) { "rank must not end in 0: $rank" }
    }

    /** Key strictly between [lo] and [hi]. Empty [lo] stands for 0, null [hi] for 1. */
    private fun mid(lo: String, hi: String?): String {
        if (hi != null) {
            var n = 0
            while (n < hi.length && (lo.getOrNull(n) ?: ZERO) == hi[n]) n++
            if (n > 0) return hi.substring(0, n) + mid(lo.drop(n), hi.substring(n))
        }
        val dLo = if (lo.isNotEmpty()) digit(lo[0]) else 0
        val dHi = if (hi != null) digit(hi[0]) else BASE
        if (dHi - dLo > 1) return DIGITS[(dLo + dHi) / 2].toString()
        if (hi != null && hi.length > 1) return hi.substring(0, 1)
        return DIGITS[dLo].toString() + mid(lo.drop(1), null)
    }

    fun between(before: String?, after: String?): String {
        before?.let { assertValid(it) }
        after?.let { assertValid(it) }
        if (before == null) return before(after)
        if (after == null) return after(before)
        require(before < after) { "ranks out of order: $before >= $after" }
        return mid(before, after)
    }

    private fun headValue(rank: String): Int {
        var v = 0
        for (i in 0 until HEAD) v = v * BASE + (if (i < rank.length) digit(rank[i]) else 0)
        return v
    }

    private fun encodeHead(value: Int): String {
        var v = value
        val chars = CharArray(HEAD)
        for (i in HEAD - 1 downTo 0) {
            chars[i] = DIGITS[v % BASE]
            v /= BASE
        }
        return String(chars).trimEnd(ZERO)
    }

    /** Rank for an item placed above a list whose first rank is [first]. */
    fun before(first: String?): String {
        if (first == null) return START
        assertValid(first)
        val v = if (first.length > HEAD) headValue(first) else headValue(first) - 1
        return if (v >= 1) encodeHead(v) else mid("", first)
    }

    /** Rank for an item placed below a list whose last rank is [last]. */
    fun after(last: String?): String {
        if (last == null) return START
        assertValid(last)
        val v = headValue(last) + 1
        return if (v < HEAD_LIMIT) encodeHead(v) else mid(last, null)
    }

    /** Rank for the item moved to [toIndex] in [ranks], the current order without the moved item. */
    fun forIndex(ranks: List<String>, toIndex: Int): String {
        val i = toIndex.coerceIn(0, ranks.size)
        return between(ranks.getOrNull(i - 1), ranks.getOrNull(i))
    }
}
