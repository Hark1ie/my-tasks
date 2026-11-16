package app.mytasks.data

import app.mytasks.core.Entity
import app.mytasks.core.LIST_COLORS
import app.mytasks.core.Lists
import app.mytasks.core.Rank
import app.mytasks.core.Repeat
import app.mytasks.core.Schedule
import app.mytasks.core.SubList
import app.mytasks.core.SubTask
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
    const val SUB_TASKS = "subTasks"
    const val SETTINGS = "settings"
    val ALL = listOf(TASK_LISTS, SUB_LISTS, TASKS, SUB_TASKS, SETTINGS)
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
    private val _subTasks = MutableStateFlow<List<SubTask>>(emptyList())
    private val _ready = MutableStateFlow(false)

    /** Live task lists in menu order. */
    val lists: StateFlow<List<TaskList>> = _lists
    /** Live sub-lists, ordered. */
    val subLists: StateFlow<List<SubList>> = _subLists
    /** Every task, including ones in Deleted Items. */
    val tasks: StateFlow<List<Task>> = _tasks
    /** Live sub-tasks of every task, ordered. */
    val subTasks: StateFlow<List<SubTask>> = _subTasks
    /** True once the server has confirmed the contents at least once. */
    val ready: StateFlow<Boolean> = _ready

    /** Called when the synced settings document arrives or changes. */
    var onSettings: ((Map<String, Any?>) -> Unit)? = null

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
                Collections.SUB_TASKS ->
                    _subTasks.value = docs.map { SubTask.fromMap(it.key, it.value) }.observe().filter { it.isLive }.sortedBy { it.rank }
                Collections.SETTINGS -> docs[SETTINGS_DOC]?.let { onSettings?.invoke(it) }
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
    private fun put(subTask: SubTask) = backend.put(Collections.SUB_TASKS, subTask.id, subTask.toMap())

    /** Writes the synced settings. Device-only settings never come through here. */
    fun putSettings(values: Map<String, Any>) {
        backend.put(Collections.SETTINGS, SETTINGS_DOC, values + mapOf("updatedAt" to stamp(), "updatedBy" to deviceId))
    }

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

    /** Reorder Task Lists: [orderedIds] is the new order; only the moved list is written. */
    fun moveList(listId: String, orderedIds: List<String>) {
        val byId = _lists.value.associateBy { it.id }
        val list = byId[listId] ?: return
        val rank = rankAt(listId, orderedIds) { byId[it]?.rank } ?: return
        if (rank != list.rank) put(list.copy(rank = rank, updatedAt = stamp(), updatedBy = deviceId))
    }

    private fun rankAt(id: String, orderedIds: List<String>, rankOf: (String) -> String?): String? {
        val index = orderedIds.indexOf(id)
        if (index < 0) return null
        val before = orderedIds.getOrNull(index - 1)?.let(rankOf)
        val after = orderedIds.getOrNull(index + 1)?.let(rankOf)
        return if (before != null && after != null && before >= after) Rank.after(before) else Rank.between(before, after)
    }

    // ---- Sub-lists ----

    fun addSubList(listId: String, title: String): SubList {
        val t = stamp()
        val sub = SubList(newId(), t, t, null, deviceId, Rank.after(subListsOf(listId).lastOrNull()?.rank), listId, title.trim())
        put(sub)
        return sub
    }

    fun editSubList(sub: SubList, title: String, completedSubListId: String? = sub.completedSubListId) =
        put(sub.copy(title = title.trim(), completedSubListId = completedSubListId, updatedAt = stamp(), updatedBy = deviceId))

    fun setSort(sub: SubList, sortOrder: Int) =
        put(sub.copy(sortOrder = sortOrder, updatedAt = stamp(), updatedBy = deviceId))

    fun setHideCompleted(sub: SubList, hide: Boolean) =
        put(sub.copy(hideCompleted = hide, updatedAt = stamp(), updatedBy = deviceId))

    /** Reorder Sub Lists: [orderedIds] is the new order; only the moved sub-list is written. */
    fun moveSubList(subId: String, orderedIds: List<String>) {
        val byId = _subLists.value.associateBy { it.id }
        val sub = byId[subId] ?: return
        val rank = rankAt(subId, orderedIds) { byId[it]?.rank } ?: return
        if (rank != sub.rank) put(sub.copy(rank = rank, updatedAt = stamp(), updatedBy = deviceId))
    }

    /** Move Sub List to another task list, with its tasks. False if it is the list's last sub-list. */
    fun moveSubListTo(sub: SubList, targetListId: String): Boolean {
        if (sub.listId == targetListId) return true
        if (subListsOf(sub.listId).size <= 1) return false
        val rank = Rank.after(subListsOf(targetListId).lastOrNull()?.rank)
        put(sub.copy(listId = targetListId, rank = rank, updatedAt = stamp(), updatedBy = deviceId))
        tasksOf(sub.id).forEach { put(it.copy(listId = targetListId, updatedAt = stamp(), updatedBy = deviceId)) }
        return true
    }

    /** Duplicate Sub List: a copy with the same tasks and sub-tasks, placed last. */
    fun duplicateSubList(sub: SubList): SubList {
        val copy = addSubList(sub.listId, sub.title)
        for (task in tasksOf(sub.id)) {
            val t = stamp()
            val newTask = task.copy(id = newId(), createdAt = t, updatedAt = t, updatedBy = deviceId, subListId = copy.id)
            put(newTask)
            for (st in subTasksOf(task.id)) {
                val t2 = stamp()
                put(st.copy(id = newId(), createdAt = t2, updatedAt = t2, updatedBy = deviceId, taskId = newTask.id))
            }
        }
        return copy
    }

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

    /**
     * Ticks or unticks a task. Ticking a repeating task moves it to its next
     * due date instead and leaves it open, until the repeat has run out.
     */
    fun setCompleted(task: Task, completed: Boolean, keepHistory: Boolean = false) {
        val t = stamp()
        val next = if (completed && task.repeat != Repeat.NONE) Schedule.nextDue(task, clock()) else null
        if (next != null) {
            if (keepHistory) {
                // "Save recurring task history": leave a completed copy behind.
                val peers = ordered(task.subListId)
                val after = peers.getOrNull(peers.indexOfFirst { it.id == task.id } + 1)?.rank
                put(task.copy(id = newId(), createdAt = t, updatedAt = t, updatedBy = deviceId, completed = true, completedAt = t,
                    repeat = Repeat.NONE, reminderMode = 0, rank = Rank.between(task.rank, after)))
            }
            put(task.copy(dueAt = next, snoozedUntil = null, updatedAt = t, updatedBy = deviceId))
            subTasksOf(task.id).filter { it.completed }.forEach {
                put(it.copy(completed = false, completedAt = null, updatedAt = stamp(), updatedBy = deviceId))
            }
        } else {
            // "Where should completed tasks go?": a completed task can move to another sub-list.
            val home = _subLists.value.firstOrNull { it.id == task.subListId }
            val target = if (completed) _subLists.value.firstOrNull { it.id == home?.completedSubListId && it.id != task.subListId } else null
            val moved = if (target != null) {
                task.copy(listId = target.listId, subListId = target.id, rank = Rank.before(ordered(target.id).firstOrNull()?.rank))
            } else {
                task
            }
            put(moved.copy(completed = completed, completedAt = if (completed) t else null, updatedAt = t, updatedBy = deviceId))
        }
    }

    /** A new, unsaved task for the task screen. Nothing is stored until [saveTask]. */
    fun draftTask(sub: SubList): Task {
        val t = clock()
        return Task(newId(), t, t, null, deviceId, "V", sub.listId, sub.id, "")
    }

    /**
     * Saves the task screen: the task and its sub-tasks. [subTasks] is the full
     * list in order as (id or null for new, title, completed).
     */
    fun saveTask(task: Task, isNew: Boolean, atTop: Boolean, subTasks: List<Triple<String?, String, Boolean>>) {
        val title = task.title.trim()
        if (title.isEmpty()) return
        val stored = current(task.id)
        val movedSubList = stored != null && stored.subListId != task.subListId
        val rank = if (isNew || movedSubList) {
            val peers = ordered(task.subListId)
            if (atTop) Rank.before(peers.firstOrNull()?.rank) else Rank.after(peers.lastOrNull()?.rank)
        } else {
            stored?.rank ?: task.rank
        }
        val t = stamp()
        val completedAt = if (task.completed) (task.completedAt ?: t) else null
        put(task.copy(title = title, rank = rank, completedAt = completedAt, createdAt = if (isNew) t else task.createdAt, updatedAt = t, updatedBy = deviceId))

        val existing = subTasksOf(task.id).associateBy { it.id }
        val kept = HashSet<String>()
        var last: String? = null
        for ((id, rawTitle, done) in subTasks) {
            val subTitle = rawTitle.trim()
            if (subTitle.isEmpty()) continue
            last = Rank.after(last)
            val old = id?.let { existing[it] }
            val ts = stamp()
            if (old == null) {
                put(SubTask(newId(), ts, ts, null, deviceId, last, task.id, subTitle, done, if (done) ts else null))
            } else {
                kept.add(old.id)
                val changed = old.title != subTitle || old.completed != done || old.rank != last
                if (changed) {
                    put(old.copy(title = subTitle, completed = done, completedAt = if (done) (old.completedAt ?: ts) else null, rank = last, updatedAt = ts, updatedBy = deviceId))
                }
            }
        }
        for (old in existing.values) {
            if (old.id !in kept) {
                val ts = stamp()
                put(old.copy(deletedAt = ts, updatedAt = ts, updatedBy = deviceId))
            }
        }
    }

    fun subTasksOf(taskId: String): List<SubTask> = _subTasks.value.filter { it.taskId == taskId }

    /** Ticks a sub-task; when every sub-task is done the parent task is completed too. */
    fun toggleSubTask(subTaskId: String, completeParent: Boolean) {
        val st = _subTasks.value.firstOrNull { it.id == subTaskId } ?: return
        val t = stamp()
        val done = !st.completed
        put(st.copy(completed = done, completedAt = if (done) t else null, updatedAt = t, updatedBy = deviceId))
        if (done && completeParent) {
            val all = subTasksOf(st.taskId)
            if (all.all { it.completed }) current(st.taskId)?.takeIf { !it.completed }?.let { setCompleted(it, true) }
        }
    }

    /** Snooze: the reminder fires again at [until]. */
    fun snooze(taskId: String, until: Long) {
        current(taskId)?.let { put(it.copy(snoozedUntil = until, updatedAt = stamp(), updatedBy = deviceId)) }
    }

    fun hasTask(taskId: String): Boolean = current(taskId) != null

    fun setExpanded(taskId: String, expanded: Boolean) {
        current(taskId)?.let { put(it.copy(subTasksExpanded = expanded, updatedAt = stamp(), updatedBy = deviceId)) }
    }

    /** Move task to the top or bottom of its sub-list. */
    fun moveTaskToEdge(taskId: String, top: Boolean) {
        val task = current(taskId) ?: return
        val peers = ordered(task.subListId).filter { it.id != taskId }
        val rank = if (top) Rank.before(peers.firstOrNull()?.rank) else Rank.after(peers.lastOrNull()?.rank)
        put(task.copy(rank = rank, updatedAt = stamp(), updatedBy = deviceId))
    }

    /** Move tasks to another sub-list, keeping their order. */
    fun moveTasksTo(taskIds: List<String>, target: SubList, atTop: Boolean) {
        val moving = ordered2(taskIds)
        var edge = ordered(target.id).let { if (atTop) it.firstOrNull()?.rank else it.lastOrNull()?.rank }
        for (task in if (atTop) moving.reversed() else moving) {
            edge = if (atTop) Rank.before(edge) else Rank.after(edge)
            put(task.copy(listId = target.listId, subListId = target.id, rank = edge, updatedAt = stamp(), updatedBy = deviceId))
        }
    }

    private fun ordered2(taskIds: List<String>): List<Task> {
        val ids = taskIds.toSet()
        return _tasks.value.filter { it.id in ids && it.isLive }.sortedBy { it.rank }
    }

    fun duplicateTask(taskId: String) {
        val task = current(taskId) ?: return
        val peers = ordered(task.subListId)
        val index = peers.indexOfFirst { it.id == taskId }
        val rank = Rank.between(task.rank, peers.getOrNull(index + 1)?.rank)
        val t = stamp()
        val copy = task.copy(id = newId(), createdAt = t, updatedAt = t, updatedBy = deviceId, rank = rank)
        put(copy)
        for (st in subTasksOf(task.id)) {
            val t2 = stamp()
            put(st.copy(id = newId(), createdAt = t2, updatedAt = t2, updatedBy = deviceId, taskId = copy.id))
        }
    }

    private fun current(taskId: String): Task? = _tasks.value.firstOrNull { it.id == taskId }

    /** Ticks or unticks the task as it is stored now. */
    fun toggleCompleted(taskId: String, keepHistory: Boolean = false) {
        current(taskId)?.let { setCompleted(it, !it.completed, keepHistory) }
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
        const val SETTINGS_DOC = "synced"
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
