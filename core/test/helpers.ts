import { type Task, Priority, ReminderMode, ReminderType, Repeat, RepeatFrom, RepeatUntil, TimeUnit } from "../src/index.js";

let counter = 0;

export function task(over: Partial<Task> = {}): Task {
  counter += 1;
  return {
    id: over.id ?? `t${counter}`,
    createdAt: 1000 + counter,
    updatedAt: 1000 + counter,
    deletedAt: null,
    updatedBy: "dev-a",
    rank: String.fromCharCode(64 + counter),
    listId: "l1",
    subListId: "s1",
    title: `Task ${counter}`,
    notes: "",
    completed: false,
    completedAt: null,
    priority: Priority.None,
    highlight: false,
    highlightColor: 0xfffec656,
    tagIds: [],
    subTasksExpanded: false,
    dueAt: null,
    allDay: false,
    reminderMode: ReminderMode.DontRemind,
    reminderType: ReminderType.Notification,
    reminderAdvance: 0,
    reminderAdvanceUnit: TimeUnit.Minutes,
    snoozedUntil: null,
    repeat: Repeat.None,
    repeatDaysOfWeek: 0,
    repeatInterval: 1,
    repeatUnit: TimeUnit.Days,
    repeatFrom: RepeatFrom.DueDate,
    repeatUntil: RepeatUntil.Forever,
    repeatUntilAt: null,
    deletedFromSubListId: null,
    ...over,
  };
}

/** Local time in the test zone. Month is 1-based. */
export function at(y: number, m: number, d: number, h = 0, min = 0): number {
  return new Date(y, m - 1, d, h, min, 0, 0).getTime();
}
