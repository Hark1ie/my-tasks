import { useState, useSyncExternalStore } from "react";
import { settings } from "./data/settings";
import { isTauri, session, signIn, signOut } from "./session";
import { Main } from "./ui/Main";
import { Widget } from "./ui/Widget";

function SignIn() {
  const s = useSyncExternalStore(settings.subscribe, settings.get);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const theme = ["light", "dark", "black", "dark", "black"][s.theme] ?? "black";
  return (
    <div className={`signin theme-${theme}`}>
      <h1>My Tasks</h1>
      <p>Sign in with the same Google account as on your phone to see your tasks here and keep them in sync.</p>
      {busy ? (
        <p>{isTauri ? "Finish signing in in your browser…" : "Signing in…"}</p>
      ) : (
        <button className="btn" onClick={async () => { setBusy(true); setError(null); setError(await signIn()); setBusy(false); }}>Sign in with Google</button>
      )}
      {error && <div className="error">{error}</div>}
    </div>
  );
}

export function App({ widget = false }: { widget?: boolean }) {
  const s = useSyncExternalStore(session.subscribe, session.get);
  if (widget) return <Widget store={s.kind === "signedIn" || s.kind === "demo" ? s.store : null} />;
  switch (s.kind) {
    case "loading":
      return <div className="signin theme-black"><p>Loading…</p></div>;
    case "signedOut":
      return <SignIn />;
    case "demo":
      return <Main store={s.store} account={null} onSignOut={null} />;
    case "signedIn":
      return <Main store={s.store} account={s.email} onSignOut={() => void signOut()} />;
  }
}
