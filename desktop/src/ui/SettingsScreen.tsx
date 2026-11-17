import { type ReactNode, useEffect, useState } from "react";
import type { Settings, TaskList } from "@core";
import { settings as model } from "../data/settings";
import { ChoiceDialog, TextDialog } from "./Dialogs";
import { Check } from "./TaskScreen";
import { Icon, P } from "./icons";

type BoolKey = { [K in keyof Settings]: Settings[K] extends boolean ? K : never }[keyof Settings];
type NumKey = { [K in keyof Settings]: Settings[K] extends number ? K : never }[keyof Settings];

const HEADING = ["None", "Completed / Total", "Percentage completed", "Overdue", "Total", "Uncompleted"];
const SIZES = ["Comfortable", "Cozy", "Compact"];
const TOP_BOTTOM = ["Top", "Bottom"];

/** Same groups, order and options as the Android Settings screen. Phone-only options are marked. */
export function SettingsScreen({ s, lists, account, onSignOut, onClose }: {
  s: Settings; lists: TaskList[]; account: string | null; onSignOut: (() => void) | null; onClose: () => void;
}) {
  const [choice, setChoice] = useState<{ key: NumKey; title: string; labels: string[]; values: number[] } | null>(null);
  const [number, setNumber] = useState<{ key: NumKey; title: string; min: number; max: number } | null>(null);
  const [defaultList, setDefaultList] = useState(false);

  useEffect(() => {
    const key = (e: KeyboardEvent) => e.key === "Escape" && !choice && !number && !defaultList && onClose();
    window.addEventListener("keydown", key);
    return () => window.removeEventListener("keydown", key);
  });

  const Row = ({ title, summary, onClick, disabled, children }: { title: string; summary?: string; onClick?: () => void; disabled?: boolean; children?: ReactNode }) => (
    <div className={"setting" + (onClick && !disabled ? " clickable" : "") + (disabled ? " disabled" : "")} onClick={disabled ? undefined : onClick}>
      <div className="grow"><div className="t">{title}</div>{summary && <div className="s">{summary}</div>}</div>
      {children}
    </div>
  );
  const check = (key: BoolKey, title: string, summary?: string, disabled = false) => (
    <Row title={title} summary={summary} disabled={disabled} onClick={() => model.set(key, !s[key])}>
      <Check on={s[key]} onChange={(v) => !disabled && model.set(key, v)} />
    </Row>
  );
  const pick = (key: NumKey, title: string, labels: string[], values = labels.map((_, i) => i)) => (
    <Row title={title} summary={labels[values.indexOf(s[key])] ?? ""} onClick={() => setChoice({ key, title, labels, values })} />
  );
  const num = (key: NumKey, title: string, min: number, max: number, unit: string) => (
    <Row title={title} summary={`${s[key]} ${unit}`} onClick={() => setNumber({ key, title, min, max })} />
  );
  const Cat = ({ children }: { children: ReactNode }) => <div className="cat">{children}</div>;
  const phone = "Applies on your phone";

  return (
    <div className="settings">
      <div className="bar">
        <button className="icon-btn" onClick={onClose} title="Back"><Icon d={P.back} /></button>Settings
      </div>
      <div className="scroll"><div className="inner">
        <Cat>Sync</Cat>
        <Row title="Account" summary={account ?? "Not signed in (demo)"} />
        <Row title="Last sync" summary="Changes sync live while My Tasks is open" />
        {onSignOut && <Row title="Sign out" summary="Your tasks stay in the cloud" onClick={onSignOut} />}

        <Cat>Application Settings</Cat>
        {pick("theme", "Theme", ["Light", "Dark", "Black", "Follow system (light / dark)", "Follow system (light / black)"])}
        {check("showCalendar", "Show calendar", "Show the calendar list. It has fewer functions than a normal list")}
        {check("swipeToDelete", "Swipe to delete enabled", "On a computer: show the delete button when the pointer is over a task")}
        {check("showQuickAdd", "Show quick add", "Show quick add at the bottom of the task list. Not shown on filtered lists")}
        {check("multiLineQuickAdd", "Multi line quick add", "On: Shift+Enter starts a new line. Off: one line only", !s.showQuickAdd)}
        {pick("fontSize", "Font size", ["Extra small", "Small", "Normal", "Large", "Extra Large"])}
        <Row title="Default list when opening app" summary={lists.find((l) => l.id === s.defaultList)?.title ?? "Continue where you left off"} onClick={() => setDefaultList(true)} />
        {num("removeDeletedItemsDays", "Remove deleted items older than", 1, 30, "days")}

        <Cat>List Settings</Cat>
        {check("moveCompletedToBottom", "Move completed tasks", "Move completed tasks to the bottom of the list")}
        {pick("displayMode", "Display mode", SIZES)}
        {num("taskPadding", "List item padding", 0, 100, "pixels")}
        {pick("tagSize", "Tag size", SIZES)}
        {check("deleteListConfirmation", "Delete list confirmation", "Ask before deleting a task list")}
        {check("autoLink", "Auto link text", "Show web addresses, email addresses and phone numbers as links")}
        {check("showListColor", "Show list colour", "Show the list colour on each task. Not used on filtered lists")}
        {pick("menuListInfo", "Menu list information", HEADING)}
        {pick("subListHeading", "Sub list heading information", HEADING)}
        {pick("insertNewTasks", "Where should new tasks be inserted?", TOP_BOTTOM)}
        {pick("insertNewSubTasks", "Where should new sub-tasks be inserted?", TOP_BOTTOM)}
        {pick("notesView", "How should Task notes be displayed?", ["All", "2 Lines", "1 Line", "None"])}
        {pick("reminderView", "How should the Task reminder be displayed?", ["Left align", "Right align", "Hide"])}
        {check("verboseDateTime", "Display date and time", "Always show the date and the time for each task")}
        {check("autoNumberTasks", "Auto number tasks", "Number each task in the list")}
        {check("autoNumberSubTasks", "Auto number sub tasks", "Number each sub-task")}
        {check("includeReminderAdvance", "Include reminder advance", "Show the reminder time, not the due time, in the list")}

        <Cat>Task Settings</Cat>
        {check("showCompleteDate", "Show complete date", "Show when a task was completed")}
        {check("strikethroughCompleted", "Strikethrough completed tasks", "Strike through the title and notes of completed tasks")}
        {check("subTasksCompleteTask", "Complete all sub-tasks completes task", "When every sub-task is done, the task is marked as done too")}
        {check("subTasksExpandDefault", "Sub tasks expanded", "Show sub-tasks in the list. Applies to new tasks only")}
        {check("enableReminders", "Enable reminder", "New tasks start with a reminder switched on")}
        {pick("defaultReminderType", "Default reminder type", ["Notification", "Alarm", "None"])}
        {pick("defaultPriority", "Default priority", ["High", "Medium", "Low", "None"], [3, 2, 1, 0])}
        {check("saveRecurringHistory", "Save recurring task history?", "Keep a completed copy each time a repeating task is ticked")}

        <Cat>Task Detail Screen</Cat>
        {check("detailNotes", "Notes", "Show/hide the notes section")}
        {check("detailAttachment", "Attachment", "Show/hide the attachment section")}
        {check("detailSubTasks", "Sub Tasks", "Show/hide the sub-tasks section")}
        {check("detailTags", "Tags", "Show/hide the tags section")}
        {check("detailPriority", "Priority", "Show/hide the priority section")}
        {check("detailHighlight", "Highlight", "Show/hide the highlight section")}
        {check("detailCreated", "Created", "Show/hide the created date section")}

        <Cat>Notification/Alarm Settings</Cat>
        {check("autoIncrementReminder", "Automatically increment reminder", phone)}
        {check("completeClearsNotification", "Complete task clears notification", phone)}
        {check("persistentNotifications", "Persistent notifications", phone)}
        {num("customSnoozeMinutes", "Custom snooze duration", 0, 10080, "minutes")}
        <Row title="Sounds, vibration, alarms" summary="Set these on your phone" disabled />

        <Cat>About</Cat>
        <Row title="Build version" summary="0.1 (PC)" />
      </div></div>

      {choice && (
        <ChoiceDialog title={choice.title} options={choice.labels} selected={choice.values.indexOf(s[choice.key])} onClose={() => setChoice(null)}
          onPick={(i) => model.set(choice.key, choice.values[i]!)} />
      )}
      {number && (
        <TextDialog title={number.title} initial={String(s[number.key])} hint={`${number.min} to ${number.max}`} onClose={() => setNumber(null)}
          onSave={(v) => { const n = Math.round(Number(v)); if (Number.isFinite(n)) model.set(number.key, Math.min(number.max, Math.max(number.min, n))); }} />
      )}
      {defaultList && (
        <ChoiceDialog title="Default list when opening app" options={["Continue where you left off", ...lists.map((l) => l.title)]}
          selected={lists.findIndex((l) => l.id === s.defaultList) + 1} onClose={() => setDefaultList(false)}
          onPick={(i) => model.set("defaultList", i === 0 ? "" : lists[i - 1]!.id)} />
      )}
    </div>
  );
}
