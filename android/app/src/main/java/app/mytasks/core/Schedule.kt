package app.mytasks.core

import java.util.Calendar

object ReminderMode { const val DONT_REMIND = 0; const val WHEN_DUE = 1; const val IN_ADVANCE = 2 }
object ReminderType { const val NOTIFICATION = 0; const val ALARM = 1; const val NONE = 2 }
object Repeat {
    const val NONE = 0; const val DAILY = 1; const val WEEKLY = 2; const val MONTHLY = 3
    const val YEARLY = 4; const val DAYS_OF_WEEK = 5; const val ADVANCED = 6
}
object TimeUnit { const val DAYS = 0; const val WEEKS = 1; const val MONTHS = 2; const val YEARS = 3; const val MINUTES = 4; const val HOURS = 5 }
object RepeatFrom { const val DUE_DATE = 0; const val COMPLETION_DATE_TIME = 1; const val COMPLETION_DATE_ONLY = 2 }
object RepeatUntil { const val FOREVER = 0; const val UNTIL_DATE = 1 }

/**
 * Reminder times and repeating tasks, the Kotlin twin of core/src/schedule.ts.
 * Calendar maths uses the device's time zone, so "every day at 9:00" stays at 9:00.
 */
object Schedule {
    /** Adds [n] units in local calendar time. Month and year steps clamp to the end of the month. */
    fun addUnits(t: Long, n: Int, unit: Int): Long {
        val c = Calendar.getInstance().apply { timeInMillis = t }
        when (unit) {
            TimeUnit.MINUTES -> return t + n * 60_000L
            TimeUnit.HOURS -> return t + n * 3_600_000L
            TimeUnit.DAYS -> c.add(Calendar.DAY_OF_MONTH, n)
            TimeUnit.WEEKS -> c.add(Calendar.DAY_OF_MONTH, 7 * n)
            TimeUnit.MONTHS -> c.add(Calendar.MONTH, n)
            TimeUnit.YEARS -> c.add(Calendar.YEAR, n)
        }
        return c.timeInMillis
    }

    /** When the reminder fires, or null if the task has none. */
    fun reminderAt(task: Task): Long? {
        val due = task.dueAt ?: return null
        return when (task.reminderMode) {
            ReminderMode.WHEN_DUE -> due
            ReminderMode.IN_ADVANCE -> addUnits(due, -maxOf(0, task.reminderAdvance), task.reminderAdvanceUnit)
            else -> null
        }
    }

    private fun step(task: Task, from: Long): Long = when (task.repeat) {
        Repeat.DAILY -> addUnits(from, 1, TimeUnit.DAYS)
        Repeat.WEEKLY -> addUnits(from, 1, TimeUnit.WEEKS)
        Repeat.MONTHLY -> addUnits(from, 1, TimeUnit.MONTHS)
        Repeat.YEARLY -> addUnits(from, 1, TimeUnit.YEARS)
        Repeat.ADVANCED -> addUnits(from, maxOf(1, task.repeatInterval), task.repeatUnit)
        Repeat.DAYS_OF_WEEK -> {
            val mask = task.repeatDaysOfWeek and 0x7f
            var next = addUnits(from, 1, TimeUnit.DAYS)
            if (mask != 0) {
                // Bit 0 is Sunday, like Calendar.SUNDAY - 1.
                var guard = 0
                while (guard < 7 && mask and (1 shl (dayOfWeek(next))) == 0) {
                    next = addUnits(next, 1, TimeUnit.DAYS)
                    guard++
                }
            }
            next
        }
        else -> from
    }

    private fun dayOfWeek(t: Long): Int = Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.DAY_OF_WEEK) - 1

    /**
     * The next due time after a repeating task is completed at [completedAt],
     * or null if it does not repeat again. See schedule.ts for the rules.
     */
    fun nextDue(task: Task, completedAt: Long): Long? {
        if (task.repeat == Repeat.NONE) return null
        val due = task.dueAt
        var next: Long
        if (task.repeatFrom == RepeatFrom.COMPLETION_DATE_TIME || due == null) {
            next = step(task, completedAt)
        } else if (task.repeatFrom == RepeatFrom.COMPLETION_DATE_ONLY) {
            val d = Calendar.getInstance().apply { timeInMillis = due }
            val base = Calendar.getInstance().apply {
                timeInMillis = completedAt
                set(Calendar.HOUR_OF_DAY, d.get(Calendar.HOUR_OF_DAY))
                set(Calendar.MINUTE, d.get(Calendar.MINUTE))
                set(Calendar.SECOND, d.get(Calendar.SECOND))
                set(Calendar.MILLISECOND, d.get(Calendar.MILLISECOND))
            }
            next = step(task, base.timeInMillis)
        } else {
            next = step(task, due)
            var guard = 0
            while (next <= completedAt && guard < 100_000) {
                next = step(task, next)
                guard++
            }
        }
        val until = task.repeatUntilAt
        if (task.repeatUntil == RepeatUntil.UNTIL_DATE && until != null && next > until) return null
        return next
    }
}
