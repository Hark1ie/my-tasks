//! The Windows app: the main window (same interface as the web version), the
//! desktop widget window, a tray icon, start at login, and one running copy.
//! It also does what a browser cannot: open the system browser for Google
//! sign-in and receive its reply on a one-time local address.

use std::path::PathBuf;

use serde_json::{json, Value};
use tauri::menu::{CheckMenuItem, Menu, MenuItem, PredefinedMenuItem};
use tauri::tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent};
use tauri::{AppHandle, Manager, WindowEvent};
use tauri_plugin_autostart::{MacosLauncher, ManagerExt};
use tauri_plugin_window_state::StateFlags;

/// Small settings the Windows shell keeps for itself.
fn prefs_path(app: &AppHandle) -> Option<PathBuf> {
    app.path().app_config_dir().ok().map(|d| d.join("shell.json"))
}

fn load_prefs(app: &AppHandle) -> Value {
    prefs_path(app)
        .and_then(|p| std::fs::read_to_string(p).ok())
        .and_then(|s| serde_json::from_str(&s).ok())
        .unwrap_or_else(|| json!({}))
}

fn save_pref(app: &AppHandle, key: &str, value: Value) {
    let mut prefs = load_prefs(app);
    prefs[key] = value;
    if let Some(p) = prefs_path(app) {
        if let Some(dir) = p.parent() {
            let _ = std::fs::create_dir_all(dir);
        }
        let _ = std::fs::write(p, prefs.to_string());
    }
}

fn show_main(app: &AppHandle) {
    if let Some(w) = app.get_webview_window("main") {
        let _ = w.unminimize();
        let _ = w.show();
        let _ = w.set_focus();
    }
}

fn place_widget(app: &AppHandle, on_top: bool) {
    if let Some(w) = app.get_webview_window("widget") {
        let _ = w.set_always_on_bottom(!on_top);
        let _ = w.set_always_on_top(on_top);
    }
}

fn set_widget_visible(app: &AppHandle, visible: bool) {
    if let Some(w) = app.get_webview_window("widget") {
        let _ = if visible { w.show() } else { w.hide() };
    }
    save_pref(app, "widget", json!(visible));
}

#[tauri::command]
fn open_main(app: AppHandle) {
    show_main(&app);
}

#[tauri::command]
fn set_widget_on_top(app: AppHandle, on_top: bool) {
    place_widget(&app, on_top);
    save_pref(&app, "onTop", json!(on_top));
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_single_instance::init(|app, _args, _cwd| show_main(app)))
        .plugin(tauri_plugin_autostart::init(MacosLauncher::LaunchAgent, Some(vec!["--autostart"])))
        .plugin(
            tauri_plugin_window_state::Builder::default()
                .with_state_flags(StateFlags::all() & !StateFlags::VISIBLE & !StateFlags::DECORATIONS)
                .build(),
        )
        .plugin(tauri_plugin_opener::init())
        .plugin(tauri_plugin_http::init())
        .plugin(tauri_plugin_oauth::init())
        .invoke_handler(tauri::generate_handler![open_main, set_widget_on_top])
        .setup(|app| {
            let handle = app.handle().clone();
            let prefs = load_prefs(&handle);

            // Start at login is on from the first run; the tray menu turns it off.
            if prefs.get("autostartSet").is_none() {
                let _ = handle.autolaunch().enable();
                save_pref(&handle, "autostartSet", json!(true));
            }
            let started_at_login = std::env::args().any(|a| a == "--autostart");
            if !started_at_login {
                show_main(&handle);
            }

            let widget_on = prefs.get("widget").and_then(Value::as_bool).unwrap_or(true);
            let on_top = prefs.get("onTop").and_then(Value::as_bool).unwrap_or(false);
            place_widget(&handle, on_top);
            if widget_on {
                if let Some(w) = handle.get_webview_window("widget") {
                    let _ = w.show();
                }
            }

            let open = MenuItem::with_id(app, "open", "Open My Tasks", true, None::<&str>)?;
            let widget = CheckMenuItem::with_id(app, "widget", "Show desktop widget", true, widget_on, None::<&str>)?;
            let login = CheckMenuItem::with_id(
                app, "login", "Start when I sign in to Windows", true,
                handle.autolaunch().is_enabled().unwrap_or(false), None::<&str>,
            )?;
            let quit = MenuItem::with_id(app, "quit", "Quit", true, None::<&str>)?;
            let sep = PredefinedMenuItem::separator(app)?;
            let menu = Menu::with_items(app, &[&open, &widget, &login, &sep, &quit])?;

            let (widget_item, login_item) = (widget.clone(), login.clone());
            TrayIconBuilder::with_id("tray")
                .icon(app.default_window_icon().expect("app icon").clone())
                .tooltip("My Tasks")
                .menu(&menu)
                .show_menu_on_left_click(false)
                .on_menu_event(move |app, event| match event.id.as_ref() {
                    "open" => show_main(app),
                    "widget" => set_widget_visible(app, widget_item.is_checked().unwrap_or(true)),
                    "login" => {
                        let on = login_item.is_checked().unwrap_or(false);
                        let _ = if on { app.autolaunch().enable() } else { app.autolaunch().disable() };
                    }
                    "quit" => app.exit(0),
                    _ => {}
                })
                .on_tray_icon_event(|tray, event| {
                    if let TrayIconEvent::Click { button: MouseButton::Left, button_state: MouseButtonState::Up, .. } = event {
                        show_main(tray.app_handle());
                    }
                })
                .build(app)?;
            Ok(())
        })
        .on_window_event(|window, event| {
            // Closing the main window keeps My Tasks running in the tray, so the
            // widget stays live; Quit is in the tray menu.
            if let WindowEvent::CloseRequested { api, .. } = event {
                if window.label() == "main" {
                    api.prevent_close();
                    let _ = window.hide();
                }
            }
        })
        .run(tauri::generate_context!())
        .expect("error while running My Tasks");
}
