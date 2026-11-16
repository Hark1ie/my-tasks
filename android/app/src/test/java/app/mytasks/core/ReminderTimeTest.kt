package app.mytasks.core

import app.mytasks.reminders.PendingReminder
import app.mytasks.reminders.ReminderScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderTimeTest {
    private val hour = 3_600_000L
    private val now = 1_000 * hour

    private fun task(build: Task.() -> Task = { this }) = Task(
        id = "t", createdAt = 1, updatedAt = 1, deletedAt = null, updatedBy = "test", rank = "V",
        listId = "l", subListId = "s", title = "Pay | rent", dueAt = now + 2 * hour, reminderMode = ReminderMode.WHEN_DUE,
    ).build()

    @Test
    fun firesWhenDueOrInAdvanceOnlyInTheFuture() {
        assertEquals(now + 2 * hour, ReminderScheduler.fireTime(task(), now))
        assertEquals(
            now + hour,
            ReminderScheduler.fireTime(task { copy(reminderMode = ReminderMode.IN_ADVANCE, reminderAdvance = 1, reminderAdvanceUnit = TimeUnit.HOURS) }, now),
        )
        assertNull(ReminderScheduler.fireTime(task { copy(dueAt = now - hour) }, now))
        assertNull(ReminderScheduler.fireTime(task { copy(reminderMode = ReminderMode.DONT_REMIND) }, now))
        assertNull(ReminderScheduler.fireTime(task { copy(reminderType = ReminderType.NONE) }, now))
        assertNull(ReminderScheduler.fireTime(task { copy(completed = true) }, now))
        assertNull(ReminderScheduler.fireTime(task { copy(deletedAt = 5) }, now))
    }

    @Test
    fun aSnoozeReplacesTheNormalTimeUntilItPasses() {
        assertEquals(now + 600_000, ReminderScheduler.fireTime(task { copy(dueAt = now - hour, snoozedUntil = now + 600_000) }, now))
        assertEquals(now + 2 * hour, ReminderScheduler.fireTime(task { copy(snoozedUntil = now - 1) }, now))
    }

    @Test
    fun pendingRemindersSurviveSavingAndLoading() {
        val pending = ReminderScheduler.pendingFor(listOf(task()), now).getValue("t")
        assertEquals("Pay | rent", pending.title)
        assertEquals(pending, PendingReminder.decode(pending.encode()))
        assertNull(PendingReminder.decode("broken"))
    }
}
