import { describe, expect, it } from "vitest";
import {
  type FilteredList,
  HeadingInfo,
  Priority,
  SortOrder,
  headingInfo,
  headingTitle,
  isOverdue,
  matchesFilter,
  shareText,
  splitQuickAdd,
  visibleTasks,
} from "../src/index.js";
import { at, task } from "./helpers.js";

const now = at(2026, 11, 16, 19, 0);

describe("sub-list heading", () => {
  const tasks = [
    task({ title: "Tut sheet 6" }),
    task({ title: "Triple integral lec" }),
    task({ title: "Triple Integral finish" }),
  ];

  it("matches the owner's screenshot: Monday 16th Nov (0/3)", () => {
    expect(headingTitle("Monday 16th Nov", tasks, HeadingInfo.CompletedOfTotal, now)).toBe("Monday 16th Nov (0/3)");
  });

  it("supports every heading mode", () => {
    const mixed = [
      task({ completed: true, completedAt: now }),
      task({ dueAt: at(2026, 11, 15, 9, 0) }),
      task({ dueAt: at(2026, 11, 17, 9, 0) }),
      task({ deletedAt: now }),
    ];
    expect(headingInfo(mixed, HeadingInfo.None, now)).toBe("");
    expect(headingInfo(mixed, HeadingInfo.CompletedOfTotal, now)).toBe("1/3");
    expect(headingInfo(mixed, HeadingInfo.Percentage, now)).toBe("33%");
    expect(headingInfo(mixed, HeadingInfo.Overdue, now)).toBe("1");
    expect(headingInfo(mixed, HeadingInfo.Total, now)).toBe("3");
    expect(headingInfo(mixed, HeadingInfo.Uncompleted, now)).toBe("2");
    expect(headingInfo([], HeadingInfo.Percentage, now)).toBe("0%");
    expect(headingTitle("Empty", [], HeadingInfo.None, now)).toBe("Empty");
  });

  it("treats an all-day task as overdue only after its day ends", () => {
    expect(isOverdue(task({ dueAt: at(2026, 11, 16), allDay: true }), now)).toBe(false);
    expect(isOverdue(task({ dueAt: at(2026, 11, 15), allDay: true }), now)).toBe(true);
    expect(isOverdue(task({ dueAt: at(2026, 11, 15), completed: true }), now)).toBe(false);
  });
});

describe("sorting", () => {
  const a = task({ id: "a", title: "banana", rank: "B", priority: Priority.Low, createdAt: 3, updatedAt: 30, dueAt: at(2026, 11, 20) });
  const b = task({ id: "b", title: "Apple", rank: "C", priority: Priority.High, createdAt: 1, updatedAt: 10, highlight: true });
  const c = task({ id: "c", title: "cherry", rank: "A", priority: Priority.None, createdAt: 2, updatedAt: 20, dueAt: at(2026, 11, 18), completed: true, completedAt: 99 });
  const all = [a, b, c];
  const ids = (order: SortOrder, extra = {}) =>
    visibleTasks(all, { sortOrder: order, moveCompletedToBottom: false, hideCompleted: false, ...extra }).map((t) => t.id).join("");

  it("orders by each sort option", () => {
    expect(ids(SortOrder.UserDraggable)).toBe("cab");
    expect(ids(SortOrder.Priority)).toBe("bac");
    expect(ids(SortOrder.AlphaAZ)).toBe("bac");
    expect(ids(SortOrder.AlphaZA)).toBe("cab");
    expect(ids(SortOrder.CreatedNewest)).toBe("acb");
    expect(ids(SortOrder.CreatedOldest)).toBe("bca");
    expect(ids(SortOrder.ModifiedNewest)).toBe("acb");
    expect(ids(SortOrder.ModifiedOldest)).toBe("bca");
    expect(ids(SortOrder.DueNewest)).toBe("acb");
    expect(ids(SortOrder.DueOldest)).toBe("cab");
    expect(ids(SortOrder.Highlighted)).toBe("bca");
    expect(ids(SortOrder.CompletedNewest)).toBe("cab");
  });

  it("moves completed tasks to the bottom and hides them when asked", () => {
    expect(ids(SortOrder.UserDraggable, { moveCompletedToBottom: true })).toBe("abc");
    expect(ids(SortOrder.UserDraggable, { hideCompleted: true })).toBe("ab");
  });

  it("never shows deleted tasks", () => {
    const withDeleted = [...all, task({ id: "d", deletedAt: 5 })];
    expect(visibleTasks(withDeleted, { sortOrder: SortOrder.UserDraggable, moveCompletedToBottom: false, hideCompleted: false })).toHaveLength(3);
  });
});

describe("filtered lists", () => {
  const base: FilteredList = {
    id: "f1", createdAt: 1, updatedAt: 1, deletedAt: null, updatedBy: "d", rank: "V",
    title: "Filter", color: 0, subListIds: [], tagIds: [], completed: -1, highlighted: -1,
    priority: -1, due: { kind: "all" }, includeOverdue: false, sortOrder: SortOrder.UserDraggable,
  };

  it("filters by sub-list, completion, highlight, priority and tag", () => {
    const t = task({ subListId: "s2", priority: Priority.High, highlight: true, tagIds: ["g1"] });
    expect(matchesFilter(t, base, now)).toBe(true);
    expect(matchesFilter(t, { ...base, subListIds: ["s1"] }, now)).toBe(false);
    expect(matchesFilter(t, { ...base, subListIds: ["s1", "s2"] }, now)).toBe(true);
    expect(matchesFilter(t, { ...base, completed: 1 }, now)).toBe(false);
    expect(matchesFilter(t, { ...base, completed: 0 }, now)).toBe(true);
    expect(matchesFilter(t, { ...base, highlighted: 0 }, now)).toBe(false);
    expect(matchesFilter(t, { ...base, priority: Priority.Low }, now)).toBe(false);
    expect(matchesFilter(t, { ...base, priority: Priority.High }, now)).toBe(true);
    expect(matchesFilter(t, { ...base, tagIds: ["g2"] }, now)).toBe(false);
    expect(matchesFilter(task({ deletedAt: 1 }), base, now)).toBe(false);
  });

  it("filters by due date window, with and without overdue tasks", () => {
    const none = task();
    const today = task({ dueAt: at(2026, 11, 16, 23, 0) });
    const in3 = task({ dueAt: at(2026, 11, 19, 8, 0) });
    const in5 = task({ dueAt: at(2026, 11, 21, 8, 0) });
    const late = task({ dueAt: at(2026, 11, 10, 8, 0) });

    expect(matchesFilter(none, { ...base, due: { kind: "noDate" } }, now)).toBe(true);
    expect(matchesFilter(today, { ...base, due: { kind: "noDate" } }, now)).toBe(false);

    const f0: FilteredList = { ...base, due: { kind: "today" } };
    expect([none, today, in3, late].map((t) => matchesFilter(t, f0, now))).toEqual([false, true, false, false]);

    const f3: FilteredList = { ...base, due: { kind: "nextDays", days: 3 } };
    expect([today, in3, in5, late].map((t) => matchesFilter(t, f3, now))).toEqual([true, true, false, false]);
    expect(matchesFilter(late, { ...f3, includeOverdue: true }, now)).toBe(true);
    expect(matchesFilter({ ...late, completed: true }, { ...f3, includeOverdue: true }, now)).toBe(false);
  });
});

describe("share and quick add", () => {
  it("shares a list as text with open and done marks", () => {
    const text = shareText("To do", [task({ title: "Tut sheet 6" }), task({ title: "Done one", completed: true }), task({ title: "gone", deletedAt: 1 })]);
    expect(text).toBe("To do\n◻ Tut sheet 6\n✔ Done one");
  });

  it("splits pasted text into one task per non-empty line", () => {
    expect(splitQuickAdd("one\r\n two \n\nthree")).toEqual(["one", "two", "three"]);
    expect(splitQuickAdd("single")).toEqual(["single"]);
    expect(splitQuickAdd("  \n ")).toEqual([]);
  });
});
