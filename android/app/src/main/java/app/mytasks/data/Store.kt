package app.mytasks.data

import app.mytasks.core.Entity
import app.mytasks.core.LIST_COLORS
import app.mytasks.core.Lists
import app.mytasks.core.Rank
import app.mytasks.core.SubList
import app.mytasks.core.Task
import app.mytasks.core.TaskList
import app.mytasks.core.isLive
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Where the data lives: Firestore on a signed-in device, memory in tests. */
interface Backend {
    /**
     * Starts listening. [onSnapshot] is called with the full, current contents
     * of a collection every time it changes, including right after a local write.
     * `fromServer` is false while the contents come only from the offline cache.
     */
    fun start(onSnapshot: (collection: String, docs: Map<String, Map<String, Any?>>, fromServer: Boolean) -> Unit)
    fun put(collection: String, id: String, data: Map<String, Any?>)
    fun stop()
}

object Collections {
    const val TASK_LISTS = "taskLists"
    const val SUB_LISTS = "subLists"
    const val TASKS = "tasks"
    val ALL = listOf(TASK_LISTS, SUB_LISTS, TASKS)
}

/**
 * The app's data and every change the user can make to it.
 *
 * State always comes from the backend's snapshots, so what is on screen is
 * exactly what is stored. Every write stamps `updatedAt`/`updatedBy`; the newest
 * edit wins between devices.
 */
class Store(
    private val backend: Backend,
    private val deviceId: String,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val _lists = MutableStateFlow<List<TaskList>>(emptyList())
    private val _subLists = MutableStateFlow<List<SubList>>(emptyList())
    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    private val _ready = MutableStateFlow(false)

    /** Live task lists in menu order. */
    val lists: StateFlow<List<TaskList>> = _lists
    /** Live sub-lists, ordered. */
    val subLists: StateFlow<List<SubList>> = _subLists
    /** Every task, including ones in Deleted Items. */
    val tasks: StateFlow<List<Task>> = _tasks
    /** True once the server has confirmed the contents at least once. */
    val ready: StateFlow<Boolean> = _ready

    private val seen = HashSet<String>()
    private var lastStamp = 0L

    fun start() {
        backend.start { collection, docs, fromServer ->
            when (collection) {
                Collections.TASK_LISTS ->
                    _lists.value = docs.map { TaskList.fromMap(it.key, it.value) }.observe().filter { it.isLive }.sortedBy { it.rank }
                Collections.SUB_LISTS ->
                    _subLists.value = docs.map { SubList.fromMap(it.key, it.value) }.observe().filter { it.isLive }.sortedBy { it.rank }
                Collections.TASKS ->
                    _tasks.value = docs.map { Task.fromMap(it.key, it.value) }.observe()
            }
            if (fromServer) seen.add(collection)
            if (!_ready.value && seen.containsAll(Collections.ALL)) {
                _ready.value = true
                if (_lists.value.isEmpty()) addList("To do", LIST_COLORS[0])
            }
        }
    }

    fun stop() = backend.stop()

    private fun <T : Entity> List<T>.observe(): List<T> {
        for (e in this) if (e.updatedAt > lastStamp) lastStamp = e.updatedAt
        return this
    }

    /** Edit time that always moves forward, even if the device clock jumps back. */
    private fun stamp(): Long {
        val t = maxOf(clock(), lastStamp + 1)
        lastStamp = t
        return t
    }

    private fun put(list: TaskList) = backend.put(Collections.TASK_LISTS, list.id, list.toMap())
    private fun put(subList: SubList) = backend.put(Collections.SUB_LISTS, subList.id, subList.toMap())
    private fun put(task: Task) = backend.put(Collections.TASKS, task.id, task.toMap())

    // ---- Queries ----

    fun subListsOf(listId: String): List<SubList> = _subLists.value.filter { it.listId == listId }

    fun tasksOf(subListId: String): List<Task> = _tasks.value.filter { it.subListId == subListId && it.isLive }

    private fun ordered(subListId: String): List<Task> = tasksOf(subListId).sortedBy { it.rank }

    // ---- Task lists ----

    /** A list always has at least one sub-list, so a new list gets one. */
    fun addList(title: String, color: Long): TaskList {
        val t = stamp()
        val list = TaskList(newId(), t, t, null, deviceId, Rank.after(_lists.value.lastOrNull()?.rank), title.trim(), color)
        put(list)
        addSubList(list.id, DEFAULT_SUB_LIST_TITLE)
        return list
    }

    fun editList(list: TaskList, title: String, color: Long) =
        put(list.copy(title = title.trim(), color = color, updatedAt = stamp(), updatedBy = deviceId))

    fun deleteList(list: TaskList) {
        val t = stamp()
        put(list.copy(deletedAt = t, updatedAt = t, updatedBy = deviceId))
    }

    // ---- Sub-lists ----

    fun addSubList(listId: String, title: String): SubList {
        val t = stamp()
        val sub = SubList(newId(), t, t, null, deviceId, Rank.after(subListsOf(listId).lastOrNull()?.rank), listId, title.trim())
        put(sub)
        return sub
    }

    fun editSubList(sub: SubList, title: String) =
        put(sub.copy(title = title.trim(), updatedAt = stamp(), updatedBy = deviceId))

    fun setSort(sub: SubList, sortOrder: Int) =
        put(sub.copy(sortOrder = sortOrder, updatedAt = stamp(), updatedBy = deviceId))

    fun setHideCompleted(sub: SubList, hide: Boolean) =
        put(sub.copy(hideCompleted = hide, updatedAt = stamp(), updatedBy = deviceId))

    /** Returns false, and deletes nothing, if this is the list's last sub-list. */
    fun deleteSubList(sub: SubList): Boolean {
        if (subListsOf(sub.listId).size <= 1) return false
        val t = stamp()
        put(sub.copy(deletedAt = t, updatedAt = t, updatedBy = deviceId))
        return true
    }

    // ---- Tasks ----

    fun addTask(sub: SubList, title: String, atTop: Boolean): Task? {
        val clean = title.trim()
        if (clean.isEmpty()) return null
        val current = ordered(sub.id)
        val rank = if (atTop) Rank.before(current.firstOrNull()?.rank) else Rank.after(current.lastOrNull()?.rank)
        val t = stamp()
        val task = Task(newId(), t, t, null, deviceId, rank, sub.listId, sub.id, clean)
        put(task)
        return task
    }

    /** Adds several tasks so they end up in the order given. */
    fun addTasks(sub: SubList, titles: List<String>, atTop: Boolean) {
        val clean = titles.map { it.trim() }.filter { it.isNotEmpty() }
        val current = ordered(sub.id)
        var edge = if (atTop) current.firstOrNull()?.rank else current.lastOrNull()?.rank
        for (title in if (atTop) clean.reversed() else clean) {
            edge = if (atTop) Rank.before(edge) else Rank.after(edge)
            val t = stamp()
            put(Task(newId(), t, t, null, deviceId, edge, sub.listId, sub.id, title))
        }
    }

    fun editTaskTitle(task: Task, title: String) {
        val clean = title.trim()
        if (clean.isNotEmpty() && clean != task.title) put(task.copy(title = clean, updatedAt = stamp(), updatedBy = deviceId))
    }

    fun setCompleted(task: Task, completed: Boolean) {
        val t = stamp()
        put(task.copy(completed = completed, completedAt = if (completed) t else null, updatedAt = t, updatedBy = deviceId))
    }

    private fun current(taskId: String): Task? = _tasks.value.firstOrNull { it.id == taskId }

    /** Ticks or unticks the task as it is stored now. */
    fun toggleCompleted(taskId: String) {
        current(taskId)?.let { setCompleted(it, !it.completed) }
    }

    fun deleteTask(taskId: String) {
        current(taskId)?.takeIf { it.isLive }?.let { deleteTask(it) }
    }

    fun editTaskTitle(taskId: String, title: String) {
        current(taskId)?.let { editTaskTitle(it, title) }
    }

    /** Moves a task to Deleted Items. */
    fun deleteTask(task: Task) {
        val t = stamp()
        put(task.copy(deletedAt = t, deletedFromSubListId = task.subListId, updatedAt = t, updatedBy = deviceId))
    }

    fun restoreTask(taskId: String) {
        val task = _tasks.value.firstOrNull { it.id == taskId } ?: return
        put(task.copy(deletedAt = null, updatedAt = stamp(), updatedBy = deviceId))
    }

    /**
     * Drag and drop: [orderedIds] is the order on screen after the drop. Only
     * the moved task is written.
     */
    fun moveTask(taskId: String, orderedIds: List<String>) {
        val byId = _tasks.value.associateBy { it.id }
        val task = byId[taskId] ?: return
        val index = orderedIds.indexOf(taskId)
        if (index < 0) return
        val before = orderedIds.getOrNull(index - 1)?.let { byId[it]?.rank }
        val after = orderedIds.getOrNull(index + 1)?.let { byId[it]?.rank }
        val rank = when {
            before != null && after != null && before >= after -> Rank.after(before)
            else -> Rank.between(before, after)
        }
        if (rank != task.rank) put(task.copy(rank = rank, updatedAt = stamp(), updatedBy = deviceId))
    }

    fun completeAll(sub: SubList) = tasksOf(sub.id).filter { !it.completed }.forEach { setCompleted(it, true) }

    fun uncheckCompleted(sub: SubList) = tasksOf(sub.id).filter { it.completed }.forEach { setCompleted(it, false) }

    fun removeCompleted(sub: SubList): List<String> =
        tasksOf(sub.id).filter { it.completed }.onEach { deleteTask(it) }.map { it.id }

    fun deleteAll(sub: SubList): List<String> = tasksOf(sub.id).onEach { deleteTask(it) }.map { it.id }

    fun shareText(list: TaskList, sub: SubList, moveCompletedToBottom: Boolean): String =
        Lists.shareText(sub.title, Lists.visibleTasks(tasksOf(sub.id), sub.sortOrder, moveCompletedToBottom, false))

    companion object {
        const val DEFAULT_SUB_LIST_TITLE = "Sub List 1"
    }
}

/** Keeps everything in memory. Used by tests and when sync is not set up. */
class MemoryBackend : Backend {
    private val data = HashMap<String, MutableMap<String, Map<String, Any?>>>()
    private var listener: ((String, Map<String, Map<String, Any?>>, Boolean) -> Unit)? = null

    override fun start(onSnapshot: (String, Map<String, Map<String, Any?>>, Boolean) -> Unit) {
        listener = onSnapshot
        for (c in Collections.ALL) onSnapshot(c, data[c]?.toMap() ?: emptyMap(), true)
    }

    override fun put(collection: String, id: String, data: Map<String, Any?>) {
        val docs = this.data.getOrPut(collection) { LinkedHashMap() }
        val current = docs[id]
        // Same rule as the server: an older edit never replaces a newer one.
        if (current != null && (current["updatedAt"] as Long) > (data["updatedAt"] as Long)) return
        docs[id] = data
        listener?.invoke(collection, docs.toMap(), true)
    }

    override fun stop() {
        listener = null
    }
}
