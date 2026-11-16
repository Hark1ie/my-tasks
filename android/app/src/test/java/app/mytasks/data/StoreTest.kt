package app.mytasks.data

import app.mytasks.core.Lists
import app.mytasks.core.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreTest {
    private var now = 1000L
    private var ids = 0
    private fun store(backend: Backend = MemoryBackend(), device: String = "phone") =
        Store(backend, device, clock = { now }, newId = { "id${++ids}" }).also { it.start() }

    private fun Store.titles(subId: String) =
        Lists.visibleTasks(tasksOf(subId), SortOrder.USER_DRAGGABLE, false, false).map { it.title }

    @Test
    fun firstRunCreatesOneListWithOneSubList() {
        val s = store()
        assertTrue(s.ready.value)
        assertEquals(listOf("To do"), s.lists.value.map { it.title })
        assertEquals(1, s.subListsOf(s.lists.value[0].id).size)
    }

    @Test
    fun newTasksGoToTheTopOrBottom() {
        val s = store()
        val sub = s.subLists.value[0]
        s.addTask(sub, "a", atTop = true)
        s.addTask(sub, "b", atTop = true)
        s.addTask(sub, "c", atTop = false)
        assertEquals(listOf("b", "a", "c"), s.titles(sub.id))
        assertNull(s.addTask(sub, "   ", atTop = true))
    }

    @Test
    fun pastedLinesKeepTheirOrder() {
        val s = store()
        val sub = s.subLists.value[0]
        s.addTask(sub, "old", atTop = true)
        s.addTasks(sub, listOf("1", "2", "3"), atTop = true)
        assertEquals(listOf("1", "2", "3", "old"), s.titles(sub.id))
        s.addTasks(sub, listOf("x", "y"), atTop = false)
        assertEquals(listOf("1", "2", "3", "old", "x", "y"), s.titles(sub.id))
    }

    @Test
    fun draggingWritesOnlyTheMovedTask() {
        val s = store()
        val sub = s.subLists.value[0]
        listOf("a", "b", "c", "d").forEach { s.addTask(sub, it, atTop = false) }
        val before = s.tasksOf(sub.id).associate { it.title to it }
        val order = Lists.visibleTasks(s.tasksOf(sub.id), SortOrder.USER_DRAGGABLE, false, false).map { it.id }.toMutableList()
        val moved = order.removeAt(3)
        order.add(1, moved)
        s.moveTask(moved, order)
        assertEquals(listOf("a", "d", "b", "c"), s.titles(sub.id))
        val after = s.tasksOf(sub.id).associate { it.title to it }
        for (t in listOf("a", "b", "c")) assertEquals(before[t], after[t])
    }

    @Test
    fun deleteAndUndo() {
        val s = store()
        val sub = s.subLists.value[0]
        val t = s.addTask(sub, "a", atTop = true)!!
        s.deleteTask(s.tasksOf(sub.id)[0])
        assertEquals(emptyList<String>(), s.titles(sub.id))
        s.restoreTask(t.id)
        assertEquals(listOf("a"), s.titles(sub.id))
    }

    @Test
    fun aListAlwaysKeepsOneSubList() {
        val s = store()
        val list = s.lists.value[0]
        assertFalse(s.deleteSubList(s.subListsOf(list.id)[0]))
        val second = s.addSubList(list.id, "Second")
        assertTrue(s.deleteSubList(second))
        assertEquals(1, s.subListsOf(list.id).size)
    }

    @Test
    fun completeAllAndRemoveCompleted() {
        val s = store()
        val sub = s.subLists.value[0]
        listOf("a", "b").forEach { s.addTask(sub, it, atTop = false) }
        s.setCompleted(s.tasksOf(sub.id).first { it.title == "a" }, true)
        assertEquals(listOf("b"), s.tasksOf(sub.id).filter { !it.completed }.map { it.title })
        s.removeCompleted(sub)
        assertEquals(listOf("b"), s.titles(sub.id))
        s.completeAll(sub)
        assertTrue(s.tasksOf(sub.id).all { it.completed })
    }

    @Test
    fun twoDevicesShareOneBackendAndTheNewestEditWins() {
        val backend = MemoryBackend()
        val phone = store(backend, "phone")
        val sub = phone.subLists.value[0]
        val task = phone.addTask(sub, "a", atTop = true)!!
        now = 5000
        phone.editTaskTitle(phone.tasksOf(sub.id)[0], "newer")
        // A stale copy with an older edit time, as an offline device would send it.
        backend.put(Collections.TASKS, task.id, task.copy(title = "stale", updatedAt = 2000).toMap())
        assertEquals(listOf("newer"), phone.titles(sub.id))
    }

    @Test
    fun savingTheTaskScreenStoresTaskAndSubTasks() {
        val s = store()
        val sub = s.subLists.value[0]
        s.addTask(sub, "existing", atTop = true)
        val draft = s.draftTask(sub).copy(title = "  New one ", notes = "n", priority = 3)
        s.saveTask(draft, isNew = true, atTop = true, subTasks = listOf(Triple(null, "a", false), Triple(null, " ", false), Triple(null, "b", true)))
        assertEquals(listOf("New one", "existing"), s.titles(sub.id))
        val saved = s.tasksOf(sub.id).first { it.title == "New one" }
        assertEquals(listOf("a", "b"), s.subTasksOf(saved.id).map { it.title })

        val subs = s.subTasksOf(saved.id)
        s.saveTask(saved.copy(title = "Renamed"), isNew = false, atTop = true, subTasks = listOf(Triple(subs[1].id, "b", true), Triple(null, "c", false)))
        assertEquals(listOf("Renamed", "existing"), s.titles(sub.id))
        assertEquals(listOf("b", "c"), s.subTasksOf(saved.id).map { it.title })
    }

    @Test
    fun completingEverySubTaskCompletesTheTask() {
        val s = store()
        val sub = s.subLists.value[0]
        val draft = s.draftTask(sub).copy(title = "parent")
        s.saveTask(draft, isNew = true, atTop = true, subTasks = listOf(Triple(null, "a", false), Triple(null, "b", false)))
        val parent = s.tasksOf(sub.id)[0]
        s.subTasksOf(parent.id).forEach { s.toggleSubTask(it.id, completeParent = true) }
        assertTrue(s.tasksOf(sub.id)[0].completed)
    }

    @Test
    fun tickingARepeatingTaskMovesItToTheNextDate() {
        val s = store()
        val sub = s.subLists.value[0]
        val day = 24L * 60 * 60 * 1000
        now = 100 * day + 1000
        val draft = s.draftTask(sub).copy(title = "daily", dueAt = 100 * day, repeat = 1)
        s.saveTask(draft, isNew = true, atTop = true, subTasks = emptyList())
        s.toggleCompleted(s.tasksOf(sub.id)[0].id)
        val after = s.tasksOf(sub.id)[0]
        assertFalse(after.completed)
        assertEquals(101 * day, after.dueAt)
    }

    @Test
    fun reorderingListsAndMovingSubLists() {
        val s = store()
        val first = s.lists.value[0]
        val second = s.addList("Second", 0xFFFF9800)
        val third = s.addList("Third", 0xFFFF9800)
        s.moveList(third.id, listOf(third.id, first.id, second.id))
        assertEquals(listOf("Third", "To do", "Second"), s.lists.value.map { it.title })

        val extra = s.addSubList(first.id, "Extra")
        s.addTask(extra, "t", atTop = true)
        assertTrue(s.moveSubListTo(extra, second.id))
        assertEquals(listOf(Store.DEFAULT_SUB_LIST_TITLE, "Extra"), s.subListsOf(second.id).map { it.title })
        assertEquals(second.id, s.tasksOf(extra.id)[0].listId)
        assertFalse(s.moveSubListTo(s.subListsOf(first.id)[0], second.id))

        val copy = s.duplicateSubList(s.subListsOf(second.id)[1])
        assertEquals(listOf("t"), s.titles(copy.id))
    }

    @Test
    fun editTimesAlwaysMoveForward() {
        val s = store()
        val sub = s.subLists.value[0]
        now = 9000
        s.addTask(sub, "a", atTop = true)
        now = 10
        s.editTaskTitle(s.tasksOf(sub.id)[0], "b")
        assertEquals(listOf("b"), s.titles(sub.id))
    }
}
