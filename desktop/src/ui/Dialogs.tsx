import { type ReactNode, useEffect, useRef, useState } from "react";
import { DndContext, type DragEndEvent, PointerSensor, closestCenter, useSensor, useSensors } from "@dnd-kit/core";
import { SortableContext, arrayMove, useSortable, verticalListSortingStrategy } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { type SubList, type TaskList, LIST_COLORS } from "@core";
import { Icon, P } from "./icons";
import { css } from "./format";

/** Closes on Escape and on a click outside. */
function Modal({ onClose, children }: { onClose: () => void; children: ReactNode }) {
  useEffect(() => {
    const key = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", key);
    return () => window.removeEventListener("keydown", key);
  }, [onClose]);
  return (
    <div className="scrim" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      {children}
    </div>
  );
}

export function TextDialog({ title, initial, hint, onClose, onSave }: {
  title: string; initial: string; hint: string; onClose: () => void; onSave: (v: string) => void;
}) {
  const [v, setV] = useState(initial);
  const save = () => {
    if (v.trim()) onSave(v);
    onClose();
  };
  return (
    <Modal onClose={onClose}>
      <div className="dialog">
        <h2>{title}</h2>
        <input className="field" autoFocus value={v} placeholder={hint} onChange={(e) => setV(e.target.value)} onKeyDown={(e) => e.key === "Enter" && save()} />
        <div className="actions">
          <button className="text-btn" onClick={onClose}>Cancel</button>
          <button className="text-btn" onClick={save}>Save</button>
        </div>
      </div>
    </Modal>
  );
}

export function ConfirmDialog({ title, text, confirm, dismiss = "Cancel", onClose, onDismissButton, onConfirm }: {
  title: string; text: string; confirm: string; dismiss?: string; onClose: () => void; onDismissButton?: () => void; onConfirm: () => void;
}) {
  return (
    <Modal onClose={onClose}>
      <div className="dialog">
        <h2>{title}</h2>
        <div>{text}</div>
        <div className="actions">
          <button className="text-btn" onClick={() => { onDismissButton?.(); onClose(); }}>{dismiss}</button>
          <button className="text-btn" onClick={() => { onConfirm(); onClose(); }}>{confirm}</button>
        </div>
      </div>
    </Modal>
  );
}

export function Radio({ on }: { on: boolean }) {
  return <span className={"radio" + (on ? " on" : "")} />;
}

/** A list of options; picking one closes the dialog. */
export function ChoiceDialog({ title, options, selected, onClose, onPick }: {
  title: string; options: string[]; selected: number; onClose: () => void; onPick: (i: number) => void;
}) {
  return (
    <Modal onClose={onClose}>
      <div className="dialog">
        <h2>{title}</h2>
        {options.map((o, i) => (
          <div key={i} className="radio-row" onClick={() => { onClose(); onPick(i); }}>
            <Radio on={i === selected} /> {o}
          </div>
        ))}
        <div className="actions"><button className="text-btn" onClick={onClose}>Cancel</button></div>
      </div>
    </Modal>
  );
}

/** Pick a sub-list from any task list. */
export function SubListChooser({ title, lists, subLists, selected, onClose, onPick }: {
  title: string; lists: TaskList[]; subLists: SubList[]; selected: string | null; onClose: () => void; onPick: (s: SubList) => void;
}) {
  return (
    <Modal onClose={onClose}>
      <div className="dialog">
        <h2>{title}</h2>
        {lists.map((l) => (
          <div key={l.id}>
            <div className="group-label">{l.title}</div>
            {subLists.filter((s) => s.listId === l.id).map((s) => (
              <div key={s.id} className="radio-row" onClick={() => { onPick(s); onClose(); }}>
                <Radio on={s.id === selected} /> {s.title}
              </div>
            ))}
          </div>
        ))}
        <div className="actions"><button className="text-btn" onClick={onClose}>Cancel</button></div>
      </div>
    </Modal>
  );
}

function PanelDialog({ title, name, setName, onClose, onDelete, onSave, children }: {
  title: string; name: string; setName: (v: string) => void; onClose: () => void; onDelete?: () => void; onSave: () => void; children: ReactNode;
}) {
  const ref = useRef<HTMLInputElement>(null);
  useEffect(() => ref.current?.focus(), []);
  return (
    <Modal onClose={onClose}>
      <div className="panel-dialog">
        <div className="top">
          <h2>{title}</h2>
          <input ref={ref} value={name} onChange={(e) => setName(e.target.value)} onKeyDown={(e) => e.key === "Enter" && onSave()} />
        </div>
        <div className="panel">
          {children}
          <div className="buttons">
            {onDelete && <button className="panel-btn danger" onClick={onDelete}>DELETE</button>}
            <span className="spacer" />
            <button className="panel-btn" onClick={onClose}>CANCEL</button>
            <button className="panel-btn" onClick={onSave}>SAVE</button>
          </div>
        </div>
      </div>
    </Modal>
  );
}

/** HSV (h 0-360, s and v 0-1) to an ARGB number. */
function hsv(h: number, s: number, v: number): number {
  const f = (n: number) => {
    const k = (n + h / 60) % 6;
    return Math.round(255 * (v - v * s * Math.max(0, Math.min(k, 4 - k, 1))));
  };
  return ((0xff << 24) | (f(5) << 16) | (f(3) << 8) | f(1)) >>> 0;
}

function toHsv(argb: number): [number, number, number] {
  const r = ((argb >>> 16) & 0xff) / 255, g = ((argb >>> 8) & 0xff) / 255, b = (argb & 0xff) / 255;
  const max = Math.max(r, g, b), min = Math.min(r, g, b), d = max - min;
  let h = 0;
  if (d) h = max === r ? ((g - b) / d) % 6 : max === g ? (b - r) / d + 2 : (r - g) / d + 4;
  return [((h * 60) + 360) % 360, max ? d / max : 0, max];
}

const RINGS = 8;

/** Rings of colour dots (hue around, strength outwards) and a brightness slider, as on the phone. */
export function ColorWheel({ color, onColor }: { color: number; onColor: (c: number) => void }) {
  const start = toHsv(color);
  const [hue, setHue] = useState(start[0]);
  const [sat, setSat] = useState(start[1]);
  const [val, setVal] = useState(start[2]);
  const size = 340, c = size / 2, step = c / (RINGS - 0.5), dot = step * 0.48;
  const dots: ReactNode[] = [];
  for (let ring = 0; ring < RINGS; ring++) {
    const count = ring === 0 ? 1 : ring * 6;
    for (let i = 0; i < count; i++) {
      const h = (360 * i) / count, s = ring / (RINGS - 1), a = (2 * Math.PI * i) / count;
      const on = ring === 0 ? sat === 0 : Math.abs(s - sat) < 1e-6 && Math.abs(h - hue) < 1e-6;
      dots.push(
        <circle key={`${ring}-${i}`} cx={c + ring * step * Math.cos(a)} cy={c + ring * step * Math.sin(a)} r={dot}
          fill={css(hsv(h, s, val))} stroke={on ? "var(--text)" : "none"} strokeWidth={2} style={{ cursor: "pointer" }}
          onClick={() => {
            const v = val < 0.05 ? 1 : val;
            setHue(h); setSat(s); setVal(v); onColor(hsv(h, s, v));
          }} />,
      );
    }
  }
  return (
    <div style={{ padding: "12px 12px 0" }}>
      <svg viewBox={`0 0 ${size} ${size}`} width="100%">{dots}</svg>
      <input type="range" min={0} max={100} value={Math.round(val * 100)} style={{ width: "100%", accentColor: css(hsv(hue, sat, 1)) }}
        onChange={(e) => { const v = Number(e.target.value) / 100; setVal(v); onColor(hsv(hue, sat, v)); }} />
    </div>
  );
}

export function ListDialog({ list, onClose, onDelete, onSave }: {
  list: TaskList | null; onClose: () => void; onDelete?: () => void; onSave: (title: string, color: number) => void;
}) {
  const [name, setName] = useState(list?.title ?? "");
  const [color, setColor] = useState(list?.color ?? LIST_COLORS[0]!);
  const save = () => { if (name.trim()) onSave(name, color); onClose(); };
  return (
    <PanelDialog title="List Name" name={name} setName={setName} onClose={onClose} onDelete={onDelete} onSave={save}>
      <ColorWheel color={color} onColor={setColor} />
    </PanelDialog>
  );
}

export function SubListDialog({ sub, others, onClose, onDelete, onSave }: {
  sub: SubList | null; others: SubList[]; onClose: () => void; onDelete?: () => void; onSave: (title: string, target: string | null) => void;
}) {
  const [name, setName] = useState(sub?.title ?? "");
  const [target, setTarget] = useState<string | null>(sub?.completedSubListId && others.some((o) => o.id === sub.completedSubListId) ? sub.completedSubListId : null);
  const save = () => { if (name.trim()) onSave(name, target); onClose(); };
  return (
    <PanelDialog title="Sub List Name" name={name} setName={setName} onClose={onClose} onDelete={onDelete} onSave={save}>
      <div style={{ padding: "12px 24px 0", color: "var(--text2)", fontSize: 15 }}>Where should completed tasks go?</div>
      <div style={{ display: "flex", alignItems: "center", gap: 24, padding: "8px 24px" }}>
        <Icon d={P.list} />
        <select className="inline-input" style={{ flex: 1, fontSize: 18, background: "transparent", border: 0 }} value={target ?? ""}
          onChange={(e) => setTarget(e.target.value || null)}>
          <option value="">This list</option>
          {others.map((o) => <option key={o.id} value={o.id}>{o.title}</option>)}
        </select>
      </div>
    </PanelDialog>
  );
}

function SortableItem({ id, title }: { id: string; title: string }) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id });
  return (
    <div ref={setNodeRef} style={{ transform: CSS.Transform.toString(transform), transition, background: isDragging ? "var(--surface)" : undefined, display: "flex", alignItems: "center", minHeight: 48 }}>
      <span style={{ flex: 1 }}>{title}</span>
      <span {...attributes} {...listeners} style={{ cursor: "grab", padding: 8, color: "var(--text2)" }}><Icon d={P.dragHandle} /></span>
    </div>
  );
}

/** Drag rows by their handle to reorder; onMove gets the moved id and the new order. */
export function ReorderDialog({ title, items, onClose, onMove }: {
  title: string; items: { id: string; title: string }[]; onClose: () => void; onMove: (id: string, order: string[]) => void;
}) {
  const [order, setOrder] = useState(items);
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 4 } }));
  const end = (e: DragEndEvent) => {
    if (!e.over || e.active.id === e.over.id) return;
    const from = order.findIndex((x) => x.id === e.active.id), to = order.findIndex((x) => x.id === e.over!.id);
    const next = arrayMove(order, from, to);
    setOrder(next);
    onMove(String(e.active.id), next.map((x) => x.id));
  };
  return (
    <Modal onClose={onClose}>
      <div className="dialog" style={{ minWidth: 360 }}>
        <h2>{title}</h2>
        <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={end}>
          <SortableContext items={order.map((x) => x.id)} strategy={verticalListSortingStrategy}>
            {order.map((x) => <SortableItem key={x.id} id={x.id} title={x.title} />)}
          </SortableContext>
        </DndContext>
        <div className="actions"><button className="text-btn" onClick={onClose}>Close</button></div>
      </div>
    </Modal>
  );
}

export { Modal };
