package app.mytasks.data

import android.content.Context
import app.mytasks.core.HeadingInfo
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Settings that stay on this device. The synced settings arrive with the Settings screen. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("device", Context.MODE_PRIVATE)

    /** Identifies this device in `updatedBy`. */
    val deviceId: String = sp.getString("deviceId", null) ?: UUID.randomUUID().toString().also {
        sp.edit().putString("deviceId", it).apply()
    }

    // Owner preset: Black theme (option 2). The reference default is Light (0).
    private val _theme = MutableStateFlow(sp.getInt("theme", 2))
    val theme: StateFlow<Int> = _theme

    fun setTheme(value: Int) {
        sp.edit().putInt("theme", value).apply()
        _theme.value = value
    }

    var lastListId: String?
        get() = sp.getString("lastListId", null)
        set(value) = sp.edit().putString("lastListId", value).apply()

    // Reference defaults, fixed until the Settings screen exists.
    val insertNewTasksAtTop = true
    val moveCompletedToBottom = false
    val subListHeading = HeadingInfo.COMPLETED_OF_TOTAL
    val menuListInfo = HeadingInfo.COMPLETED_OF_TOTAL
    val swipeToDelete = true
    val showQuickAdd = true
    val strikethroughCompleted = true
}
