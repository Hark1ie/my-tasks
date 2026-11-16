import { describe, expect, it } from "vitest";
import { ReminderMode, Repeat, RepeatFrom, RepeatUntil, TimeUnit, addUnits, nextDue, reminderAt } from "../src/index.js";
import { at, task } from "./helpers.js";

describe("calendar steps", () => {
  it("clamps month steps to the last day of a shorter month", () => {
    expect(addUnits(at(2026, 1, 31, 9, 0), 1, TimeUnit.Months)).toBe(at(2026, 2, 28, 9, 0));
    expect(addUnits(at(2024, 1, 31, 9, 0), 1, TimeUnit.Months)).toBe(at(2024, 2, 29, 9, 0));
    expect(addUnits(at(2024, 2, 29, 9, 0), 1, TimeUnit.Years)).toBe(at(2025, 2, 28, 9, 0));
    expect(addUnits(at(2026, 12, 15, 9, 0), 1, TimeUnit.Months)).toBe(at(2027, 1, 15, 9, 0));
  });

  it("steps minutes, hours, days and weeks", () => {
    const t = at(2026, 11, 16, 9, 0);
    expect(addUnits(t, 90, TimeUnit.Minutes)).toBe(at(2026, 11, 16, 10, 30));
    expect(addUnits(t, 20, TimeUnit.Hours)).toBe(at(2026, 11, 17, 5, 0));
    expect(addUnits(t, 20, TimeUnit.Days)).toBe(at(2026, 12, 6, 9, 0));
    expect(addUnits(t, 2, TimeUnit.Weeks)).toBe(at(2026, 11, 30, 9, 0));
  });
});

describe("reminders", () => {
  const due = at(2026, 11, 20, 18, 0);
  it("fires when due, in advance, or not at all", () => {
    expect(reminderAt(task({ dueAt: due, reminderMode: ReminderMode.DontRemind }))).toBeNull();
    expect(reminderAt(task({ dueAt: null, reminderMode: ReminderMode.WhenDue }))).toBeNull();
    expect(reminderAt(task({ dueAt: due, reminderMode: ReminderMode.WhenDue }))).toBe(due);
    expect(reminderAt(task({ dueAt: due, reminderMode: ReminderMode.InAdvance, reminderAdvance: 30, reminderAdvanceUnit: TimeUnit.Minutes }))).toBe(at(2026, 11, 20, 17, 30));
    expect(reminderAt(task({ dueAt: due, reminderMode: ReminderMode.InAdvance, reminderAdvance: 2, reminderAdvanceUnit: TimeUnit.Days }))).toBe(at(2026, 11, 18, 18, 0));
    expect(reminderAt(task({ dueAt: due, reminderMode: ReminderMode.InAdvance, reminderAdvance: 1, reminderAdvanceUnit: TimeUnit.Months }))).toBe(at(2026, 10, 20, 18, 0));
  });
});

describe("repeating tasks", () => {
  const due = at(2026, 11, 16, 9, 0); // a Monday
  const doneSameDay = at(2026, 11, 16, 21, 0);

  it("does not repeat when repeat is off", () => {
    expect(nextDue(task({ dueAt: due }), doneSameDay)).toBeNull();
  });

  it("repeats daily, weekly, monthly and yearly from the due date", () => {
    expect(nextDue(task({ dueAt: due, repeat: Repeat.Daily }), doneSameDay)).toBe(at(2026, 11, 17, 9, 0));
    expect(nextDue(task({ dueAt: due, repeat: Repeat.Weekly }), doneSameDay)).toBe(at(2026, 11, 23, 9, 0));
    expect(nextDue(task({ dueAt: due, repeat: Repeat.Monthly }), doneSameDay)).toBe(at(2026, 12, 16, 9, 0));
    expect(nextDue(task({ dueAt: due, repeat: Repeat.Yearly }), doneSameDay)).toBe(at(2027, 11, 16, 9, 0));
  });

  it("skips to the next future date when an overdue task is completed late", () => {
    const doneLate = at(2026, 11, 19, 21, 0);
    expect(nextDue(task({ dueAt: due, repeat: Repeat.Daily }), doneLate)).toBe(at(2026, 11, 20, 9, 0));
    expect(nextDue(task({ dueAt: due, repeat: Repeat.Weekly }), doneLate)).toBe(at(2026, 11, 23, 9, 0));
  });

  it("repeats on specific days of the week", () => {
    const monWedFri = (1 << 1) | (1 << 3) | (1 << 5);
    const t = task({ dueAt: due, repeat: Repeat.DaysOfWeek, repeatDaysOfWeek: monWedFri });
    expect(nextDue(t, doneSameDay)).toBe(at(2026, 11, 18, 9, 0));
    expect(nextDue({ ...t, dueAt: at(2026, 11, 20, 9, 0) }, at(2026, 11, 20, 10, 0))).toBe(at(2026, 11, 23, 9, 0));
  });

  it("repeats every N units in advanced mode", () => {
    const every = (n: number, unit: TimeUnit) => task({ dueAt: due, repeat: Repeat.Advanced, repeatInterval: n, repeatUnit: unit });
    expect(nextDue(every(3, TimeUnit.Days), at(2026, 11, 16, 9, 5))).toBe(at(2026, 11, 19, 9, 0));
    expect(nextDue(every(2, TimeUnit.Weeks), at(2026, 11, 16, 9, 5))).toBe(at(2026, 11, 30, 9, 0));
    expect(nextDue(every(90, TimeUnit.Minutes), at(2026, 11, 16, 9, 5))).toBe(at(2026, 11, 16, 10, 30));
  });

  it("repeats from the completion date and time", () => {
    const t = task({ dueAt: due, repeat: Repeat.Daily, repeatFrom: RepeatFrom.CompletionDateTime });
    expect(nextDue(t, at(2026, 11, 19, 21, 30))).toBe(at(2026, 11, 20, 21, 30));
  });

  it("repeats from the completion date only, keeping the original time", () => {
    const t = task({ dueAt: due, repeat: Repeat.Weekly, repeatFrom: RepeatFrom.CompletionDateOnly });
    expect(nextDue(t, at(2026, 11, 19, 21, 30))).toBe(at(2026, 11, 26, 9, 0));
  });

  it("stops after the repeat-until date", () => {
    const t = task({ dueAt: due, repeat: Repeat.Daily, repeatUntil: RepeatUntil.UntilDate, repeatUntilAt: at(2026, 11, 17, 23, 59) });
    expect(nextDue(t, doneSameDay)).toBe(at(2026, 11, 17, 9, 0));
    expect(nextDue({ ...t, dueAt: at(2026, 11, 17, 9, 0) }, at(2026, 11, 17, 10, 0))).toBeNull();
  });
});
