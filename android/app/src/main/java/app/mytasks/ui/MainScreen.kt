package app.mytasks.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import app.mytasks.core.Task
import app.mytasks.core.TaskList
import app.mytasks.core.isLive
import app.mytasks.data.Prefs
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
    data object NewTask : Dialog
    data class EditTask(val task: Task) : Dialog
    data class Split(val text: String, val lines: List<String>) : Dialog
    data object Sort : Dialog
    data object Settings : Dialog
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
    val ready by store.ready.collectAsStateWithLifecycle()
    val theme by prefs.theme.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val snackbar = remember { SnackbarHostState() }
    var listId by rememberSaveable { mutableStateOf(prefs.lastListId) }
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    val list = lists.firstOrNull { it.id == listId } ?: lists.firstOrNull()
    val subs = remember(allSubLists, list?.id) { allSubLists.filter { it.listId == list?.id } }
    val liveTasks = remember(allTasks) { allTasks.filter { it.isLive } }
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = colors.background, drawerContentColor = colors.text) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "My Tasks",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 20.dp, top = 20.dp, end = 20.dp),
                    )
                    Text(
                        account ?: "Sync is not set up in this build. Nothing is saved.",
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 20.dp, top = 2.dp, end = 20.dp, bottom = 12.dp),
                    )
                    lists.forEach { item ->
                        val info = Lists.headingInfo(liveTasks.filter { it.listId == item.id }, prefs.menuListInfo, now)
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
                    DrawerRow("New List", colors = colors) {
                        scope.launch { drawerState.close() }
                        dialog = Dialog.NewList
                    }
                    DrawerRow("New Filtered List", colors = colors) { later() }
                    DrawerRow("Reorder Task Lists", colors = colors) { later() }
                    DrawerRow("Edit List", colors = colors) {
                        scope.launch { drawerState.close() }
                        list?.let { dialog = Dialog.EditList(it) }
                    }
                    HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = 8.dp))
                    DrawerRow("Tags", colors = colors) { later() }
                    DrawerRow("Deleted Items", colors = colors) { later() }
                    HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = 8.dp))
                    DrawerRow("Help", colors = colors) { later() }
                    DrawerRow("Settings", colors = colors) {
                        scope.launch { drawerState.close() }
                        dialog = Dialog.Settings
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
                    title = {
                        Text(list?.title ?: "", fontWeight = FontWeight.Medium, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                                    putExtra(Intent.EXTRA_TEXT, store.shareText(list, sub, prefs.moveCompletedToBottom))
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
                                MenuEntry("Reorder Sub Lists", close) { later() }
                                MenuEntry("Move Sub List", close) { later() }
                                MenuEntry("Sort", close) { dialog = Dialog.Sort }
                                MenuEntry("Select Multiple…", close) { later() }
                                MenuEntry("Duplicate Sub List", close) { later() }
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
                        onClick = { dialog = Dialog.NewTask },
                        shape = CircleShape,
                        containerColor = colors.accent,
                        contentColor = Color.Black,
                    ) { Icon(Icons.Filled.Add, contentDescription = "Add task") }
                }
            },
            bottomBar = {
                if (prefs.showQuickAdd && sub != null) {
                    QuickAddBar(colors) { text ->
                        val lines = Lists.splitQuickAdd(text)
                        when {
                            lines.isEmpty() -> Unit
                            lines.size > 1 -> dialog = Dialog.Split(text, lines)
                            else -> store.addTask(sub, lines[0], prefs.insertNewTasksAtTop)
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
                            Lists.headingTitle(s.title, liveTasks.filter { it.subListId == s.id }, prefs.subListHeading, now)
                        },
                        selected = pagerState.currentPage,
                        colors = colors,
                        onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
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
                                prefs = prefs,
                                store = store,
                                onDeleted = { id -> deleted("Task deleted", listOf(id)) },
                                onOpen = { dialog = Dialog.EditTask(it) },
                            )
                        }
                    }
                }
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        is Dialog.NewList -> ListDialog("New List", null, onDismiss = { dialog = null }) { title, color ->
            openList(store.addList(title, color).id)
        }
        is Dialog.EditList -> ListDialog("Edit List", d.list, onDismiss = { dialog = null }, onDelete = { dialog = Dialog.DeleteList(d.list) }) { title, color ->
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
        is Dialog.NewSubList -> TextDialog("New Sub List", "", "Sub List Name", onDismiss = { dialog = null }) { title ->
            list?.let {
                store.addSubList(it.id, title)
                scope.launch { pagerState.animateScrollToPage(subs.size) }
            }
        }
        is Dialog.EditSubList -> TextDialog("Edit Sub List", d.sub.title, "Sub List Name", onDismiss = { dialog = null }) { title ->
            store.editSubList(d.sub, title)
        }
        is Dialog.NewTask -> TextDialog("New Task", "", "Add task", onDismiss = { dialog = null }) { title ->
            sub?.let { store.addTask(it, title, prefs.insertNewTasksAtTop) }
        }
        is Dialog.EditTask -> TextDialog("Edit Task", d.task.title, "Add task", onDismiss = { dialog = null }) { title ->
            store.editTaskTitle(d.task.id, title)
        }
        is Dialog.Split -> ConfirmDialog(
            title = "Split into multiple tasks?",
            text = "Line breaks have been detected, would you like to split the text into multiple tasks?",
            confirm = "YES",
            dismiss = "NO",
            onDismiss = { dialog = null },
            onDismissButton = { sub?.let { store.addTask(it, d.text, prefs.insertNewTasksAtTop) } },
        ) {
            sub?.let { store.addTasks(it, d.lines, prefs.insertNewTasksAtTop) }
        }
        is Dialog.Sort -> ChoiceDialog(
            title = "Sort",
            options = SORT_OPTIONS.map { it.second },
            selected = SORT_OPTIONS.indexOfFirst { it.first == (sub?.sortOrder ?: SortOrder.USER_DRAGGABLE) },
            onDismiss = { dialog = null },
        ) { index -> sub?.let { store.setSort(it, SORT_OPTIONS[index].first) } }
        is Dialog.Settings -> SettingsDialog(
            theme = theme,
            account = account,
            onTheme = { prefs.setTheme(it) },
            onSignOut = onSignOut,
            onDismiss = { dialog = null },
        )
    }
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
    colors: AppColors,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) colors.surface else Color.Transparent)
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.size(12.dp).background(dot, CircleShape))
            Spacer(Modifier.width(16.dp))
        }
        Text(title, color = colors.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
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
    prefs: Prefs,
    store: Store,
    onDeleted: (String) -> Unit,
    onOpen: (Task) -> Unit,
) {
    val colors = appColors()
    val visible = remember(tasks, sub.sortOrder, sub.hideCompleted) {
        Lists.visibleTasks(tasks, sub.sortOrder, prefs.moveCompletedToBottom, sub.hideCompleted)
    }
    // The order on screen follows the finger while dragging, then the stored order.
    var order by remember(visible) { mutableStateOf(visible) }
    val listState = rememberLazyListState()
    val canDrag = sub.sortOrder == SortOrder.USER_DRAGGABLE
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        order = order.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
        items(order, key = { it.id }) { task ->
            ReorderableItem(reorderState, key = task.id) { dragging ->
                val handle = if (canDrag) {
                    Modifier.draggableHandle(onDragStopped = { store.moveTask(task.id, order.map { it.id }) })
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
                    gesturesEnabled = prefs.swipeToDelete && !dragging,
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
                        colors = colors,
                        strikethrough = prefs.strikethroughCompleted,
                        dragging = dragging,
                        handle = handle,
                        onToggle = { store.toggleCompleted(task.id) },
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
    colors: AppColors,
    strikethrough: Boolean,
    dragging: Boolean,
    handle: Modifier?,
    onToggle: () -> Unit,
    onClick: () -> Unit,
) {
    Column(Modifier.background(if (dragging) colors.surface else colors.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onToggle, modifier = Modifier.padding(start = 4.dp)) {
                Icon(
                    imageVector = if (task.completed) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                    contentDescription = if (task.completed) "Mark as not done" else "Mark as done",
                    tint = colors.checkbox,
                )
            }
            Text(
                text = task.title,
                color = if (task.completed) colors.textSecondary else colors.text,
                fontSize = 17.sp,
                textDecoration = if (task.completed && strikethrough) TextDecoration.LineThrough else TextDecoration.None,
                modifier = Modifier.weight(1f).padding(vertical = 10.dp),
            )
            if (handle != null) {
                Box(handle.size(width = 44.dp, height = 56.dp), contentAlignment = Alignment.Center) {
                    DragHandle(colors.checkbox)
                }
            } else {
                Spacer(Modifier.width(12.dp))
            }
        }
        HorizontalDivider(Modifier.padding(start = 52.dp), thickness = 1.dp, color = colors.divider)
    }
}

/** Two columns of six dots. */
@Composable
private fun DragHandle(color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(6) {
            Row {
                Box(Modifier.size(3.dp).background(color, CircleShape))
                Spacer(Modifier.width(3.dp))
                Box(Modifier.size(3.dp).background(color, CircleShape))
            }
        }
    }
}

@Composable
private fun QuickAddBar(colors: AppColors, onAdd: (String) -> Unit) {
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
            modifier = Modifier.weight(1f).heightIn(min = 44.dp),
            shape = RoundedCornerShape(22.dp),
            color = Color.Transparent,
            border = BorderStroke(1.dp, colors.outline),
        ) {
            Box(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
                if (text.isEmpty()) Text("Quick add task…", color = colors.textSecondary, fontSize = 16.sp)
                // Multi line quick add is on by default: Enter adds a line, the arrow adds the task.
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = TextStyle(color = colors.text, fontSize = 16.sp),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    keyboardActions = KeyboardActions(onSend = { submit() }),
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        IconButton(onClick = { submit() }) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Add task", tint = colors.text)
        }
    }
}
