package app.mytasks.core

import java.util.Calendar

/** How a list is shown: heading counters, sort orders, quick add. Twin of core/src/lists.ts. */
object Lists {
    private const val DAY = 24L * 60 * 60 * 1000

    fun startOfDay(t: Long): Long = Calendar.getInstance().apply {
        timeInMillis = t
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun isOverdue(task: Task, now: Long): Boolean {
        val due = task.dueAt ?: return false
        if (task.completed) return false
        return if (task.allDay) startOfDay(due) + DAY <= now else due < now
    }

    /** The counter shown after a title, without brackets. Empty for "None". */
    fun headingInfo(tasks: List<Task>, mode: Int, now: Long): String {
        val live = tasks.filter { it.isLive }
        val total = live.size
        val done = live.count { it.completed }
        return when (mode) {
            HeadingInfo.COMPLETED_OF_TOTAL -> "$done/$total"
            HeadingInfo.PERCENTAGE -> "${if (total == 0) 0 else Math.round(done * 100.0 / total)}%"
            HeadingInfo.OVERDUE -> live.count { isOverdue(it, now) }.toString()
            HeadingInfo.TOTAL -> total.toString()
            HeadingInfo.UNCOMPLETED -> (total - done).toString()
            else -> ""
        }
    }

    fun headingTitle(title: String, tasks: List<Task>, mode: Int, now: Long): String {
        val info = headingInfo(tasks, mode, now)
        return if (info.isEmpty()) title else "$title ($info)"
    }

    private fun <T : Comparable<T>> by(dir: Int, pick: (Task) -> T?): Comparator<Task> = Comparator { a, b ->
        val x = pick(a)
        val y = pick(b)
        when {
            x == null && y == null -> 0
            x == null -> 1
            y == null -> -1
            else -> x.compareTo(y) * dir
        }
    }

    private val byRank: Comparator<Task> = Comparator { a, b -> a.rank.compareTo(b.rank) }
    private val byTitle: Comparator<Task> = Comparator { a, b -> a.title.compareTo(b.title, ignoreCase = true) }

    private fun comparator(order: Int): Comparator<Task> = when (order) {
        SortOrder.PRIORITY -> by(-1) { it.priority }
        SortOrder.ALPHA_AZ -> byTitle
        SortOrder.ALPHA_ZA -> byTitle.reversed()
        SortOrder.CREATED_NEWEST -> by(-1) { it.createdAt }
        SortOrder.CREATED_OLDEST -> by(1) { it.createdAt }
        SortOrder.MODIFIED_NEWEST -> by(-1) { it.updatedAt }
        SortOrder.MODIFIED_OLDEST -> by(1) { it.updatedAt }
        SortOrder.DUE_NEWEST -> by(-1) { it.dueAt }
        SortOrder.DUE_OLDEST -> by(1) { it.dueAt }
        SortOrder.HIGHLIGHTED -> by(-1) { if (it.highlight) 1 else 0 }
        SortOrder.COMPLETED_NEWEST -> by(-1) { it.completedAt }
        SortOrder.COMPLETED_OLDEST -> by(1) { it.completedAt }
        else -> byRank
    }

    /** Live tasks in display order. Equal tasks keep their dragged order. */
    fun visibleTasks(
        tasks: List<Task>,
        sortOrder: Int,
        moveCompletedToBottom: Boolean,
        hideCompleted: Boolean,
    ): List<Task> {
        val cmp = comparator(sortOrder)
        return tasks
            .filter { it.isLive && !(hideCompleted && it.completed) }
            .sortedWith { a, b ->
                if (moveCompletedToBottom && a.completed != b.completed) {
                    if (a.completed) 1 else -1
                } else {
                    val c = cmp.compare(a, b)
                    if (c != 0) c else {
                        val r = byRank.compare(a, b)
                        if (r != 0) r else a.id.compareTo(b.id)
                    }
                }
            }
    }

    /** Quick add: one task per non-empty line. */
    fun splitQuickAdd(text: String): List<String> =
        text.split("\r\n", "\n").map { it.trim() }.filter { it.isNotEmpty() }

    /** "Share list" text: the title, then one line per task. */
    fun shareText(title: String, tasks: List<Task>): String =
        (listOf(title) + tasks.filter { it.isLive }.map { (if (it.completed) "✔ " else "◻ ") + it.title })
            .joinToString("\n")
}
