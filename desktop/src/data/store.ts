/**
 * The app's data and every change the user can make to it. A port of the
 * Android app's Store (android/.../data/Store.kt): same Firestore layout, same
 * field names, same rank keys and edit stamps, so both apps share one account.
 */
import {
  type SubList,
  type SubTask,
  type Task,
  type TaskList,
  LIST_COLORS,
  Repeat,
  isLive,
  nextDue,
  rankAfter,
  rankBefore,
  rankBetween,
  shareText as coreShareText,
  visibleTasks,
} from "@core";

export type Doc = Record<string, unknown>;

/** Where the data lives: Firestore when signed in, memory in demo mode. */
export interface Backend {
  /**
   * Called with the full, current contents of a collection whenever it
   * changes, including right after a local write. `fromServer` is false while
   * the contents come only from the offline cache.
   */
  start(onSnapshot: (collection: string, docs: Map<string, Doc>, fromServer: boolean) => void): void;
  put(collection: string, id: string, data: Doc): void;
  stop(): void;
}

export const Collections = {
  taskLists: "taskLists",
  subLists: "subLists",
  tasks: "tasks",
  subTasks: "subTasks",
  settings: "settings",
} as const;

const ALL = Object.values(Collections);
export const SETTINGS_DOC = "synced";
export const DEFAULT_SUB_LIST_TITLE = "Sub List 1";

export interface State {
  /** Live task lists in menu order. */
  lists: TaskList[];
  /** Live sub-lists, ordered. */
  subLists: SubList[];
  /** Every task, including ones in Deleted Items. */
  tasks: Task[];
  /** Live sub-tasks, ordered. */
  subTasks: SubTask[];
  /** True once the server has confirmed the contents at least once. */
  ready: boolean;
  /** The synced settings document, once it has arrived. */
  cloudSettings: Doc | null;
}

// ---- reading documents (missing fields get the same defaults as on Android) ----

const num = (v: unknown, d = 0): number => (typeof v === "number" ? v : d);
const numOrNull = (v: unknown): number | null => (typeof v === "number" ? v : null);
const str = (v: unknown, d = ""): string => (typeof v === "string" ? v : d);
const bool = (v: unknown, d = false): boolean => (typeof v === "boolean" ? v : d);

function meta(id: string, m: Doc) {
  return {
    id,
    createdAt: num(m.createdAt),
    updatedAt: num(m.updatedAt),
    deletedAt: numOrNull(m.deletedAt),
    updatedBy: str(m.updatedBy),
  };
}

export function readList(id: string, m: Doc): TaskList {
  return { ...meta(id, m), rank: str(m.rank, "V"), title: str(m.title), color: num(m.color, LIST_COLORS[0]) };
}

export function readSubList(id: string, m: Doc): SubList {
  return {
    ...meta(id, m),
    rank: str(m.rank, "V"),
    listId: str(m.listId),
    title: str(m.title),
    sortOrder: num(m.sortOrder) as SubList["sortOrder"],
    hideCompleted: bool(m.hideCompleted),
    completedSubListId: typeof m.completedSubListId === "string" ? m.completedSubListId : null,
  };
}

export function readTask(id: string, m: Doc): Task {
  return {
    ...meta(id, m),
    rank: str(m.rank, "V"),
    listId: str(m.listId),
    subListId: str(m.subListId),
    title: str(m.title),
    notes: str(m.notes),
    completed: bool(m.completed),
    completedAt: numOrNull(m.completedAt),
    priority: num(m.priority) as Task["priority"],
    highlight: bool(m.highlight),
    highlightColor: num(m.highlightColor, 0xfffec656),
    tagIds: Array.isArray(m.tagIds) ? m.tagIds.filter((x): x is string => typeof x === "string") : [],
    subTasksExpanded: bool(m.subTasksExpanded),
    dueAt: numOrNull(m.dueAt),
    allDay: bool(m.allDay),
    reminderMode: num(m.reminderMode) as Task["reminderMode"],
    reminderType: num(m.reminderType) as Task["reminderType"],
    reminderAdvance: num(m.reminderAdvance),
    reminderAdvanceUnit: num(m.reminderAdvanceUnit, 4) as Task["reminderAdvanceUnit"],
    snoozedUntil: numOrNull(m.snoozedUntil),
    repeat: num(m.repeat) as Task["repeat"],
    repeatDaysOfWeek: num(m.repeatDaysOfWeek),
    repeatInterval: num(m.repeatInterval, 1),
    repeatUnit: num(m.repeatUnit) as Task["repeatUnit"],
    repeatFrom: num(m.repeatFrom) as Task["repeatFrom"],
    repeatUntil: num(m.repeatUntil) as Task["repeatUntil"],
    repeatUntilAt: numOrNull(m.repeatUntilAt),
    deletedFromSubListId: typeof m.deletedFromSubListId === "string" ? m.deletedFromSubListId : null,
  };
}

export function readSubTask(id: string, m: Doc): SubTask {
  return {
    ...meta(id, m),
    rank: str(m.rank, "V"),
    taskId: str(m.taskId),
    title: str(m.title),
    completed: bool(m.completed),
    completedAt: numOrNull(m.completedAt),
  };
}

/** Firestore document contents: everything except the id, which is the document name. */
function toDoc<T extends { id: string }>(e: T): Doc {
  const { id: _id, ...rest } = e;
  return rest as Doc;
}

const byRank = <T extends { rank: string }>(a: T, b: T) => (a.rank < b.rank ? -1 : a.rank > b.rank ? 1 : 0);

export function newId(): string {
  return crypto.randomUUID();
}

/** One sub-task row of the task screen: id (null when new), title, done. */
export interface SubDraft {
  id: string | null;
  title: string;
  completed: boolean;
}

export class Store {
  private state: State = { lists: [], subLists: [], tasks: [], subTasks: [], ready: false, cloudSettings: null };
  private listeners = new Set<() => void>();
  private seen = new Set<string>();
  private lastStamp = 0;

  constructor(
    private readonly backend: Backend,
    private readonly deviceId: string,
    private readonly clock: () => number = () => Date.now(),
  ) {}

  // ---- subscription, for React's useSyncExternalStore ----

  getState = (): State => this.state;

  subscribe = (fn: () => void): (() => void) => {
    this.listeners.add(fn);
    return () => this.listeners.delete(fn);
  };

  private set(patch: Partial<State>) {
    this.state = { ...this.state, ...patch };
    this.listeners.forEach((fn) => fn());
  }

  start() {
    this.backend.start((collection, docs, fromServer) => {
      const entries = [...docs.entries()];
      switch (collection) {
        case Collections.taskLists:
          this.set({ lists: this.observe(entries.map(([id, m]) => readList(id, m))).filter(isLive).sort(byRank) });
          break;
        case Collections.subLists:
          this.set({ subLists: this.observe(entries.map(([id, m]) => readSubList(id, m))).filter(isLive).sort(byRank) });
          break;
        case Collections.tasks:
          this.set({ tasks: this.observe(entries.map(([id, m]) => readTask(id, m))) });
          break;
        case Collections.subTasks:
          this.set({ subTasks: this.observe(entries.map(([id, m]) => readSubTask(id, m))).filter(isLive).sort(byRank) });
          break;
        case Collections.settings:
          this.set({ cloudSettings: docs.get(SETTINGS_DOC) ?? null });
          break;
      }
      if (fromServer) this.seen.add(collection);
      if (!this.state.ready && ALL.every((c) => this.seen.has(c))) {
        this.set({ ready: true });
        if (this.state.lists.length === 0) this.addList("To do", LIST_COLORS[0]!);
      }
    });
  }

  stop() {
    this.backend.stop();
  }

  private observe<T extends { updatedAt: number }>(items: T[]): T[] {
    for (const e of items) if (e.updatedAt > this.lastStamp) this.lastStamp = e.updatedAt;
    return items;
  }

  /** Edit time that always moves forward, even if the clock jumps back. */
  private stamp(): number {
    const t = Math.max(this.clock(), this.lastStamp + 1);
    this.lastStamp = t;
    return t;
  }

  private put<T extends { id: string }>(collection: string, e: T) {
    this.backend.put(collection, e.id, toDoc(e));
  }

  private edited<T extends { updatedAt: number; updatedBy: string }>(e: T, changes: Partial<T>): T {
    return { ...e, ...changes, updatedAt: this.stamp(), updatedBy: this.deviceId };
  }

  // ---- queries ----

  subListsOf(listId: string): SubList[] {
    return this.state.subLists.filter((s) => s.listId === listId);
  }

  tasksOf(subListId: string): Task[] {
    return this.state.tasks.filter((t) => t.subListId === subListId && isLive(t));
  }

  subTasksOf(taskId: string): SubTask[] {
    return this.state.subTasks.filter((s) => s.taskId === taskId);
  }

  private ordered(subListId: string): Task[] {
    return this.tasksOf(subListId).sort(byRank);
  }

  private current(taskId: string): Task | undefined {
    return this.state.tasks.find((t) => t.id === taskId);
  }

  // ---- settings ----

  /** Writes the synced settings. Device-only settings never come through here. */
  putSettings(values: Doc) {
    this.backend.put(Collections.settings, SETTINGS_DOC, { ...values, updatedAt: this.stamp(), updatedBy: this.deviceId });
  }

  // ---- task lists ----

  /** A list always has at least one sub-list, so a new list gets one. */
  addList(title: string, color: number): TaskList {
    const t = this.stamp();
    const list: TaskList = {
      id: newId(), createdAt: t, updatedAt: t, deletedAt: null, updatedBy: this.deviceId,
      rank: rankAfter(this.state.lists.at(-1)?.rank ?? null), title: title.trim(), color,
    };
    this.put(Collections.taskLists, list);
    this.addSubList(list.id, DEFAULT_SUB_LIST_TITLE);
    return list;
  }

  editList(list: TaskList, title: string, color: number) {
    this.put(Collections.taskLists, this.edited(list, { title: title.trim(), color }));
  }

  deleteList(list: TaskList) {
    const t = this.stamp();
    this.put(Collections.taskLists, { ...list, deletedAt: t, updatedAt: t, updatedBy: this.deviceId });
  }

  private rankAt(id: string, orderedIds: string[], rankOf: (id: string) => string | undefined): string | null {
    const i = orderedIds.indexOf(id);
    if (i < 0) return null;
    const before = i > 0 ? rankOf(orderedIds[i - 1]!) ?? null : null;
    const after = i < orderedIds.length - 1 ? rankOf(orderedIds[i + 1]!) ?? null : null;
    if (before !== null && after !== null && before >= after) return rankAfter(before);
    if (before === null) return rankBefore(after);
    if (after === null) return rankAfter(before);
    return rankBetween(before, after);
  }

  /** Reorder Task Lists: only the moved list is written. */
  moveList(listId: string, orderedIds: string[]) {
    const byId = new Map(this.state.lists.map((l) => [l.id, l]));
    const list = byId.get(listId);
    const rank = this.rankAt(listId, orderedIds, (id) => byId.get(id)?.rank);
    if (list && rank !== null && rank !== list.rank) this.put(Collections.taskLists, this.edited(list, { rank }));
  }

  // ---- sub-lists ----

  addSubList(listId: string, title: string, completedSubListId: string | null = null): SubList {
    const t = this.stamp();
    const sub: SubList = {
      id: newId(), createdAt: t, updatedAt: t, deletedAt: null, updatedBy: this.deviceId,
      rank: rankAfter(this.subListsOf(listId).at(-1)?.rank ?? null), listId, title: title.trim(),
      sortOrder: 0, hideCompleted: false, completedSubListId,
    };
    this.put(Collections.subLists, sub);
    return sub;
  }

  editSubList(sub: SubList, title: string, completedSubListId: string | null = sub.completedSubListId) {
    this.put(Collections.subLists, this.edited(sub, { title: title.trim(), completedSubListId }));
  }

  setSort(sub: SubList, sortOrder: SubList["sortOrder"]) {
    this.put(Collections.subLists, this.edited(sub, { sortOrder }));
  }

  setHideCompleted(sub: SubList, hideCompleted: boolean) {
    this.put(Collections.subLists, this.edited(sub, { hideCompleted }));
  }

  moveSubList(subId: string, orderedIds: string[]) {
    const byId = new Map(this.state.subLists.map((s) => [s.id, s]));
    const sub = byId.get(subId);
    const rank = this.rankAt(subId, orderedIds, (id) => byId.get(id)?.rank);
    if (sub && rank !== null && rank !== sub.rank) this.put(Collections.subLists, this.edited(sub, { rank }));
  }

  /** False, and nothing moves, if it is its list's last sub-list. */
  moveSubListTo(sub: SubList, targetListId: string): boolean {
    if (sub.listId === targetListId) return true;
    if (this.subListsOf(sub.listId).length <= 1) return false;
    const rank = rankAfter(this.subListsOf(targetListId).at(-1)?.rank ?? null);
    this.put(Collections.subLists, this.edited(sub, { listId: targetListId, rank }));
    for (const task of this.tasksOf(sub.id)) this.put(Collections.tasks, this.edited(task, { listId: targetListId }));
    return true;
  }

  duplicateSubList(sub: SubList): SubList {
    const copy = this.addSubList(sub.listId, sub.title);
    for (const task of this.tasksOf(sub.id)) {
      const t = this.stamp();
      const newTask: Task = { ...task, id: newId(), createdAt: t, updatedAt: t, updatedBy: this.deviceId, subListId: copy.id };
      this.put(Collections.tasks, newTask);
      for (const st of this.subTasksOf(task.id)) {
        const t2 = this.stamp();
        this.put(Collections.subTasks, { ...st, id: newId(), createdAt: t2, updatedAt: t2, updatedBy: this.deviceId, taskId: newTask.id });
      }
    }
    return copy;
  }

  /** False, and nothing is deleted, if it is its list's last sub-list. */
  deleteSubList(sub: SubList): boolean {
    if (this.subListsOf(sub.listId).length <= 1) return false;
    const t = this.stamp();
    this.put(Collections.subLists, { ...sub, deletedAt: t, updatedAt: t, updatedBy: this.deviceId });
    return true;
  }

  // ---- tasks ----

  /** A new, unsaved task for the task screen. Nothing is stored until saveTask. */
  draftTask(sub: SubList): Task {
    const t = this.clock();
    return {
      id: newId(), createdAt: t, updatedAt: t, deletedAt: null, updatedBy: this.deviceId, rank: "V",
      listId: sub.listId, subListId: sub.id, title: "", notes: "", completed: false, completedAt: null,
      priority: 0, highlight: false, highlightColor: 0xfffec656, tagIds: [], subTasksExpanded: false,
      dueAt: null, allDay: false, reminderMode: 0, reminderType: 0, reminderAdvance: 0, reminderAdvanceUnit: 4,
      snoozedUntil: null, repeat: 0, repeatDaysOfWeek: 0, repeatInterval: 1, repeatUnit: 0, repeatFrom: 0,
      repeatUntil: 0, repeatUntilAt: null, deletedFromSubListId: null,
    };
  }

  addTask(sub: SubList, title: string, atTop: boolean): Task | null {
    const clean = title.trim();
    if (!clean) return null;
    const peers = this.ordered(sub.id);
    const rank = atTop ? rankBefore(peers[0]?.rank ?? null) : rankAfter(peers.at(-1)?.rank ?? null);
    const t = this.stamp();
    const task: Task = { ...this.draftTask(sub), title: clean, rank, createdAt: t, updatedAt: t };
    this.put(Collections.tasks, task);
    return task;
  }

  /** Adds several tasks so they end up in the order given. */
  addTasks(sub: SubList, titles: string[], atTop: boolean) {
    const clean = titles.map((s) => s.trim()).filter(Boolean);
    const peers = this.ordered(sub.id);
    let edge = atTop ? peers[0]?.rank ?? null : peers.at(-1)?.rank ?? null;
    for (const title of atTop ? [...clean].reverse() : clean) {
      edge = atTop ? rankBefore(edge) : rankAfter(edge);
      const t = this.stamp();
      this.put(Collections.tasks, { ...this.draftTask(sub), title, rank: edge, createdAt: t, updatedAt: t });
    }
  }

  /**
   * Ticks or unticks a task. Ticking a repeating task moves it to its next
   * due date instead, until its repeat runs out. A sub-list can send its
   * completed tasks to another sub-list.
   */
  setCompleted(task: Task, completed: boolean, keepHistory = false) {
    const t = this.stamp();
    const next = completed && task.repeat !== Repeat.None ? nextDue(task, this.clock()) : null;
    if (next !== null) {
      if (keepHistory) {
        const peers = this.ordered(task.subListId);
        const after = peers[peers.findIndex((p) => p.id === task.id) + 1]?.rank ?? null;
        this.put(Collections.tasks, {
          ...task, id: newId(), createdAt: t, updatedAt: t, updatedBy: this.deviceId, completed: true, completedAt: t,
          repeat: Repeat.None, reminderMode: 0, rank: rankBetween(task.rank, after),
        });
      }
      this.put(Collections.tasks, { ...task, dueAt: next, snoozedUntil: null, updatedAt: t, updatedBy: this.deviceId });
      for (const st of this.subTasksOf(task.id).filter((s) => s.completed)) {
        this.put(Collections.subTasks, this.edited(st, { completed: false, completedAt: null }));
      }
      return;
    }
    const home = this.state.subLists.find((s) => s.id === task.subListId);
    const target = completed
      ? this.state.subLists.find((s) => s.id === home?.completedSubListId && s.id !== task.subListId)
      : undefined;
    const moved = target
      ? { ...task, listId: target.listId, subListId: target.id, rank: rankBefore(this.ordered(target.id)[0]?.rank ?? null) }
      : task;
    this.put(Collections.tasks, { ...moved, completed, completedAt: completed ? t : null, updatedAt: t, updatedBy: this.deviceId });
  }

  toggleCompleted(taskId: string, keepHistory = false) {
    const task = this.current(taskId);
    if (task) this.setCompleted(task, !task.completed, keepHistory);
  }

  /** Moves a task to Deleted Items. */
  deleteTask(taskId: string) {
    const task = this.current(taskId);
    if (!task || !isLive(task)) return;
    const t = this.stamp();
    this.put(Collections.tasks, { ...task, deletedAt: t, deletedFromSubListId: task.subListId, updatedAt: t, updatedBy: this.deviceId });
  }

  restoreTask(taskId: string) {
    const task = this.current(taskId);
    if (task) this.put(Collections.tasks, this.edited(task, { deletedAt: null }));
  }

  /** Drag and drop: orderedIds is the order on screen after the drop. Only the moved task is written. */
  moveTask(taskId: string, orderedIds: string[]) {
    const byId = new Map(this.state.tasks.map((t) => [t.id, t]));
    const task = byId.get(taskId);
    const rank = this.rankAt(taskId, orderedIds, (id) => byId.get(id)?.rank);
    if (task && rank !== null && rank !== task.rank) this.put(Collections.tasks, this.edited(task, { rank }));
  }

  /** Saves the task screen: the task and its sub-tasks, in order. */
  saveTask(task: Task, isNew: boolean, atTop: boolean, subTasks: SubDraft[], subTasksAtTop = false) {
    const title = task.title.trim();
    if (!title) return;
    const stored = this.current(task.id);
    const movedSubList = stored !== undefined && stored.subListId !== task.subListId;
    let rank = stored?.rank ?? task.rank;
    if (isNew || movedSubList) {
      const peers = this.ordered(task.subListId);
      rank = atTop ? rankBefore(peers[0]?.rank ?? null) : rankAfter(peers.at(-1)?.rank ?? null);
    }
    const t = this.stamp();
    const completedAt = task.completed ? task.completedAt ?? t : null;
    this.put(Collections.tasks, {
      ...task, title, rank, completedAt, createdAt: isNew ? t : task.createdAt, updatedAt: t, updatedBy: this.deviceId,
    });

    const existing = new Map(this.subTasksOf(task.id).map((s) => [s.id, s]));
    const kept = new Set<string>();
    let last: string | null = null;
    void subTasksAtTop;
    for (const draft of subTasks) {
      const subTitle = draft.title.trim();
      if (!subTitle) continue;
      last = rankAfter(last);
      const old = draft.id ? existing.get(draft.id) : undefined;
      const ts = this.stamp();
      if (!old) {
        this.put(Collections.subTasks, {
          id: newId(), createdAt: ts, updatedAt: ts, deletedAt: null, updatedBy: this.deviceId, rank: last,
          taskId: task.id, title: subTitle, completed: draft.completed, completedAt: draft.completed ? ts : null,
        } satisfies SubTask);
      } else {
        kept.add(old.id);
        if (old.title !== subTitle || old.completed !== draft.completed || old.rank !== last) {
          this.put(Collections.subTasks, {
            ...old, title: subTitle, completed: draft.completed,
            completedAt: draft.completed ? old.completedAt ?? ts : null, rank: last, updatedAt: ts, updatedBy: this.deviceId,
          });
        }
      }
    }
    for (const old of existing.values()) {
      if (!kept.has(old.id)) {
        const ts = this.stamp();
        this.put(Collections.subTasks, { ...old, deletedAt: ts, updatedAt: ts, updatedBy: this.deviceId });
      }
    }
  }

  /** Ticks a sub-task; when every sub-task is done the task can be completed too. */
  toggleSubTask(subTaskId: string, completeParent: boolean) {
    const st = this.state.subTasks.find((s) => s.id === subTaskId);
    if (!st) return;
    const t = this.stamp();
    const done = !st.completed;
    this.put(Collections.subTasks, { ...st, completed: done, completedAt: done ? t : null, updatedAt: t, updatedBy: this.deviceId });
    if (done && completeParent) {
      const others = this.subTasksOf(st.taskId).filter((s) => s.id !== st.id);
      const parent = this.current(st.taskId);
      if (others.every((s) => s.completed) && parent && !parent.completed) this.setCompleted(parent, true);
    }
  }

  setExpanded(taskId: string, expanded: boolean) {
    const task = this.current(taskId);
    if (task) this.put(Collections.tasks, this.edited(task, { subTasksExpanded: expanded }));
  }

  moveTasksTo(taskIds: string[], target: SubList, atTop: boolean) {
    const ids = new Set(taskIds);
    const moving = this.state.tasks.filter((t) => ids.has(t.id) && isLive(t)).sort(byRank);
    const peers = this.ordered(target.id);
    let edge = atTop ? peers[0]?.rank ?? null : peers.at(-1)?.rank ?? null;
    for (const task of atTop ? [...moving].reverse() : moving) {
      edge = atTop ? rankBefore(edge) : rankAfter(edge);
      this.put(Collections.tasks, this.edited(task, { listId: target.listId, subListId: target.id, rank: edge }));
    }
  }

  completeAll(sub: SubList) {
    this.tasksOf(sub.id).filter((t) => !t.completed).forEach((t) => this.setCompleted(t, true));
  }

  uncheckCompleted(sub: SubList) {
    this.tasksOf(sub.id).filter((t) => t.completed).forEach((t) => this.setCompleted(t, false));
  }

  removeCompleted(sub: SubList): string[] {
    const ids = this.tasksOf(sub.id).filter((t) => t.completed).map((t) => t.id);
    ids.forEach((id) => this.deleteTask(id));
    return ids;
  }

  deleteAll(sub: SubList): string[] {
    const ids = this.tasksOf(sub.id).map((t) => t.id);
    ids.forEach((id) => this.deleteTask(id));
    return ids;
  }

  shareText(sub: SubList, moveCompletedToBottom: boolean): string {
    return coreShareText(sub.title, visibleTasks(this.tasksOf(sub.id), { sortOrder: sub.sortOrder, moveCompletedToBottom, hideCompleted: false }));
  }
}

/** Keeps everything in memory: demo mode and tests. Same "newest edit wins" rule as the server. */
export class MemoryBackend implements Backend {
  private data = new Map<string, Map<string, Doc>>();
  private listener: ((c: string, d: Map<string, Doc>, s: boolean) => void) | null = null;

  constructor(seed?: Record<string, Record<string, Doc>>) {
    if (seed) for (const [c, docs] of Object.entries(seed)) this.data.set(c, new Map(Object.entries(docs)));
  }

  start(onSnapshot: (c: string, d: Map<string, Doc>, s: boolean) => void) {
    this.listener = onSnapshot;
    for (const c of ALL) onSnapshot(c, new Map(this.data.get(c) ?? []), true);
  }

  put(collection: string, id: string, data: Doc) {
    let docs = this.data.get(collection);
    if (!docs) this.data.set(collection, (docs = new Map()));
    const cur = docs.get(id);
    if (cur && num(cur.updatedAt) > num(data.updatedAt)) return;
    docs.set(id, data);
    this.listener?.(collection, new Map(docs), true);
  }

  stop() {
    this.listener = null;
  }
}
