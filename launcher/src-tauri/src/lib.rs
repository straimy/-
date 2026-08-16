mod core;
mod runtime;

use core::{
    bootstrap::BootstrapInfo,
    updater::{self, SyncReport, UpdatePlan},
};
use runtime::minecraft::{self, JavaRuntimeInfo, LaunchPreparation, RuntimeCheck};
use std::path::PathBuf;
use tauri::AppHandle;

#[tauri::command]
fn bootstrap_info() -> BootstrapInfo {
    BootstrapInfo::current()
}

#[tauri::command]
fn detect_java(custom_java: Option<String>) -> Vec<JavaRuntimeInfo> {
    minecraft::detect_java(custom_java.as_deref())
}

#[tauri::command]
fn check_runtime(install_dir: String, custom_java: Option<String>) -> RuntimeCheck {
    minecraft::check_runtime(&PathBuf::from(install_dir), custom_java.as_deref())
}

#[tauri::command]
fn prepare_launch(install_dir: String, custom_java: Option<String>) -> LaunchPreparation {
    minecraft::prepare_launch(&PathBuf::from(install_dir), custom_java.as_deref())
}

#[tauri::command]
async fn check_game(manifest_url: String, install_dir: String) -> Result<UpdatePlan, String> {
    let http = updater::client().map_err(|err| err.to_string())?;
    let manifest = updater::fetch_manifest(&http, &manifest_url)
        .await
        .map_err(|err| err.to_string())?;
    updater::build_plan(&manifest, &PathBuf::from(install_dir))
        .await
        .map_err(|err| err.to_string())
}

#[tauri::command]
async fn sync_game(
    app: AppHandle,
    manifest_url: String,
    install_dir: String,
) -> Result<SyncReport, String> {
    updater::sync(&app, &manifest_url, &PathBuf::from(install_dir), false)
        .await
        .map_err(|err| err.to_string())
}

#[tauri::command]
async fn repair_game(
    app: AppHandle,
    manifest_url: String,
    install_dir: String,
) -> Result<SyncReport, String> {
    updater::sync(&app, &manifest_url, &PathBuf::from(install_dir), true)
        .await
        .map_err(|err| err.to_string())
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .invoke_handler(tauri::generate_handler![
            bootstrap_info,
            detect_java,
            check_runtime,
            prepare_launch,
            check_game,
            sync_game,
            repair_game
        ])
        .run(tauri::generate_context!())
        .expect("error while running GunGloryOnline launcher");
}
