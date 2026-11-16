package app.mytasks

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mytasks.ui.AppTheme
import app.mytasks.ui.MainScreen
import app.mytasks.ui.SignInScreen
import app.mytasks.ui.ThemeChoice
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as App
        setContent {
            val theme by app.prefs.theme.collectAsStateWithLifecycle()
            val session by app.session.collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()
            AppTheme(choice = ThemeChoice.entries.getOrElse(theme) { ThemeChoice.Black }) {
                when (val s = session) {
                    is Session.SignedOut -> SignInScreen(onSignIn = { app.signIn(this@MainActivity) })
                    is Session.SignedIn -> MainScreen(
                        store = s.store,
                        prefs = app.prefs,
                        account = s.email,
                        onSignOut = { scope.launch { app.signOut(this@MainActivity) } },
                    )
                    is Session.Local -> MainScreen(store = s.store, prefs = app.prefs, account = null, onSignOut = null)
                }
            }
        }
    }
}
