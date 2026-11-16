package app.mytasks

import android.app.Activity
import android.app.Application
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import app.mytasks.data.FirestoreBackend
import app.mytasks.data.MemoryBackend
import app.mytasks.data.Prefs
import app.mytasks.data.Store
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

class App : Application() {
    lateinit var prefs: Prefs
        private set

    private val _session = MutableStateFlow<Session>(Session.SignedOut)
    val session: StateFlow<Session> = _session

    private var store: Store? = null

    /** False when this build has no Firebase connection details. */
    val syncConfigured: Boolean
        get() = FirebaseApp.getApps(this).isNotEmpty() && getString(R.string.default_web_client_id).isNotEmpty()

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        if (!syncConfigured) {
            _session.value = Session.Local(Store(MemoryBackend(), prefs.deviceId).also { it.start() })
            return
        }
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            val user = auth.currentUser
            store?.stop()
            store = null
            _session.value = if (user == null) {
                Session.SignedOut
            } else {
                val s = Store(FirestoreBackend(FirebaseFirestore.getInstance(), user.uid), prefs.deviceId)
                s.start()
                store = s
                Session.SignedIn(s, user.email)
            }
        }
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
