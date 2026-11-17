package app.mytasks

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalView
import app.mytasks.data.Keys
import app.mytasks.ui.AppTheme
import app.mytasks.ui.MainScreen
import app.mytasks.ui.SignInScreen
import app.mytasks.ui.ThemeChoice
import app.mytasks.ui.appColors
import kotlinx.coroutines.launch

/** Font size setting: Extra small, Small, Normal, Large, Extra Large. */
private val FONT_SCALES = listOf(0.8f, 0.9f, 1f, 1.15f, 1.3f)

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_OPEN_SUB_LIST = "app.mytasks.OPEN_SUB_LIST"
        const val EXTRA_OPEN_TASK = "app.mytasks.OPEN_TASK"
        const val EXTRA_NEW_TASK = "app.mytasks.NEW_TASK"
        const val EXTRA_NEW_TASK_DUE = "app.mytasks.NEW_TASK_DUE"
    }

    private fun readOpenRequest(intent: Intent?): OpenRequest? {
        if (intent == null) return null
        val sub = intent.getStringExtra(EXTRA_OPEN_SUB_LIST)
        val task = intent.getStringExtra(EXTRA_OPEN_TASK)
        val newTask = intent.getBooleanExtra(EXTRA_NEW_TASK, false)
        val due = intent.getLongExtra(EXTRA_NEW_TASK_DUE, -1L).takeIf { it >= 0 }
        if (sub == null && task == null && !newTask) return null
        return OpenRequest(subListId = sub, taskId = task, newTask = newTask, newTaskDue = due)
    }

    override fun onStart() {
        super.onStart()
        // Starting it while the app is on screen is always allowed.
        (application as App).startBackgroundSync()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        (application as App).requestOpen(readOpenRequest(intent))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) (application as App).requestOpen(readOpenRequest(intent))
        enableEdgeToEdge()
        val app = application as App
        setContent {
            val settings by app.prefs.state.collectAsStateWithLifecycle()
            val theme = settings[Keys.theme]
            val fontScale = FONT_SCALES.getOrElse(settings[Keys.fontSize]) { 1f }
            val session by app.session.collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()
            AppTheme(choice = ThemeChoice.entries.getOrElse(theme) { ThemeChoice.Black }, fontScale = fontScale) {
                val keepOn = settings[Keys.keepScreenOn]
                val view = LocalView.current
                LaunchedEffect(keepOn) { view.keepScreenOn = keepOn }
                // Status and navigation bar icons follow the app theme, not the system theme.
                val dark = appColors().isDark
                LaunchedEffect(dark) {
                    val style = if (dark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    }
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                when (val s = session) {
                    is Session.SignedOut -> SignInScreen(onSignIn = { app.signIn(this@MainActivity) })
                    is Session.SignedIn -> MainScreen(
                        store = s.store,
                        prefs = app.prefs,
                        account = s.email,
                        onSignOut = { scope.launch { app.signOut(this@MainActivity) } },
                        openRequests = app.openRequest,
                        onOpenHandled = { app.clearOpenRequest() },
                    )
                    is Session.Local -> MainScreen(
                        store = s.store,
                        prefs = app.prefs,
                        account = null,
                        onSignOut = null,
                        openRequests = app.openRequest,
                        onOpenHandled = { app.clearOpenRequest() },
                    )
                }
            }
        }
    }
}
