package app.mytasks.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RankTest {
    @Test
    fun betweenIsStrictlyBetween() {
        for ((a, b) in listOf("A" to "B", "A" to "A1", "Az" to "B", "001" to "002", "V" to "V1", "zz" to "zzz")) {
            val m = Rank.between(a, b)
            assertTrue("$a < $m < $b", a < m && m < b)
            assertFalse(m.endsWith("0"))
        }
    }

    @Test
    fun matchesTheTypeScriptImplementation() {
        // Same inputs and expected outputs as core/src/rank.ts.
        assertEquals("V", Rank.after(null))
        assertEquals("V", Rank.before(null))
        assertEquals("V01", Rank.after("V"))
        assertEquals("Uzz", Rank.before("V"))
        assertEquals("AV", Rank.between("A", "B"))
        assertEquals("000V", Rank.before("001"))
        assertEquals("zzzV", Rank.after("zzz"))
    }

    @Test
    fun staysShortForFiveThousandAppendsAndPrepends() {
        var last: String? = null
        var first: String? = null
        val bottom = ArrayList<String>()
        val top = ArrayList<String>()
        repeat(5000) {
            last = Rank.after(last); bottom.add(last!!)
            first = Rank.before(first); top.add(0, first!!)
        }
        assertEquals(bottom.sorted(), bottom)
        assertEquals(top.sorted(), top)
        assertEquals(5000, bottom.toSet().size)
        assertTrue(bottom.maxOf { it.length } <= 3 && top.maxOf { it.length } <= 3)
    }

    @Test
    fun survivesRandomMoves() {
        var seed = 42L
        fun rand(n: Int): Int {
            seed = (seed * 1103515245 + 12345) and 0x7fffffff
            return (seed % n).toInt()
        }
        val items = ArrayList<String>()
        repeat(20) { items.add(Rank.after(items.lastOrNull())) }
        repeat(2000) {
            items.removeAt(rand(items.size))
            val to = rand(items.size + 1)
            items.add(to, Rank.forIndex(items, to))
            assertEquals(items.sorted(), items)
        }
        assertEquals(items.size, items.toSet().size)
    }
}
