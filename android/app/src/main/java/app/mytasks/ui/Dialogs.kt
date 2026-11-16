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

/** Name and colour of a task list. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ListDialog(
    title: String,
    list: TaskList?,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onSave: (String, Long) -> Unit,
) {
    val colors = appColors()
    var value by remember { mutableStateOf(fieldValue(list?.title ?: "")) }
    var color by remember { mutableStateOf(list?.color ?: LIST_COLORS[0]) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                NameField(value, "Let's give it a name…") { value = it }
                Spacer(Modifier.height(16.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LIST_COLORS.forEach { c ->
                        Box(
                            Modifier
                                .size(32.dp)
                                .background(Color(c), CircleShape)
                                .border(if (c == color) 3.dp else 0.dp, if (c == color) colors.text else Color.Transparent, CircleShape)
                                .clickable { color = c },
                        )
                    }
                }
            }
        },
        confirmButton = {
            DialogButton("SAVE") {
                if (value.text.isNotBlank()) onSave(value.text, color)
                onDismiss()
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) DialogButton("DELETE", onDelete)
                DialogButton("CANCEL", onDismiss)
            }
        },
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
                        onPick(index)
                        onDismiss()
                    }
                }
            }
        },
        confirmButton = { DialogButton("CANCEL", onDismiss) },
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
