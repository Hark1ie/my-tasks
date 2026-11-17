package app.mytasks

import android.app.Activity
import android.app.Application
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import app.mytasks.data.FirestoreBackend
import app.mytasks.data.Keys
import app.mytasks.reminders.Notifications
import app.mytasks.reminders.ReminderScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import app.mytasks.data.MemoryBackend
import app.mytasks.data.Prefs
import app.mytasks.data.Snapshot
import app.mytasks.data.Store
import app.mytasks.core.isLive
import app.mytasks.widgets.WidgetRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withContext
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

/** What the app is showing: sign-in, or the data of the signed-in account. */
sealed interface Session {
    /** Sync is not configured in this build; data lives in memory only. */
    data class Local(val store: Store) : Session
    data object SignedOut : Session
    data class SignedIn(val store: Store, val email: String?) : Session
}

/** Where the app should go when it is opened from a widget or a shortcut. */
data class OpenRequest(
    val subListId: String? = null,
    val taskId: String? = null,
    val newTask: Boolean = false,
    /** For a new task from the calendar widget: the day it is due (all day). */
    val newTaskDue: Long? = null,
)

class App : Application() {
    lateinit var prefs: Prefs
        private set

    private val _session = MutableStateFlow<Session>(Session.SignedOut)
    val session: StateFlow<Session> = _session

    private var store: Store? = null
    private val scope = MainScope()
    private var reminderJob: Job? = null
    private var widgetJob: Job? = null

    private val _openRequest = MutableStateFlow<OpenRequest?>(null)
    val openRequest: StateFlow<OpenRequest?> = _openRequest

    fun requestOpen(request: OpenRequest?) {
        if (request != null) _openRequest.value = request
    }

    fun clearOpenRequest() {
        _openRequest.value = null
    }

    /** False when this build has no Firebase connection details. */
    val syncConfigured: Boolean
        get() = FirebaseApp.getApps(this).isNotEmpty() && getString(R.string.default_web_client_id).isNotEmpty()

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        Notifications.ensureChannels(this)
        // Widgets also redraw when a setting they show changes (theme, headings, sort rules).
        scope.launch { prefs.state.drop(1).collect { WidgetRenderer.updateAll(this@App) } }
        if (!syncConfigured) {
            val s = Store(MemoryBackend(), prefs.deviceId).also { it.start() }
            store = s
            watchForWidgets(s)
            _session.value = Session.Local(s)
            return
        }
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            val user = auth.currentUser
            store?.stop()
            store = null
            prefs.onSyncedChange = null
            reminderJob?.cancel()
            widgetJob?.cancel()
            if (user == null) {
                ReminderScheduler.cancelAll(this)
                Snapshot.clear(this)
                WidgetRenderer.updateAll(this)
            }
            _session.value = if (user == null) {
                Session.SignedOut
            } else {
                val s = Store(FirestoreBackend(FirebaseFirestore.getInstance(), user.uid), prefs.deviceId)
                s.onSettings = { prefs.applyCloud(it) }
                prefs.onSyncedChange = { s.putSettings(it) }
                s.start()
                store = s
                // Keep the phone's alarms in step with the tasks, whichever device changed them.
                reminderJob = scope.launch {
                    s.tasks.collect { tasks -> if (s.ready.value || tasks.isNotEmpty()) ReminderScheduler.sync(this@App, tasks) }
                }
                watchForWidgets(s)
                Session.SignedIn(s, user.email)
            }
        }
    }

    /**
     * Whenever the data changes, on this device or another, save a copy for the
     * widgets and redraw them.
     */
    @OptIn(FlowPreview::class)
    private fun watchForWidgets(s: Store) {
        widgetJob = scope.launch {
            combine(s.lists, s.subLists, s.tasks, s.subTasks, s.ready) { lists, subLists, tasks, subTasks, ready ->
                if (ready) Snapshot(lists, subLists, tasks.filter { it.isLive }, subTasks) else null
            }
                .filterNotNull()
                .debounce(300)
                .collect { snap ->
                    withContext(Dispatchers.IO) { Snapshot.save(this@App, snap) }
                    WidgetRenderer.updateAll(this@App)
                }
        }
    }

    /** The data the widgets draw: live when loaded, otherwise the copy saved on the phone. */
    fun widgetSnapshot(): Snapshot {
        val s = store
        if (s != null && s.ready.value) {
            return Snapshot(s.lists.value, s.subLists.value, s.tasks.value.filter { it.isLive }, s.subTasks.value)
        }
        return Snapshot.load(this) ?: Snapshot.EMPTY
    }

    /** Waits until the signed-in data has loaded, for [timeoutMs] at most. */
    suspend fun awaitReady(timeoutMs: Long) {
        withTimeoutOrNull(timeoutMs) {
            while (store?.ready?.value != true) delay(200)
        }
    }

    /** Widget taps run here so they finish even after the tap's own screen has closed. */
    fun toggleTaskInBackground(taskId: String) {
        scope.launch {
            val s = storeWith(taskId) ?: return@launch
            s.toggleCompleted(taskId, keepHistory = prefs.state.value[Keys.saveRecurringHistory])
        }
    }

    fun toggleSubTaskInBackground(subTaskId: String) {
        scope.launch {
            val s = withTimeoutOrNull(8_000) {
                var found: Store? = null
                while (found == null) {
                    val cur = store
                    if (cur != null && cur.subTasks.value.any { it.id == subTaskId }) found = cur else delay(100)
                }
                found
            } ?: return@launch
            s.toggleSubTask(subTaskId, completeParent = prefs.state.value[Keys.subTasksCompleteTask])
        }
    }

    fun toggleExpandedInBackground(taskId: String) {
        scope.launch {
            val s = storeWith(taskId) ?: return@launch
            val task = s.tasks.value.firstOrNull { it.id == taskId } ?: return@launch
            s.setExpanded(taskId, !task.subTasksExpanded)
        }
    }

    /** Waits, for a few seconds at most, until the signed-in data has loaded this task. */
    private suspend fun storeWith(taskId: String): Store? = withTimeoutOrNull(8_000) {
        var found: Store? = null
        while (found == null) {
            val s = store
            if (s != null && s.hasTask(taskId)) found = s else delay(100)
        }
        found
    }

    /** MARK AS DONE from a reminder. The change is saved offline and synced when possible. */
    suspend fun completeTask(taskId: String) {
        val s = storeWith(taskId) ?: return
        val task = s.tasks.value.firstOrNull { it.id == taskId } ?: return
        if (!task.completed) s.setCompleted(task, true, keepHistory = prefs.state.value[Keys.saveRecurringHistory])
    }

    /** SNOOZE from a reminder. */
    suspend fun snoozeTask(taskId: String, until: Long) {
        storeWith(taskId)?.snooze(taskId, until)
    }

    /** Google sign-in. Returns an error message, or null on success. */
    suspend fun signIn(activity: Activity): String? = try {
        val option = GetSignInWithGoogleOption.Builder(getString(R.string.default_web_client_id)).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = CredentialManager.create(activity).getCredential(activity, request).credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            FirebaseAuth.getInstance().signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
            null
        } else {
            "Google did not return a sign-in token."
        }
    } catch (e: androidx.credentials.exceptions.GetCredentialException) {
        // Show Google's own reason. A setup problem also arrives as "cancelled",
        // so the generic wording alone would hide it.
        android.util.Log.w("MyTasks", "Google sign-in failed", e)
        "Sign-in did not finish.\n${e.javaClass.simpleName}\n${e.type}\n${e.message ?: "(no details)"}"
    } catch (e: Exception) {
        android.util.Log.w("MyTasks", "Sign-in failed", e)
        "Sign-in failed.\n${e.javaClass.simpleName}\n${e.message ?: "(no details)"}"
    }

    suspend fun signOut(activity: Activity) {
        FirebaseAuth.getInstance().signOut()
        try {
            CredentialManager.create(activity).clearCredentialState(ClearCredentialStateRequest())
        } catch (_: Exception) {
            // Nothing to clear.
        }
    }
}
