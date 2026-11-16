package app.mytasks

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mytasks.ui.AppColors
import app.mytasks.ui.AppTheme
import app.mytasks.ui.ThemeChoice
import app.mytasks.ui.appColors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme(choice = ThemeChoice.Black) {
                MainScreen()
            }
        }
    }
}

/** Temporary in-memory rows so the first build shows the main screen. Replaced by synced data next. */
private data class DemoTask(val id: Int, val title: String, val completed: Boolean = false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val colors = appColors()
    val tasks = remember {
        mutableStateListOf(
            DemoTask(1, "Tut sheet 6"),
            DemoTask(2, "Triple integral lec"),
            DemoTask(3, "Triple Integral finish"),
        )
    }
    var nextId by remember { mutableIntStateOf(4) }
    val subListTitle = "Monday 16th Nov"
    val done = tasks.count { it.completed }

    fun add(title: String) {
        val clean = title.trim()
        if (clean.isNotEmpty()) {
            tasks.add(0, DemoTask(nextId, clean))
            nextId += 1
        }
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text("To do", fontWeight = FontWeight.Medium, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = {}) { Icon(Icons.Filled.Menu, contentDescription = "Menu") }
                },
                actions = {
                    IconButton(onClick = {}) { Icon(Icons.Filled.Search, contentDescription = "Search") }
                    IconButton(onClick = {}) { Icon(Icons.Filled.Share, contentDescription = "Share list") }
                    IconButton(onClick = {}) { Icon(Icons.Filled.MoreVert, contentDescription = "More options") }
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
            FloatingActionButton(
                onClick = {},
                shape = CircleShape,
                containerColor = colors.accent,
                contentColor = Color.Black,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "New task")
            }
        },
        bottomBar = { QuickAddBar(colors = colors, onAdd = ::add) },
    ) { inner ->
        Column(Modifier.padding(inner).fillMaxSize()) {
            SubListTabs(
                titles = listOf("$subListTitle ($done/${tasks.size})"),
                selected = 0,
                colors = colors,
                onSelect = {},
                onAddSubList = {},
            )
            LazyColumn(Modifier.fillMaxSize()) {
                items(tasks, key = { it.id }) { task ->
                    TaskRow(
                        title = task.title,
                        completed = task.completed,
                        colors = colors,
                        onToggle = {
                            val i = tasks.indexOfFirst { it.id == task.id }
                            if (i >= 0) tasks[i] = task.copy(completed = !task.completed)
                        },
                    )
                }
            }
        }
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
            Icon(Icons.Filled.AddCircle, contentDescription = "Add sub list", tint = colors.text)
        }
    }
}

@Composable
private fun TaskRow(title: String, completed: Boolean, colors: AppColors, onToggle: () -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onToggle, modifier = Modifier.padding(start = 4.dp)) {
                Icon(
                    imageVector = if (completed) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                    contentDescription = if (completed) "Mark as not done" else "Mark as done",
                    tint = colors.checkbox,
                )
            }
            Text(
                text = title,
                color = if (completed) colors.textSecondary else colors.text,
                fontSize = 17.sp,
                textDecoration = if (completed) TextDecoration.LineThrough else TextDecoration.None,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(vertical = 10.dp),
            )
            DragHandle(colors.checkbox)
        }
        HorizontalDivider(Modifier.padding(start = 52.dp), thickness = 1.dp, color = colors.divider)
    }
}

/** Two columns of six dots, like the reference app's drag handle. */
@Composable
private fun DragHandle(color: Color) {
    Column(Modifier.width(20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(6) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
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
        onAdd(text)
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
                if (text.isEmpty()) {
                    Text("Quick add task…", color = colors.textSecondary, fontSize = 16.sp)
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = TextStyle(color = colors.text, fontSize = 16.sp),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
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

@Suppress("unused")
@Composable
private fun PreviewHolder() {
    MaterialTheme { MainScreen() }
}
