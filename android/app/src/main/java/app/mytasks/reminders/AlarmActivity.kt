package app.mytasks.reminders

import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mytasks.App
import app.mytasks.data.Keys
import app.mytasks.ui.AppTheme
import app.mytasks.ui.ThemeChoice
import app.mytasks.ui.appColors
import java.util.Date
import kotlinx.coroutines.launch

/**
 * Full-screen alarm over the lock screen, and the snooze picker opened from a
 * reminder's SNOOZE button.
 */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val app = application as App
        val taskId = intent.getStringExtra(ReminderScheduler.EXTRA_TASK_ID) ?: return finish()
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: ""
        val dueAt = intent.getLongExtra(ReminderScheduler.EXTRA_DUE, System.currentTimeMillis())
        val startInSnooze = intent.action == MODE_SNOOZE
        val settings = app.prefs.state.value
        val custom = settings[Keys.customSnoozeMinutes]

        setContent {
            AppTheme(choice = ThemeChoice.entries.getOrElse(settings[Keys.theme]) { ThemeChoice.Black }) {
                val colors = appColors()
                val scope = rememberCoroutineScope()
                var snoozing by remember { mutableStateOf(startInSnooze) }

                fun snooze(minutes: Int) {
                    Notifications.cancel(this, taskId)
                    scope.launch {
                        app.snoozeTask(taskId, System.currentTimeMillis() + minutes * 60_000L)
                        finish()
                    }
                }

                Column(
                    Modifier.fillMaxSize().background(colors.background).systemBarsPadding().padding(24.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val due = Date(dueAt)
                    Text(title, color = colors.text, fontSize = 26.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        DateFormat.getMediumDateFormat(this@AlarmActivity).format(due) + " " + DateFormat.getTimeFormat(this@AlarmActivity).format(due),
                        color = colors.textSecondary,
                        fontSize = 16.sp,
                    )
                    Spacer(Modifier.height(32.dp))
                    if (snoozing) {
                        Text("Snooze for", color = colors.accent, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        val options = listOf(5, 10, 15, 30, 60, 120) + (if (custom > 0 && custom !in listOf(5, 10, 15, 30, 60, 120)) listOf(custom) else emptyList())
                        options.forEach { minutes ->
                            Text(
                                snoozeLabel(minutes),
                                color = colors.text,
                                fontSize = 18.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().clickable { snooze(minutes) }.padding(vertical = 14.dp),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { finish() }) { Text("CANCEL", color = colors.accent) }
                    } else {
                        Button(
                            onClick = {
                                Notifications.cancel(this@AlarmActivity, taskId)
                                scope.launch {
                                    app.completeTask(taskId)
                                    finish()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = Color.Black),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("MARK AS DONE") }
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = { snoozing = true }, modifier = Modifier.fillMaxWidth()) { Text("SNOOZE", color = colors.accent) }
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                Notifications.cancel(this@AlarmActivity, taskId)
                                finish()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("DISMISS", color = colors.accent) }
                    }
                }
            }
        }
    }

    private fun snoozeLabel(minutes: Int): String = when {
        minutes % 1440 == 0 -> "${minutes / 1440} " + if (minutes == 1440) "day" else "days"
        minutes % 60 == 0 -> "${minutes / 60} " + if (minutes == 60) "hour" else "hours"
        else -> "$minutes minutes"
    }

    companion object {
        const val MODE_ALARM = "app.mytasks.ALARM"
        const val MODE_SNOOZE = "app.mytasks.SNOOZE"
    }
}
