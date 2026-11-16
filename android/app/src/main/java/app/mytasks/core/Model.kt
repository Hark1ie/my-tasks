package app.mytasks.core

/**
 * Data model, the Kotlin twin of core/src/model.ts. Field names are the
 * Firestore field names. Times are milliseconds since the Unix epoch.
 */
interface Entity {
    val id: String
    val createdAt: Long
    val updatedAt: Long
    val deletedAt: Long?
    val updatedBy: String
    fun toMap(): Map<String, Any?>
}

val Entity.isLive: Boolean get() = deletedAt == null

object Priority { const val NONE = 0; const val LOW = 1; const val MEDIUM = 2; const val HIGH = 3 }

object SortOrder {
    const val USER_DRAGGABLE = 0
    const val ALPHA_AZ = 1
    const val ALPHA_ZA = 2
    const val CREATED_NEWEST = 3
    const val CREATED_OLDEST = 4
    const val MODIFIED_NEWEST = 5
    const val MODIFIED_OLDEST = 6
    const val TASK_LIST_GROUPED = 7
    const val DUE_NEWEST = 8
    const val DUE_OLDEST = 9
    const val PRIORITY = 10
    const val HIGHLIGHTED = 11
    const val COMPLETED_NEWEST = 12
    const val COMPLETED_OLDEST = 13
}

object HeadingInfo {
    const val NONE = 0
    const val COMPLETED_OF_TOTAL = 1
    const val PERCENTAGE = 2
    const val OVERDUE = 3
    const val TOTAL = 4
    const val UNCOMPLETED = 5
}

val LIST_COLORS: List<Long> = listOf(
    0xFFFF9800, 0xFFFF5722, 0xFFF44336, 0xFFE91E63, 0xFF9C27B0, 0xFF673AB7, 0xFF3F51B5,
    0xFF2196F3, 0xFF03A9F4, 0xFF00BCD4, 0xFF009688, 0xFF4CAF50, 0xFF8BC34A,
)

data class TaskList(
    override val id: String,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long?,
    override val updatedBy: String,
    val rank: String,
    val title: String,
    val color: Long,
) : Entity {
    override fun toMap() = meta() + mapOf("rank" to rank, "title" to title, "color" to color)

    companion object {
        fun fromMap(id: String, m: Map<String, Any?>) = TaskList(
            id, m.long("createdAt"), m.long("updatedAt"), m.longOrNull("deletedAt"), m.string("updatedBy"),
            rank = m.string("rank", "V"), title = m.string("title"), color = m.long("color", LIST_COLORS[0]),
        )
    }
}

data class SubList(
    override val id: String,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long?,
    override val updatedBy: String,
    val rank: String,
    val listId: String,
    val title: String,
    val sortOrder: Int = SortOrder.USER_DRAGGABLE,
    val hideCompleted: Boolean = false,
    /** "Where should completed tasks go?": another sub-list, or null for this one. */
    val completedSubListId: String? = null,
) : Entity {
    override fun toMap() = meta() + mapOf(
        "rank" to rank, "listId" to listId, "title" to title, "sortOrder" to sortOrder, "hideCompleted" to hideCompleted,
        "completedSubListId" to completedSubListId,
    )

    companion object {
        fun fromMap(id: String, m: Map<String, Any?>) = SubList(
            id, m.long("createdAt"), m.long("updatedAt"), m.longOrNull("deletedAt"), m.string("updatedBy"),
            rank = m.string("rank", "V"), listId = m.string("listId"), title = m.string("title"),
            sortOrder = m.long("sortOrder").toInt(), hideCompleted = m["hideCompleted"] as? Boolean ?: false,
            completedSubListId = m["completedSubListId"] as? String,
        )
    }
}

data class Task(
    override val id: String,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long?,
    override val updatedBy: String,
    val rank: String,
    val listId: String,
    val subListId: String,
    val title: String,
    val notes: String = "",
    val completed: Boolean = false,
    val completedAt: Long? = null,
    val priority: Int = Priority.NONE,
    val highlight: Boolean = false,
    val highlightColor: Long = 0xFFFEC656,
    val tagIds: List<String> = emptyList(),
    val subTasksExpanded: Boolean = false,
    val dueAt: Long? = null,
    val allDay: Boolean = false,
    val reminderMode: Int = 0,
    val reminderType: Int = 0,
    val reminderAdvance: Int = 0,
    val reminderAdvanceUnit: Int = 4,
    val snoozedUntil: Long? = null,
    val repeat: Int = 0,
    val repeatDaysOfWeek: Int = 0,
    val repeatInterval: Int = 1,
    val repeatUnit: Int = 0,
    val repeatFrom: Int = 0,
    val repeatUntil: Int = 0,
    val repeatUntilAt: Long? = null,
    val deletedFromSubListId: String? = null,
) : Entity {
    override fun toMap() = meta() + mapOf(
        "rank" to rank, "listId" to listId, "subListId" to subListId, "title" to title, "notes" to notes,
        "completed" to completed, "completedAt" to completedAt, "priority" to priority,
        "highlight" to highlight, "highlightColor" to highlightColor, "tagIds" to tagIds,
        "subTasksExpanded" to subTasksExpanded, "dueAt" to dueAt, "allDay" to allDay,
        "reminderMode" to reminderMode, "reminderType" to reminderType, "reminderAdvance" to reminderAdvance,
        "reminderAdvanceUnit" to reminderAdvanceUnit, "snoozedUntil" to snoozedUntil, "repeat" to repeat,
        "repeatDaysOfWeek" to repeatDaysOfWeek, "repeatInterval" to repeatInterval, "repeatUnit" to repeatUnit,
        "repeatFrom" to repeatFrom, "repeatUntil" to repeatUntil, "repeatUntilAt" to repeatUntilAt,
        "deletedFromSubListId" to deletedFromSubListId,
    )

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(id: String, m: Map<String, Any?>) = Task(
            id, m.long("createdAt"), m.long("updatedAt"), m.longOrNull("deletedAt"), m.string("updatedBy"),
            rank = m.string("rank", "V"), listId = m.string("listId"), subListId = m.string("subListId"),
            title = m.string("title"), notes = m.string("notes"),
            completed = m["completed"] as? Boolean ?: false, completedAt = m.longOrNull("completedAt"),
            priority = m.long("priority").toInt(), highlight = m["highlight"] as? Boolean ?: false,
            highlightColor = m.long("highlightColor", 0xFFFEC656),
            tagIds = (m["tagIds"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            subTasksExpanded = m["subTasksExpanded"] as? Boolean ?: false,
            dueAt = m.longOrNull("dueAt"), allDay = m["allDay"] as? Boolean ?: false,
            reminderMode = m.long("reminderMode").toInt(), reminderType = m.long("reminderType").toInt(),
            reminderAdvance = m.long("reminderAdvance").toInt(),
            reminderAdvanceUnit = m.long("reminderAdvanceUnit", 4).toInt(),
            snoozedUntil = m.longOrNull("snoozedUntil"), repeat = m.long("repeat").toInt(),
            repeatDaysOfWeek = m.long("repeatDaysOfWeek").toInt(), repeatInterval = m.long("repeatInterval", 1).toInt(),
            repeatUnit = m.long("repeatUnit").toInt(), repeatFrom = m.long("repeatFrom").toInt(),
            repeatUntil = m.long("repeatUntil").toInt(), repeatUntilAt = m.longOrNull("repeatUntilAt"),
            deletedFromSubListId = m["deletedFromSubListId"] as? String,
        )
    }
}

data class SubTask(
    override val id: String,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long?,
    override val updatedBy: String,
    val rank: String,
    val taskId: String,
    val title: String,
    val completed: Boolean = false,
    val completedAt: Long? = null,
) : Entity {
    override fun toMap() = meta() + mapOf(
        "rank" to rank, "taskId" to taskId, "title" to title, "completed" to completed, "completedAt" to completedAt,
    )

    companion object {
        fun fromMap(id: String, m: Map<String, Any?>) = SubTask(
            id, m.long("createdAt"), m.long("updatedAt"), m.longOrNull("deletedAt"), m.string("updatedBy"),
            rank = m.string("rank", "V"), taskId = m.string("taskId"), title = m.string("title"),
            completed = m["completed"] as? Boolean ?: false, completedAt = m.longOrNull("completedAt"),
        )
    }
}

val PRIORITY_COLORS: Map<Int, Long> = mapOf(
    Priority.HIGH to 0xFFD32F2F, Priority.MEDIUM to 0xFFF57C00, Priority.LOW to 0xFFFEC656,
)

val HIGHLIGHT_COLORS: List<Long> = listOf(0xFFFEC656) + LIST_COLORS + listOf(0xFFCDDC39)

private fun Entity.meta(): Map<String, Any?> = mapOf(
    "createdAt" to createdAt, "updatedAt" to updatedAt, "deletedAt" to deletedAt, "updatedBy" to updatedBy,
)

private fun Map<String, Any?>.long(key: String, default: Long = 0): Long = (this[key] as? Number)?.toLong() ?: default
private fun Map<String, Any?>.longOrNull(key: String): Long? = (this[key] as? Number)?.toLong()
private fun Map<String, Any?>.string(key: String, default: String = ""): String = this[key] as? String ?: default
