package app.mytasks.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ListsTest {
    private var n = 0
    private fun task(title: String = "t", rank: String = "V", completed: Boolean = false, priority: Int = 0, deleted: Boolean = false) =
        Task(
            id = "id${++n}", createdAt = n.toLong(), updatedAt = n.toLong(), deletedAt = if (deleted) 1L else null,
            updatedBy = "test", rank = rank, listId = "l", subListId = "s", title = title,
            completed = completed, priority = priority,
        )

    @Test
    fun headingMatchesTheOwnersScreenshot() {
        val tasks = listOf(task("Tut sheet 6"), task("Triple integral lec"), task("Triple Integral finish"))
        assertEquals("Monday 16th Nov (0/3)", Lists.headingTitle("Monday 16th Nov", tasks, HeadingInfo.COMPLETED_OF_TOTAL, 0))
    }

    @Test
    fun headingModes() {
        val tasks = listOf(task(completed = true), task(), task(), task(deleted = true))
        assertEquals("", Lists.headingInfo(tasks, HeadingInfo.NONE, 0))
        assertEquals("1/3", Lists.headingInfo(tasks, HeadingInfo.COMPLETED_OF_TOTAL, 0))
        assertEquals("33%", Lists.headingInfo(tasks, HeadingInfo.PERCENTAGE, 0))
        assertEquals("3", Lists.headingInfo(tasks, HeadingInfo.TOTAL, 0))
        assertEquals("2", Lists.headingInfo(tasks, HeadingInfo.UNCOMPLETED, 0))
    }

    @Test
    fun sortsAndHides() {
        val a = task("banana", rank = "B", priority = Priority.LOW)
        val b = task("Apple", rank = "C", priority = Priority.HIGH)
        val c = task("cherry", rank = "A", completed = true)
        val all = listOf(a, b, c, task(deleted = true))
        fun titles(order: Int, bottom: Boolean = false, hide: Boolean = false) =
            Lists.visibleTasks(all, order, bottom, hide).joinToString(",") { it.title }
        assertEquals("cherry,banana,Apple", titles(SortOrder.USER_DRAGGABLE))
        assertEquals("Apple,banana,cherry", titles(SortOrder.ALPHA_AZ))
        assertEquals("Apple,banana,cherry", titles(SortOrder.PRIORITY))
        assertEquals("banana,Apple,cherry", titles(SortOrder.USER_DRAGGABLE, bottom = true))
        assertEquals("banana,Apple", titles(SortOrder.USER_DRAGGABLE, hide = true))
    }

    @Test
    fun quickAddSplitsLines() {
        assertEquals(listOf("one", "two", "three"), Lists.splitQuickAdd("one\r\n two \n\nthree"))
        assertEquals(emptyList<String>(), Lists.splitQuickAdd("  \n "))
    }
}
