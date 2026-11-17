package app.mytasks.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import app.mytasks.App
import app.mytasks.MainActivity
import app.mytasks.R
import app.mytasks.data.Keys

/**
 * "Always-on sync": keeps the app running in the background so its live
 * connection to the database stays open. Changes from other devices then
 * reach the widgets and reminders within seconds, even with the app closed.
 * The service itself does no work; it only keeps the process alive.
 */
class SyncService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as App
        if (!app.prefs.state.value[Keys.alwaysOnSync]) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            startForeground(ID, notification(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } catch (_: Exception) {
            // Android refused to start it from the background; the next app open tries again.
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    companion object {
        private const val ID = 4201
        private const val CHANNEL = "sync"

        private fun notification(context: Context): Notification {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(
                NotificationChannel(CHANNEL, "Background sync", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "Shown while always-on sync keeps your tasks up to date. You can hide it."
                    setShowBadge(false)
                },
            )
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
            )
            return Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("My Tasks is syncing")
                .setContentText("Keeps your widgets up to date. Turn off in Settings > Sync.")
                .setContentIntent(open)
                .setOngoing(true)
                .setShowWhen(false)
                .build()
        }

        /** Starts or stops the service to match the setting and the sign-in state. */
        fun update(context: Context, enabled: Boolean) {
            val intent = Intent(context, SyncService::class.java)
            if (enabled) {
                try {
                    context.startForegroundService(intent)
                } catch (_: Exception) {
                    // Not allowed right now (app in the background); tried again later.
                }
            } else {
                context.stopService(intent)
            }
        }
    }
}
