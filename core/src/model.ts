/**
 * Data model shared by every client.
 *
 * Firestore layout (everything lives under the signed-in user):
 *   users/{uid}/taskLists/{id}
 *   users/{uid}/subLists/{id}
 *   users/{uid}/tasks/{id}
 *   users/{uid}/subTasks/{id}
 *   users/{uid}/tags/{id}
 *   users/{uid}/filteredLists/{id}
 *   users/{uid}/settings/synced
 *
 * Numeric option codes are the same ones the reference app stores, so the
 * option order and meaning stay identical.
 */

/** Milliseconds since the Unix epoch. */
export type Millis = number;

/** Fields every synced entity carries. */
export interface SyncMeta {
  /** UUID, also the Firestore document id. */
  id: string;
  createdAt: Millis;
  /** Time of the last edit, set by the editing device. Newest edit wins. */
  updatedAt: Millis;
  /** Soft delete marker. `null` means the entity is live. */
  deletedAt: Millis | null;
  /** Id of the device that made the last edit. */
  updatedBy: string;
}

/** Entities the user can drag into an order. */
export interface Ranked {
  /** Lexicographic rank key, see rank.ts. */
  rank: string;
}

export const Priority = { None: 0, Low: 1, Medium: 2, High: 3 } as const;
export type Priority = (typeof Priority)[keyof typeof Priority];

export const ReminderMode = { DontRemind: 0, WhenDue: 1, InAdvance: 2 } as const;
export type ReminderMode = (typeof ReminderMode)[keyof typeof ReminderMode];

export const ReminderType = { Notification: 0, Alarm: 1, None: 2 } as const;
export type ReminderType = (typeof ReminderType)[keyof typeof ReminderType];

export const Repeat = {
  None: 0,
  Daily: 1,
  Weekly: 2,
  Monthly: 3,
  Yearly: 4,
  DaysOfWeek: 5,
  Advanced: 6,
} as const;
export type Repeat = (typeof Repeat)[keyof typeof Repeat];

/** Units for "every N ..." and "remind N ... in advance". */
export const TimeUnit = { Days: 0, Weeks: 1, Months: 2, Years: 3, Minutes: 4, Hours: 5 } as const;
export type TimeUnit = (typeof TimeUnit)[keyof typeof TimeUnit];

export const RepeatFrom = { DueDate: 0, CompletionDateTime: 1, CompletionDateOnly: 2 } as const;
export type RepeatFrom = (typeof RepeatFrom)[keyof typeof RepeatFrom];

export const RepeatUntil = { Forever: 0, UntilDate: 1 } as const;
export type RepeatUntil = (typeof RepeatUntil)[keyof typeof RepeatUntil];

export const SortOrder = {
  UserDraggable: 0,
  AlphaAZ: 1,
  AlphaZA: 2,
  CreatedNewest: 3,
  CreatedOldest: 4,
  ModifiedNewest: 5,
  ModifiedOldest: 6,
  TaskListGrouped: 7,
  DueNewest: 8,
  DueOldest: 9,
  Priority: 10,
  Highlighted: 11,
  CompletedNewest: 12,
  CompletedOldest: 13,
} as const;
export type SortOrder = (typeof SortOrder)[keyof typeof SortOrder];

export interface TaskList extends SyncMeta, Ranked {
  title: string;
  /** ARGB colour, one of LIST_COLORS. */
  color: number;
}

export interface SubList extends SyncMeta, Ranked {
  listId: string;
  title: string;
  sortOrder: SortOrder;
  hideCompleted: boolean;
}

export interface Task extends SyncMeta, Ranked {
  listId: string;
  subListId: string;
  title: string;
  notes: string;

  completed: boolean;
  completedAt: Millis | null;

  priority: Priority;
  highlight: boolean;
  /** ARGB colour, one of HIGHLIGHT_COLORS. */
  highlightColor: number;
  tagIds: string[];
  subTasksExpanded: boolean;

  /** Due date. `null` when no date is set. */
  dueAt: Millis | null;
  allDay: boolean;

  reminderMode: ReminderMode;
  reminderType: ReminderType;
  /** "Remind me N units in advance", used when reminderMode is InAdvance. */
  reminderAdvance: number;
  reminderAdvanceUnit: TimeUnit;
  snoozedUntil: Millis | null;

  repeat: Repeat;
  /** Bit i set = weekday i selected, 0 = Sunday ... 6 = Saturday. */
  repeatDaysOfWeek: number;
  /** "Every N units", used when repeat is Advanced. */
  repeatInterval: number;
  repeatUnit: TimeUnit;
  repeatFrom: RepeatFrom;
  repeatUntil: RepeatUntil;
  repeatUntilAt: Millis | null;

  /** Sub-list the task was in when it went to Deleted Items, for restore. */
  deletedFromSubListId: string | null;
}

export interface SubTask extends SyncMeta, Ranked {
  taskId: string;
  title: string;
  completed: boolean;
  completedAt: Millis | null;
}

export interface Tag extends SyncMeta {
  title: string;
  color: number;
  filled: boolean;
}

/** -1 means "All" in the three filter fields below. */
export interface FilteredList extends SyncMeta, Ranked {
  title: string;
  color: number;
  /** Sub-lists to pull tasks from. */
  subListIds: string[];
  tagIds: string[];
  /** 1 = yes, 0 = no, -1 = all. */
  completed: 1 | 0 | -1;
  /** 1 = yes, 0 = no, -1 = all. */
  highlighted: 1 | 0 | -1;
  /** A Priority value, or -1 for all. */
  priority: Priority | -1;
  due: DueFilter;
  includeOverdue: boolean;
  sortOrder: SortOrder;
}

export type DueFilter =
  | { kind: "all" }
  | { kind: "noDate" }
  | { kind: "today" }
  | { kind: "nextDays"; days: number };

export const ACCENT = 0xfffec656;

export const LIST_COLORS: readonly number[] = [
  0xffff9800, 0xffff5722, 0xfff44336, 0xffe91e63, 0xff9c27b0, 0xff673ab7, 0xff3f51b5,
  0xff2196f3, 0xff03a9f4, 0xff00bcd4, 0xff009688, 0xff4caf50, 0xff8bc34a,
];

export const HIGHLIGHT_COLORS: readonly number[] = [
  0xfffec656, 0xffff9800, 0xffff5722, 0xfff44336, 0xffe91e63, 0xff9c27b0, 0xff673ab7,
  0xff3f51b5, 0xff2196f3, 0xff03a9f4, 0xff00bcd4, 0xff009688, 0xff4caf50, 0xff8bc34a,
  0xffcddc39,
];

export const PRIORITY_COLORS: Readonly<Record<Priority, number>> = {
  [Priority.High]: 0xffd32f2f,
  [Priority.Medium]: 0xfff57c00,
  [Priority.Low]: 0xfffec656,
  [Priority.None]: 0x00ffffff,
};

export const SNOOZE_MINUTES: readonly number[] = [5, 10, 15, 30, 60, 120];

export function isLive(e: Pick<SyncMeta, "deletedAt">): boolean {
  return e.deletedAt === null;
}
