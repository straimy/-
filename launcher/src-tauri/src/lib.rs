mod core;
mod runtime;

use core::{
    bootstrap::BootstrapInfo,
    microsoft_auth::{self, MicrosoftLoginResult, MicrosoftSessionStore},
    updater::{self, SyncReport, UpdatePlan},
};
use runtime::{
    ggo_local_install::{self, LocalInstallReport},
    minecraft::{self, JavaRuntimeInfo, LaunchPreparation, RuntimeCheck},
    minecraft_install::{self, RuntimeInstallReport},
    minecraft_launch::{self, LaunchCommandPreview, LaunchOptions, LaunchResult},
    minecraft_process,
};
use std::path::PathBuf;
use tauri::{AppHandle, State};

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
async fn install_runtime(
    app: AppHandle,
    install_dir: String,
    custom_java: Option<String>,
) -> Result<RuntimeInstallReport, String> {
    let http = updater::client().map_err(|err| err.to_string())?;
    minecraft_install::install_runtime(
        &app,
        &http,
        &PathBuf::from(install_dir),
        custom_java.as_deref(),
    )
    .await
    .map_err(|error| error.to_string())
}

#[tauri::command]
async fn install_local_ggo(
    package_zip: String,
    resource_pack_zip: String,
    install_dir: String,
) -> Result<LocalInstallReport, String> {
    tokio::task::spawn_blocking(move || {
        ggo_local_install::install_local(
            &PathBuf::from(package_zip),
            &PathBuf::from(resource_pack_zip),
            &PathBuf::from(install_dir),
        )
    })
    .await
    .map_err(|error| format!("local installer task failed: {error}"))?
    .map_err(|error| error.to_string())
}

#[tauri::command]
async fn preview_minecraft_launch(
    store: State<'_, MicrosoftSessionStore>,
    install_dir: String,
    custom_java: Option<String>,
    options: LaunchOptions,
) -> Result<LaunchCommandPreview, String> {
    let session = store
        .snapshot()
        .await
        .ok_or_else(|| "Minecraft account is not authenticated".to_string())?;
    minecraft_launch::preview(
        &PathBuf::from(install_dir),
        custom_java.as_deref(),
        &session,
        &options,
    )
    .map_err(|error| error.to_string())
}

#[tauri::command]
async fn launch_minecraft(
    store: State<'_, MicrosoftSessionStore>,
    install_dir: String,
    custom_java: Option<String>,
    options: LaunchOptions,
) -> Result<LaunchResult, String> {
    let session = store
        .snapshot()
        .await
        .ok_or_else(|| "Minecraft account is not authenticated".to_string())?;
    minecraft_process::launch_with_natives(
        &PathBuf::from(install_dir),
        custom_java.as_deref(),
        &session,
        &options,
    )
    .map_err(|error| error.to_string())
}

#[tauri::command]
async fn microsoft_login(
    store: State<'_, MicrosoftSessionStore>,
) -> Result<MicrosoftLoginResult, String> {
    let http = updater::client().map_err(|err| err.to_string())?;
    microsoft_auth::login(&http, store.inner())
        .await
        .map_err(|err| err.to_string())
}

#[tauri::command]
async fn microsoft_auth_status(
    store: State<'_, MicrosoftSessionStore>,
) -> Result<MicrosoftLoginResult, String> {
    Ok(match store.snapshot().await {
        Some(session) => MicrosoftLoginResult {
            authenticated: !session.minecraft_access_token.is_empty(),
            expires_in_seconds: session.expires_in_seconds,
            refresh_available: session.refresh_token.is_some(),
            minecraft_profile: Some(session.minecraft_profile),
        },
        None => MicrosoftLoginResult {
            authenticated: false,
            expires_in_seconds: 0,
            refresh_available: false,
            minecraft_profile: None,
        },
    })
}

#[tauri::command]
async fn microsoft_logout(store: State<'_, MicrosoftSessionStore>) -> Result<(), String> {
    store.clear().await;
    Ok(())
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
        .manage(MicrosoftSessionStore::default())
        .invoke_handler(tauri::generate_handler![
            bootstrap_info,
            detect_java,
            check_runtime,
            prepare_launch,
            install_runtime,
            install_local_ggo,
            preview_minecraft_launch,
            launch_minecraft,
            microsoft_login,
            microsoft_auth_status,
            microsoft_logout,
            check_game,
            sync_game,
            repair_game
        ])
        .run(tauri::generate_context!())
        .expect("error while running GunGloryOnline launcher");
}
