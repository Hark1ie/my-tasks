package app.mytasks.widgets

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mytasks.App
import app.mytasks.data.Keys
import app.mytasks.ui.AppTheme
import app.mytasks.ui.SubListChooser
import app.mytasks.ui.ThemeChoice
import app.mytasks.ui.appColors

/**
 * Over the home screen: "Change To" (pick the sub-list a widget shows) and the
 * widget's Settings. Also opens when a list widget is first added.
 */
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return finish()
        val adding = intent.action == AppWidgetManager.ACTION_APPWIDGET_CONFIGURE
        // Until a list is picked, backing out of a new widget removes it.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        val mode = if (adding) MODE_CHOOSE else intent.getStringExtra(EXTRA_MODE) ?: MODE_CHOOSE
        val app = application as App
        val data = app.widgetSnapshot()
        val theme = app.prefs.state.value[Keys.theme]

        fun done(options: WidgetOptions) {
            WidgetPrefs.put(this, id, options)
            WidgetRenderer.render(this, AppWidgetManager.getInstance(this), id)
            WidgetRenderer.schedule(this)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
            finish()
        }

        setContent {
            AppTheme(choice = ThemeChoice.entries.getOrElse(theme) { ThemeChoice.Black }) {
                val current = WidgetPrefs.get(this, id)
                if (mode == MODE_SETTINGS) {
                    WidgetSettingsDialog(current, onCancel = { finish() }, onSave = { done(it) })
                } else if (data.subLists.isEmpty()) {
                    AlertDialog(
                        onDismissRequest = { finish() },
                        title = { Text("Change To") },
                        text = { Text("Open My Tasks and sign in first, then add the widget again.") },
                        confirmButton = { TextButton(onClick = { finish() }) { Text("OK", color = appColors().accent) } },
                    )
                } else {
                    SubListChooser(
                        title = "Change To",
                        lists = data.lists,
                        subLists = data.subLists,
                        selectedSubListId = current.subListId,
                        onDismiss = { if (!isFinishing) finish() },
                        onPick = { done(current.copy(subListId = it.id)) },
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_MODE = "app.mytasks.widget.MODE"
        const val MODE_CHOOSE = "choose"
        const val MODE_SETTINGS = "settings"
    }
}

private val FONT_LABELS = listOf("Extra small", "Small", "Normal", "Large", "Extra large")

/** Same options as the reference app's widget Settings dialog. */
@Composable
private fun WidgetSettingsDialog(start: WidgetOptions, onCancel: () -> Unit, onSave: (WidgetOptions) -> Unit) {
    val colors = appColors()
    var background by remember { mutableFloatStateOf(start.backgroundAlpha.toFloat()) }
    var item by remember { mutableFloatStateOf(start.itemAlpha.toFloat()) }
    var showAdd by remember { mutableStateOf(start.showAdd) }
    var moveAdd by remember { mutableStateOf(start.addInHeading) }
    var font by remember { mutableFloatStateOf(start.fontSize.toFloat()) }
    var tag by remember { mutableFloatStateOf(start.tagSize.toFloat()) }
    val sliderColors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent)

    @Composable
    fun label(text: String) = Text(text, fontSize = 15.sp, modifier = Modifier.padding(top = 12.dp))

    @Composable
    fun check(text: String, value: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
        Row(
            Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!value) }.padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, fontSize = 15.sp, color = if (enabled) colors.text else colors.textSecondary, modifier = Modifier.weight(1f))
            Checkbox(
                checked = value,
                onCheckedChange = onChange,
                enabled = enabled,
                colors = CheckboxDefaults.colors(checkedColor = colors.accent, checkmarkColor = Color.Black),
            )
        }
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                label("Background transparency")
                Slider(value = background, onValueChange = { background = it }, valueRange = 0f..255f, colors = sliderColors)
                label("List item transparency")
                Slider(value = item, onValueChange = { item = it }, valueRange = 0f..255f, colors = sliderColors)
                check("Show add button", showAdd) { showAdd = it }
                check("Move add button to heading", moveAdd, enabled = showAdd) { moveAdd = it }
                label("Font size: " + FONT_LABELS[font.toInt().coerceIn(0, 4)])
                Slider(value = font, onValueChange = { font = it }, valueRange = 0f..4f, steps = 3, colors = sliderColors)
                label("Tag size")
                Slider(value = tag, onValueChange = { tag = it }, valueRange = 0f..4f, steps = 3, colors = sliderColors)
                Spacer(Modifier.height(4.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    start.copy(
                        backgroundAlpha = background.toInt(),
                        itemAlpha = item.toInt(),
                        showAdd = showAdd,
                        addInHeading = moveAdd,
                        fontSize = font.toInt(),
                        tagSize = tag.toInt(),
                    ),
                )
            }) { Text("SAVE", color = colors.accent) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("CANCEL", color = colors.accent) } },
    )
}
