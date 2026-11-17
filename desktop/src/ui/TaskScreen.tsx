import { type ReactNode, useEffect, useRef, useState } from "react";
import {
  type Settings,
  type SubList,
  type SubTask,
  type Task,
  type TaskList,
  HIGHLIGHT_COLORS,
  Priority,
  ReminderMode,
  Repeat,
  RepeatUntil,
  TimeUnit,
} from "@core";
import type { SubDraft } from "../data/store";
import { ConfirmDialog, Modal, SubListChooser, TextDialog } from "./Dialogs";
import { Icon, P } from "./icons";
import { css, formatDate, formatTime } from "./format";

const UNITS: [number, string][] = [
  [TimeUnit.Minutes, "minutes"], [TimeUnit.Hours, "hours"], [TimeUnit.Days, "days"],
  [TimeUnit.Weeks, "weeks"], [TimeUnit.Months, "months"], [TimeUnit.Years, "years"],
];
const REPEAT_LABELS = ["Does not repeat", "Daily", "Weekly", "Monthly", "Yearly", "Specific days of the week", "Advanced"];
const REMINDER_TYPES = ["Notification", "Alarm", "None"];
const REPEAT_FROM = ["Due date", "Completion date and time", "Completion date only"];
const DAYS = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];

const unitLabel = (u: number) => UNITS.find(([c]) => c === u)?.[1] ?? "minutes";

export function reminderText(t: Task): string {
  if (t.reminderMode === ReminderMode.WhenDue) return "Remind me when due";
  if (t.reminderMode === ReminderMode.InAdvance) return `Remind me ${t.reminderAdvance} ${unitLabel(t.reminderAdvanceUnit)} in advance`;
  return "Don't remind me";
}

export function repeatText(t: Task): string {
  if (t.repeat === Repeat.DaysOfWeek) {
    const days = DAYS.filter((_, i) => t.repeatDaysOfWeek & (1 << i)).map((d) => d.slice(0, 3)).join(", ");
    return days || REPEAT_LABELS[5]!;
  }
  if (t.repeat === Repeat.Advanced) return `Every ${t.repeatInterval} ${unitLabel(t.repeatUnit)}`;
  return REPEAT_LABELS[t.repeat] ?? REPEAT_LABELS[0]!;
}

const pad = (n: number) => String(n).padStart(2, "0");
const dateValue = (t: number) => { const d = new Date(t); return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`; };
const timeValue = (t: number) => { const d = new Date(t); return `${pad(d.getHours())}:${pad(d.getMinutes())}`; };

function withDate(base: number, value: string): number {
  const [y, m, d] = value.split("-").map(Number);
  const out = new Date(base);
  out.setFullYear(y!, m! - 1, d!);
  return out.getTime();
}

function withTime(base: number, value: string): number {
  const [h, m] = value.split(":").map(Number);
  const out = new Date(base);
  out.setHours(h!, m!, 0, 0);
  return out.getTime();
}

function nextHour(): number {
  const d = new Date();
  d.setHours(d.getHours() + 1, 0, 0, 0);
  return d.getTime();
}

function Section({ icon, lead, label, trailing, onClick, children }: {
  icon?: string; lead?: ReactNode; label: string; trailing?: ReactNode; onClick?: () => void; children?: ReactNode;
}) {
  return (
    <div className={"section" + (onClick ? " clickable" : "")} onClick={onClick}>
      <div className="lead">{lead ?? (icon ? <Icon d={icon} /> : null)}</div>
      <div className="content">
        <div className="label"><span className="grow">{label}</span>{trailing}</div>
        {children}
      </div>
    </div>
  );
}

export function Check({ on, onChange }: { on: boolean; onChange: (v: boolean) => void }) {
  return (
    <span className={"cb" + (on ? " on" : "")} onClick={(e) => { e.stopPropagation(); onChange(!on); }} role="checkbox" aria-checked={on}>
      {on && <Icon d={P.check} size={14} />}
    </span>
  );
}

type Picker = null | "list" | "newSub" | { edit: number } | "discard" | "highlight";

/** Every field of one task. Changes stay in a draft until the tick button. */
export function TaskScreen({ initial, initialSubTasks, isNew, settings, lists, subLists, onSave, onDelete, onShare, onClose }: {
  initial: Task; initialSubTasks: SubTask[]; isNew: boolean; settings: Settings; lists: TaskList[]; subLists: SubList[];
  onSave: (t: Task, subs: SubDraft[], addAnother: boolean) => void; onDelete: () => void; onShare: (t: Task) => void; onClose: () => void;
}) {
  const [task, setTask] = useState(initial);
  const start = initialSubTasks.map((s) => ({ id: s.id, title: s.title, completed: s.completed }));
  const [subs, setSubs] = useState<SubDraft[]>(start);
  const [picker, setPicker] = useState<Picker>(null);
  const [menu, setMenu] = useState(false);
  const titleRef = useRef<HTMLInputElement>(null);
  const up = (c: Partial<Task>) => setTask((t) => ({ ...t, ...c }));

  useEffect(() => { if (isNew) titleRef.current?.focus(); }, [isNew]);

  const changed = JSON.stringify(task) !== JSON.stringify(initial) || JSON.stringify(subs) !== JSON.stringify(start);
  const close = () => (changed && task.title.trim() ? setPicker("discard") : onClose());
  const save = (another = false) => (task.title.trim() ? onSave(task, subs, another) : onClose());

  useEffect(() => {
    const key = (e: KeyboardEvent) => {
      if (e.key === "Escape" && picker === null) close();
      if (e.key === "Enter" && (e.ctrlKey || e.metaKey)) save();
    };
    window.addEventListener("keydown", key);
    return () => window.removeEventListener("keydown", key);
  });

  const setDue = (on: boolean) =>
    up(on ? { dueAt: task.dueAt ?? nextHour() } : { dueAt: null, reminderMode: ReminderMode.DontRemind, repeat: Repeat.None });
  const ensureDue = () => (task.dueAt === null ? { dueAt: nextHour() } : {});
  const sub = subLists.find((s) => s.id === task.subListId);
  const list = lists.find((l) => l.id === task.listId);

  return (
    <div className="task-screen">
      <div className="task-head">
        <div className="bar">
          <button className="icon-btn" onClick={close} title="Back"><Icon d={P.back} /></button>
          <span className="spacer" />
          {isNew ? (
            <button className="text-btn" onClick={() => save(true)}>Save &amp; Add</button>
          ) : (
            <span style={{ position: "relative" }}>
              <button className="icon-btn" onClick={() => setMenu(true)} title="More options"><Icon d={P.more} /></button>
              {menu && (
                <div className="scrim clear" onMouseDown={() => setMenu(false)}>
                  <div className="menu" style={{ position: "fixed", top: 8, right: 8 }} onMouseDown={(e) => e.stopPropagation()}>
                    <button onClick={() => { setMenu(false); onShare(task); }}>Share task</button>
                    <button onClick={() => { setMenu(false); onDelete(); }}>Delete task</button>
                  </div>
                </div>
              )}
            </span>
          )}
        </div>
        <input ref={titleRef} placeholder="Task Name" value={task.title} onChange={(e) => up({ title: e.target.value })} onKeyDown={(e) => e.key === "Enter" && save()} />
      </div>

      <div className="task-body">
        {settings.detailNotes && (
          <Section icon={P.notes} label="Notes">
            <textarea rows={Math.max(1, task.notes.split("\n").length)} placeholder="Tap to add notes" value={task.notes} onChange={(e) => up({ notes: e.target.value })} />
          </Section>
        )}

        {settings.detailSubTasks && (
          <Section icon={P.check} label="Sub Tasks" trailing={<button className="icon-btn" style={{ width: 32, height: 32 }} onClick={() => setPicker("newSub")} title="Add sub task"><Icon d={P.add} /></button>}>
            {subs.map((s, i) => (
              <div key={i} style={{ display: "flex", alignItems: "center", gap: 10, padding: "6px 0" }}>
                <Check on={s.completed} onChange={(v) => setSubs(subs.map((x, j) => (j === i ? { ...x, completed: v } : x)))} />
                <span style={{ flex: 1, cursor: "pointer", textDecoration: s.completed ? "line-through" : undefined, color: s.completed ? "var(--text2)" : undefined }}
                  onClick={() => setPicker({ edit: i })}>{s.title}</span>
                <button className="icon-btn" style={{ width: 32, height: 32, color: "var(--text2)" }} onClick={() => setSubs(subs.filter((_, j) => j !== i))} title="Remove sub task">
                  <Icon d={P.close} size={18} />
                </button>
              </div>
            ))}
          </Section>
        )}

        <Section icon={P.list} label="List" onClick={() => setPicker("list")}>
          <div className="value">{[list?.title, sub?.title].filter(Boolean).join(" / ")}</div>
        </Section>

        {settings.detailTags && <Section icon={P.label} label="Tags"><div className="value secondary">No tags selected</div></Section>}

        <Section lead={<Check on={task.dueAt !== null} onChange={setDue} />} label="Due Date"
          trailing={<><span>All Day</span><span className={"switch" + (task.allDay ? " on" : "")} onClick={() => up({ ...ensureDue(), allDay: !task.allDay })} /></>}>
          {task.dueAt === null ? (
            <div className="value secondary" style={{ cursor: "pointer" }} onClick={() => setDue(true)}>No date set</div>
          ) : (
            <div className="value" style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
              <input type="date" className="inline-input" value={dateValue(task.dueAt)} onChange={(e) => e.target.value && up({ dueAt: withDate(task.dueAt!, e.target.value) })} />
              {!task.allDay && (<>
                <span style={{ color: "var(--accent)" }}>@</span>
                <input type="time" className="inline-input" value={timeValue(task.dueAt)} onChange={(e) => e.target.value && up({ dueAt: withTime(task.dueAt!, e.target.value) })} />
              </>)}
            </div>
          )}
        </Section>

        <Section lead={<Check on={task.reminderMode !== ReminderMode.DontRemind} onChange={(on) => up({ ...ensureDue(), reminderMode: on ? ReminderMode.WhenDue : ReminderMode.DontRemind })} />} label="Reminder">
          <div className="value" style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
            <select className="inline-input" value={task.reminderMode} onChange={(e) => {
              const mode = Number(e.target.value) as Task["reminderMode"];
              up({ ...(mode ? ensureDue() : {}), reminderMode: mode, reminderAdvance: mode === ReminderMode.InAdvance && !task.reminderAdvance ? 10 : task.reminderAdvance });
            }}>
              <option value={0}>Don't remind me</option>
              <option value={1}>Remind me when due</option>
              <option value={2}>Remind me in advance</option>
            </select>
            {task.reminderMode === ReminderMode.InAdvance && (<>
              <input type="number" min={0} max={9999} className="inline-input" style={{ width: 80 }} value={task.reminderAdvance}
                onChange={(e) => up({ reminderAdvance: Math.max(0, Number(e.target.value) || 0) })} />
              <select className="inline-input" value={task.reminderAdvanceUnit} onChange={(e) => up({ reminderAdvanceUnit: Number(e.target.value) as Task["reminderAdvanceUnit"] })}>
                {UNITS.map(([c, l]) => <option key={c} value={c}>{l}</option>)}
              </select>
              <span className="secondary">in advance</span>
            </>)}
          </div>
        </Section>

        <Section icon={P.schedule} label="Reminder Type">
          <select className="inline-input value" value={task.reminderType} onChange={(e) => up({ reminderType: Number(e.target.value) as Task["reminderType"] })}>
            {REMINDER_TYPES.map((l, i) => <option key={i} value={i}>{l}</option>)}
          </select>
        </Section>

        <Section lead={<Check on={task.repeat !== Repeat.None} onChange={(on) => up({ ...ensureDue(), repeat: on ? Repeat.Daily : Repeat.None })} />} label="Repeat">
          <div className="value" style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
            <select className="inline-input" value={task.repeat} onChange={(e) => { const r = Number(e.target.value) as Task["repeat"]; up({ ...(r ? ensureDue() : {}), repeat: r }); }}>
              {REPEAT_LABELS.map((l, i) => <option key={i} value={i}>{l}</option>)}
            </select>
            {task.repeat === Repeat.Advanced && (<>
              <span>Every</span>
              <input type="number" min={1} className="inline-input" style={{ width: 70 }} value={task.repeatInterval} onChange={(e) => up({ repeatInterval: Math.max(1, Number(e.target.value) || 1) })} />
              <select className="inline-input" value={task.repeatUnit} onChange={(e) => up({ repeatUnit: Number(e.target.value) as Task["repeatUnit"] })}>
                {UNITS.map(([c, l]) => <option key={c} value={c}>{l}</option>)}
              </select>
            </>)}
          </div>
          {task.repeat === Repeat.DaysOfWeek && (
            <div style={{ display: "flex", gap: 12, flexWrap: "wrap", marginTop: 8 }}>
              {DAYS.map((d, i) => (
                <label key={d} style={{ display: "flex", gap: 6, alignItems: "center", cursor: "pointer" }}>
                  <Check on={!!(task.repeatDaysOfWeek & (1 << i))} onChange={() => up({ repeatDaysOfWeek: task.repeatDaysOfWeek ^ (1 << i) })} />{d.slice(0, 3)}
                </label>
              ))}
            </div>
          )}
        </Section>

        {task.repeat !== Repeat.None && (<>
          <Section icon={P.repeat} label="Repeat From">
            <select className="inline-input value" value={task.repeatFrom} onChange={(e) => up({ repeatFrom: Number(e.target.value) as Task["repeatFrom"] })}>
              {REPEAT_FROM.map((l, i) => <option key={i} value={i}>{l}</option>)}
            </select>
          </Section>
          <Section icon={P.repeat} label="Repeat Until">
            <div className="value" style={{ display: "flex", gap: 8, alignItems: "center" }}>
              <select className="inline-input" value={task.repeatUntil} onChange={(e) => {
                const u = Number(e.target.value) as Task["repeatUntil"];
                up(u === RepeatUntil.UntilDate ? { repeatUntil: u, repeatUntilAt: task.repeatUntilAt ?? (task.dueAt ?? Date.now()) + 30 * 86_400_000 } : { repeatUntil: u, repeatUntilAt: null });
              }}>
                <option value={0}>Forever</option>
                <option value={1}>Until date</option>
              </select>
              {task.repeatUntil === RepeatUntil.UntilDate && task.repeatUntilAt !== null && (
                <input type="date" className="inline-input" value={dateValue(task.repeatUntilAt)}
                  onChange={(e) => { if (!e.target.value) return; const d = new Date(withDate(task.repeatUntilAt!, e.target.value)); d.setHours(23, 59, 59, 0); up({ repeatUntilAt: d.getTime() }); }} />
              )}
            </div>
          </Section>
        </>)}

        {settings.detailPriority && (
          <Section icon={P.flag} label="Priority">
            <div className="prio">
              {([[Priority.None, "None"], [Priority.Low, "Low"], [Priority.Medium, "Medium"], [Priority.High, "High"]] as const).map(([v, l]) => (
                <label key={v} onClick={() => up({ priority: v })}><span className={"radio" + (task.priority === v ? " on" : "")} />{l}</label>
              ))}
            </div>
          </Section>
        )}

        {settings.detailHighlight && (
          <Section icon={P.star} label="Highlight" onClick={() => up({ highlight: !task.highlight })}
            trailing={<span onClick={(e) => { e.stopPropagation(); setPicker("highlight"); }}
              style={{ width: 24, height: 24, borderRadius: "50%", display: "inline-block", cursor: "pointer", background: task.highlight ? css(task.highlightColor) : "transparent", border: `1.5px solid ${task.highlight ? css(task.highlightColor) : "var(--icon)"}` }} />}>
            <div className="value secondary">Make this task stand out in the list view</div>
          </Section>
        )}

        <Section lead={<Check on={task.completed} onChange={(v) => up({ completed: v, completedAt: v ? Date.now() : null })} />} label="Completed">
          {task.completed && task.completedAt !== null && <div className="value secondary">{formatDate(task.completedAt)} {formatTime(task.completedAt)}</div>}
        </Section>

        {!isNew && settings.detailCreated && (
          <Section icon={P.schedule} label="Created"><div className="value secondary">{formatDate(task.createdAt)} {formatTime(task.createdAt)}</div></Section>
        )}
      </div>

      <button className="fab lower" onClick={() => save()} title="Save (Ctrl+Enter)"><Icon d={P.check} /></button>

      {picker === "list" && (
        <SubListChooser title="List" lists={lists} subLists={subLists} selected={task.subListId} onClose={() => setPicker(null)}
          onPick={(s) => up({ listId: s.listId, subListId: s.id })} />
      )}
      {picker === "newSub" && (
        <TextDialog title="Sub Task" initial="" hint="Sub Task" onClose={() => setPicker(null)}
          onSave={(v) => setSubs(settings.insertNewSubTasks === 0 ? [{ id: null, title: v, completed: false }, ...subs] : [...subs, { id: null, title: v, completed: false }])} />
      )}
      {picker !== null && typeof picker === "object" && "edit" in picker && (
        <TextDialog title="Sub Task" initial={subs[picker.edit]?.title ?? ""} hint="Sub Task" onClose={() => setPicker(null)}
          onSave={(v) => setSubs(subs.map((x, j) => (j === picker.edit ? { ...x, title: v } : x)))} />
      )}
      {picker === "highlight" && (
        <Modal onClose={() => setPicker(null)}>
          <div className="dialog">
            <h2>Highlight</h2>
            <div className="swatches">
              {HIGHLIGHT_COLORS.map((c) => (
                <span key={c} className={"swatch" + (task.highlight && task.highlightColor === c ? " on" : "")} style={{ background: css(c) }}
                  onClick={() => { up({ highlight: true, highlightColor: c }); setPicker(null); }} />
              ))}
            </div>
            <div className="actions"><button className="text-btn" onClick={() => setPicker(null)}>Cancel</button></div>
          </div>
        </Modal>
      )}
      {picker === "discard" && (
        <ConfirmDialog title="Save changes?" text="This task has changes that have not been saved." confirm="Save" dismiss="Discard"
          onClose={() => setPicker(null)} onDismissButton={onClose} onConfirm={() => onSave(task, subs, false)} />
      )}
    </div>
  );
}
