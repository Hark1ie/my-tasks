/**
 * Every setting, with its default value from the reference app's settings
 * screen and whether it syncs between devices or stays on one device.
 *
 * `ref` is the preference key in the reference app, kept so the parity check
 * in the last milestone can tick each one off.
 */


export type Scope = "synced" | "device";

export type Section =
  | "sync"
  | "application"
  | "list"
  | "task"
  | "taskDetail"
  | "notification";

export interface SettingDef<T> {
  section: Section;
  default: T;
  scope: Scope;
  ref: string | null;
}

function def<T>(section: Section, value: T, scope: Scope, ref: string | null): SettingDef<T> {
  return { section, default: value, scope, ref };
}

/** Option codes, in the order the reference app lists them. */
export const Theme = { Light: 0, Dark: 1, Black: 2, SystemLightDark: 3, SystemLightBlack: 4 } as const;
export const FontSize = { ExtraSmall: 0, Small: 1, Normal: 2, Large: 3, ExtraLarge: 4 } as const;
export const DisplayMode = { Comfortable: 0, Cozy: 1, Compact: 2 } as const;
export const HeadingInfo = {
  None: 0,
  CompletedOfTotal: 1,
  Percentage: 2,
  Overdue: 3,
  Total: 4,
  Uncompleted: 5,
} as const;
export type HeadingInfo = (typeof HeadingInfo)[keyof typeof HeadingInfo];
export const InsertAt = { Top: 0, Bottom: 1 } as const;
export const NotesView = { All: 0, TwoLines: 1, OneLine: 2, None: 3 } as const;
export const ReminderView = { LeftAlign: 0, RightAlign: 1, Hide: 2 } as const;
/** Codes are not in display order in the reference app: Forever, 15 s, 30 s, 1 min, 5 min, 10 min. */
export const AlarmDuration = { Forever: 0, Min1: 1, Min5: 2, Min10: 3, Sec15: 4, Sec30: 5 } as const;

/**
 * Same names, defaults and sync scope as the Android app's Keys (data/Prefs.kt),
 * which writes them to users/{uid}/settings/synced.
 */
export const SETTINGS = {
  // Sync
  syncOnClose: def("sync", true, "device", "pref_key_sync_on_close"),
  syncWifiOnly: def("sync", false, "device", "pref_key_sync_wifi_only"),
  /** Not in the reference app: the phone keeps syncing while the app is closed. */
  alwaysOnSync: def("sync", true, "device", null),

  // Application Settings
  theme: def("application", Theme.Light as number, "device", "pref_key_theme"),
  showCalendar: def("application", false, "synced", "pref_key_show_calendar"),
  swipeToDelete: def("application", true, "synced", "pref_key_swipe_to_delete_enabled"),
  showQuickAdd: def("application", true, "synced", "pref_key_show_quick_add"),
  multiLineQuickAdd: def("application", true, "synced", "pref_key_multi_line_quick_add"),
  keepScreenOn: def("application", false, "device", "pref_key_keep_screen_on"),
  vibrateUi: def("application", true, "device", "pref_key_vibrate_ui"),
  fontSize: def("application", FontSize.Normal as number, "device", "pref_key_font_size"),
  /** "" = continue where you left off, otherwise a list id. */
  defaultList: def("application", "", "synced", "pref_key_default_list"),
  removeDeletedItemsDays: def("application", 7, "synced", "pref_key_remove_deleted_items_days"),
  showMenuOnBack: def("application", false, "synced", "pref_key_show_menu_on_back"),
  showMenuOnOpen: def("application", false, "synced", "pref_key_show_menu_on_open"),

  // List Settings
  moveCompletedToBottom: def("list", false, "synced", "pref_key_sort_completed"),
  displayMode: def("list", DisplayMode.Comfortable as number, "device", "pref_key_list_view_mode"),
  taskPadding: def("list", 10, "device", "pref_key_task_padding"),
  tagSize: def("list", DisplayMode.Comfortable as number, "device", "pref_key_tag_size"),
  deleteListConfirmation: def("list", true, "synced", "pref_key_delete_confirmation"),
  autoLink: def("list", true, "synced", "pref_key_auto_link"),
  showListColor: def("list", true, "synced", "pref_key_show_list_color"),
  menuListInfo: def("list", HeadingInfo.CompletedOfTotal as number, "synced", "pref_key_menu_list_info"),
  subListHeading: def("list", HeadingInfo.CompletedOfTotal as number, "synced", "pref_key_sub_task_list_heading"),
  insertNewTasks: def("list", InsertAt.Top as number, "synced", "pref_key_insert_new_tasks"),
  insertNewSubTasks: def("list", InsertAt.Bottom as number, "synced", "pref_key_insert_new_sub_tasks"),
  notesView: def("list", NotesView.All as number, "synced", "pref_key_notes_view"),
  reminderView: def("list", ReminderView.LeftAlign as number, "synced", "pref_key_reminder_view"),
  verboseDateTime: def("list", false, "synced", "pref_key_verbose_date_time"),
  autoNumberTasks: def("list", false, "synced", "pref_key_auto_number_tasks"),
  autoNumberSubTasks: def("list", false, "synced", "pref_key_auto_number_sub_tasks"),
  includeReminderAdvance: def("list", false, "synced", "pref_key_include_reminder_advance"),

  // Task Settings
  showCompleteDate: def("task", true, "synced", "pref_key_show_complete_date"),
  strikethroughCompleted: def("task", true, "synced", "pref_key_strikethrough_completed"),
  subTasksCompleteTask: def("task", true, "synced", "pref_key_sub_tasks_complete_task"),
  subTasksExpandDefault: def("task", false, "synced", "pref_key_sub_tasks_expand_default"),
  enableReminders: def("task", false, "synced", "pref_key_enable_reminders"),
  defaultReminderType: def("task", 0, "synced", "pref_key_default_reminder_type"),
  /** A Priority code: 0 none, 1 low, 2 medium, 3 high. */
  defaultPriority: def("task", 0, "synced", "pref_key_default_priority"),
  saveRecurringHistory: def("task", false, "synced", "pref_key_duplicate_on_complete"),

  // Task Detail Screen
  detailNotes: def("taskDetail", true, "synced", "pref_key_details_notes"),
  detailAttachment: def("taskDetail", true, "synced", "pref_key_details_attachment"),
  detailSubTasks: def("taskDetail", true, "synced", "pref_key_details_sub_tasks"),
  detailTags: def("taskDetail", true, "synced", "pref_key_details_tags"),
  detailPriority: def("taskDetail", true, "synced", "pref_key_details_priority"),
  detailHighlight: def("taskDetail", true, "synced", "pref_key_details_highlight"),
  detailCreated: def("taskDetail", true, "synced", "pref_key_details_created"),

  // Notification/Alarm Settings
  autoIncrementReminder: def("notification", false, "synced", "pref_key_auto_increment_reminder"),
  addFromNotification: def("notification", true, "device", "pref_key_add_from_notification"),
  completeClearsNotification: def("notification", true, "synced", "pref_key_complete_clears_notification"),
  persistentNotifications: def("notification", false, "synced", "pref_key_notification_persistent"),
  notificationVibrate: def("notification", true, "device", "pref_key_notification_vibrate"),
  notificationLed: def("notification", true, "device", "pref_key_notification_led"),
  alarmDuration: def("notification", AlarmDuration.Forever as number, "device", "pref_key_alarm_duration"),
  /** Minutes, 0 to 10080. */
  customSnoozeMinutes: def("notification", 720, "synced", "pref_key_custom_snooze_duration"),
  muteAlarmOnCall: def("notification", true, "device", "pref_key_mute_alarm_on_call"),
} as const;

export type SettingKey = keyof typeof SETTINGS;
export type SettingValue<K extends SettingKey> = (typeof SETTINGS)[K]["default"];
export type Settings = { [K in SettingKey]: SettingValue<K> };

export function defaultSettings(): Settings {
  const out: Record<string, unknown> = {};
  for (const [k, d] of Object.entries(SETTINGS)) out[k] = d.default;
  return out as Settings;
}

export function keysWithScope(scope: Scope): SettingKey[] {
  return (Object.keys(SETTINGS) as SettingKey[]).filter((k) => SETTINGS[k].scope === scope);
}

/** The part of the settings that is written to the cloud. */
export function syncedPart(s: Settings): Partial<Settings> {
  const out: Record<string, unknown> = {};
  for (const k of keysWithScope("synced")) out[k] = s[k];
  return out as Partial<Settings>;
}

/**
 * Settings after applying a cloud document on top of this device's values.
 * Unknown keys and device-only keys in the cloud document are ignored.
 */
export function applySynced(local: Settings, cloud: Record<string, unknown>): Settings {
  const out: Record<string, unknown> = { ...local };
  for (const k of keysWithScope("synced")) {
    const v = cloud[k];
    const d = SETTINGS[k].default;
    if (v === undefined) continue;
    if (d === null ? v === null || typeof v === "string" : typeof v === typeof d) out[k] = v;
  }
  return out as Settings;
}

/**
 * Starting configuration for this app: the reference defaults, changed only
 * where the owner's screenshots show a different choice.
 */
export function ownerPreset(): Settings {
  return { ...defaultSettings(), theme: Theme.Black };
}

/** Widget options. Always per device and per widget. */
export interface WidgetSettings {
  /** Sub-list or filtered list shown. `null` until the user picks one. */
  sourceId: string | null;
  /** 0 to 100. */
  backgroundTransparency: number;
  /** 0 to 100. */
  listItemTransparency: number;
  fontSize: number;
  showAddButton: boolean;
  addButtonInHeading: boolean;
}
