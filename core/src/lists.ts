/**
 * How a list is shown: heading counters, sort orders, filtered lists,
 * share-as-text and quick add.
 */

import {
  type FilteredList,
  type Millis,
  type Task,
  Priority,
  SortOrder,
  isLive,
} from "./model.js";
import { compareRanks } from "./rank.js";
import { HeadingInfo } from "./settings.js";

const DAY = 24 * 60 * 60 * 1000;

export function startOfDay(t: Millis): Millis {
  const d = new Date(t);
  d.setHours(0, 0, 0, 0);
  return d.getTime();
}

export function isOverdue(task: Pick<Task, "dueAt" | "allDay" | "completed">, now: Millis): boolean {
  if (task.completed || task.dueAt === null) return false;
  // An all-day task is only overdue once its day is over.
  return task.allDay ? startOfDay(task.dueAt) + DAY <= now : task.dueAt < now;
}

/** The counter shown after a sub-list or list title, without the brackets. Empty for "None". */
export function headingInfo(tasks: readonly Task[], mode: HeadingInfo, now: Millis): string {
  const live = tasks.filter(isLive);
  const total = live.length;
  const done = live.filter((t) => t.completed).length;
  switch (mode) {
    case HeadingInfo.None:
      return "";
    case HeadingInfo.CompletedOfTotal:
      return `${done}/${total}`;
    case HeadingInfo.Percentage:
      return `${total === 0 ? 0 : Math.round((done * 100) / total)}%`;
    case HeadingInfo.Overdue:
      return String(live.filter((t) => isOverdue(t, now)).length);
    case HeadingInfo.Total:
      return String(total);
    case HeadingInfo.Uncompleted:
      return String(total - done);
  }
}

/** "Monday 16th Nov (0/3)". Tabs show this in upper case. */
export function headingTitle(title: string, tasks: readonly Task[], mode: HeadingInfo, now: Millis): string {
  const info = headingInfo(tasks, mode, now);
  return info === "" ? title : `${title} (${info})`;
}

type Cmp = (a: Task, b: Task) => number;

const byRank: Cmp = (a, b) => compareRanks(a.rank, b.rank);
const byTitle: Cmp = (a, b) => a.title.localeCompare(b.title, undefined, { sensitivity: "base" });
const num = (pick: (t: Task) => number | null, dir: 1 | -1): Cmp => (a, b) => {
  const x = pick(a);
  const y = pick(b);
  // Tasks without a value always go last.
  if (x === null || y === null) return x === y ? 0 : x === null ? 1 : -1;
  return (x - y) * dir;
};

function comparator(order: SortOrder): Cmp {
  switch (order) {
    case SortOrder.UserDraggable:
    case SortOrder.TaskListGrouped:
      return byRank;
    case SortOrder.Priority:
      return num((t) => t.priority, -1);
    case SortOrder.AlphaAZ:
      return byTitle;
    case SortOrder.AlphaZA:
      return (a, b) => byTitle(b, a);
    case SortOrder.CreatedNewest:
      return num((t) => t.createdAt, -1);
    case SortOrder.CreatedOldest:
      return num((t) => t.createdAt, 1);
    case SortOrder.ModifiedNewest:
      return num((t) => t.updatedAt, -1);
    case SortOrder.ModifiedOldest:
      return num((t) => t.updatedAt, 1);
    case SortOrder.DueNewest:
      return num((t) => t.dueAt, -1);
    case SortOrder.DueOldest:
      return num((t) => t.dueAt, 1);
    case SortOrder.Highlighted:
      return num((t) => (t.highlight ? 1 : 0), -1);
    case SortOrder.CompletedNewest:
      return num((t) => t.completedAt, -1);
    case SortOrder.CompletedOldest:
      return num((t) => t.completedAt, 1);
  }
}

export interface ViewOptions {
  sortOrder: SortOrder;
  moveCompletedToBottom: boolean;
  hideCompleted: boolean;
}

/** Live tasks in display order. Equal tasks keep their dragged order. */
export function visibleTasks(tasks: readonly Task[], opts: ViewOptions): Task[] {
  const cmp = comparator(opts.sortOrder);
  return tasks
    .filter((t) => isLive(t) && !(opts.hideCompleted && t.completed))
    .sort((a, b) => {
      if (opts.moveCompletedToBottom && a.completed !== b.completed) return a.completed ? 1 : -1;
      return cmp(a, b) || byRank(a, b) || (a.id < b.id ? -1 : a.id > b.id ? 1 : 0);
    });
}

/** Whether a task belongs in a filtered list. Filtered lists never own tasks. */
export function matchesFilter(task: Task, f: FilteredList, now: Millis): boolean {
  if (!isLive(task)) return false;
  if (f.subListIds.length > 0 && !f.subListIds.includes(task.subListId)) return false;
  if (f.tagIds.length > 0 && !f.tagIds.some((id) => task.tagIds.includes(id))) return false;
  if (f.completed !== -1 && task.completed !== (f.completed === 1)) return false;
  if (f.highlighted !== -1 && task.highlight !== (f.highlighted === 1)) return false;
  if (f.priority !== -1 && task.priority !== f.priority) return false;

  if (f.due.kind === "all") return true;
  if (f.due.kind === "noDate") return task.dueAt === null;
  if (task.dueAt === null) return false;
  const today = startOfDay(now);
  const windowEnd = today + (f.due.kind === "today" ? 1 : f.due.days + 1) * DAY;
  if (task.dueAt >= today && task.dueAt < windowEnd) return true;
  return f.includeOverdue && task.dueAt < today && !task.completed;
}

/** "Share list" text: the title, then one line per task. */
export function shareText(title: string, tasks: readonly Task[]): string {
  const lines = tasks.filter(isLive).map((t) => `${t.completed ? "✔" : "◻"} ${t.title}`);
  return [title, ...lines].join("\n");
}

/**
 * Quick add. Text with line breaks can become one task per line; the app asks
 * "Split into multiple tasks?" when this returns more than one title.
 */
export function splitQuickAdd(text: string): string[] {
  return text
    .split(/\r?\n/)
    .map((s) => s.trim())
    .filter((s) => s.length > 0);
}

export function priorityLabel(p: Priority): string {
  return p === Priority.High ? "High" : p === Priority.Medium ? "Medium" : p === Priority.Low ? "Low" : "None";
}
