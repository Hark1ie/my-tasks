/**
 * Who is signed in and which data the app shows. The web version signs in
 * with Google's pop-up; the Windows app signs in through the system browser,
 * because Google does not allow sign-in inside an app's own window.
 */
import { initializeApp } from "firebase/app";
import {
  GoogleAuthProvider,
  getAuth,
  onAuthStateChanged,
  signInWithCredential,
  signInWithPopup,
  signOut as fbSignOut,
} from "firebase/auth";
import config from "../../firebase/web-config.json";
import { firestore, FirestoreBackend } from "./data/firestore";
import { settings } from "./data/settings";
import { MemoryBackend, Store } from "./data/store";
import { demoSeed } from "./demo";
import { DESKTOP_CLIENT_ID, DESKTOP_CLIENT_SECRET } from "./oauthClient";

export type Session =
  | { kind: "loading" }
  | { kind: "signedOut" }
  | { kind: "signedIn"; store: Store; email: string | null }
  | { kind: "demo"; store: Store };

export const isTauri = typeof window !== "undefined" && "__TAURI_INTERNALS__" in window;
export const isDemo = typeof location !== "undefined" && new URLSearchParams(location.search).has("demo");

function deviceId(): string {
  try {
    let id = localStorage.getItem("mytasks.deviceId");
    if (!id) {
      id = crypto.randomUUID();
      localStorage.setItem("mytasks.deviceId", id);
    }
    return id;
  } catch {
    return crypto.randomUUID();
  }
}

const app = initializeApp(config);
let current: Session = { kind: "loading" };
const listeners = new Set<() => void>();
let store: Store | null = null;

function set(s: Session) {
  current = s;
  listeners.forEach((fn) => fn());
}

export const session = {
  get: (): Session => current,
  subscribe: (fn: () => void) => {
    listeners.add(fn);
    return () => listeners.delete(fn);
  },
};

if (isDemo) {
  const s = new Store(new MemoryBackend(demoSeed()), "demo");
  s.start();
  settings.attach(null);
  set({ kind: "demo", store: s });
} else {
  onAuthStateChanged(getAuth(app), (user) => {
    store?.stop();
    store = null;
    if (!user) {
      settings.attach(null);
      set({ kind: "signedOut" });
      return;
    }
    const s = new Store(new FirestoreBackend(firestore(app), user.uid), deviceId());
    s.start();
    store = s;
    settings.attach(s);
    set({ kind: "signedIn", store: s, email: user.email });
  });
}

const b64url = (bytes: ArrayBuffer) =>
  btoa(String.fromCharCode(...new Uint8Array(bytes))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");

function randomString(): string {
  const a = new Uint8Array(32);
  crypto.getRandomValues(a);
  return b64url(a.buffer);
}

const DONE_PAGE = `<!doctype html><meta charset="utf-8"><title>My Tasks</title>
<body style="font-family:Segoe UI,Roboto,sans-serif;background:#000;color:#fff;display:flex;align-items:center;justify-content:center;height:90vh">
<div style="text-align:center"><h2>Signed in</h2><p>You can close this tab and go back to My Tasks.</p></div></body>`;

/** Desktop sign-in: system browser, a one-time local address for Google's reply, PKCE. */
async function desktopSignIn(): Promise<void> {
  if (!DESKTOP_CLIENT_ID) throw new Error("Sign-in for the PC app is not set up yet.");
  const { start, cancel, onUrl } = await import("@fabianlars/tauri-plugin-oauth");
  const { openUrl } = await import("@tauri-apps/plugin-opener");
  const { fetch: tauriFetch } = await import("@tauri-apps/plugin-http");

  const port = await start({ response: DONE_PAGE });
  const redirect = `http://127.0.0.1:${port}`;
  const verifier = randomString();
  const state = randomString();
  const challenge = b64url(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(verifier)));
  try {
    const reply = new Promise<string>((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error("Sign-in timed out.")), 5 * 60 * 1000);
      void onUrl((u) => {
        clearTimeout(timer);
        resolve(u);
      });
    });
    const auth = new URL("https://accounts.google.com/o/oauth2/v2/auth");
    auth.search = new URLSearchParams({
      client_id: DESKTOP_CLIENT_ID,
      redirect_uri: redirect,
      response_type: "code",
      scope: "openid email profile",
      code_challenge: challenge,
      code_challenge_method: "S256",
      state,
      prompt: "select_account",
    }).toString();
    await openUrl(auth.toString());

    const back = new URL(await reply);
    if (back.searchParams.get("state") !== state) throw new Error("Sign-in reply did not match. Please try again.");
    const code = back.searchParams.get("code");
    if (!code) throw new Error(back.searchParams.get("error") ?? "Sign-in was cancelled.");

    const res = await tauriFetch("https://oauth2.googleapis.com/token", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        code,
        client_id: DESKTOP_CLIENT_ID,
        client_secret: DESKTOP_CLIENT_SECRET,
        redirect_uri: redirect,
        grant_type: "authorization_code",
        code_verifier: verifier,
      }).toString(),
    });
    const body = (await res.json()) as { id_token?: string; error_description?: string; error?: string };
    if (!body.id_token) throw new Error(body.error_description ?? body.error ?? "Google did not return a sign-in token.");
    await signInWithCredential(getAuth(app), GoogleAuthProvider.credential(body.id_token));
  } finally {
    await cancel(port).catch(() => undefined);
  }
}

/** Returns an error message, or null on success. */
export async function signIn(): Promise<string | null> {
  try {
    if (isTauri) await desktopSignIn();
    else await signInWithPopup(getAuth(app), new GoogleAuthProvider());
    return null;
  } catch (e) {
    return e instanceof Error ? e.message : String(e);
  }
}

export async function signOut() {
  await fbSignOut(getAuth(app));
}
