/**
 * Reminder times and repeating tasks. All calendar maths uses the device's
 * local time zone, so "every day at 9:00" stays at 9:00 across clock changes.
 *
 * Open points to confirm against the reference app in milestone 3 are marked
 * CONFIRM.
 */

import { type Millis, type Task, ReminderMode, Repeat, RepeatFrom, RepeatUntil, TimeUnit } from "./model.js";

/** Adds `n` units in local calendar time. Month and year steps clamp to the last day of the month. */
export function addUnits(t: Millis, n: number, unit: TimeUnit): Millis {
  const d = new Date(t);
  switch (unit) {
    case TimeUnit.Minutes:
      return t + n * 60_000;
    case TimeUnit.Hours:
      return t + n * 3_600_000;
    case TimeUnit.Days:
      d.setDate(d.getDate() + n);
      return d.getTime();
    case TimeUnit.Weeks:
      d.setDate(d.getDate() + 7 * n);
      return d.getTime();
    case TimeUnit.Months:
      return addMonths(d, n);
    case TimeUnit.Years:
      return addMonths(d, 12 * n);
  }
}

function addMonths(d: Date, n: number): Millis {
  const day = d.getDate();
  const out = new Date(d.getTime());
  out.setDate(1);
  out.setMonth(out.getMonth() + n);
  const lastDay = new Date(out.getFullYear(), out.getMonth() + 1, 0).getDate();
  out.setDate(Math.min(day, lastDay));
  return out.getTime();
}

type ReminderFields = Pick<Task, "dueAt" | "reminderMode" | "reminderAdvance" | "reminderAdvanceUnit">;

/** When the reminder fires, or `null` if the task has none. */
export function reminderAt(task: ReminderFields): Millis | null {
  if (task.dueAt === null || task.reminderMode === ReminderMode.DontRemind) return null;
  if (task.reminderMode === ReminderMode.WhenDue) return task.dueAt;
  return addUnits(task.dueAt, -Math.max(0, task.reminderAdvance), task.reminderAdvanceUnit);
}

type RepeatFields = Pick<
  Task,
  | "dueAt"
  | "repeat"
  | "repeatDaysOfWeek"
  | "repeatInterval"
  | "repeatUnit"
  | "repeatFrom"
  | "repeatUntil"
  | "repeatUntilAt"
>;

/** One step of the repeat rule after `from`. */
function step(task: RepeatFields, from: Millis): Millis {
  switch (task.repeat) {
    case Repeat.None:
      return from;
    case Repeat.Daily:
      return addUnits(from, 1, TimeUnit.Days);
    case Repeat.Weekly:
      return addUnits(from, 1, TimeUnit.Weeks);
    case Repeat.Monthly:
      return addUnits(from, 1, TimeUnit.Months);
    case Repeat.Yearly:
      return addUnits(from, 1, TimeUnit.Years);
    case Repeat.Advanced:
      return addUnits(from, Math.max(1, task.repeatInterval), task.repeatUnit);
    case Repeat.DaysOfWeek: {
      const mask = task.repeatDaysOfWeek & 0x7f;
      if (mask === 0) return addUnits(from, 1, TimeUnit.Days);
      for (let i = 1; i <= 7; i++) {
        const next = addUnits(from, i, TimeUnit.Days);
        if (mask & (1 << new Date(next).getDay())) return next;
      }
      return addUnits(from, 7, TimeUnit.Days);
    }
  }
}

/**
 * The next due time of a repeating task that was completed at `completedAt`,
 * or `null` if it does not repeat again.
 *
 * - Repeat from due date: steps from the old due date. CONFIRM: steps are
 *   repeated until the result is after the completion time, so completing an
 *   overdue daily task moves it to the next future day, not to yesterday.
 * - Repeat from completion date and time: steps once from the completion time.
 * - Repeat from completion date only: steps once from the completion day,
 *   keeping the original time of day.
 */
export function nextDue(task: RepeatFields, completedAt: Millis): Millis | null {
  if (task.repeat === Repeat.None) return null;

  let next: Millis;
  if (task.repeatFrom === RepeatFrom.CompletionDateTime || task.dueAt === null) {
    next = step(task, completedAt);
  } else if (task.repeatFrom === RepeatFrom.CompletionDateOnly) {
    const due = new Date(task.dueAt);
    const base = new Date(completedAt);
    base.setHours(due.getHours(), due.getMinutes(), due.getSeconds(), due.getMilliseconds());
    next = step(task, base.getTime());
  } else {
    next = step(task, task.dueAt);
    for (let guard = 0; next <= completedAt && guard < 100_000; guard++) next = step(task, next);
  }

  if (task.repeatUntil === RepeatUntil.UntilDate && task.repeatUntilAt !== null && next > task.repeatUntilAt) {
    return null;
  }
  return next;
}
