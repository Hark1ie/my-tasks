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
- Authentication: Google sign-in enabled.
- Apps: Android `app.mytasks` (signing fingerprint added) and web "My Tasks Web". Connection details are in `android/app/src/main/res/values/firebase_config.xml` and `firebase/web-config.json`.
- The Android app currently uses the project's web API key. If sign-in ever reports an API key error, switch to the Android key from the console.

## Builds

- Repo: `Hark1ie/my-tasks` (private). Every push to `main` that touches `android/` builds a signed APK and publishes it on the Releases page as `android-<build number>`.
- The signing key is kept in `android/signing/` because repository secrets could not be set from the build session. Keep the repo private.
- Release `android-1` was signed with a temporary key; ignore it.

## Milestones

1. Data model, Firebase project, security rules, shared sync logic with tests. **Done.**
2. Android main screen. **Build 5 (16 Nov 2026), waiting for the owner's test on the phone.** Working: Google sign-in, synced lists, sub-list tabs, add/edit/complete tasks, quick add with line splitting, drag reorder, swipe to delete with undo, list menu (sort, complete all, uncheck, hide/remove completed, delete all), share list, themes. Still to do from this milestone: reorder task lists and sub-lists, move and duplicate sub-list, select multiple, search. New task and edit task are simple title dialogs until milestone 3.
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
- Names of the first list and sub-list on a fresh install (currently "To do" and "Sub List 1").
- Swipe-to-delete background and icons.
