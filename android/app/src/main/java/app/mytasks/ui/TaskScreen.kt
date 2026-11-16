package app.mytasks.ui

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mytasks.core.HIGHLIGHT_COLORS
import app.mytasks.core.Priority
import app.mytasks.core.ReminderMode
import app.mytasks.core.Repeat
import app.mytasks.core.RepeatUntil
import app.mytasks.core.SubList
import app.mytasks.core.SubTask
import app.mytasks.core.Task
import app.mytasks.core.TaskList
import app.mytasks.core.TimeUnit
import app.mytasks.data.Keys
import app.mytasks.data.SettingsState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** One sub-task row while the task screen is open. */
data class SubDraft(val id: String?, val title: String, val completed: Boolean)

private val UNIT_LABELS = listOf("minutes", "hours", "days", "weeks", "months", "years")
private val UNIT_CODES = listOf(TimeUnit.MINUTES, TimeUnit.HOURS, TimeUnit.DAYS, TimeUnit.WEEKS, TimeUnit.MONTHS, TimeUnit.YEARS)
private val REPEAT_LABELS = listOf("Does not repeat", "Daily", "Weekly", "Monthly", "Yearly", "Specific days of the week", "Advanced")
private val REMINDER_TYPE_LABELS = listOf("Notification", "Alarm", "None")
private val REPEAT_FROM_LABELS = listOf("Due date", "Completion date and time", "Completion date only")
private val DAY_LABELS = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

fun formatDate(t: Long): String = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(Date(t))

@Composable
fun formatTime(t: Long): String = DateFormat.getTimeFormat(LocalContext.current).format(Date(t))

private fun unitLabel(unit: Int) = UNIT_LABELS[UNIT_CODES.indexOf(unit).coerceAtLeast(0)]

fun reminderText(task: Task): String = when (task.reminderMode) {
    ReminderMode.WHEN_DUE -> "Remind me when due"
    ReminderMode.IN_ADVANCE -> "Remind me ${task.reminderAdvance} ${unitLabel(task.reminderAdvanceUnit)} in advance"
    else -> "Don't remind me"
}

fun repeatText(task: Task): String = when (task.repeat) {
    Repeat.DAYS_OF_WEEK -> {
        val days = DAY_LABELS.filterIndexed { i, _ -> task.repeatDaysOfWeek and (1 shl i) != 0 }.joinToString(", ") { it.take(3) }
        if (days.isEmpty()) REPEAT_LABELS[Repeat.DAYS_OF_WEEK] else days
    }
    Repeat.ADVANCED -> "Every ${task.repeatInterval} ${unitLabel(task.repeatUnit)}"
    else -> REPEAT_LABELS.getOrElse(task.repeat) { REPEAT_LABELS[0] }
}

private sealed interface Picker {
    data object Date : Picker
    data object Time : Picker
    data object Reminder : Picker
    data object Advance : Picker
    data object ReminderType : Picker
    data object RepeatKind : Picker
    data object RepeatDays : Picker
    data object RepeatAdvanced : Picker
    data object RepeatFrom : Picker
    data object RepeatUntilChoice : Picker
    data object UntilDate : Picker
    data object HighlightColor : Picker
    data object MoveTo : Picker
    data object NewSubTask : Picker
    data class EditSubTask(val index: Int) : Picker
    data object Discard : Picker
}

/**
 * The task screen: every field of one task. Changes are kept in a draft and
 * stored when the tick button is pressed.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskScreen(
    initial: Task,
    initialSubTasks: List<SubTask>,
    isNew: Boolean,
    settings: SettingsState,
    lists: List<TaskList>,
    subLists: List<SubList>,
    onSave: (Task, List<SubDraft>, addAnother: Boolean) -> Unit,
    onDelete: () -> Unit,
    onShare: (Task) -> Unit,
    onClose: () -> Unit,
) {
    val colors = appColors()
    var task by remember { mutableStateOf(initial) }
    val subs = remember { mutableStateListOf<SubDraft>().apply { addAll(initialSubTasks.map { SubDraft(it.id, it.title, it.completed) }) } }
    val startSubs = remember { initialSubTasks.map { SubDraft(it.id, it.title, it.completed) } }
    var picker by remember { mutableStateOf<Picker?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    val changed = task != initial || subs.toList() != startSubs
    val is24h = DateFormat.is24HourFormat(LocalContext.current)

    fun close() {
        if (changed && task.title.isNotBlank()) picker = Picker.Discard else onClose()
    }

    /** Turns the due date on with a sensible default, or off together with what depends on it. */
    fun setDue(enabled: Boolean) {
        task = if (enabled) {
            val c = Calendar.getInstance().apply {
                add(Calendar.HOUR_OF_DAY, 1)
                set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            task.copy(dueAt = c.timeInMillis)
        } else {
            task.copy(dueAt = null, reminderMode = ReminderMode.DONT_REMIND, repeat = Repeat.NONE)
        }
    }

    fun withDate(utcMidnight: Long, base: Long): Long {
        val picked = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMidnight }
        return Calendar.getInstance().apply {
            timeInMillis = base
            set(picked.get(Calendar.YEAR), picked.get(Calendar.MONTH), picked.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }

    fun toUtcMidnight(local: Long): Long {
        val l = Calendar.getInstance().apply { timeInMillis = local }
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(l.get(Calendar.YEAR), l.get(Calendar.MONTH), l.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }

    BackHandler { close() }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            Column(Modifier.background(colors.surface)) {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = { close() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    },
                    actions = {
                        if (isNew) {
                            TextButton(onClick = { if (task.title.isNotBlank()) onSave(task, subs.toList(), true) }) {
                                Text("SAVE & ADD", color = colors.accent)
                            }
                        } else {
                            Box {
                                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More options") }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(text = { Text("Share task") }, onClick = { menuOpen = false; onShare(task) })
                                    DropdownMenuItem(text = { Text("Delete task") }, onClick = { menuOpen = false; onDelete() })
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colors.surface,
                        navigationIconContentColor = colors.text,
                        actionIconContentColor = colors.text,
                    ),
                )
                Box(Modifier.fillMaxWidth().padding(start = 56.dp, end = 16.dp, bottom = 28.dp)) {
                    if (task.title.isEmpty()) Text("Task Name", color = colors.textSecondary, fontSize = 20.sp)
                    BasicTextField(
                        value = task.title,
                        onValueChange = { task = task.copy(title = it) },
                        textStyle = TextStyle(color = colors.text, fontSize = 20.sp),
                        cursorBrush = SolidColor(colors.accent),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { if (task.title.isNotBlank()) onSave(task, subs.toList(), false) else onClose() },
                shape = CircleShape,
                containerColor = colors.accent,
                contentColor = Color.Black,
                modifier = Modifier.imePadding(),
            ) { Icon(Icons.Filled.Check, contentDescription = "Save") }
        },
    ) { inner ->
        Column(Modifier.padding(inner).fillMaxSize().verticalScroll(rememberScrollState()).imePadding()) {
            // Notes
            if (settings[Keys.detailNotes]) Section(Icons.AutoMirrored.Filled.Notes, "Notes") {
                Box(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    if (task.notes.isEmpty()) Text("Tap to add notes", color = colors.textSecondary, fontSize = 14.sp)
                    BasicTextField(
                        value = task.notes,
                        onValueChange = { task = task.copy(notes = it) },
                        textStyle = TextStyle(color = colors.text, fontSize = 14.sp),
                        cursorBrush = SolidColor(colors.accent),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Sub Tasks
            if (settings[Keys.detailSubTasks]) Section(
                icon = Icons.Filled.Check,
                label = "Sub Tasks",
                trailing = {
                    IconButton(onClick = { picker = Picker.NewSubTask }) { Icon(Icons.Filled.Add, contentDescription = "Add sub task", tint = colors.checkbox) }
                },
            ) {
                subs.forEachIndexed { index, st ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = st.completed,
                            onCheckedChange = { subs[index] = st.copy(completed = it) },
                            colors = CheckboxDefaults.colors(checkedColor = colors.accent, checkmarkColor = Color.Black, uncheckedColor = colors.checkbox),
                        )
                        Text(
                            st.title,
                            color = if (st.completed) colors.textSecondary else colors.text,
                            fontSize = 15.sp,
                            textDecoration = if (st.completed) TextDecoration.LineThrough else TextDecoration.None,
                            modifier = Modifier.weight(1f).clickable { picker = Picker.EditSubTask(index) }.padding(vertical = 8.dp),
                        )
                        IconButton(onClick = { subs.removeAt(index) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove sub task", tint = colors.textSecondary)
                        }
                    }
                }
            }

            // List
            val subList = subLists.firstOrNull { it.id == task.subListId }
            val list = lists.firstOrNull { it.id == task.listId }
            Section(Icons.AutoMirrored.Filled.List, "List", onClick = { picker = Picker.MoveTo }) {
                Value(listOfNotNull(list?.title, subList?.title).joinToString(" / "))
            }

            // Tags
            if (settings[Keys.detailTags]) Section(Icons.Outlined.Label, "Tags") { Value("No tags selected", secondary = true) }

            // Due Date
            CheckSection(
                checked = task.dueAt != null,
                onChecked = { setDue(it) },
                label = "Due Date",
                trailing = {
                    Text("All Day", color = colors.textSecondary, fontSize = 13.sp)
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = task.allDay,
                        onCheckedChange = { on ->
                            if (task.dueAt == null) setDue(true)
                            task = task.copy(allDay = on)
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = Color.Black),
                    )
                },
            ) {
                val due = task.dueAt
                if (due == null) {
                    Value("No date set", secondary = true, onClick = { setDue(true); picker = Picker.Date })
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Value(formatDate(due), onClick = { picker = Picker.Date })
                        if (!task.allDay) {
                            Text("  @  ", color = colors.accent, fontSize = 15.sp)
                            Value(formatTime(due), onClick = { picker = Picker.Time })
                        }
                    }
                }
            }

            // Reminder
            CheckSection(
                checked = task.reminderMode != ReminderMode.DONT_REMIND,
                onChecked = { on ->
                    if (on && task.dueAt == null) setDue(true)
                    task = task.copy(reminderMode = if (on) ReminderMode.WHEN_DUE else ReminderMode.DONT_REMIND)
                },
                label = "Reminder",
                onClick = { picker = Picker.Reminder },
            ) { Value(reminderText(task)) }

            // Reminder Type
            Section(Icons.Filled.Schedule, "Reminder Type", onClick = { picker = Picker.ReminderType }) {
                Value(REMINDER_TYPE_LABELS.getOrElse(task.reminderType) { REMINDER_TYPE_LABELS[0] })
            }

            // Repeat
            CheckSection(
                checked = task.repeat != Repeat.NONE,
                onChecked = { on ->
                    if (on && task.dueAt == null) setDue(true)
                    task = task.copy(repeat = if (on) Repeat.DAILY else Repeat.NONE)
                },
                label = "Repeat",
                onClick = { picker = Picker.RepeatKind },
            ) { Value(repeatText(task)) }

            if (task.repeat != Repeat.NONE) {
                Section(Icons.Filled.Schedule, "Repeat From", onClick = { picker = Picker.RepeatFrom }) {
                    Value(REPEAT_FROM_LABELS.getOrElse(task.repeatFrom) { REPEAT_FROM_LABELS[0] })
                }
                Section(Icons.Filled.Schedule, "Repeat Until", onClick = { picker = Picker.RepeatUntilChoice }) {
                    val until = task.repeatUntilAt
                    Value(if (task.repeatUntil == RepeatUntil.UNTIL_DATE && until != null) formatDate(until) else "Forever")
                }
            }

            // Priority
            if (settings[Keys.detailPriority]) Section(Icons.Filled.Flag, "Priority") {
                // Four equal columns, so "High" never runs off a narrow screen.
                Row(Modifier.fillMaxWidth()) {
                    listOf(Priority.NONE to "None", Priority.LOW to "Low", Priority.MEDIUM to "Medium", Priority.HIGH to "High").forEach { (value, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f).clickable { task = task.copy(priority = value) },
                        ) {
                            RadioButton(
                                selected = task.priority == value,
                                onClick = { task = task.copy(priority = value) },
                                colors = RadioButtonDefaults.colors(selectedColor = colors.accent, unselectedColor = colors.textSecondary),
                                modifier = Modifier.size(32.dp),
                            )
                            Text(label, color = colors.text, fontSize = 13.sp, maxLines = 1)
                        }
                    }
                }
            }

            // Highlight
            if (settings[Keys.detailHighlight]) Section(
                icon = Icons.Filled.Star,
                label = "Highlight",
                onClick = { task = task.copy(highlight = !task.highlight) },
                trailing = {
                    Box(
                        Modifier
                            .padding(end = 12.dp)
                            .size(24.dp)
                            .background(if (task.highlight) Color(task.highlightColor) else Color.Transparent, CircleShape)
                            .border(1.5.dp, if (task.highlight) Color(task.highlightColor) else colors.checkbox, CircleShape)
                            .clickable { picker = Picker.HighlightColor },
                    )
                },
            ) { Value("Make this task stand out in the list view", secondary = true) }

            // Completed
            CheckSection(
                checked = task.completed,
                onChecked = { task = task.copy(completed = it, completedAt = if (it) System.currentTimeMillis() else null) },
                label = "Completed",
            ) {
                val at = task.completedAt
                if (task.completed && at != null) Value("${formatDate(at)} ${formatTime(at)}", secondary = true)
            }

            // Created
            if (!isNew && settings[Keys.detailCreated]) {
                Section(Icons.Filled.Schedule, "Created") { Value("${formatDate(task.createdAt)} ${formatTime(task.createdAt)}", secondary = true) }
            }
            Spacer(Modifier.height(96.dp))
        }
    }

    when (val p = picker) {
        null -> Unit
        Picker.Date -> DatePick(toUtcMidnight(task.dueAt ?: System.currentTimeMillis()), onDismiss = { picker = null }) {
            task = task.copy(dueAt = withDate(it, task.dueAt ?: System.currentTimeMillis()))
        }
        Picker.UntilDate -> DatePick(toUtcMidnight(task.repeatUntilAt ?: System.currentTimeMillis()), onDismiss = { picker = null }) {
            val end = Calendar.getInstance().apply {
                timeInMillis = withDate(it, System.currentTimeMillis())
                set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
            }
            task = task.copy(repeatUntil = RepeatUntil.UNTIL_DATE, repeatUntilAt = end.timeInMillis)
        }
        Picker.Time -> {
            val c = Calendar.getInstance().apply { timeInMillis = task.dueAt ?: System.currentTimeMillis() }
            val state = rememberTimePickerState(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), is24h)
            AlertDialog(
                onDismissRequest = { picker = null },
                text = { TimePicker(state = state) },
                confirmButton = {
                    TextButton(onClick = {
                        c.set(Calendar.HOUR_OF_DAY, state.hour); c.set(Calendar.MINUTE, state.minute)
                        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
                        task = task.copy(dueAt = c.timeInMillis)
                        picker = null
                    }) { Text("OK", color = colors.accent) }
                },
                dismissButton = { TextButton(onClick = { picker = null }) { Text("CANCEL", color = colors.accent) } },
            )
        }
        Picker.Reminder -> ChoiceDialog(
            "Reminder",
            listOf("Don't remind me", "Remind me when due", "Remind me in advance"),
            task.reminderMode,
            onDismiss = { picker = null },
        ) { mode ->
            if (mode != ReminderMode.DONT_REMIND && task.dueAt == null) setDue(true)
            task = task.copy(reminderMode = mode, reminderAdvance = if (mode == ReminderMode.IN_ADVANCE && task.reminderAdvance == 0) 10 else task.reminderAdvance)
            if (mode == ReminderMode.IN_ADVANCE) picker = Picker.Advance
        }
        Picker.Advance -> AmountDialog("Reminder Advance", task.reminderAdvance, task.reminderAdvanceUnit, onDismiss = { picker = null }) { n, unit ->
            task = task.copy(reminderAdvance = n, reminderAdvanceUnit = unit)
        }
        Picker.ReminderType -> ChoiceDialog("Reminder Type", REMINDER_TYPE_LABELS, task.reminderType, onDismiss = { picker = null }) {
            task = task.copy(reminderType = it)
        }
        Picker.RepeatKind -> ChoiceDialog("Repeat Frequency", REPEAT_LABELS, task.repeat, onDismiss = { picker = null }) { kind ->
            if (kind != Repeat.NONE && task.dueAt == null) setDue(true)
            task = task.copy(repeat = kind)
            if (kind == Repeat.DAYS_OF_WEEK) picker = Picker.RepeatDays
            if (kind == Repeat.ADVANCED) picker = Picker.RepeatAdvanced
        }
        Picker.RepeatDays -> AlertDialog(
            onDismissRequest = { picker = null },
            title = { Text("Specific days of the week") },
            text = {
                Column {
                    DAY_LABELS.forEachIndexed { i, day ->
                        val on = task.repeatDaysOfWeek and (1 shl i) != 0
                        Row(
                            Modifier.fillMaxWidth().clickable { task = task.copy(repeatDaysOfWeek = task.repeatDaysOfWeek xor (1 shl i)) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = on,
                                onCheckedChange = { task = task.copy(repeatDaysOfWeek = task.repeatDaysOfWeek xor (1 shl i)) },
                                colors = CheckboxDefaults.colors(checkedColor = colors.accent, checkmarkColor = Color.Black),
                            )
                            Text(day)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picker = null }) { Text("SAVE", color = colors.accent) } },
        )
        Picker.RepeatAdvanced -> AmountDialog("Advanced", task.repeatInterval, task.repeatUnit, prefix = "Every", onDismiss = { picker = null }) { n, unit ->
            task = task.copy(repeatInterval = maxOf(1, n), repeatUnit = unit)
        }
        Picker.RepeatFrom -> ChoiceDialog("Repeat From", REPEAT_FROM_LABELS, task.repeatFrom, onDismiss = { picker = null }) {
            task = task.copy(repeatFrom = it)
        }
        Picker.RepeatUntilChoice -> ChoiceDialog("Repeat Until", listOf("Forever", "Until date"), task.repeatUntil, onDismiss = { picker = null }) {
            if (it == RepeatUntil.UNTIL_DATE) {
                picker = Picker.UntilDate
            } else {
                task = task.copy(repeatUntil = RepeatUntil.FOREVER, repeatUntilAt = null)
            }
        }
        Picker.HighlightColor -> AlertDialog(
            onDismissRequest = { picker = null },
            title = { Text("Highlight") },
            text = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HIGHLIGHT_COLORS.forEach { c ->
                        Box(
                            Modifier.size(32.dp).background(Color(c), CircleShape).clickable {
                                task = task.copy(highlight = true, highlightColor = c)
                                picker = null
                            },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picker = null }) { Text("CANCEL", color = colors.accent) } },
        )
        Picker.MoveTo -> SubListChooser("List", lists, subLists, task.subListId, onDismiss = { picker = null }) { target ->
            task = task.copy(listId = target.listId, subListId = target.id)
        }
        Picker.NewSubTask -> TextDialog("Sub Task", "", "Sub Task", onDismiss = { picker = null }) {
            // "Where should new sub-tasks be inserted?": 0 = top, 1 = bottom.
            if (settings[Keys.insertNewSubTasks] == 0) subs.add(0, SubDraft(null, it, false)) else subs.add(SubDraft(null, it, false))
        }
        is Picker.EditSubTask -> TextDialog("Sub Task", subs.getOrNull(p.index)?.title ?: "", "Sub Task", onDismiss = { picker = null }) { title ->
            subs.getOrNull(p.index)?.let { subs[p.index] = it.copy(title = title) }
        }
        Picker.Discard -> ConfirmDialog(
            title = "Save changes?",
            text = "This task has changes that have not been saved.",
            confirm = "SAVE",
            dismiss = "DISCARD",
            onDismiss = { picker = null },
            onDismissButton = onClose,
        ) { onSave(task, subs.toList(), false) }
    }
}

@Composable
private fun Section(
    icon: ImageVector,
    label: String,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = appColors()
    SectionFrame(
        lead = { Icon(icon, contentDescription = null, tint = colors.checkbox, modifier = Modifier.padding(horizontal = 16.dp)) },
        label = label,
        onClick = onClick,
        trailing = trailing,
        content = content,
    )
}

@Composable
private fun CheckSection(
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    label: String,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = appColors()
    SectionFrame(
        lead = {
            Checkbox(
                checked = checked,
                onCheckedChange = onChecked,
                colors = CheckboxDefaults.colors(checkedColor = colors.accent, checkmarkColor = Color.Black, uncheckedColor = colors.checkbox),
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        },
        label = label,
        onClick = onClick,
        trailing = trailing,
        content = content,
    )
}

@Composable
private fun SectionFrame(
    lead: @Composable () -> Unit,
    label: String,
    onClick: (() -> Unit)?,
    trailing: (@Composable () -> Unit)?,
    content: @Composable () -> Unit,
) {
    val colors = appColors()
    Row(
        modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        lead()
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = colors.textSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                trailing?.invoke()
            }
            Box(Modifier.padding(top = 4.dp, bottom = 12.dp)) { content() }
            HorizontalDivider(thickness = 0.5.dp, color = colors.divider)
        }
    }
}

@Composable
private fun Value(text: String, secondary: Boolean = false, onClick: (() -> Unit)? = null) {
    val colors = appColors()
    Text(
        text,
        color = if (secondary) colors.textSecondary else colors.text,
        fontSize = 15.sp,
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePick(initialUtc: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val colors = appColors()
    val state = rememberDatePickerState(initialSelectedDateMillis = initialUtc)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let(onPick)
                onDismiss()
            }) { Text("OK", color = colors.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL", color = colors.accent) } },
    ) { DatePicker(state = state) }
}

/** A number and a unit: "10 minutes", "Every 2 weeks". */
@Composable
private fun AmountDialog(title: String, amount: Int, unit: Int, prefix: String? = null, onDismiss: () -> Unit, onSave: (Int, Int) -> Unit) {
    val colors = appColors()
    var text by remember { mutableStateOf(amount.toString()) }
    var selected by remember { mutableStateOf(unit) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (prefix != null) {
                        Text(prefix)
                        Spacer(Modifier.width(12.dp))
                    }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { v -> text = v.filter { it.isDigit() }.take(4) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.width(96.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                UNIT_LABELS.forEachIndexed { i, label ->
                    Row(
                        Modifier.fillMaxWidth().clickable { selected = UNIT_CODES[i] },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected == UNIT_CODES[i],
                            onClick = { selected = UNIT_CODES[i] },
                            colors = RadioButtonDefaults.colors(selectedColor = colors.accent, unselectedColor = colors.textSecondary),
                        )
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(text.toIntOrNull() ?: 0, selected)
                onDismiss()
            }) { Text("SAVE", color = colors.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL", color = colors.accent) } },
    )
}

/** Pick a sub-list from any task list. */
@Composable
fun SubListChooser(
    title: String,
    lists: List<TaskList>,
    subLists: List<SubList>,
    selectedSubListId: String?,
    onDismiss: () -> Unit,
    onPick: (SubList) -> Unit,
) {
    val colors = appColors()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                lists.forEach { list ->
                    Text(list.title, color = colors.accent, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                    subLists.filter { it.listId == list.id }.forEach { sub ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                onPick(sub)
                                onDismiss()
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = sub.id == selectedSubListId,
                                onClick = {
                                    onPick(sub)
                                    onDismiss()
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = colors.accent, unselectedColor = colors.textSecondary),
                            )
                            Text(sub.title, fontSize = 15.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("CANCEL", color = colors.accent) } },
    )
}
