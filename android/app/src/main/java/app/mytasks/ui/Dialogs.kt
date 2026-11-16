package app.mytasks.ui

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mytasks.core.LIST_COLORS
import app.mytasks.core.TaskList
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
private fun DialogButton(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(label, color = appColors().accent) }
}

@Composable
private fun NameField(value: TextFieldValue, hint: String, onChange: (TextFieldValue) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(hint) },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
    )
}

private fun fieldValue(text: String) = TextFieldValue(text, TextRange(text.length))

/** One text field with CANCEL and SAVE. */
@Composable
fun TextDialog(title: String, initial: String, hint: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(fieldValue(initial)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { NameField(value, hint) { value = it } },
        confirmButton = {
            DialogButton("SAVE") {
                if (value.text.isNotBlank()) onSave(value.text)
                onDismiss()
            }
        },
        dismissButton = { DialogButton("CANCEL", onDismiss) },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    dismiss: String = "CANCEL",
    onDismiss: () -> Unit,
    onDismissButton: () -> Unit = {},
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            DialogButton(confirm) {
                onConfirm()
                onDismiss()
            }
        },
        dismissButton = {
            DialogButton(dismiss) {
                onDismissButton()
                onDismiss()
            }
        },
    )
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = appColors()
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = colors.accent, unselectedColor = colors.textSecondary),
        )
        Text(label, fontSize = 15.sp)
    }
}

/** A list of options; picking one closes the dialog. */
@Composable
fun ChoiceDialog(title: String, options: List<String>, selected: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEachIndexed { index, label ->
                    RadioRow(label, index == selected) {
                        onDismiss()
                        onPick(index)
                    }
                }
            }
        },
        confirmButton = { DialogButton("CANCEL", onDismiss) },
    )
}

/** Drag rows by their handle to reorder. [onMove] gets the moved id and the new order. */
@Composable
fun ReorderDialog(title: String, items: List<Pair<String, String>>, onDismiss: () -> Unit, onMove: (String, List<String>) -> Unit) {
    val colors = appColors()
    var order by remember(items) { mutableStateOf(items) }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        order = order.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(state = listState, modifier = Modifier.heightIn(max = 420.dp)) {
                items(order, key = { it.first }) { item ->
                    ReorderableItem(reorderState, key = item.first) { dragging ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(if (dragging) colors.surface else Color.Transparent)
                                .heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(item.second, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            Icon(
                                Icons.Filled.DragHandle,
                                contentDescription = "Drag to reorder",
                                tint = colors.textSecondary,
                                modifier = Modifier
                                    .draggableHandle(onDragStopped = { onMove(item.first, order.map { it.first }) })
                                    .size(40.dp)
                                    .padding(8.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { DialogButton("CLOSE", onDismiss) },
    )
}

private val THEME_OPTIONS = listOf("Light", "Dark", "Black", "Follow system (light / dark)", "Follow system (light / black)")

/** Temporary home for Theme and the account until the full Settings screen is built. */
@Composable
fun SettingsDialog(theme: Int, account: String?, onTheme: (Int) -> Unit, onSignOut: (() -> Unit)?, onDismiss: () -> Unit) {
    val colors = appColors()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Theme", color = colors.accent, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
                THEME_OPTIONS.forEachIndexed { index, label -> RadioRow(label, index == theme) { onTheme(index) } }
                Spacer(Modifier.height(12.dp))
                Text("Account", color = colors.accent, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
                Text(account ?: "Sync is not set up in this build.", fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                Text("The full Settings screen comes in a later build.", color = colors.textSecondary, fontSize = 13.sp)
            }
        },
        confirmButton = { DialogButton("CLOSE", onDismiss) },
        dismissButton = {
            if (onSignOut != null) {
                DialogButton("SIGN OUT") {
                    onDismiss()
                    onSignOut()
                }
            }
        },
    )
}
