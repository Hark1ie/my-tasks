/** Sample data for demo mode (?demo): the owner's lists from his screenshots. */
import type { Doc } from "./data/store";

export function demoSeed(): Record<string, Record<string, Doc>> {
  const t = Date.now() - 3_600_000;
  const meta = (n: number) => ({ createdAt: t + n, updatedAt: t + n, deletedAt: null, updatedBy: "demo" });
  const task = (id: string, sub: string, rank: string, title: string, extra: Doc = {}) => [
    id,
    {
      ...meta(Number(id.slice(1))), rank, listId: "l1", subListId: sub, title, notes: "", completed: false, completedAt: null,
      priority: 0, highlight: false, highlightColor: 0xfffec656, tagIds: [], subTasksExpanded: false, dueAt: null, allDay: false,
      reminderMode: 0, reminderType: 0, reminderAdvance: 0, reminderAdvanceUnit: 4, snoozedUntil: null, repeat: 0,
      repeatDaysOfWeek: 0, repeatInterval: 1, repeatUnit: 0, repeatFrom: 0, repeatUntil: 0, repeatUntilAt: null,
      deletedFromSubListId: null, ...extra,
    },
  ] as const;
  return {
    taskLists: {
      l1: { ...meta(1), rank: "V", title: "To do", color: 0xffff9800 },
      l2: { ...meta(2), rank: "V01", title: "College", color: 0xff2196f3 },
    },
    subLists: {
      s1: { ...meta(3), rank: "V", listId: "l1", title: "Monday 16th Nov", sortOrder: 0, hideCompleted: false, completedSubListId: null },
      s2: { ...meta(4), rank: "V01", listId: "l1", title: "Tuesday 17th", sortOrder: 0, hideCompleted: false, completedSubListId: null },
      s3: { ...meta(5), rank: "V", listId: "l2", title: "Sub List 1", sortOrder: 0, hideCompleted: false, completedSubListId: null },
    },
    tasks: Object.fromEntries([
      task("t10", "s1", "V", "Tut sheet 6"),
      task("t11", "s1", "V01", "Triple integral lec", { dueAt: Date.now() + 2 * 3_600_000, reminderMode: 1 }),
      task("t12", "s1", "V02", "Triple Integral finish", { notes: "Chapter 15.6 and 15.7" }),
      task("t13", "s2", "V", "Bio lab slides", { priority: 3 }),
    ]),
    subTasks: {},
    settings: {},
  };
}
