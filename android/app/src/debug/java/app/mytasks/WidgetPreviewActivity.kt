package app.mytasks

import android.app.Activity
import android.appwidget.AppWidgetHostView
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.TextView
import app.mytasks.widgets.WidgetRenderer

/** Test builds only: shows the three widgets as the home screen would draw them. */
class WidgetPreviewActivity : Activity() {
    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(40), dp(16), dp(16))
            // A busy, wallpaper-like background so transparency is visible.
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF1B3A5C.toInt(), 0xFF7A5A2E.toInt(), 0xFF101010.toInt()))
        }
        val sub = WidgetRenderer.firstSubList(this)?.id
        fun add(kind: String, w: Int, h: Int) {
            root.addView(TextView(this).apply { text = kind; setTextColor(0xFFFFFFFF.toInt()) })
            // Android draws widget lists only inside a widget frame, as a home screen does.
            val host = AppWidgetHostView(this)
            try {
                host.updateAppWidget(WidgetRenderer.preview(this, kind, sub))
            } catch (e: Exception) {
                host.addView(TextView(this).apply { text = "Could not draw: $e"; setTextColor(0xFFFF5555.toInt()) })
            }
            root.addView(host, LinearLayout.LayoutParams(dp(w), dp(h)).apply { bottomMargin = dp(12) })
        }
        add("large", 320, 300)
        add("small", 80, 80)
        add("calendar", 320, 200)
        setContentView(root)
    }
}
