# My Tasks: plan and decisions

A personal to-do app for Android, Windows and the web, with real-time sync and a widget on the phone and the PC desktop. Its behaviour, settings and defaults follow a reference Android to-do app; the code is written fresh and the icons come from the standard Material set.

## Decisions (from the owner, 16 Nov 2026)

| Topic | Decision |
|---|---|
| Name | My Tasks |
| Devices | OnePlus 11R on Android 16; one Windows 11 PC; web version for other computers |
| Sign-in | Google sign-in |
| Backend | Firebase on the free Spark plan only. No paid plan, so no Cloud Functions and no Cloud Storage |
| Widget freshness | Instant while the app is open. With the app closed, the phone widget refreshes every 15 minutes and when the app opens. An optional "always-on sync" switch makes it instant at the cost of a permanent notification |
| Attachments | Stay on the device they were added on |
| Shared lists | Left out; data model kept compatible |
| Import | None. The app starts empty, pre-set to match the owner's two screenshots (Black theme) |
| Conflicts | Newest edit wins, per item |
| Removed | Premium screens and billing, ads and analytics, rate/donate/sale pop-ups, licence check, developer screen. Tags are unlimited |
| Languages | English only |
| Desktop widget | Pinned to the desktop under other windows, starts at login, tray icon, always-on-top toggle, list switcher in the header |
| Builds | GitHub Actions builds the APK and the Windows installer; the owner only downloads them |

## Tech stack

- Android: Kotlin, Jetpack Compose, widgets in RemoteViews/Glance, WorkManager, AlarmManager.
- Desktop and web: Tauri with React and TypeScript; the same UI is deployed to Firebase Hosting.
- Sync: Firestore real-time listeners with its built-in offline storage as the local database on every device.
- Shared logic: `core/` (TypeScript) is the reference implementation; Android mirrors it in Kotlin with the same test cases.

## Firebase project

- Project: My Tasks (`my-tasks-32155`), Spark plan.
- Firestore: Standard edition, `(default)` database, location asia-south1 (Mumbai), production mode.
- Security rules: published from `firebase/firestore.rules` on 16 Nov 2026.
- Still to do in the console: enable Google as a sign-in provider; register the web app and the Android app (the Android app needs the signing key fingerprint from the first build).

## Milestones

1. Data model, Firebase project, security rules, shared sync logic with tests. **Done except Google sign-in provider and app registration, which move to the start of milestone 2.**
2. Android main screen: lists, sub-list tabs, tasks, quick add, drag reorder, swipe delete, themes. First installable APK.
3. Task detail, reminders, alarms, repeat, notifications, quick tile, shortcut.
4. Settings screen.
5. Android widgets (List, Large, Calendar).
6. Desktop and web app.
7. Desktop widget.
8. Filtered lists, tags, Deleted Items, search, backup and export.
9. Parity check against the reference app and the owner's screenshots, with a table of differences.

## Open points to confirm against the reference app

- Repeating tasks: whether completing an overdue task skips to the next future date (current behaviour) or steps once.
- When an all-day reminder fires.
- Exact text layout of "share list".
- Percentage heading format.
