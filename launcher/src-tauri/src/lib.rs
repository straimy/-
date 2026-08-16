mod core;
mod runtime;

use core::bootstrap::BootstrapInfo;

#[tauri::command]
fn bootstrap_info() -> BootstrapInfo {
    BootstrapInfo::current()
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .invoke_handler(tauri::generate_handler![bootstrap_info])
        .run(tauri::generate_context!())
        .expect("error while running GunGloryOnline launcher");
}
