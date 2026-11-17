package app.mytasks.widgets

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.format.DateFormat
import android.text.style.StrikethroughSpan
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.mytasks.App
import app.mytasks.MainActivity
import app.mytasks.R
import app.mytasks.core.HeadingInfo
import app.mytasks.core.Lists
import app.mytasks.core.PRIORITY_COLORS
import app.mytasks.core.ReminderMode
import app.mytasks.core.SubList
import app.mytasks.core.SubTask
import app.mytasks.core.Task
import app.mytasks.core.isLive
import app.mytasks.data.Keys
import app.mytasks.data.SettingsState
import app.mytasks.data.Snapshot
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit

/** The small 1x1 widget: a sub-list's name and how many of its tasks are done. */
class SmallListWidget : BaseWidget()

/** The list widget: the tasks of one sub-list, tickable from the home screen. */
class ListWidget : BaseWidget()

/** The calendar widget: tasks due on one day, across all lists. */
class CalendarWidget : BaseWidget()

abstract class BaseWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { WidgetRenderer.render(context, manager, it) }
        WidgetRenderer.schedule(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        WidgetRenderer.render(context, manager, id)
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        ids.forEach { WidgetPrefs.remove(context, it) }
    }

    override fun onEnabled(context: Context) = WidgetRenderer.schedule(context)

    override fun onDisabled(context: Context) = WidgetRenderer.cancelIfNone(context)
}

/** Draws every widget from the app's data. */
object WidgetRenderer {
    const val EXTRA_ACTION = "app.mytasks.widget.ACTION"
    const val EXTRA_TASK = "app.mytasks.widget.TASK"
    const val EXTRA_SUB_TASK = "app.mytasks.widget.SUB_TASK"
    const val ACTION_TOGGLE = "toggle"
    const val ACTION_TOGGLE_SUB = "toggleSub"
    const val ACTION_EXPAND = "expand"
    const val ACTION_OPEN = "open"
    private const val WORK = "widget-refresh"

    /** Font size option to text size in sp: extra small ... extra large. */
    private val FONT_SP = listOf(12f, 14f, 16f, 18f, 20f)
    private const val DAY = 24L * 60 * 60 * 1000

    private val PROVIDERS = listOf(SmallListWidget::class.java, ListWidget::class.java, CalendarWidget::class.java)

    private data class Palette(
        val background: Int,
        val heading: Int,
        val row: Int,
        val text: Int,
        val secondary: Int,
        val icon: Int,
        val divider: Int,
    )

    private val LIGHT = Palette(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xDE000000.toInt(), 0x99000000.toInt(), 0x8A000000.toInt(), 0x1F000000)
    private val DARK = Palette(0xFF303030.toInt(), 0xFF212121.toInt(), 0xFF424242.toInt(), 0xFFFFFFFF.toInt(), 0xB3FFFFFF.toInt(), 0xB3FFFFFF.toInt(), 0x1FFFFFFF)
    private val BLACK = DARK.copy(heading = 0xFF000000.toInt(), row = 0xFF000000.toInt(), divider = 0xFF2C2C2C.toInt())
    private const val OVERDUE = 0xFFEF5350.toInt()

    /** Widgets follow the app's Theme setting. */
    private fun palette(context: Context, theme: Int): Palette {
        val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        return when (theme) {
            0 -> LIGHT
            1 -> DARK
            2 -> BLACK
            3 -> if (night) DARK else LIGHT
            else -> if (night) BLACK else LIGHT
        }
    }

    private fun alpha(color: Int, a: Int) = (a.coerceIn(0, 255) shl 24) or (color and 0xFFFFFF)

    fun ids(context: Context): List<Int> {
        val m = AppWidgetManager.getInstance(context)
        return PROVIDERS.flatMap { m.getAppWidgetIds(ComponentName(context, it)).toList() }
    }

    fun updateAll(context: Context) {
        val m = AppWidgetManager.getInstance(context)
        for (id in ids(context)) render(context, m, id)
    }

    /** Refreshes the widgets every 15 minutes, which also keeps reminders up to date. */
    fun schedule(context: Context) {
        if (ids(context).isEmpty()) return
        val app = context.applicationContext as App
        val network = if (app.prefs.state.value[Keys.syncWifiOnly]) NetworkType.UNMETERED else NetworkType.CONNECTED
        val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancelIfNone(context: Context) {
        if (ids(context).isEmpty()) WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }

    fun render(context: Context, manager: AppWidgetManager, id: Int) {
        val provider = manager.getAppWidgetInfo(id)?.provider?.className ?: return
        val views = when (provider) {
            SmallListWidget::class.java.name -> small(context, id, WidgetPrefs.get(context, id))
            CalendarWidget::class.java.name -> calendar(context, id, WidgetPrefs.get(context, id))
            else -> large(context, id, WidgetPrefs.get(context, id))
        }
        manager.updateAppWidget(id, views)
    }

    // ---- intents ----

    private fun unique(id: Int, what: String) = Uri.parse("mytasks://widget/$id/$what")

    private fun openApp(context: Context, id: Int, what: String, build: Intent.() -> Unit): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setData(unique(id, what))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply(build)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun config(context: Context, id: Int, mode: String): PendingIntent {
        val intent = Intent(context, WidgetConfigActivity::class.java)
            .setData(unique(id, mode))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .putExtra(WidgetConfigActivity.EXTRA_MODE, mode)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun dayShift(context: Context, id: Int, days: Int): PendingIntent {
        val intent = Intent(context, WidgetActionReceiver::class.java)
            .setAction(WidgetActionReceiver.ACTION_SHIFT_DAY)
            .setData(unique(id, "day$days"))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .putExtra(WidgetActionReceiver.EXTRA_DAYS, days)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** One template for all rows of a list; each row fills in what was tapped. */
    private fun rowTemplate(context: Context, id: Int): PendingIntent {
        val intent = Intent(context, WidgetActionActivity::class.java)
            .setData(unique(id, "rows"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
    }

    private fun fillIn(action: String, task: String, sub: String? = null) =
        Intent().putExtra(EXTRA_ACTION, action).putExtra(EXTRA_TASK, task).apply { if (sub != null) putExtra(EXTRA_SUB_TASK, sub) }

    // ---- widgets ----

    private fun small(context: Context, id: Int, o: WidgetOptions): RemoteViews {
        val app = context.applicationContext as App
        val data = app.widgetSnapshot()
        val sub = data.subLists.firstOrNull { it.id == o.subListId }
        val rv = RemoteViews(context.packageName, R.layout.widget_small)
        if (sub == null) {
            rv.setTextViewText(R.id.widget_small_title, "Choose a list")
            rv.setViewVisibility(R.id.widget_small_count, View.GONE)
            rv.setOnClickPendingIntent(R.id.widget_small_root, config(context, id, WidgetConfigActivity.MODE_CHOOSE))
            return rv
        }
        val tasks = data.tasks.filter { it.subListId == sub.id && it.isLive }
        rv.setTextViewText(R.id.widget_small_title, sub.title)
        rv.setViewVisibility(R.id.widget_small_count, View.VISIBLE)
        rv.setTextViewText(R.id.widget_small_count, Lists.headingInfo(tasks, HeadingInfo.COMPLETED_OF_TOTAL, System.currentTimeMillis()))
        rv.setOnClickPendingIntent(R.id.widget_small_root, openApp(context, id, "open") { putExtra(MainActivity.EXTRA_OPEN_SUB_LIST, sub.id) })
        return rv
    }

    private fun frame(context: Context, layout: Int, o: WidgetOptions, pal: Palette): RemoteViews {
        val rv = RemoteViews(context.packageName, layout)
        rv.setInt(R.id.widget_background, "setBackgroundColor", alpha(pal.background, o.backgroundAlpha))
        rv.setInt(R.id.widget_heading, "setBackgroundColor", alpha(pal.heading, o.backgroundAlpha))
        rv.setTextColor(R.id.widget_list_title, pal.text)
        rv.setTextColor(R.id.widget_empty, pal.secondary)
        for (icon in listOf(R.id.widget_add, R.id.widget_settings)) rv.setInt(icon, "setColorFilter", pal.text)
        rv.setViewVisibility(R.id.widget_fab, if (o.showAdd && !o.addInHeading) View.VISIBLE else View.GONE)
        rv.setViewVisibility(R.id.widget_add, if (o.showAdd && o.addInHeading) View.VISIBLE else View.GONE)
        return rv
    }

    private fun large(context: Context, id: Int, o: WidgetOptions): RemoteViews {
        val app = context.applicationContext as App
        val st = app.prefs.state.value
        val pal = palette(context, st[Keys.theme])
        val data = app.widgetSnapshot()
        val rv = frame(context, R.layout.widget_large, o, pal)
        rv.setInt(R.id.widget_list_chooser, "setColorFilter", pal.text)
        rv.setOnClickPendingIntent(R.id.widget_list_chooser, config(context, id, WidgetConfigActivity.MODE_CHOOSE))
        rv.setOnClickPendingIntent(R.id.widget_settings, config(context, id, WidgetConfigActivity.MODE_SETTINGS))

        val sub = data.subLists.firstOrNull { it.id == o.subListId }
        val now = System.currentTimeMillis()
        val tasks = if (sub == null) emptyList() else data.tasks.filter { it.subListId == sub.id && it.isLive }
        if (sub == null) {
            rv.setTextViewText(R.id.widget_list_title, if (data.subLists.isEmpty()) "My Tasks" else "Choose a list")
            rv.setTextViewText(R.id.widget_empty, if (data.subLists.isEmpty()) "Open My Tasks and sign in" else "Tap the list icon to choose a list")
        } else {
            rv.setTextViewText(R.id.widget_list_title, Lists.headingTitle(sub.title, tasks, st[Keys.subListHeading], now))
            rv.setTextViewText(R.id.widget_empty, "No tasks")
            rv.setOnClickPendingIntent(R.id.widget_list_title, openApp(context, id, "open") { putExtra(MainActivity.EXTRA_OPEN_SUB_LIST, sub.id) })
            val add = openApp(context, id, "add") {
                putExtra(MainActivity.EXTRA_NEW_TASK, true)
                putExtra(MainActivity.EXTRA_OPEN_SUB_LIST, sub.id)
            }
            rv.setOnClickPendingIntent(R.id.widget_fab, add)
            rv.setOnClickPendingIntent(R.id.widget_add, add)
        }
        val visible = if (sub == null) emptyList() else Lists.visibleTasks(tasks, sub.sortOrder, st[Keys.moveCompletedToBottom], sub.hideCompleted)
        setRows(context, rv, id, visible, data, st, pal, o, now, showDate = true)
        return rv
    }

    private fun startOfDay(t: Long): Long = Lists.startOfDay(t)

    private fun calendar(context: Context, id: Int, o: WidgetOptions): RemoteViews {
        val app = context.applicationContext as App
        val st = app.prefs.state.value
        val pal = palette(context, st[Keys.theme])
        val data = app.widgetSnapshot()
        val now = System.currentTimeMillis()
        val today = startOfDay(now)
        val day = o.day ?: today
        val rv = frame(context, R.layout.widget_calendar, o, pal)
        for (icon in listOf(R.id.widget_prev, R.id.widget_next)) rv.setInt(icon, "setColorFilter", pal.text)
        rv.setOnClickPendingIntent(R.id.widget_prev, dayShift(context, id, -1))
        rv.setOnClickPendingIntent(R.id.widget_next, dayShift(context, id, 1))
        rv.setOnClickPendingIntent(R.id.widget_settings, config(context, id, WidgetConfigActivity.MODE_SETTINGS))
        val title = when (day) {
            today -> "Today"
            today + DAY -> "Tomorrow"
            today - DAY -> "Yesterday"
            else -> DateFormat.format("EEE, d MMM", Date(day)).toString()
        }
        rv.setTextViewText(R.id.widget_list_title, title)
        rv.setOnClickPendingIntent(R.id.widget_list_title, dayShift(context, id, 0))
        val add = openApp(context, id, "add$day") {
            putExtra(MainActivity.EXTRA_NEW_TASK, true)
            putExtra(MainActivity.EXTRA_NEW_TASK_DUE, day)
        }
        rv.setOnClickPendingIntent(R.id.widget_fab, add)
        rv.setOnClickPendingIntent(R.id.widget_add, add)
        rv.setTextViewText(R.id.widget_empty, "Nothing due")

        val due = data.tasks
            .filter { it.isLive && it.dueAt != null && startOfDay(it.dueAt!!) == day }
            .sortedWith(compareBy<Task>({ !it.allDay }, { it.dueAt }, { it.title.lowercase() }))
        setRows(context, rv, id, due, data, st, pal, o, now, showDate = false)
        return rv
    }

    private fun setRows(
        context: Context,
        rv: RemoteViews,
        id: Int,
        tasks: List<Task>,
        data: Snapshot,
        st: SettingsState,
        pal: Palette,
        o: WidgetOptions,
        now: Long,
        showDate: Boolean,
    ) {
        val subsByTask = data.subTasks.groupBy { it.taskId }
        val items = RemoteViews.RemoteCollectionItems.Builder().setHasStableIds(true).setViewTypeCount(2)
        tasks.forEachIndexed { index, task ->
            val subs = subsByTask[task.id].orEmpty()
            items.addItem(task.id.hashCode().toLong() and 0xFFFFFFFFL, taskRow(context, task, index, subs, st, pal, o, now, showDate))
            if (task.subTasksExpanded) {
                subs.forEachIndexed { subIndex, sub ->
                    items.addItem((1L shl 32) or (sub.id.hashCode().toLong() and 0xFFFFFFFFL), subRow(context, task, sub, subIndex, st, pal, o))
                }
            }
        }
        rv.setRemoteAdapter(R.id.widget_list, items.build())
        rv.setEmptyView(R.id.widget_list, R.id.widget_empty)
        rv.setPendingIntentTemplate(R.id.widget_list, rowTemplate(context, id))
    }

    private fun struck(text: String, on: Boolean): CharSequence {
        if (!on) return text
        return SpannableString(text).apply { setSpan(StrikethroughSpan(), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
    }

    private fun taskRow(
        context: Context,
        task: Task,
        index: Int,
        subs: List<SubTask>,
        st: SettingsState,
        pal: Palette,
        o: WidgetOptions,
        now: Long,
        showDate: Boolean,
    ): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_task_item)
        val font = FONT_SP.getOrElse(o.fontSize) { 16f }
        val strike = task.completed && st[Keys.strikethroughCompleted]
        rv.setInt(R.id.item_background, "setBackgroundColor", alpha(pal.row, o.itemAlpha))
        rv.setInt(R.id.item_divider, "setBackgroundColor", pal.divider)
        rv.setInt(R.id.item_highlight, "setBackgroundColor", if (task.highlight) task.highlightColor.toInt() else 0)

        rv.setImageViewResource(R.id.item_checkbox, if (task.completed) R.drawable.ic_w_check else R.drawable.ic_w_circle)
        rv.setInt(R.id.item_checkbox, "setColorFilter", PRIORITY_COLORS[task.priority]?.toInt() ?: pal.icon)

        val title = if (st[Keys.autoNumberTasks]) "${index + 1}. ${task.title}" else task.title
        rv.setTextViewText(R.id.item_title, struck(title, strike))
        rv.setTextColor(R.id.item_title, if (task.completed) pal.secondary else pal.text)
        rv.setTextViewTextSize(R.id.item_title, TypedValue.COMPLEX_UNIT_SP, font)

        val due = task.dueAt
        if (due != null && st[Keys.reminderView] != 2) {
            val time = if (task.allDay) "All day" else DateFormat.getTimeFormat(context).format(Date(due))
            val bell = if (task.reminderMode != ReminderMode.DONT_REMIND) "⏰ " else ""
            val text = if (showDate) bell + DateFormat.format("EEE, d MMM yyyy", Date(due)) + (if (task.allDay) "" else " $time") else bell + time
            rv.setTextViewText(R.id.item_due, text)
            rv.setTextColor(R.id.item_due, if (Lists.isOverdue(task, now)) OVERDUE else pal.secondary)
            rv.setTextViewTextSize(R.id.item_due, TypedValue.COMPLEX_UNIT_SP, font - 4)
            rv.setViewVisibility(R.id.item_due, View.VISIBLE)
        } else {
            rv.setViewVisibility(R.id.item_due, View.GONE)
        }

        val noteLines = listOf(Int.MAX_VALUE, 2, 1, 0).getOrElse(st[Keys.notesView]) { 2 }
        if (task.notes.isNotBlank() && noteLines > 0) {
            rv.setTextViewText(R.id.item_notes, struck(task.notes, strike))
            rv.setTextColor(R.id.item_notes, pal.secondary)
            rv.setTextViewTextSize(R.id.item_notes, TypedValue.COMPLEX_UNIT_SP, font - 3)
            rv.setInt(R.id.item_notes, "setMaxLines", noteLines)
            rv.setViewVisibility(R.id.item_notes, View.VISIBLE)
        } else {
            rv.setViewVisibility(R.id.item_notes, View.GONE)
        }

        if (subs.isNotEmpty()) {
            val arrow = if (task.subTasksExpanded) " ▴" else " ▾"
            rv.setTextViewText(R.id.item_subtasks, "${subs.count { it.completed }}/${subs.size}$arrow")
            rv.setTextColor(R.id.item_subtasks, pal.secondary)
            rv.setViewVisibility(R.id.item_subtasks, View.VISIBLE)
            rv.setOnClickFillInIntent(R.id.item_subtasks, fillIn(ACTION_EXPAND, task.id))
        } else {
            rv.setViewVisibility(R.id.item_subtasks, View.GONE)
        }

        rv.setOnClickFillInIntent(R.id.item_checkbox, fillIn(ACTION_TOGGLE, task.id))
        rv.setOnClickFillInIntent(R.id.item_background, fillIn(ACTION_OPEN, task.id))
        return rv
    }

    private fun subRow(context: Context, task: Task, sub: SubTask, index: Int, st: SettingsState, pal: Palette, o: WidgetOptions): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_sub_task_item)
        val font = FONT_SP.getOrElse(o.fontSize) { 16f } - 2
        rv.setInt(R.id.sub_background, "setBackgroundColor", alpha(pal.row, o.itemAlpha))
        rv.setImageViewResource(R.id.sub_checkbox, if (sub.completed) R.drawable.ic_w_check else R.drawable.ic_w_circle)
        rv.setInt(R.id.sub_checkbox, "setColorFilter", pal.icon)
        val title = if (st[Keys.autoNumberSubTasks]) "${index + 1}. ${sub.title}" else sub.title
        rv.setTextViewText(R.id.sub_title, struck(title, sub.completed && st[Keys.strikethroughCompleted]))
        rv.setTextColor(R.id.sub_title, if (sub.completed) pal.secondary else pal.text)
        rv.setTextViewTextSize(R.id.sub_title, TypedValue.COMPLEX_UNIT_SP, font)
        rv.setOnClickFillInIntent(R.id.sub_checkbox, fillIn(ACTION_TOGGLE_SUB, task.id, sub.id))
        rv.setOnClickFillInIntent(R.id.sub_background, fillIn(ACTION_OPEN, task.id))
        return rv
    }

    /** Used by the screenshot test to draw a widget inside the app. */
    fun preview(context: Context, kind: String, subListId: String?): RemoteViews {
        val o = WidgetOptions(subListId = subListId)
        return when (kind) {
            "small" -> small(context, -1, o)
            "calendar" -> calendar(context, -1, o)
            else -> large(context, -1, o)
        }
    }

    fun firstSubList(context: Context): SubList? = (context.applicationContext as App).widgetSnapshot().subLists.firstOrNull()
}

/** Calendar widget day arrows. */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SHIFT_DAY) return
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        if (id < 0) return
        val days = intent.getIntExtra(EXTRA_DAYS, 0)
        val o = WidgetPrefs.get(context, id)
        val today = Lists.startOfDay(System.currentTimeMillis())
        val newDay = if (days == 0) {
            null
        } else {
            val c = Calendar.getInstance().apply { timeInMillis = o.day ?: today; add(Calendar.DAY_OF_MONTH, days) }
            Lists.startOfDay(c.timeInMillis).takeIf { it != today }
        }
        WidgetPrefs.put(context, id, o.copy(day = newDay))
        WidgetRenderer.render(context, AppWidgetManager.getInstance(context), id)
    }

    companion object {
        const val ACTION_SHIFT_DAY = "app.mytasks.widget.SHIFT_DAY"
        const val EXTRA_DAYS = "days"
    }
}

/**
 * Receives taps on widget rows. It has no window: it does the change (or opens
 * the app on the task) and closes at once.
 */
class WidgetActionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as App
        val task = intent.getStringExtra(WidgetRenderer.EXTRA_TASK)
        if (task != null) {
            when (intent.getStringExtra(WidgetRenderer.EXTRA_ACTION)) {
                WidgetRenderer.ACTION_TOGGLE -> app.toggleTaskInBackground(task)
                WidgetRenderer.ACTION_TOGGLE_SUB -> intent.getStringExtra(WidgetRenderer.EXTRA_SUB_TASK)?.let { app.toggleSubTaskInBackground(it) }
                WidgetRenderer.ACTION_EXPAND -> app.toggleExpandedInBackground(task)
                WidgetRenderer.ACTION_OPEN -> startActivity(
                    Intent(this, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_OPEN_TASK, task)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                )
            }
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}

/** Wakes the app every 15 minutes so the widgets show changes made on other devices. */
class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as App
        app.awaitReady(25_000)
        WidgetRenderer.updateAll(app)
        return Result.success()
    }
}
