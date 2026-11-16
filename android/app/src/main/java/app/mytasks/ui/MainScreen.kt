package app.mytasks.ui

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import app.mytasks.reminders.Notifications
import app.mytasks.reminders.ReminderScheduler
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mytasks.core.Lists
import app.mytasks.core.SortOrder
import app.mytasks.core.SubList
import app.mytasks.core.SubTask
import app.mytasks.core.PRIORITY_COLORS
import app.mytasks.core.Priority
import app.mytasks.core.ReminderMode
import app.mytasks.core.Task
import app.mytasks.core.TaskList
import app.mytasks.core.isLive
import app.mytasks.data.Keys
import app.mytasks.data.Prefs
import app.mytasks.data.SettingsState
import app.mytasks.core.Schedule
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardCapitalization
import app.mytasks.data.Store
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** Dialogs the main screen can show. */
private sealed interface Dialog {
    data object NewList : Dialog
    data class EditList(val list: TaskList) : Dialog
    data class DeleteList(val list: TaskList) : Dialog
    data object NewSubList : Dialog
    data class EditSubList(val sub: SubList) : Dialog
    data object ReorderLists : Dialog
    data object ReorderSubLists : Dialog
    data class MoveSubList(val sub: SubList) : Dialog
    data class Split(val text: String, val lines: List<String>) : Dialog
    data object Sort : Dialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(store: Store, prefs: Prefs, account: String?, onSignOut: (() -> Unit)?) {
    val colors = appColors()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lists by store.lists.collectAsStateWithLifecycle()
    val allSubLists by store.subLists.collectAsStateWithLifecycle()
    val allTasks by store.tasks.collectAsStateWithLifecycle()
    val allSubTasks by store.subTasks.collectAsStateWithLifecycle()
    val ready by store.ready.collectAsStateWithLifecycle()
    val st by prefs.state.collectAsStateWithLifecycle()
    val atTop = st[Keys.insertNewTasks] == 0

    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val drawerState = rememberDrawerState(if (st[Keys.showMenuOnOpen]) DrawerValue.Open else DrawerValue.Closed)
    val snackbar = remember { SnackbarHostState() }
    var listId by rememberSaveable { mutableStateOf(st[Keys.defaultList].ifEmpty { prefs.lastListId }) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    // The task open in the task screen, and whether it is a new, unsaved one.
    var editing by remember { mutableStateOf<Pair<Task, Boolean>?>(null) }

    val list = lists.firstOrNull { it.id == listId } ?: lists.firstOrNull()
    val subs = remember(allSubLists, list?.id) { allSubLists.filter { it.listId == list?.id } }
    val liveTasks = remember(allTasks) { allTasks.filter { it.isLive } }
    val subTasksByTask = remember(allSubTasks) { allSubTasks.groupBy { it.taskId } }
    val now = System.currentTimeMillis()

    val pagerState = rememberPagerState(pageCount = { subs.size })
    LaunchedEffect(list?.id) { pagerState.scrollToPage(0) }
    val sub = subs.getOrNull(pagerState.currentPage.coerceIn(0, maxOf(subs.lastIndex, 0)))

    fun message(text: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text, duration = SnackbarDuration.Short)
        }
    }

    fun later() = message("Coming in a later build")

    fun deleted(text: String, ids: List<String>) {
        if (ids.isEmpty()) return
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(text, actionLabel = "UNDO", duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) ids.forEach { store.restoreTask(it) }
        }
    }

    fun openList(id: String) {
        listId = id
        prefs.lastListId = id
        scope.launch { drawerState.close() }
    }

    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
    // "Show main menu when back pressed"
    BackHandler(enabled = drawerState.isClosed && st[Keys.showMenuOnBack] && editing == null && !settingsOpen) {
        scope.launch { drawerState.open() }
    }

    /** A new task with the defaults from Task Settings. */
    fun newDraft(target: SubList): Task {
        var draft = store.draftTask(target).copy(
            priority = st[Keys.defaultPriority],
            reminderType = st[Keys.defaultReminderType],
            subTasksExpanded = st[Keys.subTasksExpandDefault],
        )
        if (st[Keys.enableReminders]) {
            val due = java.util.Calendar.getInstance().apply {
                add(java.util.Calendar.HOUR_OF_DAY, 1)
                set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            draft = draft.copy(dueAt = due, reminderMode = ReminderMode.WHEN_DUE)
        }
        return draft
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            // Same width rule as the reference app's menu: screen width minus 56dp, at most 280dp.
            val drawerWidth = minOf(LocalConfiguration.current.screenWidthDp - 56, 280).dp
            ModalDrawerSheet(
                modifier = Modifier.width(drawerWidth),
                drawerContainerColor = colors.background,
                drawerContentColor = colors.text,
                drawerShape = RectangleShape,
            ) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                        Text("My Tasks", fontSize = 18.sp)
                    }
                    lists.forEach { item ->
                        val info = Lists.headingInfo(liveTasks.filter { it.listId == item.id }, st[Keys.menuListInfo], now)
                        DrawerRow(
                            title = item.title,
                            trailing = info,
                            selected = item.id == list?.id,
                            dot = Color(item.color),
                            colors = colors,
                            onClick = { openList(item.id) },
                        )
                    }
                    HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = 8.dp))
                    DrawerRow("New List", icon = Icons.AutoMirrored.Filled.PlaylistAdd, colors = colors) {
                        scope.launch { drawerState.close() }
                        dialog = Dialog.NewList
                    }
                    DrawerRow("New Filtered List", icon = Icons.Filled.FilterList, colors = colors) { later() }
                    DrawerRow("Reorder Task Lists", icon = Icons.Filled.SwapVert, colors = colors) {
                        scope.launch { drawerState.close() }
                        dialog = Dialog.ReorderLists
                    }
                    DrawerRow("Edit List", icon = Icons.Filled.Edit, colors = colors) {
                        scope.launch { drawerState.close() }
                        list?.let { dialog = Dialog.EditList(it) }
                    }
                    HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = 8.dp))
                    DrawerRow("Tags", icon = Icons.AutoMirrored.Filled.Label, colors = colors) { later() }
                    DrawerRow("Deleted Items", icon = Icons.Filled.Delete, colors = colors) { later() }
                    HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = 8.dp))
                    DrawerRow("Help", icon = Icons.AutoMirrored.Filled.HelpOutline, colors = colors) { later() }
                    DrawerRow("Settings", icon = Icons.Filled.Settings, colors = colors) {
                        scope.launch { drawerState.close() }
                        settingsOpen = true
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        },
    ) {
        Scaffold(
            containerColor = colors.background,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    expandedHeight = 56.dp,
                    title = {
                        // Tapping the list name opens "List Name", as in the reference app.
                        Text(
                            list?.title ?: "",
                            fontWeight = FontWeight.Medium,
                            fontSize = 20.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(start = 16.dp)
                                .clickable { list?.let { dialog = Dialog.EditList(it) } },
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        IconButton(onClick = { later() }) { Icon(Icons.Filled.Search, contentDescription = "Search") }
                        IconButton(onClick = {
                            if (list != null && sub != null) {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, store.shareText(list, sub, st[Keys.moveCompletedToBottom]))
                                }
                                context.startActivity(Intent.createChooser(send, "Share List"))
                            }
                        }) { Icon(Icons.Filled.Share, contentDescription = "Share List") }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                val close = { menuOpen = false }
                                MenuEntry("Edit Sub List", close) { sub?.let { dialog = Dialog.EditSubList(it) } }
                                MenuEntry("Reorder Sub Lists", close) { dialog = Dialog.ReorderSubLists }
                                MenuEntry("Move Sub List", close) {
                                    sub?.let { dialog = Dialog.MoveSubList(it) }
                                }
                                MenuEntry("Sort", close) { dialog = Dialog.Sort }
                                MenuEntry("Select Multiple…", close) { later() }
                                MenuEntry("Duplicate Sub List", close) {
                                    sub?.let {
                                        store.duplicateSubList(it)
                                        scope.launch { pagerState.animateScrollToPage(subs.size) }
                                    }
                                }
                                MenuEntry("Uncheck Completed", close) { sub?.let { store.uncheckCompleted(it) } }
                                MenuEntry("Complete All", close) { sub?.let { store.completeAll(it) } }
                                MenuEntry(if (sub?.hideCompleted == true) "Show Completed" else "Hide Completed", close) {
                                    sub?.let { store.setHideCompleted(it, !it.hideCompleted) }
                                }
                                HorizontalDivider(color = colors.divider)
                                MenuEntry("Remove Completed", close) { sub?.let { deleted("Tasks deleted", store.removeCompleted(it)) } }
                                MenuEntry("Delete Sub List", close) {
                                    sub?.let { if (!store.deleteSubList(it)) message("A task list must have at least one sub list.") }
                                }
                                MenuEntry("Delete All", close) { sub?.let { deleted("Tasks deleted", store.deleteAll(it)) } }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colors.background,
                        titleContentColor = colors.text,
                        navigationIconContentColor = colors.text,
                        actionIconContentColor = colors.text,
                    ),
                )
            },
            floatingActionButton = {
                if (sub != null) {
                    FloatingActionButton(
                        onClick = { editing = newDraft(sub) to true },
                        shape = CircleShape,
                        containerColor = colors.accent,
                        contentColor = Color.Black,
                    ) { Icon(Icons.Filled.Add, contentDescription = "Add task") }
                }
            },
            bottomBar = {
                if (st[Keys.showQuickAdd] && sub != null) {
                    QuickAddBar(colors, multiLine = st[Keys.multiLineQuickAdd]) { text ->
                        val lines = Lists.splitQuickAdd(text)
                        when {
                            lines.isEmpty() -> Unit
                            lines.size > 1 -> dialog = Dialog.Split(text, lines)
                            else -> store.addTask(sub, lines[0], atTop)
                        }
                    }
                }
            },
        ) { inner ->
            Column(Modifier.padding(inner).fillMaxSize()) {
                if (list == null || subs.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (!ready) CircularProgressIndicator(color = colors.accent)
                    }
                } else {
                    SubListTabs(
                        titles = subs.map { s ->
                            Lists.headingTitle(s.title, liveTasks.filter { it.subListId == s.id }, st[Keys.subListHeading], now)
                        },
                        selected = pagerState.currentPage,
                        colors = colors,
                        // Tapping the open tab edits the sub-list; tapping another tab switches to it.
                        onSelect = { index ->
                            if (index == pagerState.currentPage) {
                                subs.getOrNull(index)?.let { dialog = Dialog.EditSubList(it) }
                            } else {
                                scope.launch { pagerState.animateScrollToPage(index) }
                            }
                        },
                        onAddSubList = { dialog = Dialog.NewSubList },
                    )
                    // Like the reference app, sub-lists change by tapping a tab, so a sideways
                    // swipe on a task always means "delete".
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = false,
                        key = { subs.getOrNull(it)?.id ?: it },
                    ) { page ->
                        val pageSub = subs.getOrNull(page)
                        if (pageSub != null) {
                            TaskPage(
                                sub = pageSub,
                                tasks = liveTasks.filter { it.subListId == pageSub.id },
                                subTasks = subTasksByTask,
                                st = st,
                                store = store,
                                onDeleted = { id -> deleted("Task deleted", listOf(id)) },
                                onOpen = { editing = it to false },
                            )
                        }
                    }
                }
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        is Dialog.NewList -> ListDialog(null, onDismiss = { dialog = null }) { title, color ->
            openList(store.addList(title, color).id)
        }
        is Dialog.EditList -> ListDialog(d.list, onDismiss = { dialog = null }, onDelete = {
            if (st[Keys.deleteListConfirmation]) {
                dialog = Dialog.DeleteList(d.list)
            } else if (lists.size > 1) {
                dialog = null
                store.deleteList(d.list)
                message("Task list deleted")
            } else {
                dialog = null
                message("This is your only list.")
            }
        }) { title, color ->
            store.editList(d.list, title, color)
        }
        is Dialog.DeleteList -> ConfirmDialog(
            title = "Delete List",
            text = "Delete \"${d.list.title}\" and all of its tasks?",
            confirm = "DELETE",
            onDismiss = { dialog = null },
        ) {
            if (lists.size > 1) {
                store.deleteList(d.list)
                message("Task list deleted")
            } else {
                message("This is your only list.")
            }
        }
        is Dialog.NewSubList -> SubListDialog(null, subs, onDismiss = { dialog = null }) { title, target ->
            list?.let {
                val created = store.addSubList(it.id, title)
                if (target != null) store.editSubList(created, title, target)
                scope.launch { pagerState.animateScrollToPage(subs.size) }
            }
        }
        is Dialog.EditSubList -> SubListDialog(
            sub = d.sub,
            others = subs.filter { it.id != d.sub.id },
            onDismiss = { dialog = null },
            onDelete = {
                dialog = null
                if (!store.deleteSubList(d.sub)) message("A task list must have at least one sub list.")
            },
        ) { title, target -> store.editSubList(d.sub, title, target) }
        is Dialog.Split -> ConfirmDialog(
            title = "Split into multiple tasks?",
            text = "Line breaks have been detected, would you like to split the text into multiple tasks?",
            confirm = "YES",
            dismiss = "NO",
            onDismiss = { dialog = null },
            onDismissButton = { sub?.let { store.addTask(it, d.text, atTop) } },
        ) {
            sub?.let { store.addTasks(it, d.lines, atTop) }
        }
        is Dialog.Sort -> ChoiceDialog(
            title = "Sort",
            options = SORT_OPTIONS.map { it.second },
            selected = SORT_OPTIONS.indexOfFirst { it.first == (sub?.sortOrder ?: SortOrder.USER_DRAGGABLE) },
            onDismiss = { dialog = null },
        ) { index -> sub?.let { store.setSort(it, SORT_OPTIONS[index].first) } }
    }
    editing?.let { (draft, isNew) ->
        key(draft.id) {
            TaskScreen(
                initial = draft,
                initialSubTasks = if (isNew) emptyList() else subTasksByTask[draft.id].orEmpty(),
                isNew = isNew,
                settings = st,
                lists = lists,
                subLists = allSubLists,
                onSave = { task, subDrafts, addAnother ->
                    store.saveTask(task, isNew, atTop, subDrafts.map { Triple(it.id, it.title, it.completed) })
                    val fireAt = if (task.title.isBlank()) null else ReminderScheduler.fireTime(task, System.currentTimeMillis())
                    if (fireAt != null) {
                        if (!Notifications.canPost(context)) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        message("Reminder in: " + durationText(fireAt - System.currentTimeMillis()))
                    }
                    val target = allSubLists.firstOrNull { it.id == task.subListId }
                    editing = if (addAnother && target != null) newDraft(target) to true else null
                },
                onDelete = {
                    store.deleteTask(draft.id)
                    deleted("Task deleted", listOf(draft.id))
                    editing = null
                },
                onShare = { shareTask(context, it) },
                onClose = { editing = null },
            )
        }
    }
    if (settingsOpen) {
        SettingsScreen(prefs = prefs, lists = lists, account = account, onSignOut = onSignOut, onClose = { settingsOpen = false })
    }
}

/** "2 days 3 hours", "45 minutes". */
private fun durationText(ms: Long): String {
    val minutes = (ms + 59_999) / 60_000
    val d = minutes / 1440
    val h = (minutes % 1440) / 60
    val m = minutes % 60
    val parts = ArrayList<String>()
    if (d > 0) parts.add("$d " + if (d == 1L) "day" else "days")
    if (h > 0) parts.add("$h " + if (h == 1L) "hour" else "hours")
    if (m > 0 && d == 0L) parts.add("$m " + if (m == 1L) "minute" else "minutes")
    return if (parts.isEmpty()) "less than a minute" else parts.joinToString(" ")
}

/** Opens the system share sheet with a task's title and notes. */
private fun shareTask(context: android.content.Context, task: Task) {
    val text = if (task.notes.isBlank()) task.title else task.title + "\n" + task.notes
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Share task"))
}

@Composable
private fun MenuEntry(label: String, close: () -> Unit, action: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = {
            close()
            action()
        },
    )
}

/** Sort options in the reference app's order, with their stored codes. */
private val SORT_OPTIONS = listOf(
    SortOrder.USER_DRAGGABLE to "User draggable",
    SortOrder.PRIORITY to "Priority",
    SortOrder.ALPHA_AZ to "Alphabetical (A to Z)",
    SortOrder.ALPHA_ZA to "Alphabetical (Z to A)",
    SortOrder.CREATED_NEWEST to "Created (newest first)",
    SortOrder.CREATED_OLDEST to "Created (oldest first)",
    SortOrder.MODIFIED_NEWEST to "Modified (newest first)",
    SortOrder.MODIFIED_OLDEST to "Modified (oldest first)",
    SortOrder.DUE_NEWEST to "Due (newest first)",
    SortOrder.DUE_OLDEST to "Due (oldest first)",
    SortOrder.HIGHLIGHTED to "Highlighted",
    SortOrder.COMPLETED_NEWEST to "Completed (newest first)",
    SortOrder.COMPLETED_OLDEST to "Completed (oldest first)",
)

@Composable
private fun DrawerRow(
    title: String,
    trailing: String = "",
    selected: Boolean = false,
    dot: Color? = null,
    icon: ImageVector? = null,
    colors: AppColors,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) colors.surface else Color.Transparent)
            .clickable(onClick = onClick)
            .height(48.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            dot != null -> {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { Box(Modifier.size(20.dp).background(dot, CircleShape)) }
                Spacer(Modifier.width(32.dp))
            }
            icon != null -> {
                Icon(icon, contentDescription = null, tint = colors.checkbox, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(32.dp))
            }
        }
        Text(title, color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (trailing.isNotEmpty()) Text(trailing, color = colors.textSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun SubListTabs(
    titles: List<String>,
    selected: Int,
    colors: AppColors,
    onSelect: (Int) -> Unit,
    onAddSubList: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
            titles.forEachIndexed { index, title ->
                val isSelected = index == selected
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .clickable { onSelect(index) }
                        .drawBehind {
                            if (isSelected) {
                                val stroke = 2.dp.toPx()
                                drawLine(
                                    color = colors.accent,
                                    start = Offset(0f, size.height - stroke / 2),
                                    end = Offset(size.width, size.height - stroke / 2),
                                    strokeWidth = stroke,
                                )
                            }
                        }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = title.uppercase(),
                        color = if (isSelected) colors.text else colors.textSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
        IconButton(onClick = onAddSubList) {
            Icon(Icons.Filled.AddCircle, contentDescription = "New Sub List", tint = colors.text)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskPage(
    sub: SubList,
    tasks: List<Task>,
    subTasks: Map<String, List<SubTask>>,
    st: SettingsState,
    store: Store,
    onDeleted: (String) -> Unit,
    onOpen: (Task) -> Unit,
) {
    val colors = appColors()
    val moveCompleted = st[Keys.moveCompletedToBottom]
    val visible = remember(tasks, sub.sortOrder, sub.hideCompleted, moveCompleted) {
        Lists.visibleTasks(tasks, sub.sortOrder, moveCompleted, sub.hideCompleted)
    }
    val haptics = LocalHapticFeedback.current
    val buzz = { if (st[Keys.vibrateUi]) haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
    // The order on screen follows the finger while dragging, then the stored order.
    var order by remember(visible) { mutableStateOf(visible) }
    val listState = rememberLazyListState()
    val canDrag = sub.sortOrder == SortOrder.USER_DRAGGABLE
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        order = order.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
        itemsIndexed(order, key = { _, it -> it.id }) { index, task ->
            ReorderableItem(reorderState, key = task.id) { dragging ->
                val handle = if (canDrag) {
                    Modifier.draggableHandle(onDragStarted = { buzz() }, onDragStopped = { store.moveTask(task.id, order.map { it.id }) })
                } else {
                    null
                }
                val onDelete by rememberUpdatedState {
                    store.deleteTask(task.id)
                    onDeleted(task.id)
                }
                val swipe = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        if (value == SwipeToDismissBoxValue.Settled) {
                            false
                        } else {
                            onDelete()
                            true
                        }
                    },
                )
                SwipeToDismissBox(
                    state = swipe,
                    gesturesEnabled = st[Keys.swipeToDelete] && !dragging,
                    backgroundContent = {
                        Row(
                            Modifier.fillMaxSize().background(colors.surface).padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null, tint = colors.textSecondary)
                            Icon(Icons.Filled.Delete, contentDescription = null, tint = colors.textSecondary)
                        }
                    },
                ) {
                    TaskRow(
                        task = task,
                        subTasks = subTasks[task.id].orEmpty(),
                        colors = colors,
                        st = st,
                        number = if (st[Keys.autoNumberTasks]) index + 1 else null,
                        dragging = dragging,
                        handle = handle,
                        onToggle = {
                            buzz()
                            store.toggleCompleted(task.id, keepHistory = st[Keys.saveRecurringHistory])
                        },
                        onToggleSubTask = { store.toggleSubTask(it, completeParent = st[Keys.subTasksCompleteTask]) },
                        onExpand = { store.setExpanded(task.id, !task.subTasksExpanded) },
                        onClick = { onOpen(task) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: Task,
    subTasks: List<SubTask>,
    colors: AppColors,
    st: SettingsState,
    number: Int?,
    dragging: Boolean,
    handle: Modifier?,
    onToggle: () -> Unit,
    onToggleSubTask: (String) -> Unit,
    onExpand: () -> Unit,
    onClick: () -> Unit,
) {
    val now = System.currentTimeMillis()
    val priorityColor = PRIORITY_COLORS[task.priority]?.let { Color(it) }
    val strikethrough = st[Keys.strikethroughCompleted]
    // Display mode: Comfortable, Cozy, Compact. "List item padding" is in pixels.
    val minHeight = listOf(64.dp, 56.dp, 48.dp).getOrElse(st[Keys.displayMode]) { 64.dp }
    val extraPadding = with(LocalDensity.current) { st[Keys.taskPadding].toDp() }
    val notesLines = listOf(Int.MAX_VALUE, 2, 1, 0).getOrElse(st[Keys.notesView]) { Int.MAX_VALUE }
    Column(Modifier.background(if (dragging) colors.surface else colors.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Highlight strip down the left edge.
            Box(Modifier.width(4.dp).fillMaxHeight().background(if (task.highlight) Color(task.highlightColor) else Color.Transparent))
            IconButton(onClick = onToggle, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = if (task.completed) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                    contentDescription = if (task.completed) "Mark as not done" else "Mark as done",
                    tint = priorityColor ?: colors.checkbox,
                )
            }
            Column(Modifier.weight(1f).heightIn(min = minHeight).padding(vertical = extraPadding / 2), verticalArrangement = Arrangement.Center) {
                Text(
                    text = if (number != null) "$number. ${task.title}" else task.title,
                    color = if (task.completed) colors.textSecondary else colors.text,
                    fontSize = 18.sp,
                    textDecoration = if (task.completed && strikethrough) TextDecoration.LineThrough else TextDecoration.None,
                )
                val due = task.dueAt
                if (due != null && st[Keys.reminderView] != 2) {
                    val overdue = Lists.isOverdue(task, now)
                    val shown = if (st[Keys.includeReminderAdvance]) Schedule.reminderAt(task) ?: due else due
                    val text = if (task.allDay) formatDate(shown) else "${formatDate(shown)} ${formatTime(shown)}"
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = if (st[Keys.reminderView] == 1) Arrangement.End else Arrangement.Start,
                    ) {
                        if (task.reminderMode != ReminderMode.DONT_REMIND) {
                            Icon(
                                Icons.Filled.Notifications,
                                contentDescription = "Reminder on",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(16.dp).padding(end = 2.dp),
                            )
                        }
                        Text(text, color = if (overdue) Color(0xFFEF5350) else colors.textSecondary, fontSize = 13.sp)
                        if (task.repeat != 0) {
                            Icon(
                                Icons.Filled.Repeat,
                                contentDescription = "Repeats",
                                tint = colors.textSecondary,
                                modifier = Modifier.padding(start = 4.dp).size(14.dp),
                            )
                        }
                    }
                }
                if (task.notes.isNotBlank() && notesLines > 0) {
                    Text(
                        task.notes,
                        color = colors.textSecondary,
                        fontSize = 14.sp,
                        maxLines = notesLines,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = if (task.completed && strikethrough) TextDecoration.LineThrough else TextDecoration.None,
                    )
                }
                val completedAt = task.completedAt
                if (task.completed && completedAt != null && st[Keys.showCompleteDate]) {
                    Text("Completed: ${formatDate(completedAt)} ${formatTime(completedAt)}", color = colors.textSecondary, fontSize = 12.sp)
                }
            }
            if (subTasks.isNotEmpty()) {
                Row(Modifier.clickable(onClick = onExpand).padding(horizontal = 6.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${subTasks.count { it.completed }}/${subTasks.size}", color = colors.textSecondary, fontSize = 12.sp)
                    Icon(
                        if (task.subTasksExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (task.subTasksExpanded) "Hide sub tasks" else "Show sub tasks",
                        tint = colors.textSecondary,
                    )
                }
            }
            if (handle != null) {
                Box(handle.width(44.dp).fillMaxHeight().padding(end = 8.dp), contentAlignment = Alignment.Center) {
                    DragHandle(colors.checkbox)
                }
            } else {
                Spacer(Modifier.width(12.dp))
            }
        }
        if (task.subTasksExpanded) {
            subTasks.forEachIndexed { subIndex, sub ->
                Row(
                    Modifier.fillMaxWidth().clickable { onToggleSubTask(sub.id) }.padding(start = 52.dp, end = 12.dp).heightIn(min = 36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (sub.completed) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                        contentDescription = if (sub.completed) "Mark as not done" else "Mark as done",
                        tint = colors.checkbox,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (st[Keys.autoNumberSubTasks]) "${subIndex + 1}. ${sub.title}" else sub.title,
                        color = if (sub.completed) colors.textSecondary else colors.text,
                        fontSize = 14.sp,
                        textDecoration = if (sub.completed && strikethrough) TextDecoration.LineThrough else TextDecoration.None,
                    )
                }
            }
        }
        HorizontalDivider(Modifier.padding(start = 48.dp, end = 12.dp), thickness = 0.5.dp, color = colors.divider)
    }
}

/** Two columns of six dots. */
@Composable
private fun DragHandle(color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(6) {
            Row {
                Box(Modifier.size(4.dp).background(color, CircleShape))
                Spacer(Modifier.width(2.dp))
                Box(Modifier.size(4.dp).background(color, CircleShape))
            }
        }
    }
}

@Composable
private fun QuickAddBar(colors: AppColors, multiLine: Boolean, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    fun submit() {
        if (text.isNotBlank()) onAdd(text)
        text = ""
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .imePadding()
            .padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.Transparent,
            border = BorderStroke(1.dp, colors.outline),
        ) {
            Box(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
                if (text.isEmpty()) Text("Quick add task…", color = colors.textSecondary, fontSize = 18.sp)
                // Multi line quick add is on by default: Enter adds a line, the arrow adds the task.
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = TextStyle(color = colors.text, fontSize = 18.sp),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = if (multiLine) ImeAction.Default else ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    singleLine = !multiLine,
                    maxLines = if (multiLine) 3 else 1,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        IconButton(onClick = { submit() }) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Add task", tint = colors.text)
        }
    }
}
