package app.mytasks.core

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ScheduleTest {
    @Before
    fun zone() = TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))

    private fun at(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0): Long =
        Calendar.getInstance().apply { clear(); set(y, m - 1, d, h, min, 0) }.timeInMillis

    private fun task(due: Long?, repeat: Int = Repeat.NONE, build: Task.() -> Task = { this }) = Task(
        id = "t", createdAt = 1, updatedAt = 1, deletedAt = null, updatedBy = "test", rank = "V",
        listId = "l", subListId = "s", title = "t", dueAt = due, repeat = repeat,
    ).build()

    private val due = at(2026, 11, 16, 9, 0) // a Monday
    private val doneSameDay = at(2026, 11, 16, 21, 0)

    @Test
    fun monthStepsClampToShorterMonths() {
        assertEquals(at(2026, 2, 28, 9), Schedule.addUnits(at(2026, 1, 31, 9), 1, TimeUnit.MONTHS))
        assertEquals(at(2024, 2, 29, 9), Schedule.addUnits(at(2024, 1, 31, 9), 1, TimeUnit.MONTHS))
        assertEquals(at(2025, 2, 28, 9), Schedule.addUnits(at(2024, 2, 29, 9), 1, TimeUnit.YEARS))
        assertEquals(at(2026, 11, 16, 10, 30), Schedule.addUnits(due, 90, TimeUnit.MINUTES))
        assertEquals(at(2026, 11, 30, 9), Schedule.addUnits(due, 2, TimeUnit.WEEKS))
    }

    @Test
    fun reminders() {
        assertNull(Schedule.reminderAt(task(due)))
        assertNull(Schedule.reminderAt(task(null) { copy(reminderMode = ReminderMode.WHEN_DUE) }))
        assertEquals(due, Schedule.reminderAt(task(due) { copy(reminderMode = ReminderMode.WHEN_DUE) }))
        assertEquals(
            at(2026, 11, 16, 8, 30),
            Schedule.reminderAt(task(due) { copy(reminderMode = ReminderMode.IN_ADVANCE, reminderAdvance = 30, reminderAdvanceUnit = TimeUnit.MINUTES) }),
        )
        assertEquals(
            at(2026, 11, 14, 9),
            Schedule.reminderAt(task(due) { copy(reminderMode = ReminderMode.IN_ADVANCE, reminderAdvance = 2, reminderAdvanceUnit = TimeUnit.DAYS) }),
        )
    }

    @Test
    fun repeatsFromTheDueDate() {
        assertNull(Schedule.nextDue(task(due), doneSameDay))
        assertEquals(at(2026, 11, 17, 9), Schedule.nextDue(task(due, Repeat.DAILY), doneSameDay))
        assertEquals(at(2026, 11, 23, 9), Schedule.nextDue(task(due, Repeat.WEEKLY), doneSameDay))
        assertEquals(at(2026, 12, 16, 9), Schedule.nextDue(task(due, Repeat.MONTHLY), doneSameDay))
        assertEquals(at(2027, 11, 16, 9), Schedule.nextDue(task(due, Repeat.YEARLY), doneSameDay))
    }

    @Test
    fun skipsToTheNextFutureDateWhenCompletedLate() {
        val late = at(2026, 11, 19, 21)
        assertEquals(at(2026, 11, 20, 9), Schedule.nextDue(task(due, Repeat.DAILY), late))
        assertEquals(at(2026, 11, 23, 9), Schedule.nextDue(task(due, Repeat.WEEKLY), late))
    }

    @Test
    fun specificDaysAndAdvanced() {
        val monWedFri = (1 shl 1) or (1 shl 3) or (1 shl 5)
        val t = task(due, Repeat.DAYS_OF_WEEK) { copy(repeatDaysOfWeek = monWedFri) }
        assertEquals(at(2026, 11, 18, 9), Schedule.nextDue(t, doneSameDay))
        assertEquals(at(2026, 11, 23, 9), Schedule.nextDue(t.copy(dueAt = at(2026, 11, 20, 9)), at(2026, 11, 20, 10)))
        val every3 = task(due, Repeat.ADVANCED) { copy(repeatInterval = 3, repeatUnit = TimeUnit.DAYS) }
        assertEquals(at(2026, 11, 19, 9), Schedule.nextDue(every3, at(2026, 11, 16, 9, 5)))
    }

    @Test
    fun repeatsFromCompletion() {
        val dateTime = task(due, Repeat.DAILY) { copy(repeatFrom = RepeatFrom.COMPLETION_DATE_TIME) }
        assertEquals(at(2026, 11, 20, 21, 30), Schedule.nextDue(dateTime, at(2026, 11, 19, 21, 30)))
        val dateOnly = task(due, Repeat.WEEKLY) { copy(repeatFrom = RepeatFrom.COMPLETION_DATE_ONLY) }
        assertEquals(at(2026, 11, 26, 9), Schedule.nextDue(dateOnly, at(2026, 11, 19, 21, 30)))
    }

    @Test
    fun stopsAfterTheUntilDate() {
        val t = task(due, Repeat.DAILY) { copy(repeatUntil = RepeatUntil.UNTIL_DATE, repeatUntilAt = at(2026, 11, 17, 23, 59)) }
        assertEquals(at(2026, 11, 17, 9), Schedule.nextDue(t, doneSameDay))
        assertNull(Schedule.nextDue(t.copy(dueAt = at(2026, 11, 17, 9)), at(2026, 11, 17, 10)))
    }
}
