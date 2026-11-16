package app.mytasks.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mytasks.core.TaskList
import app.mytasks.data.BoolKey
import app.mytasks.data.IntKey
import app.mytasks.data.Keys
import app.mytasks.data.Prefs

private val HEADING_OPTIONS = listOf("None", "Completed / Total", "Percentage completed", "Overdue", "Total", "Uncompleted")
private val SIZE_OPTIONS = listOf("Comfortable", "Cozy", "Compact")
private val TOP_BOTTOM = listOf("Top", "Bottom")

private sealed interface SettingsDialogState {
    data class Choice(val key: IntKey, val title: String, val labels: List<String>, val values: List<Int>) : SettingsDialogState
    data class Number(val key: IntKey, val title: String, val min: Int, val max: Int, val unit: String) : SettingsDialogState
    data object DefaultList : SettingsDialogState
}

/** The Settings screen: same groups, order and options as the reference app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefs: Prefs,
    lists: List<TaskList>,
    account: String?,
    onSignOut: (() -> Unit)?,
    onClose: () -> Unit,
) {
    val colors = appColors()
    val context = LocalContext.current
    val st by prefs.state.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<SettingsDialogState?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    val later = { note = "This option arrives in a later build." }
    val version = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    @Composable
    fun check(key: BoolKey, title: String, summary: String? = null, enabled: Boolean = true) {
        SettingRow(title, summary, enabled, onClick = { prefs.set(key, !st[key]) }) {
            Checkbox(
                checked = st[key],
                onCheckedChange = { prefs.set(key, it) },
                enabled = enabled,
                colors = CheckboxDefaults.colors(checkedColor = colors.accent, checkmarkColor = Color.Black, uncheckedColor = colors.checkbox),
            )
        }
    }

    @Composable
    fun choice(key: IntKey, title: String, labels: List<String>, values: List<Int> = labels.indices.toList()) {
        val current = labels.getOrNull(values.indexOf(st[key])) ?: ""
        SettingRow(title, current, onClick = { dialog = SettingsDialogState.Choice(key, title, labels, values) })
    }

    @Composable
    fun number(key: IntKey, title: String, min: Int, max: Int, unit: String) {
        SettingRow(title, "${st[key]} $unit", onClick = { dialog = SettingsDialogState.Number(key, title, min, max, unit) })
    }

    BackHandler { onClose() }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.Medium) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.text,
                    navigationIconContentColor = colors.text,
                ),
            )
        },
    ) { inner ->
        Column(Modifier.padding(inner).fillMaxSize().verticalScroll(rememberScrollState())) {
            Category("Sync")
            SettingRow("Account", account ?: "Not signed in")
            check(Keys.syncOnClose, "Sync when closing My Tasks", "Push any waiting changes when you leave the app")
            check(Keys.syncWifiOnly, "Sync on WiFi only", "Background sync waits for WiFi. Live sync while the app is open is not affected")
            SettingRow("Last sync", "Changes sync live while the app is open")
            if (onSignOut != null) SettingRow("Sign out", "Your tasks stay in the cloud", onClick = onSignOut)
            SettingRow("Delete account", "Permanently deletes your account and all data in the cloud", onClick = later)

            Category("Application Settings")
            SettingRow("I'm having trouble with reminders?", "Let My Tasks run in the background so notifications and alarms arrive", onClick = {
                try {
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                } catch (_: Exception) {
                    note = "Open Settings > Battery on your phone and allow My Tasks to run in the background."
                }
            })
            choice(Keys.theme, "Theme", listOf("Light", "Dark", "Black", "Follow system (light / dark)", "Follow system (light / black)"))
            check(Keys.showCalendar, "Show calendar", "Show the calendar list. It has fewer functions than a normal list")
            check(Keys.swipeToDelete, "Swipe to delete enabled", "Swipe a task sideways to delete it")
            check(Keys.showQuickAdd, "Show quick add", "Show quick add at the bottom of the task list. Not shown on filtered lists")
            check(Keys.multiLineQuickAdd, "Multi line quick add", "On: type several lines. Off: the enter key adds the task straight away", enabled = st[Keys.showQuickAdd])
            check(Keys.keepScreenOn, "Keep screen on", "Stop the screen turning off while a list is open")
            check(Keys.vibrateUi, "Vibrate", "Touch feedback")
            choice(Keys.fontSize, "Font size", listOf("Extra small", "Small", "Normal", "Large", "Extra Large"))
            SettingRow(
                "Default list when opening app",
                lists.firstOrNull { it.id == st[Keys.defaultList] }?.title ?: "Continue where you left off",
                onClick = { dialog = SettingsDialogState.DefaultList },
            )
            number(Keys.removeDeletedItemsDays, "Remove deleted items older than", 1, 30, "days")
            check(Keys.showMenuOnBack, "Show main menu when back pressed", "The back button opens the main menu instead of leaving the app")
            check(Keys.showMenuOnOpen, "Show main menu when opening My Tasks", "Start with the main menu open")

            Category("List Settings")
            check(Keys.moveCompletedToBottom, "Move completed tasks", "Move completed tasks to the bottom of the list")
            choice(Keys.displayMode, "Display mode", SIZE_OPTIONS)
            number(Keys.taskPadding, "List item padding", 0, 100, "pixels")
            choice(Keys.tagSize, "Tag size", SIZE_OPTIONS)
            check(Keys.deleteListConfirmation, "Delete list confirmation", "Ask before deleting a task list")
            check(Keys.autoLink, "Auto link text", "Show web addresses, email addresses and phone numbers as links")
            SettingRow("Auto link text color", "Accent yellow", onClick = later)
            check(Keys.showListColor, "Show list colour", "Show the list colour on each task. Not used on filtered lists")
            choice(Keys.menuListInfo, "Menu list information", HEADING_OPTIONS)
            choice(Keys.subListHeading, "Sub list heading information", HEADING_OPTIONS)
            choice(Keys.insertNewTasks, "Where should new tasks be inserted?", TOP_BOTTOM)
            choice(Keys.insertNewSubTasks, "Where should new sub-tasks be inserted?", TOP_BOTTOM)
            choice(Keys.notesView, "How should Task notes be displayed?", listOf("All", "2 Lines", "1 Line", "None"))
            choice(Keys.reminderView, "How should the Task reminder be displayed?", listOf("Left align", "Right align", "Hide"))
            check(Keys.verboseDateTime, "Display date and time", "Always show the date and the time for each task")
            check(Keys.autoNumberTasks, "Auto number tasks", "Number each task in the list")
            check(Keys.autoNumberSubTasks, "Auto number sub tasks", "Number each sub-task")
            check(Keys.includeReminderAdvance, "Include reminder advance", "Show the reminder time, not the due time, in the list")

            Category("Task Settings")
            check(Keys.showCompleteDate, "Show complete date", "Show when a task was completed")
            check(Keys.strikethroughCompleted, "Strikethrough completed tasks", "Strike through the title and notes of completed tasks")
            check(Keys.subTasksCompleteTask, "Complete all sub-tasks completes task", "When every sub-task is done, the task is marked as done too")
            check(Keys.subTasksExpandDefault, "Sub tasks expanded", "Show sub-tasks in the list. Applies to new tasks only")
            check(Keys.enableReminders, "Enable reminder", "New tasks start with a reminder switched on")
            choice(Keys.defaultReminderType, "Default reminder type", listOf("Notification", "Alarm", "None"))
            choice(Keys.defaultPriority, "Default priority", listOf("High", "Medium", "Low", "None"), listOf(3, 2, 1, 0))
            check(Keys.saveRecurringHistory, "Save recurring task history?", "Keep a completed copy each time a repeating task is ticked")

            Category("Task Detail Screen")
            check(Keys.detailNotes, "Notes", "Show/hide the notes section")
            check(Keys.detailAttachment, "Attachment", "Show/hide the attachment section")
            check(Keys.detailSubTasks, "Sub Tasks", "Show/hide the sub-tasks section")
            check(Keys.detailTags, "Tags", "Show/hide the tags section")
            check(Keys.detailPriority, "Priority", "Show/hide the priority section")
            check(Keys.detailHighlight, "Highlight", "Show/hide the highlight section")
            check(Keys.detailCreated, "Created", "Show/hide the created date section")

            Category("Notification/Alarm Settings")
            check(Keys.autoIncrementReminder, "Automatically increment reminder", "On: a repeating reminder moves on when its notification arrives. Off: it moves on when the task is ticked")
            check(Keys.addFromNotification, "Add tasks from notification bar", "Keep a notification that adds tasks without opening the app")
            SettingRow("Default sub list", "First sub list of the default list", onClick = later)
            check(Keys.completeClearsNotification, "Complete task clears notification", "Ticking a task removes its notification")
            check(Keys.persistentNotifications, "Persistent notifications", "Make notifications harder to remove")
            check(Keys.notificationVibrate, "Vibrate")
            check(Keys.notificationLed, "LED")
            SettingRow("Notification sound", "Phone default", onClick = later)
            SettingRow("Alarm sound", "Phone default", onClick = later)
            choice(
                Keys.alarmDuration, "Alarm duration",
                listOf("Forever", "15 seconds", "30 seconds", "1 minute", "5 minutes", "10 minutes"),
                listOf(0, 4, 5, 1, 2, 3),
            )
            number(Keys.customSnoozeMinutes, "Custom snooze duration", 0, 10080, "minutes")
            check(Keys.muteAlarmOnCall, "Mute alarm when on phone call", "Show a notification instead of sounding the alarm")
            SettingRow("Allow full-screen alarms", "Let alarms open over the lock screen", onClick = {
                try {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, android.net.Uri.parse("package:" + context.packageName)),
                    )
                } catch (_: Exception) {
                    note = "Open your phone's Settings > Apps > My Tasks and allow full-screen notifications."
                }
            })

            Category("Backup/Restore")
            SettingRow("Backup", "Save your data to a file in Download", onClick = later)
            SettingRow("Restore", "Load data from a backup file", onClick = later)
            SettingRow("Export to CSV file", "Save your data as a spreadsheet file in Download", onClick = later)

            Category("About")
            SettingRow("Build version", version)
            Spacer(Modifier.height(32.dp))
        }
    }

    when (val d = dialog) {
        null -> Unit
        is SettingsDialogState.Choice -> ChoiceDialog(d.title, d.labels, d.values.indexOf(st[d.key]), onDismiss = { dialog = null }) { index ->
            prefs.set(d.key, d.values[index])
        }
        is SettingsDialogState.Number -> NumberDialog(d.title, st[d.key], d.min, d.max, d.unit, onDismiss = { dialog = null }) { prefs.set(d.key, it) }
        is SettingsDialogState.DefaultList -> ChoiceDialog(
            "Default list when opening app",
            listOf("Continue where you left off") + lists.map { it.title },
            lists.indexOfFirst { it.id == st[Keys.defaultList] } + 1,
            onDismiss = { dialog = null },
        ) { index -> prefs.set(Keys.defaultList, if (index == 0) "" else lists[index - 1].id) }
    }

    note?.let { text ->
        AlertDialog(
            onDismissRequest = { note = null },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { note = null }) { Text("OK", color = colors.accent) } },
        )
    }
}

@Composable
private fun Category(title: String) {
    Text(
        title,
        color = appColors().accent,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingRow(
    title: String,
    summary: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = appColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 16.dp, end = if (trailing != null) 4.dp else 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) colors.text else colors.textSecondary, fontSize = 16.sp)
            if (!summary.isNullOrEmpty()) Text(summary, color = colors.textSecondary, fontSize = 14.sp)
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/** A whole number between [min] and [max]. */
@Composable
private fun NumberDialog(title: String, value: Int, min: Int, max: Int, unit: String, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    val colors = appColors()
    var text by remember { mutableStateOf(value.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() }.take(5) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.width(120.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text("$unit ($min to $max)")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave((text.toIntOrNull() ?: value).coerceIn(min, max))
                onDismiss()
            }) { Text("SAVE", color = colors.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL", color = colors.accent) } },
    )
}
