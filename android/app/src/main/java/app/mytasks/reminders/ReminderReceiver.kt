package app.mytasks.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.text.format.DateFormat
import app.mytasks.App
import app.mytasks.MainActivity
import app.mytasks.R
import app.mytasks.core.ReminderType
import app.mytasks.data.Keys
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Shows reminders and handles their MARK AS DONE, SNOOZE and DISMISS buttons. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(ReminderScheduler.EXTRA_TASK_ID) ?: return
        when (intent.action) {
            ReminderScheduler.ACTION_FIRE -> {
                ReminderScheduler.markFired(context, taskId)
                Notifications.show(
                    context,
                    taskId,
                    intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: "",
                    intent.getIntExtra(ReminderScheduler.EXTRA_TYPE, ReminderType.NOTIFICATION),
                    intent.getLongExtra(ReminderScheduler.EXTRA_DUE, System.currentTimeMillis()),
                    quiet = false,
                )
            }
            ACTION_QUIET -> Notifications.show(
                context,
                taskId,
                intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: "",
                ReminderType.NOTIFICATION,
                intent.getLongExtra(ReminderScheduler.EXTRA_DUE, System.currentTimeMillis()),
                quiet = true,
            )
            ACTION_DISMISS -> Notifications.cancel(context, taskId)
            ACTION_DONE -> {
                Notifications.cancel(context, taskId)
                val app = context.applicationContext as App
                val pending = goAsync()
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        app.completeTask(taskId)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_DONE = "app.mytasks.REMINDER_DONE"
        const val ACTION_DISMISS = "app.mytasks.REMINDER_DISMISS"
        const val ACTION_QUIET = "app.mytasks.REMINDER_QUIET"
    }
}

/** Sets the reminders again after a restart, an app update or a clock change. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderScheduler.rescheduleSaved(context)
    }
}

object Notifications {
    private const val CHANNEL_REMINDERS = "reminders"
    private const val CHANNEL_ALARMS = "alarms"
    private const val CHANNEL_QUIET = "quiet"

    /** Alarm duration setting codes to milliseconds; Forever has no entry. */
    private val ALARM_DURATIONS = mapOf(4 to 15_000L, 5 to 30_000L, 1 to 60_000L, 2 to 300_000L, 3 to 600_000L)

    fun idFor(taskId: String): Int = taskId.hashCode()

    /** Before Android 13 there is no notification permission to ask for. */
    fun canPost(context: Context): Boolean = android.os.Build.VERSION.SDK_INT < 33 ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Task reminders shown as notifications"
                enableVibration(true)
                enableLights(true)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALARMS, "Alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Task reminders that ring like an alarm"
                enableVibration(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                )
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_QUIET, "Reminders (silent)", NotificationManager.IMPORTANCE_LOW).apply {
                description = "A reminder left in the list after its alarm has stopped ringing"
            },
        )
    }

    private fun action(context: Context, action: String, taskId: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(action)
            .setData(Uri.parse("mytasks://reminder/$taskId"))
            .putExtra(ReminderScheduler.EXTRA_TASK_ID, taskId)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun screen(context: Context, mode: String, taskId: String, title: String, dueAt: Long): PendingIntent {
        val intent = Intent(context, AlarmActivity::class.java)
            .setAction(mode)
            .setData(Uri.parse("mytasks://reminder/$mode/$taskId"))
            .putExtra(ReminderScheduler.EXTRA_TASK_ID, taskId)
            .putExtra(ReminderScheduler.EXTRA_TITLE, title)
            .putExtra(ReminderScheduler.EXTRA_DUE, dueAt)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun show(context: Context, taskId: String, title: String, type: Int, dueAt: Long, quiet: Boolean) {
        if (!canPost(context)) return
        ensureChannels(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val settings = (context.applicationContext as App).prefs.state.value
        val isAlarm = type == ReminderType.ALARM && !quiet
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val due = Date(dueAt)
        val whenText = DateFormat.getMediumDateFormat(context).format(due) + " " + DateFormat.getTimeFormat(context).format(due)
        val builder = Notification.Builder(context, if (quiet) CHANNEL_QUIET else if (isAlarm) CHANNEL_ALARMS else CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(whenText)
            .setWhen(dueAt)
            .setShowWhen(true)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOngoing(settings[Keys.persistentNotifications])
            .setCategory(if (isAlarm) Notification.CATEGORY_ALARM else Notification.CATEGORY_REMINDER)
            .addAction(Notification.Action.Builder(null, "MARK AS DONE", action(context, ReminderReceiver.ACTION_DONE, taskId)).build())
            .addAction(Notification.Action.Builder(null, "SNOOZE", screen(context, AlarmActivity.MODE_SNOOZE, taskId, title, dueAt)).build())
            .addAction(Notification.Action.Builder(null, "DISMISS", action(context, ReminderReceiver.ACTION_DISMISS, taskId)).build())
        if (isAlarm) builder.setFullScreenIntent(screen(context, AlarmActivity.MODE_ALARM, taskId, title, dueAt), true)
        val notification = builder.build()
        // An alarm keeps ringing until it is answered or its duration runs out.
        if (isAlarm) notification.flags = notification.flags or Notification.FLAG_INSISTENT
        manager.notify(idFor(taskId), notification)

        val duration = ALARM_DURATIONS[settings[Keys.alarmDuration]]
        if (isAlarm && duration != null) {
            val quietIntent = Intent(context, ReminderReceiver::class.java)
                .setAction(ReminderReceiver.ACTION_QUIET)
                .setData(Uri.parse("mytasks://reminder/$taskId"))
                .putExtra(ReminderScheduler.EXTRA_TASK_ID, taskId)
                .putExtra(ReminderScheduler.EXTRA_TITLE, title)
                .putExtra(ReminderScheduler.EXTRA_DUE, dueAt)
            val pi = PendingIntent.getBroadcast(context, 0, quietIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            context.getSystemService(AlarmManager::class.java)
                ?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + duration, pi)
        }
    }

    fun cancel(context: Context, taskId: String) {
        context.getSystemService(NotificationManager::class.java)?.cancel(idFor(taskId))
    }
}
