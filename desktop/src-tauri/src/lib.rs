/// The Windows app: a window showing the same interface as the web version,
/// plus what a browser cannot do: open the system browser for Google sign-in,
/// receive its reply on a one-time local address, and call Google's token
/// endpoint without browser restrictions.
#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .plugin(tauri_plugin_http::init())
        .plugin(tauri_plugin_oauth::init())
        .run(tauri::generate_context!())
        .expect("error while running My Tasks");
}
