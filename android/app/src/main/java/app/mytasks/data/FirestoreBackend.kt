package app.mytasks.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges

/**
 * Firestore as the app's database. Its offline cache makes every read and
 * write work without a connection; listeners fire at once for local writes and
 * again when another device changes something.
 */
class FirestoreBackend(private val db: FirebaseFirestore, private val uid: String) : Backend {
    private val registrations = ArrayList<ListenerRegistration>()

    private fun collection(name: String) = db.collection("users").document(uid).collection(name)

    override fun start(onSnapshot: (String, Map<String, Map<String, Any?>>, Boolean) -> Unit) {
        for (name in Collections.ALL) {
            registrations += collection(name).addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, _ ->
                if (snapshot == null) return@addSnapshotListener
                val docs = HashMap<String, Map<String, Any?>>()
                for (doc in snapshot.documents) doc.data?.let { docs[doc.id] = it }
                onSnapshot(name, docs, !snapshot.metadata.isFromCache)
            }
        }
    }

    override fun put(collection: String, id: String, data: Map<String, Any?>) {
        // If the server already has a newer edit, the security rules reject this
        // write and Firestore restores the newer copy locally.
        collection(collection).document(id).set(data)
    }

    override fun stop() {
        registrations.forEach { it.remove() }
        registrations.clear()
    }
}
