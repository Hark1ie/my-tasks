/**
 * Firestore as the app's database, with its offline cache: every read and
 * write works without a connection and syncs when one comes back. Listeners
 * fire at once for local writes and again when another device changes data.
 */
import type { FirebaseApp } from "firebase/app";
import {
  type Firestore,
  type Unsubscribe,
  collection,
  doc,
  initializeFirestore,
  onSnapshot,
  persistentLocalCache,
  persistentMultipleTabManager,
  setDoc,
} from "firebase/firestore";
import { type Backend, type Doc, Collections } from "./store";

let db: Firestore | null = null;

export function firestore(app: FirebaseApp): Firestore {
  if (!db) {
    db = initializeFirestore(app, {
      localCache: persistentLocalCache({ tabManager: persistentMultipleTabManager() }),
    });
  }
  return db;
}

export class FirestoreBackend implements Backend {
  private unsubs: Unsubscribe[] = [];

  constructor(
    private readonly db: Firestore,
    private readonly uid: string,
  ) {}

  start(onSnapshotFn: (c: string, d: Map<string, Doc>, fromServer: boolean) => void) {
    for (const name of Object.values(Collections)) {
      this.unsubs.push(
        onSnapshot(
          collection(this.db, "users", this.uid, name),
          { includeMetadataChanges: true },
          (snap) => {
            const docs = new Map<string, Doc>();
            snap.forEach((d) => docs.set(d.id, d.data() as Doc));
            onSnapshotFn(name, docs, !snap.metadata.fromCache);
          },
          (err) => console.warn("Sync error on", name, err),
        ),
      );
    }
  }

  put(name: string, id: string, data: Doc) {
    // If the server already has a newer edit, the security rules reject this
    // write and Firestore restores the newer copy here.
    setDoc(doc(this.db, "users", this.uid, name, id), data).catch((err) => console.warn("Write rejected", name, id, err));
  }

  stop() {
    this.unsubs.forEach((u) => u());
    this.unsubs = [];
  }
}
