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

## Automated test phone

- `.github/workflows/screens.yml` builds a test version of the app (no sign-in, data in memory), starts an Android 12L emulator on GitHub, runs `tools/screens.py` to tap through the main flows, and publishes the screenshots as release `screens-<run>`. It runs on every push that touches `tools/` or the workflow file.
- First used 16 Nov 2026 (runs 1 to 4). It showed the app starts without crashing and that quick add, the task screen, date picker, reminder and repeat dialogs, saving, ticking, new sub-list, both name dialogs, the overflow menu, Sort, the sidebar and Settings all work. It found two bugs, both fixed in build 15: typing went to the quick add box behind a new task screen, and the Priority row could run off a narrow screen.
- The reference app cannot be run this way: it only starts when installed from the Play Store (its start-up protection needs parts the single APK file does not contain). Comparisons use its layouts and the owner's screenshots.
- The test phone cannot check Google sign-in, sync, real notifications arriving with the app closed, or widgets on the home screen. Those still need the owner's phone.

## Milestones

1. Data model, Firebase project, security rules, shared sync logic with tests. **Done.**
2. Android main screen. **Build 7 (16 Nov 2026), waiting for the owner's test on the phone.** Working: Google sign-in, synced lists, sub-list tabs, add/complete tasks, quick add with line splitting, drag reorder, swipe to delete with undo, list menu (sort, complete all, uncheck, hide/remove completed, delete all), reorder task lists and sub-lists, move and duplicate sub-list, share list, themes. Still to do: Select Multiple (with the task actions: move to top/bottom, move, duplicate, share, highlight, delete) and search.
3. Task detail, reminders, alarms, repeat. **Part 1 in build 7:** task screen with title, notes, sub-tasks, list, due date and time, all day, reminder choice, reminder type, repeat (all kinds), repeat from, repeat until, priority, highlight, completed, created, SAVE & ADD, save-changes prompt; ticking a repeating task moves it to its next date. **Part 2 in build 12 (16 Nov 2026), not yet tested on the phone:** reminders as exact alarms kept in step with the tasks, notifications with MARK AS DONE / SNOOZE / DISMISS, snooze picker (5 to 120 minutes plus the custom duration), Alarm type that rings until answered with a full-screen alarm screen and the alarm duration setting, rescheduling after restart, update and clock change, "Reminder in:" message, notification permission request. Build 12 also has the reference app's List Name dialog (colour wheel with brightness) opened by tapping the list title, and the Sub List Name dialog with "Where should completed tasks go?" opened by tapping the open tab. **Still to do:** automatically increment reminder, mute alarm on call, summary notification, add tasks from notification bar, notification and alarm sound pickers, quick settings tile, app shortcut, attachment, add to calendar.
4. Settings screen. **Build 9 (16 Nov 2026):** full screen with the reference app's groups, order and options; synced settings are stored in `users/{uid}/settings/synced`. Owner confirmed sign-in and sync work on the phone (builds 5 to 7). Working now: theme, font size, keep screen on, vibrate, swipe to delete, quick add and multi-line, default list, main menu on back/open, move completed, display mode, item padding, delete confirmation, menu and sub-list heading info, insert position for tasks and sub-tasks, notes and reminder display, auto numbering, complete date, strikethrough, sub-task rules, new-task defaults, recurring history, task screen sections. Stored but not active yet: sync options, show calendar, remove deleted items, tag size, auto link, list colour, display date and time, all notification and alarm options. Placeholders: delete account, auto link colour, default sub list, sounds, backup, restore, CSV export.
5. Android widgets. **Build 17 (17 Nov 2026):** the reference app's three widgets: the small 1x1 one (sub-list name and done/total pill), the list widget (heading with list chooser, title with counter, add, gear; task rows with highlight, priority-tinted circle, due date, notes, sub-task count and expandable sub-tasks; yellow add button) and the calendar widget (tasks due on a day across all lists, previous/next day, tap the title for today). Widget Settings has the reference options and defaults (background and list item transparency 165/255, show add button on, move add button to heading off, font size, tag size). "Change To" picks the sub-list, and opens when a list widget is added. Ticking from the widget works without opening the app; tapping a row opens the task; add opens a new task in that sub-list (the calendar's on that day). Widgets follow the app theme and the list settings, redraw on every change, and refresh every 15 minutes when the app is closed (WorkManager, respecting "Sync on WiFi only"). Checked on the test phone (drawn inside a widget frame, tick from the widget). Not yet: tags in rows (tags not built), the optional always-on sync for instant updates while the app is closed, reference widget preview pictures in the picker.
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
- Task screen: colour of the title area, where SAVE & ADD sits, wording of the save-changes prompt, date and time picker style (the reference uses a different picker library), date format.
- Task row: how priority is shown (currently the circle is tinted), how a repeating task's sub-tasks behave when it rolls over (currently unticked).
