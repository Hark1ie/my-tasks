package app.mytasks.widgets

import android.content.Context

/**
 * Options for one widget, kept on this phone only. Defaults are the reference
 * app's: both transparencies 165 out of 255, add button shown, not in the
 * heading, font and tag size in the middle of their range.
 */
data class WidgetOptions(
    /** Sub-list shown by a list widget. Null until one is chosen. */
    val subListId: String?,
    /** Opacity of the widget background and heading, 0 to 255. */
    val backgroundAlpha: Int = 165,
    /** Opacity of each task row, 0 to 255. */
    val itemAlpha: Int = 165,
    val showAdd: Boolean = true,
    val addInHeading: Boolean = false,
    /** 0 = extra small ... 4 = extra large. */
    val fontSize: Int = 2,
    val tagSize: Int = 2,
    /** Day shown by a calendar widget, as the start of that day. Null = today. */
    val day: Long? = null,
    /**
     * List and position of the shown sub-list, remembered so that if it is
     * deleted the widget can move to the sub-list that came after it.
     */
    val listId: String? = null,
    val rank: String? = null,
)

object WidgetPrefs {
    private const val FILE = "widgets"

    private fun sp(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun get(context: Context, id: Int): WidgetOptions {
        val p = sp(context)
        return WidgetOptions(
            subListId = p.getString("sub_$id", null),
            backgroundAlpha = p.getInt("bg_$id", 165),
            itemAlpha = p.getInt("item_$id", 165),
            showAdd = p.getBoolean("fab_$id", true),
            addInHeading = p.getBoolean("moveAdd_$id", false),
            fontSize = p.getInt("font_$id", 2),
            tagSize = p.getInt("tag_$id", 2),
            day = p.getLong("day_$id", -1L).takeIf { it >= 0 },
            listId = p.getString("list_$id", null),
            rank = p.getString("rank_$id", null),
        )
    }

    fun put(context: Context, id: Int, o: WidgetOptions) {
        sp(context).edit()
            .putString("sub_$id", o.subListId)
            .putInt("bg_$id", o.backgroundAlpha)
            .putInt("item_$id", o.itemAlpha)
            .putBoolean("fab_$id", o.showAdd)
            .putBoolean("moveAdd_$id", o.addInHeading)
            .putInt("font_$id", o.fontSize)
            .putInt("tag_$id", o.tagSize)
            .putLong("day_$id", o.day ?: -1L)
            .putString("list_$id", o.listId)
            .putString("rank_$id", o.rank)
            .apply()
    }

    fun remove(context: Context, id: Int) {
        val e = sp(context).edit()
        for (k in listOf("sub", "bg", "item", "fab", "moveAdd", "font", "tag", "day", "list", "rank")) e.remove("${k}_$id")
        e.apply()
    }
}
