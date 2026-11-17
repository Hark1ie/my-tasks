/**
 * The PC desktop widget: the phone's list widget in its own small window.
 * Same heading, rows, colours and options (background and item transparency,
 * add button, font size); tapping a task opens it in the main window.
 */
import { useMemo, useState, useSyncExternalStore } from "react";
import { type SubList, type SubTask, type Task, PRIORITY_COLORS, ReminderMode, headingTitle, isLive, isOverdue, visibleTasks } from "@core";
import { settings as model } from "../data/settings";
import type { State, Store } from "../data/store";
import { isTauri } from "../session";
import { Modal, Radio } from "./Dialogs";
import { Icon, P } from "./icons";
import { css, formatDate, formatTime } from "./format";

export type WidgetOptions = {
  listId: string | null;
  subListId: string | null;
  backgroundAlpha: number;
  itemAlpha: number;
  showAdd: boolean;
  addInHeading: boolean;
  fontSize: number;
  onTop: boolean;
};

const KEY = "mytasks.widget";
const DEFAULTS: WidgetOptions = { listId: null, subListId: null, backgroundAlpha: 165, itemAlpha: 165, showAdd: true, addInHeading: false, fontSize: 2, onTop: false };
const FONT_PX = [12, 14, 16, 18, 20];
const FONT_NAMES = ["Smallest", "Small", "Normal", "Large", "Largest"];

/** Colours per theme, the same as the phone widget. */
const PALETTES = {
  light: { background: "255,255,255", heading: "255,255,255", row: "255,255,255", text: "rgba(0,0,0,.87)", secondary: "rgba(0,0,0,.6)", icon: "rgba(0,0,0,.54)", divider: "rgba(0,0,0,.12)" },
  dark: { background: "48,48,48", heading: "33,33,33", row: "66,66,66", text: "#fff", secondary: "rgba(255,255,255,.7)", icon: "rgba(255,255,255,.7)", divider: "rgba(255,255,255,.12)" },
  black: { background: "48,48,48", heading: "0,0,0", row: "0,0,0", text: "#fff", secondary: "rgba(255,255,255,.7)", icon: "rgba(255,255,255,.7)", divider: "#2c2c2c" },
};

function loadOptions(): WidgetOptions {
  try { return { ...DEFAULTS, ...JSON.parse(localStorage.getItem(KEY) ?? "{}") }; } catch { return DEFAULTS; }
}

async function tauri() {
  const [{ invoke }, { emitTo }, { getCurrentWindow }] = await Promise.all([
    import("@tauri-apps/api/core"), import("@tauri-apps/api/event"), import("@tauri-apps/api/window"),
  ]);
  return { invoke, emitTo, win: getCurrentWindow() };
}

/** Asks the main window to open a task, or the new task screen. */
export type OpenRequest = { taskId?: string; newInSubList?: string };
async function openInMain(req: OpenRequest) {
  if (!isTauri) return;
  const t = await tauri();
  await t.invoke("open_main");
  await t.emitTo("main", "open-request", req);
}

export function Widget({ store }: { store: Store | null }) {
  const s = useSyncExternalStore(model.subscribe, model.get);
  const empty = useMemo<State>(() => ({ lists: [], subLists: [], tasks: [], subTasks: [], ready: false, cloudSettings: null }), []);
  const state = useSyncExternalStore(store ? store.subscribe : () => () => {}, store ? store.getState : () => empty);
  const [o, setO] = useState(loadOptions);
  const [dialog, setDialog] = useState<null | "change" | "settings" | "font">(null);

  function update(patch: Partial<WidgetOptions>) {
    const next = { ...o, ...patch };
    setO(next);
    try { localStorage.setItem(KEY, JSON.stringify(next)); } catch { /* not fatal */ }
    if ("onTop" in patch && isTauri) void tauri().then((t) => t.invoke("set_widget_on_top", { onTop: next.onTop }));
  }

  const dark = window.matchMedia?.("(prefers-color-scheme: dark)").matches;
  const themeName = (["light", "dark", "black", dark ? "dark" : "light", dark ? "black" : "light"][s.theme] ?? "black") as keyof typeof PALETTES;
  const pal = PALETTES[themeName];
  const font = FONT_PX[o.fontSize] ?? 16;
  const now = Date.now();

  const { lists, subLists, tasks, subTasks } = state;
  const sub: SubList | undefined =
    subLists.find((x) => x.id === o.subListId) ??
    subLists.find((x) => x.listId === (lists.find((l) => l.id === o.listId) ?? lists[0])?.id);
  const live = tasks.filter(isLive);
  const own = sub ? live.filter((t) => t.subListId === sub.id) : [];
  const rows = sub ? visibleTasks(own, { sortOrder: sub.sortOrder, moveCompletedToBottom: s.moveCompletedToBottom, hideCompleted: sub.hideCompleted }) : [];
  const subsOf = (t: Task): SubTask[] => subTasks.filter((x) => x.taskId === t.id);
  const add = () => sub && void openInMain({ newInSubList: sub.id });
  const drag = (e: React.MouseEvent) => { if (e.button === 0 && isTauri) void tauri().then((t) => t.win.startDragging()); };
  const resize = (e: React.MouseEvent) => { e.preventDefault(); if (isTauri) void tauri().then((t) => t.win.startResizeDragging("SouthEast")); };
  const noteLines = [0, 2, 1, -1][s.notesView] ?? 0;

  return (
    <div className={`widget theme-${themeName}`} style={{ background: `rgba(${pal.background},${o.backgroundAlpha / 255})`, color: pal.text }}>
      <div className="w-heading" style={{ background: `rgba(${pal.heading},${o.backgroundAlpha / 255})` }} onMouseDown={drag}>
        <button className="w-icon" title="Change list" onMouseDown={(e) => e.stopPropagation()} onClick={() => setDialog("change")}><Icon d={P.list} color={pal.icon} size={22} /></button>
        <span className="w-title">{!store ? "My Tasks" : sub ? headingTitle(sub.title, own, s.subListHeading as never, now) : "My Tasks"}</span>
        {o.showAdd && o.addInHeading && <button className="w-icon" title="Add task" onMouseDown={(e) => e.stopPropagation()} onClick={add}><Icon d={P.add} color={pal.icon} size={22} /></button>}
        <button className="w-icon" title="Widget settings" onMouseDown={(e) => e.stopPropagation()} onClick={() => setDialog("settings")}><Icon d={P.settings} color={pal.icon} size={22} /></button>
      </div>

      <div className="w-list">
        {!store && <div className="w-empty" style={{ color: pal.secondary }}>Open My Tasks and sign in</div>}
        {store && rows.length === 0 && <div className="w-empty" style={{ color: pal.secondary }}>{state.ready ? "No tasks" : "Loading…"}</div>}
        {rows.map((t, i) => {
          const subs = subsOf(t);
          const strike = t.completed && s.strikethroughCompleted;
          const prio = PRIORITY_COLORS[t.priority];
          return (
            <div key={t.id}>
              <div className="w-row" style={{ background: `rgba(${pal.row},${o.itemAlpha / 255})` }}>
                <span className="w-strip" style={{ background: t.highlight ? css(t.highlightColor) : "transparent" }} />
                <button className="w-check" title={t.completed ? "Mark as not done" : "Mark as done"} onClick={() => store?.toggleCompleted(t.id, s.saveRecurringHistory)}>
                  <Icon d={t.completed ? P.checkCircle : P.circle} size={22} color={t.priority ? css(prio) : pal.icon} />
                </button>
                <div className="w-body" onClick={() => void openInMain({ taskId: t.id })}>
                  <div style={{ fontSize: font, color: t.completed ? pal.secondary : pal.text, textDecoration: strike ? "line-through" : undefined }}>
                    {s.autoNumberTasks ? `${i + 1}. ` : ""}{t.title}
                  </div>
                  {t.dueAt !== null && s.reminderView !== 2 && (
                    <div style={{ fontSize: font - 4, color: isOverdue(t, now) ? "var(--overdue)" : pal.secondary }}>
                      {t.reminderMode !== ReminderMode.DontRemind ? "⏰ " : ""}{formatDate(t.dueAt)}{t.allDay ? "" : " " + formatTime(t.dueAt)}
                    </div>
                  )}
                  {t.notes.trim() && noteLines !== -1 && (
                    <div className="w-notes" style={{ fontSize: font - 3, color: pal.secondary, textDecoration: strike ? "line-through" : undefined, ...(noteLines > 0 ? { WebkitLineClamp: noteLines } : { display: "block" }) }}>{t.notes}</div>
                  )}
                </div>
                {subs.length > 0 && (
                  <button className="w-subcount" style={{ color: pal.secondary }} onClick={() => store?.setExpanded(t.id, !t.subTasksExpanded)}>
                    {subs.filter((x) => x.completed).length}/{subs.length}{t.subTasksExpanded ? " ▴" : " ▾"}
                  </button>
                )}
              </div>
              {t.subTasksExpanded && subs.map((st, j) => (
                <div key={st.id} className="w-row w-subrow" style={{ background: `rgba(${pal.row},${o.itemAlpha / 255})` }} onClick={() => store?.toggleSubTask(st.id, s.subTasksCompleteTask)}>
                  <Icon d={st.completed ? P.checkCircle : P.circle} size={18} color={pal.icon} />
                  <span style={{ fontSize: font - 2, color: st.completed ? pal.secondary : pal.text, textDecoration: st.completed && s.strikethroughCompleted ? "line-through" : undefined }}>
                    {s.autoNumberSubTasks ? `${j + 1}. ` : ""}{st.title}
                  </span>
                </div>
              ))}
              <div style={{ height: 1, background: pal.divider }} />
            </div>
          );
        })}
      </div>

      {o.showAdd && !o.addInHeading && store && <button className="w-fab" title="Add task" onClick={add}><Icon d={P.add} color="#000" size={24} /></button>}
      <span className="w-grip" onMouseDown={resize} title="Resize" />

      {dialog === "change" && (
        <Modal onClose={() => setDialog(null)}>
          <div className="dialog w-dialog">
            <h2>Change To</h2>
            {lists.map((l) => (
              <div key={l.id}>
                <div className="w-group" style={{ color: css(l.color) }}>{l.title}</div>
                {subLists.filter((x) => x.listId === l.id).map((x) => (
                  <button key={x.id} className="radio-row" onClick={() => { update({ listId: l.id, subListId: x.id }); setDialog(null); }}>
                    <Radio on={x.id === sub?.id} /><span>{x.title}</span>
                  </button>
                ))}
              </div>
            ))}
            <div className="actions"><button className="text-btn" onClick={() => setDialog(null)}>Cancel</button></div>
          </div>
        </Modal>
      )}

      {dialog === "settings" && (
        <Modal onClose={() => setDialog(null)}>
          <div className="dialog w-dialog">
            <h2>Widget Settings</h2>
            <label className="w-setting">Background transparency
              <input type="range" min={0} max={255} value={o.backgroundAlpha} onChange={(e) => update({ backgroundAlpha: Number(e.target.value) })} />
            </label>
            <label className="w-setting">Item transparency
              <input type="range" min={0} max={255} value={o.itemAlpha} onChange={(e) => update({ itemAlpha: Number(e.target.value) })} />
            </label>
            <button className="w-toggle" onClick={() => update({ showAdd: !o.showAdd })}><span>Show add button</span><span className={"cb" + (o.showAdd ? " on" : "")}>{o.showAdd && <Icon d={P.check} size={16} />}</span></button>
            <button className="w-toggle" disabled={!o.showAdd} onClick={() => update({ addInHeading: !o.addInHeading })}><span>Add button in heading</span><span className={"cb" + (o.addInHeading ? " on" : "")}>{o.addInHeading && <Icon d={P.check} size={16} />}</span></button>
            <button className="w-toggle" onClick={() => setDialog("font")}><span>Font size<br /><small>{FONT_NAMES[o.fontSize]}</small></span></button>
            {isTauri && <button className="w-toggle" onClick={() => update({ onTop: !o.onTop })}><span>Keep on top of other windows<br /><small>Off: stays on the desktop, behind your windows</small></span><span className={"cb" + (o.onTop ? " on" : "")}>{o.onTop && <Icon d={P.check} size={16} />}</span></button>}
            <div className="actions"><button className="text-btn" onClick={() => setDialog(null)}>Done</button></div>
          </div>
        </Modal>
      )}

      {dialog === "font" && (
        <Modal onClose={() => setDialog("settings")}>
          <div className="dialog w-dialog">
            <h2>Font size</h2>
            {FONT_NAMES.map((n, i) => (
              <button key={n} className="radio-row" onClick={() => { update({ fontSize: i }); setDialog("settings"); }}><Radio on={i === o.fontSize} /><span>{n}</span></button>
            ))}
          </div>
        </Modal>
      )}
    </div>
  );
}
