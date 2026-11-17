package app.mytasks.data

import android.content.Context
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** One setting: its stored name, its default, and whether it syncs between devices. */
sealed class Key<T>(val name: String, val default: T, val synced: Boolean)
class BoolKey(name: String, default: Boolean, synced: Boolean = true) : Key<Boolean>(name, default, synced)
class IntKey(name: String, default: Int, synced: Boolean = true) : Key<Int>(name, default, synced)
class TextKey(name: String, default: String, synced: Boolean = true) : Key<String>(name, default, synced)

/**
 * Every setting with the reference app's default. Twin of core/src/settings.ts.
 * `synced = false` keeps a setting on this device only.
 */
object Keys {
    // Sync
    val syncOnClose = BoolKey("syncOnClose", true, synced = false)
    val syncWifiOnly = BoolKey("syncWifiOnly", false, synced = false)
    /** Not in the reference app: keeps the live connection open while the app is closed. */
    val alwaysOnSync = BoolKey("alwaysOnSync", true, synced = false)

    // Application Settings. Owner preset: Black theme (2); the reference default is Light (0).
    val theme = IntKey("theme", 2, synced = false)
    val showCalendar = BoolKey("showCalendar", false)
    val swipeToDelete = BoolKey("swipeToDelete", true)
    val showQuickAdd = BoolKey("showQuickAdd", true)
    val multiLineQuickAdd = BoolKey("multiLineQuickAdd", true)
    val keepScreenOn = BoolKey("keepScreenOn", false, synced = false)
    val vibrateUi = BoolKey("vibrateUi", true, synced = false)
    val fontSize = IntKey("fontSize", 2, synced = false)
    /** Empty = continue where you left off, otherwise a list id. */
    val defaultList = TextKey("defaultList", "")
    val removeDeletedItemsDays = IntKey("removeDeletedItemsDays", 7)
    val showMenuOnBack = BoolKey("showMenuOnBack", false)
    val showMenuOnOpen = BoolKey("showMenuOnOpen", false)

    // List Settings
    val moveCompletedToBottom = BoolKey("moveCompletedToBottom", false)
    val displayMode = IntKey("displayMode", 0, synced = false)
    val taskPadding = IntKey("taskPadding", 10, synced = false)
    val tagSize = IntKey("tagSize", 0, synced = false)
    val deleteListConfirmation = BoolKey("deleteListConfirmation", true)
    val autoLink = BoolKey("autoLink", true)
    val showListColor = BoolKey("showListColor", true)
    val menuListInfo = IntKey("menuListInfo", 1)
    val subListHeading = IntKey("subListHeading", 1)
    val insertNewTasks = IntKey("insertNewTasks", 0)
    val insertNewSubTasks = IntKey("insertNewSubTasks", 1)
    val notesView = IntKey("notesView", 0)
    val reminderView = IntKey("reminderView", 0)
    val verboseDateTime = BoolKey("verboseDateTime", false)
    val autoNumberTasks = BoolKey("autoNumberTasks", false)
    val autoNumberSubTasks = BoolKey("autoNumberSubTasks", false)
    val includeReminderAdvance = BoolKey("includeReminderAdvance", false)

    // Task Settings
    val showCompleteDate = BoolKey("showCompleteDate", true)
    val strikethroughCompleted = BoolKey("strikethroughCompleted", true)
    val subTasksCompleteTask = BoolKey("subTasksCompleteTask", true)
    val subTasksExpandDefault = BoolKey("subTasksExpandDefault", false)
    val enableReminders = BoolKey("enableReminders", false)
    val defaultReminderType = IntKey("defaultReminderType", 0)
    val defaultPriority = IntKey("defaultPriority", 0)
    val saveRecurringHistory = BoolKey("saveRecurringHistory", false)

    // Task Detail Screen
    val detailNotes = BoolKey("detailNotes", true)
    val detailAttachment = BoolKey("detailAttachment", true)
    val detailSubTasks = BoolKey("detailSubTasks", true)
    val detailTags = BoolKey("detailTags", true)
    val detailPriority = BoolKey("detailPriority", true)
    val detailHighlight = BoolKey("detailHighlight", true)
    val detailCreated = BoolKey("detailCreated", true)

    // Notification/Alarm Settings
    val autoIncrementReminder = BoolKey("autoIncrementReminder", false)
    val addFromNotification = BoolKey("addFromNotification", true, synced = false)
    val completeClearsNotification = BoolKey("completeClearsNotification", true)
    val persistentNotifications = BoolKey("persistentNotifications", false)
    val notificationVibrate = BoolKey("notificationVibrate", true, synced = false)
    val notificationLed = BoolKey("notificationLed", true, synced = false)
    val alarmDuration = IntKey("alarmDuration", 0, synced = false)
    val customSnoozeMinutes = IntKey("customSnoozeMinutes", 720)
    val muteAlarmOnCall = BoolKey("muteAlarmOnCall", true, synced = false)

    val all: List<Key<*>> = listOf(
        syncOnClose, syncWifiOnly, alwaysOnSync, theme, showCalendar, swipeToDelete, showQuickAdd, multiLineQuickAdd, keepScreenOn,
        vibrateUi, fontSize, defaultList, removeDeletedItemsDays, showMenuOnBack, showMenuOnOpen, moveCompletedToBottom,
        displayMode, taskPadding, tagSize, deleteListConfirmation, autoLink, showListColor, menuListInfo, subListHeading,
        insertNewTasks, insertNewSubTasks, notesView, reminderView, verboseDateTime, autoNumberTasks, autoNumberSubTasks,
        includeReminderAdvance, showCompleteDate, strikethroughCompleted, subTasksCompleteTask, subTasksExpandDefault,
        enableReminders, defaultReminderType, defaultPriority, saveRecurringHistory, detailNotes, detailAttachment,
        detailSubTasks, detailTags, detailPriority, detailHighlight, detailCreated, autoIncrementReminder,
        addFromNotification, completeClearsNotification, persistentNotifications, notificationVibrate, notificationLed,
        alarmDuration, customSnoozeMinutes, muteAlarmOnCall,
    )
}

/** A snapshot of every setting's current value. */
class SettingsState(private val values: Map<String, Any>) {
    operator fun get(key: BoolKey): Boolean = values[key.name] as? Boolean ?: key.default
    operator fun get(key: IntKey): Int = (values[key.name] as? Number)?.toInt() ?: key.default
    operator fun get(key: TextKey): String = values[key.name] as? String ?: key.default
}

/** Settings storage on this device. Synced settings are also mirrored to the cloud through [onSyncedChange]. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("device", Context.MODE_PRIVATE)

    /** Identifies this device in `updatedBy`. */
    val deviceId: String = sp.getString("deviceId", null) ?: UUID.randomUUID().toString().also {
        sp.edit().putString("deviceId", it).apply()
    }

    private val values = HashMap<String, Any>()
    private val _state: MutableStateFlow<SettingsState>
    val state: StateFlow<SettingsState> get() = _state

    /** Called with all synced values after one of them changes on this device. */
    var onSyncedChange: ((Map<String, Any>) -> Unit)? = null

    init {
        for (key in Keys.all) {
            val stored = "s_" + key.name
            if (!sp.contains(stored)) continue
            when (key) {
                is BoolKey -> values[key.name] = sp.getBoolean(stored, key.default)
                is IntKey -> values[key.name] = sp.getInt(stored, key.default)
                is TextKey -> values[key.name] = sp.getString(stored, key.default) ?: key.default
            }
        }
        _state = MutableStateFlow(SettingsState(HashMap(values)))
    }

    fun set(key: BoolKey, value: Boolean) = write(key, value)
    fun set(key: IntKey, value: Int) = write(key, value)
    fun set(key: TextKey, value: String) = write(key, value)

    private fun write(key: Key<*>, value: Any) {
        store(key, value)
        _state.value = SettingsState(HashMap(values))
        if (key.synced) onSyncedChange?.invoke(syncedValues())
    }

    private fun store(key: Key<*>, value: Any) {
        values[key.name] = value
        val stored = "s_" + key.name
        val e = sp.edit()
        when (value) {
            is Boolean -> e.putBoolean(stored, value)
            is Int -> e.putInt(stored, value)
            is String -> e.putString(stored, value)
        }
        e.apply()
    }

    fun syncedValues(): Map<String, Any> {
        val current = _state.value
        val out = HashMap<String, Any>()
        for (key in Keys.all) {
            if (!key.synced) continue
            out[key.name] = when (key) {
                is BoolKey -> current[key]
                is IntKey -> current[key]
                is TextKey -> current[key]
            }
        }
        return out
    }

    /** Applies settings that arrived from the cloud. Device-only settings and bad values are ignored. */
    fun applyCloud(cloud: Map<String, Any?>) {
        var changed = false
        for (key in Keys.all) {
            if (!key.synced) continue
            val raw = cloud[key.name] ?: continue
            val value: Any = when (key) {
                is BoolKey -> raw as? Boolean ?: continue
                is IntKey -> (raw as? Number)?.toInt() ?: continue
                is TextKey -> raw as? String ?: continue
            }
            if (values[key.name] != value) {
                store(key, value)
                changed = true
            }
        }
        if (changed) _state.value = SettingsState(HashMap(values))
    }

    var lastListId: String?
        get() = sp.getString("lastListId", null)
        set(value) = sp.edit().putString("lastListId", value).apply()
}
