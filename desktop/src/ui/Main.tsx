import { useEffect, useMemo, useRef, useState, useSyncExternalStore } from "react";
import { DndContext, type DragEndEvent, PointerSensor, closestCenter, useSensor, useSensors } from "@dnd-kit/core";
import { SortableContext, arrayMove, useSortable, verticalListSortingStrategy } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import {
  type Settings,
  type SubList,
  type SubTask,
  type Task,
  type TaskList,
  PRIORITY_COLORS,
  ReminderMode,
  SortOrder,
  headingInfo,
  headingTitle,
  isLive,
  isOverdue,
  reminderAt,
  splitQuickAdd,
  visibleTasks,
} from "@core";
import { settings as model } from "../data/settings";
import type { Store } from "../data/store";
import { ChoiceDialog, ConfirmDialog, ListDialog, ReorderDialog, SubListDialog } from "./Dialogs";
import { SettingsScreen } from "./SettingsScreen";
import { TaskScreen } from "./TaskScreen";
import type { OpenRequest } from "./Widget";
import { isTauri } from "../session";
import { Icon, P } from "./icons";
import { css, durationText, formatDate, formatTime } from "./format";

/** Sort options in the reference app's order. */
const SORT_OPTIONS: [SortOrder, string][] = [
  [SortOrder.UserDraggable, "User draggable"], [SortOrder.Priority, "Priority"],
  [SortOrder.AlphaAZ, "Alphabetical (A to Z)"], [SortOrder.AlphaZA, "Alphabetical (Z to A)"],
  [SortOrder.CreatedNewest, "Created (newest first)"], [SortOrder.CreatedOldest, "Created (oldest first)"],
  [SortOrder.ModifiedNewest, "Modified (newest first)"], [SortOrder.ModifiedOldest, "Modified (oldest first)"],
  [SortOrder.DueNewest, "Due (newest first)"], [SortOrder.DueOldest, "Due (oldest first)"],
  [SortOrder.Highlighted, "Highlighted"], [SortOrder.CompletedNewest, "Completed (newest first)"],
  [SortOrder.CompletedOldest, "Completed (oldest first)"],
];

type Dialog =
  | { kind: "newList" } | { kind: "editList"; list: TaskList } | { kind: "deleteList"; list: TaskList }
  | { kind: "newSub" } | { kind: "editSub"; sub: SubList } | { kind: "split"; text: string; lines: string[] }
  | { kind: "sort" } | { kind: "reorderLists" } | { kind: "reorderSubs" } | { kind: "moveSub"; sub: SubList };

type Snack = { text: string; undo?: () => void; id: number };

function Dots() {
  return <span className="dots">{Array.from({ length: 12 }, (_, i) => <span key={i} />)}</span>;
}

function Row({ task, index, subs, s, canDrag, onToggle, onOpen, onDelete, onToggleSub, onExpand }: {
  task: Task; index: number; subs: SubTask[]; s: Settings; canDrag: boolean;
  onToggle: () => void; onOpen: () => void; onDelete: () => void; onToggleSub: (id: string) => void; onExpand: () => void;
}) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id: task.id, disabled: !canDrag });
  const now = Date.now();
  const strike = task.completed && s.strikethroughCompleted;
  const minHeight = [64, 56, 48][s.displayMode] ?? 64;
  const noteLines = [0, 2, 1, -1][s.notesView] ?? 0;
  const prio = PRIORITY_COLORS[task.priority];
  const shown = task.dueAt !== null && s.includeReminderAdvance ? reminderAt(task) ?? task.dueAt : task.dueAt;
  return (
    <div ref={setNodeRef} style={{ transform: CSS.Transform.toString(transform), transition }}>
      <div className={"row" + (isDragging ? " dragging" : "")} style={{ minHeight }}>
        <span className="strip" style={{ background: task.highlight ? css(task.highlightColor) : "transparent" }} />
        <button className="check" onClick={onToggle} title={task.completed ? "Mark as not done" : "Mark as done"}>
          <Icon d={task.completed ? P.checkCircle : P.circle} color={task.priority ? css(prio) : "var(--icon)"} />
        </button>
        <div className="body" onClick={onOpen} style={{ paddingTop: s.taskPadding / 2, paddingBottom: s.taskPadding / 2 }}>
          <div className={"title" + (task.completed ? " done" : "") + (strike ? " strike" : "")}>{s.autoNumberTasks ? `${index + 1}. ` : ""}{task.title}</div>
          {shown !== null && s.reminderView !== 2 && (
            <div className={"due" + (isOverdue(task, now) ? " overdue" : "")} style={{ justifyContent: s.reminderView === 1 ? "flex-end" : undefined }}>
              {task.reminderMode !== ReminderMode.DontRemind && <Icon d={P.bell} size={14} />}
              {task.allDay ? formatDate(shown) : `${formatDate(shown)} ${formatTime(shown)}`}
              {task.repeat !== 0 && <Icon d={P.repeat} size={14} />}
            </div>
          )}
          {task.notes.trim() && noteLines !== -1 && (
            <div className={"notes" + (strike ? " strike" : "")}
              style={noteLines > 0 ? { display: "-webkit-box", WebkitLineClamp: noteLines, WebkitBoxOrient: "vertical" } : undefined}>{task.notes}</div>
          )}
          {task.completed && task.completedAt !== null && s.showCompleteDate && (
            <div className="completed-on">Completed: {formatDate(task.completedAt)} {formatTime(task.completedAt)}</div>
          )}
        </div>
        {subs.length > 0 && (
          <button className="subcount" onClick={onExpand} title={task.subTasksExpanded ? "Hide sub tasks" : "Show sub tasks"}>
            {subs.filter((x) => x.completed).length}/{subs.length}
            <Icon d={task.subTasksExpanded ? P.expandLess : P.expandMore} size={20} />
          </button>
        )}
        {s.swipeToDelete && <button className="delete" onClick={onDelete} title="Delete task"><Icon d={P.delete} size={20} /></button>}
        {canDrag ? <span className="handle" {...attributes} {...listeners} title="Drag to reorder"><Dots /></span> : <span style={{ width: 12 }} />}
      </div>
      {task.subTasksExpanded && subs.map((st, i) => (
        <div key={st.id} className="subrow" onClick={() => onToggleSub(st.id)}>
          <Icon d={st.completed ? P.checkCircle : P.circle} size={18} color="var(--icon)" />
          <span style={{ textDecoration: st.completed && s.strikethroughCompleted ? "line-through" : undefined, color: st.completed ? "var(--text2)" : undefined }}>
            {s.autoNumberSubTasks ? `${i + 1}. ` : ""}{st.title}
          </span>
        </div>
      ))}
      <div className="row-divider" />
    </div>
  );
}

export function Main({ store, account, onSignOut }: { store: Store; account: string | null; onSignOut: (() => void) | null }) {
  const state = useSyncExternalStore(store.subscribe, store.getState);
  const s = useSyncExternalStore(model.subscribe, model.get);
  const { lists, subLists, tasks, subTasks, ready } = state;

  const [listId, setListId] = useState<string | null>(() => s.defaultList || localStorage.getItem("mytasks.lastList"));
  const [subIndex, setSubIndex] = useState(0);
  const [dialog, setDialog] = useState<Dialog | null>(null);
  const [editing, setEditing] = useState<{ task: Task; isNew: boolean } | null>(null);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [menu, setMenu] = useState(false);
  const [snack, setSnack] = useState<Snack | null>(null);
  const [quick, setQuick] = useState("");
  const snackTimer = useRef<number | undefined>(undefined);

  const list = lists.find((l) => l.id === listId) ?? lists[0];
  const subs = useMemo(() => subLists.filter((x) => x.listId === list?.id), [subLists, list?.id]);
  const sub = subs[Math.min(subIndex, Math.max(subs.length - 1, 0))];
  const live = useMemo(() => tasks.filter(isLive), [tasks]);
  const subTasksByTask = useMemo(() => {
    const m = new Map<string, SubTask[]>();
    for (const st of subTasks) m.set(st.taskId, [...(m.get(st.taskId) ?? []), st]);
    return m;
  }, [subTasks]);
  const atTop = s.insertNewTasks === 0;
  const now = Date.now();

  const visible = useMemo(
    () => (sub ? visibleTasks(live.filter((t) => t.subListId === sub.id), { sortOrder: sub.sortOrder, moveCompletedToBottom: s.moveCompletedToBottom, hideCompleted: sub.hideCompleted }) : []),
    [live, sub, s.moveCompletedToBottom],
  );
  const [order, setOrder] = useState<Task[]>(visible);
  useEffect(() => setOrder(visible), [visible]);

  // The desktop widget asks this window to open a task or the new task screen.
  const latest = useRef({ tasks, subLists, newDraft: (x: SubList) => newDraft(x) });
  latest.current = { tasks, subLists, newDraft: (x: SubList) => newDraft(x) };
  useEffect(() => {
    if (!isTauri) return;
    let stop: (() => void) | undefined;
    let cancelled = false;
    void import("@tauri-apps/api/event").then(({ listen }) =>
      listen<OpenRequest>("open-request", ({ payload }) => {
        const { tasks: all, subLists: sls, newDraft: draft } = latest.current;
        setSettingsOpen(false);
        if (payload.taskId) {
          const t = all.find((x) => x.id === payload.taskId);
          if (t) { setListId(t.listId); setEditing({ task: t, isNew: false }); }
        } else if (payload.newInSubList) {
          const target = sls.find((x) => x.id === payload.newInSubList);
          if (target) { setListId(target.listId); setEditing({ task: draft(target), isNew: true }); }
        }
      }).then((un) => { if (cancelled) un(); else stop = un; }),
    );
    return () => { cancelled = true; stop?.(); };
  }, []);

  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 4 } }));

  function openList(id: string) {
    setListId(id);
    setSubIndex(0);
    try { localStorage.setItem("mytasks.lastList", id); } catch { /* not fatal */ }
  }

  function message(text: string, undo?: () => void) {
    window.clearTimeout(snackTimer.current);
    setSnack({ text, undo, id: Date.now() });
    snackTimer.current = window.setTimeout(() => setSnack(null), undo ? 6000 : 3000);
  }
  const later = () => message("Coming in a later build");
  const deleted = (text: string, ids: string[]) => ids.length && message(text, () => ids.forEach((id) => store.restoreTask(id)));

  function newDraft(target: SubList): Task {
    let d: Task = { ...store.draftTask(target), priority: s.defaultPriority as Task["priority"], reminderType: s.defaultReminderType as Task["reminderType"], subTasksExpanded: s.subTasksExpandDefault };
    if (s.enableReminders) {
      const due = new Date(); due.setHours(due.getHours() + 1, 0, 0, 0);
      d = { ...d, dueAt: due.getTime(), reminderMode: ReminderMode.WhenDue };
    }
    return d;
  }

  function submitQuick() {
    if (!sub) return;
    const lines = splitQuickAdd(quick);
    if (lines.length > 1) setDialog({ kind: "split", text: quick, lines });
    else if (lines.length === 1) store.addTask(sub, lines[0]!, atTop);
    setQuick("");
  }

  function dragEnd(e: DragEndEvent) {
    if (!e.over || e.active.id === e.over.id) return;
    const from = order.findIndex((t) => t.id === e.active.id), to = order.findIndex((t) => t.id === e.over!.id);
    const next = arrayMove(order, from, to);
    setOrder(next);
    store.moveTask(String(e.active.id), next.map((t) => t.id));
  }

  function shareList() {
    if (!sub) return;
    const text = store.shareText(sub, s.moveCompletedToBottom);
    void navigator.clipboard?.writeText(text).then(() => message("List copied to the clipboard"), () => message("Could not copy the list"));
  }

  const theme = (() => {
    const dark = window.matchMedia?.("(prefers-color-scheme: dark)").matches;
    return ["light", "dark", "black", dark ? "dark" : "light", dark ? "black" : "light"][s.theme] ?? "black";
  })();
  const scale = [0.8, 0.9, 1, 1.15, 1.3][s.fontSize] ?? 1;
  const menuItems: ([string, () => void] | "-")[] = sub ? [
    ["Edit Sub List", () => setDialog({ kind: "editSub", sub })],
    ["Reorder Sub Lists", () => setDialog({ kind: "reorderSubs" })],
    ["Move Sub List", () => setDialog({ kind: "moveSub", sub })],
    ["Sort", () => setDialog({ kind: "sort" })],
    ["Select Multiple…", later],
    ["Duplicate Sub List", () => { store.duplicateSubList(sub); setSubIndex(subs.length); }],
    ["Uncheck Completed", () => store.uncheckCompleted(sub)],
    ["Complete All", () => store.completeAll(sub)],
    [sub.hideCompleted ? "Show Completed" : "Hide Completed", () => store.setHideCompleted(sub, !sub.hideCompleted)],
    "-",
    ["Remove Completed", () => deleted("Tasks deleted", store.removeCompleted(sub))],
    ["Delete Sub List", () => { if (!store.deleteSubList(sub)) message("A task list must have at least one sub list."); }],
    ["Delete All", () => deleted("Tasks deleted", store.deleteAll(sub))],
  ] : [];

  function deleteListNow(l: TaskList) {
    if (lists.length <= 1) return message("This is your only list.");
    store.deleteList(l);
    message("Task list deleted");
  }

  return (
    <div className={`app theme-${theme}`} style={{ ["--scale" as string]: scale }}>
      <nav className="sidebar">
        <div className="sidebar-head"><div className="name">My Tasks</div><div className="account">{account ?? "Demo: changes are not saved"}</div></div>
        {lists.map((l) => (
          <button key={l.id} className={"side-row" + (l.id === list?.id ? " selected" : "")} onClick={() => openList(l.id)}>
            <span className="dot" style={{ background: css(l.color) }} />
            <span className="grow">{l.title}</span>
            <span className="info">{headingInfo(live.filter((t) => t.listId === l.id), s.menuListInfo as never, now)}</span>
          </button>
        ))}
        <div className="side-divider" />
        <button className="side-row" onClick={() => setDialog({ kind: "newList" })}><Icon d={P.playlistAdd} /><span className="grow">New List</span></button>
        <button className="side-row" onClick={later}><Icon d={P.filter} /><span className="grow">New Filtered List</span></button>
        <button className="side-row" onClick={() => setDialog({ kind: "reorderLists" })}><Icon d={P.swap} /><span className="grow">Reorder Task Lists</span></button>
        <button className="side-row" onClick={() => list && setDialog({ kind: "editList", list })}><Icon d={P.edit} /><span className="grow">Edit List</span></button>
        <div className="side-divider" />
        <button className="side-row" onClick={later}><Icon d={P.label} /><span className="grow">Tags</span></button>
        <button className="side-row" onClick={later}><Icon d={P.delete} /><span className="grow">Deleted Items</span></button>
        <div className="side-divider" />
        <button className="side-row" onClick={later}><Icon d={P.help} /><span className="grow">Help</span></button>
        <button className="side-row" onClick={() => setSettingsOpen(true)}><Icon d={P.settings} /><span className="grow">Settings</span></button>
      </nav>

      <main className="main">
        <div className="topbar">
          <span className="title" onClick={() => list && setDialog({ kind: "editList", list })} title="Edit list">{list?.title ?? ""}</span>
          <button className="icon-btn" onClick={later} title="Search"><Icon d={P.search} /></button>
          <button className="icon-btn" onClick={shareList} title="Share List (copies it)"><Icon d={P.share} /></button>
          <button className="icon-btn" onClick={() => setMenu(true)} title="More options"><Icon d={P.more} /></button>
        </div>

        {!list || subs.length === 0 ? (
          <div className="empty">{ready ? "" : "Loading your tasks…"}</div>
        ) : (<>
          <div className="tabs">
            <div className="tabs-scroll">
              {subs.map((x, i) => (
                <button key={x.id} className={"tab" + (x.id === sub?.id ? " active" : "")}
                  onClick={() => (x.id === sub?.id ? setDialog({ kind: "editSub", sub: x }) : setSubIndex(i))}>
                  {headingTitle(x.title, live.filter((t) => t.subListId === x.id), s.subListHeading as never, now)}
                </button>
              ))}
            </div>
            <button className="icon-btn" onClick={() => setDialog({ kind: "newSub" })} title="New Sub List"><Icon d={P.addCircle} /></button>
          </div>

          <div className="task-list">
            {order.length === 0 && <div className="empty">No tasks</div>}
            <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={dragEnd}>
              <SortableContext items={order.map((t) => t.id)} strategy={verticalListSortingStrategy}>
                {order.map((t, i) => (
                  <Row key={t.id} task={t} index={i} subs={subTasksByTask.get(t.id) ?? []} s={s} canDrag={sub?.sortOrder === SortOrder.UserDraggable}
                    onToggle={() => store.toggleCompleted(t.id, s.saveRecurringHistory)}
                    onOpen={() => setEditing({ task: t, isNew: false })}
                    onDelete={() => { store.deleteTask(t.id); deleted("Task deleted", [t.id]); }}
                    onToggleSub={(id) => store.toggleSubTask(id, s.subTasksCompleteTask)}
                    onExpand={() => store.setExpanded(t.id, !t.subTasksExpanded)} />
                ))}
              </SortableContext>
            </DndContext>
          </div>

          {s.showQuickAdd && (
            <div className="quickadd">
              <div className="box">
                <textarea rows={1} placeholder="Quick add task…" value={quick} onChange={(e) => setQuick(e.target.value)}
                  onKeyDown={(e) => { if (e.key === "Enter" && !(e.shiftKey && s.multiLineQuickAdd)) { e.preventDefault(); submitQuick(); } }} />
              </div>
              <button className="icon-btn" onClick={submitQuick} title="Add task"><Icon d={P.send} /></button>
            </div>
          )}
          <button className={"fab" + (s.showQuickAdd ? "" : " lower")} onClick={() => sub && setEditing({ task: newDraft(sub), isNew: true })} title="Add task"><Icon d={P.add} /></button>
        </>)}

        {menu && (
          <div className="scrim clear" onMouseDown={() => setMenu(false)}>
            <div className="menu" style={{ position: "fixed" }} onMouseDown={(e) => e.stopPropagation()}>
              {menuItems.map((m, i) => m === "-" ? <hr key={i} /> : <button key={m[0]} onClick={() => { setMenu(false); m[1](); }}>{m[0]}</button>)}
            </div>
          </div>
        )}

        {snack && (
          <div className="snackbar" key={snack.id}>
            <span>{snack.text}</span>
            {snack.undo && <button onClick={() => { snack.undo!(); setSnack(null); }}>UNDO</button>}
          </div>
        )}

        {editing && (
          <TaskScreen key={editing.task.id} initial={editing.task} initialSubTasks={editing.isNew ? [] : subTasksByTask.get(editing.task.id) ?? []}
            isNew={editing.isNew} settings={s} lists={lists} subLists={subLists}
            onSave={(t, drafts, another) => {
              store.saveTask(t, editing.isNew, atTop, drafts);
              const target = subLists.find((x) => x.id === t.subListId);
              const fire = t.reminderMode !== ReminderMode.DontRemind && t.dueAt !== null ? reminderAt(t) : null;
              if (fire !== null && fire > Date.now()) message("Reminder in: " + durationText(fire - Date.now()) + " (on your phone)");
              setEditing(another && target ? { task: newDraft(target), isNew: true } : null);
            }}
            onDelete={() => { store.deleteTask(editing.task.id); deleted("Task deleted", [editing.task.id]); setEditing(null); }}
            onShare={(t) => { void navigator.clipboard?.writeText(t.notes.trim() ? `${t.title}\n${t.notes}` : t.title); message("Task copied to the clipboard"); }}
            onClose={() => setEditing(null)} />
        )}

        {settingsOpen && <SettingsScreen s={s} lists={lists} account={account} onSignOut={onSignOut} onClose={() => setSettingsOpen(false)} />}
      </main>

      {dialog?.kind === "newList" && <ListDialog list={null} onClose={() => setDialog(null)} onSave={(t, c) => openList(store.addList(t, c).id)} />}
      {dialog?.kind === "editList" && (
        <ListDialog list={dialog.list} onClose={() => setDialog(null)}
          onDelete={() => (s.deleteListConfirmation ? setDialog({ kind: "deleteList", list: dialog.list }) : (setDialog(null), deleteListNow(dialog.list)))}
          onSave={(t, c) => store.editList(dialog.list, t, c)} />
      )}
      {dialog?.kind === "deleteList" && (
        <ConfirmDialog title="Delete List" text={`Delete "${dialog.list.title}" and all of its tasks?`} confirm="Delete" onClose={() => setDialog(null)} onConfirm={() => deleteListNow(dialog.list)} />
      )}
      {dialog?.kind === "newSub" && list && (
        <SubListDialog sub={null} others={subs} onClose={() => setDialog(null)} onSave={(t, target) => { store.addSubList(list.id, t, target); setSubIndex(subs.length); }} />
      )}
      {dialog?.kind === "editSub" && (
        <SubListDialog sub={dialog.sub} others={subs.filter((x) => x.id !== dialog.sub.id)} onClose={() => setDialog(null)}
          onDelete={() => { setDialog(null); if (!store.deleteSubList(dialog.sub)) message("A task list must have at least one sub list."); }}
          onSave={(t, target) => store.editSubList(dialog.sub, t, target)} />
      )}
      {dialog?.kind === "split" && sub && (
        <ConfirmDialog title="Split into multiple tasks?" text="Line breaks have been detected, would you like to split the text into multiple tasks?"
          confirm="Yes" dismiss="No" onClose={() => setDialog(null)} onDismissButton={() => store.addTask(sub, dialog.text, atTop)} onConfirm={() => store.addTasks(sub, dialog.lines, atTop)} />
      )}
      {dialog?.kind === "sort" && sub && (
        <ChoiceDialog title="Sort" options={SORT_OPTIONS.map((o) => o[1])} selected={SORT_OPTIONS.findIndex((o) => o[0] === sub.sortOrder)}
          onClose={() => setDialog(null)} onPick={(i) => store.setSort(sub, SORT_OPTIONS[i]![0])} />
      )}
      {dialog?.kind === "reorderLists" && (
        <ReorderDialog title="Reorder Task Lists" items={lists.map((l) => ({ id: l.id, title: l.title }))} onClose={() => setDialog(null)} onMove={(id, o) => store.moveList(id, o)} />
      )}
      {dialog?.kind === "reorderSubs" && (
        <ReorderDialog title="Reorder sub lists" items={subs.map((x) => ({ id: x.id, title: x.title }))} onClose={() => setDialog(null)} onMove={(id, o) => store.moveSubList(id, o)} />
      )}
      {dialog?.kind === "moveSub" && (
        <ChoiceDialog title="Move Sub List" options={lists.map((l) => l.title)} selected={lists.findIndex((l) => l.id === dialog.sub.listId)}
          onClose={() => setDialog(null)}
          onPick={(i) => { if (!store.moveSubListTo(dialog.sub, lists[i]!.id)) message("It is not possible to move the last sub list. A task list must have at least one sub list."); }} />
      )}
    </div>
  );
}
