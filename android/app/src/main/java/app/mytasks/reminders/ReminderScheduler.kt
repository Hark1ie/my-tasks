package app.mytasks.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import app.mytasks.MainActivity
import app.mytasks.core.ReminderMode
import app.mytasks.core.ReminderType
import app.mytasks.core.Schedule
import app.mytasks.core.Task
import app.mytasks.core.isLive

/** One reminder waiting to fire. */
data class PendingReminder(val taskId: String, val at: Long, val type: Int, val dueAt: Long, val title: String) {
    fun encode(): String = listOf(taskId, at, type, dueAt, title).joinToString("|")

    companion object {
        fun decode(text: String): PendingReminder? {
            val p = text.split("|", limit = 5)
            if (p.size < 5) return null
            return PendingReminder(p[0], p[1].toLongOrNull() ?: return null, p[2].toIntOrNull() ?: return null, p[3].toLongOrNull() ?: return null, p[4])
        }
    }
}

/**
 * Keeps the phone's exact alarms in step with the tasks. The list of pending
 * reminders is also saved on the device, so they can be set again after a
 * restart without waiting for the app to be opened.
 */
object ReminderScheduler {
    const val ACTION_FIRE = "app.mytasks.REMINDER_FIRE"
    const val EXTRA_TASK_ID = "taskId"
    const val EXTRA_TITLE = "title"
    const val EXTRA_TYPE = "type"
    const val EXTRA_DUE = "dueAt"
    private const val PREFS = "reminders"
    private const val KEY = "pending"

    /** When a task's reminder should fire next, or null. A snooze replaces the normal time. */
    fun fireTime(task: Task, now: Long): Long? {
        if (!task.isLive || task.completed) return null
        if (task.reminderMode == ReminderMode.DONT_REMIND || task.reminderType == ReminderType.NONE) return null
        val snoozed = task.snoozedUntil
        val at = if (snoozed != null && snoozed > now) snoozed else Schedule.reminderAt(task)
        return at?.takeIf { it > now }
    }

    fun pendingFor(tasks: List<Task>, now: Long): Map<String, PendingReminder> {
        val out = HashMap<String, PendingReminder>()
        for (task in tasks) {
            val at = fireTime(task, now) ?: continue
            out[task.id] = PendingReminder(task.id, at, task.reminderType, task.dueAt ?: at, task.title.replace('\n', ' '))
        }
        return out
    }

    /** Sets, changes and cancels alarms so they match [tasks]. */
    fun sync(context: Context, tasks: List<Task>, now: Long = System.currentTimeMillis()) {
        val wanted = pendingFor(tasks, now)
        val saved = load(context)
        for ((id, old) in saved) if (id !in wanted) cancel(context, old)
        for ((id, reminder) in wanted) if (saved[id] != reminder) schedule(context, reminder)
        save(context, wanted)
    }

    /** After a restart or a clock change: set every saved reminder again. Overdue ones fire now. */
    fun rescheduleSaved(context: Context, now: Long = System.currentTimeMillis()) {
        for (reminder in load(context).values) {
            schedule(context, if (reminder.at <= now) reminder.copy(at = now + 5_000) else reminder)
        }
    }

    /** Removes a reminder that has fired from the saved list. */
    fun markFired(context: Context, taskId: String) {
        val saved = HashMap(load(context))
        if (saved.remove(taskId) != null) save(context, saved)
    }

    fun cancelAll(context: Context) {
        load(context).values.forEach { cancel(context, it) }
        save(context, emptyMap())
    }

    private fun fireIntent(context: Context, reminder: PendingReminder): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_FIRE)
            .setData(Uri.parse("mytasks://reminder/${reminder.taskId}"))
            .putExtra(EXTRA_TASK_ID, reminder.taskId)
            .putExtra(EXTRA_TITLE, reminder.title)
            .putExtra(EXTRA_TYPE, reminder.type)
            .putExtra(EXTRA_DUE, reminder.dueAt)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun schedule(context: Context, reminder: PendingReminder) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = fireIntent(context, reminder)
        try {
            if (reminder.type == ReminderType.ALARM) {
                val show = PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
                )
                alarms.setAlarmClock(AlarmManager.AlarmClockInfo(reminder.at, show), pi)
            } else {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.at, pi)
            }
        } catch (_: SecurityException) {
            // Exact alarms were switched off for the app: fall back to an inexact one.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.at, pi)
        }
    }

    private fun cancel(context: Context, reminder: PendingReminder) {
        context.getSystemService(AlarmManager::class.java)?.cancel(fireIntent(context, reminder))
    }

    private fun load(context: Context): Map<String, PendingReminder> {
        val set = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY, emptySet()) ?: emptySet()
        return set.mapNotNull { PendingReminder.decode(it) }.associateBy { it.taskId }
    }

    private fun save(context: Context, reminders: Map<String, PendingReminder>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet(KEY, reminders.values.map { it.encode() }.toSet()).apply()
    }
}
