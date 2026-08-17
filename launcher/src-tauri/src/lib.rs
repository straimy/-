mod core;
mod runtime;

use core::{
    bootstrap::BootstrapInfo,
    ggo_auth::{self, GgoAuthStatus, GgoSessionStore},
    identity_bridge,
    launcher_update::{self, LauncherUpdateStatus},
    microsoft_auth::{self, MicrosoftLoginResult, MicrosoftSessionStore},
    remote_content::{self, NewsFeed, ServerCatalog},
    updater::{self, SyncReport, UpdatePlan},
};
use runtime::{
    ggo_local_install::{self, LocalInstallReport},
    ggo_remote_install,
    minecraft::{self, JavaRuntimeInfo, LaunchPreparation, RuntimeCheck},
    minecraft_install::{self, RuntimeInstallReport},
    minecraft_launch::{self, LaunchCommandPreview, LaunchOptions, LaunchResult},
    minecraft_process,
};
use std::{path::PathBuf, time::Instant};
use tauri::{AppHandle, Manager, State};
use tauri_plugin_dialog::DialogExt;
use tokio::{
    net::TcpStream,
    time::{timeout, Duration},
};

#[tauri::command]
fn bootstrap_info() -> BootstrapInfo {
    BootstrapInfo::current()
}

#[tauri::command]
fn default_install_dir(app: AppHandle) -> Result<String, String> {
    app.path()
        .app_local_data_dir()
        .map(|path| path.join("game").to_string_lossy().into_owned())
        .map_err(|error| error.to_string())
}

#[tauri::command]
async fn pick_install_dir(app: AppHandle) -> Result<Option<String>, String> {
    app.dialog()
        .file()
        .blocking_pick_folder()
        .map(|path| {
            path.into_path()
                .map(|value| value.to_string_lossy().into_owned())
                .map_err(|error| error.to_string())
        })
        .transpose()
}

#[tauri::command]
async fn pick_zip_file(app: AppHandle) -> Result<Option<String>, String> {
    app.dialog()
        .file()
        .add_filter("GunGloryOnline FULL-INSTALL", &["zip"])
        .blocking_pick_file()
        .map(|path| {
            path.into_path()
                .map(|value| value.to_string_lossy().into_owned())
                .map_err(|error| error.to_string())
        })
        .transpose()
}

#[tauri::command]
fn open_game_folder(install_dir: String) -> Result<(), String> {
    let path = PathBuf::from(install_dir);
    std::fs::create_dir_all(&path).map_err(|error| error.to_string())?;
    open::that(path).map_err(|error| error.to_string())
}

#[tauri::command]
fn restart_launcher(app: AppHandle) {
    app.restart();
}

#[tauri::command]
fn local_game_installed(install_dir: String) -> bool {
    ggo_local_install::is_installed(&PathBuf::from(install_dir)).unwrap_or(false)
}

#[tauri::command]
fn write_identity_bridge(
    install_dir: String,
    ggo_player_id: Option<String>,
    display_name: String,
    skin_source: String,
    provider: String,
) -> Result<(), String> {
    identity_bridge::write(
        &PathBuf::from(install_dir),
        ggo_player_id.as_deref(),
        &display_name,
        &skin_source,
        &provider,
    )
}

#[tauri::command]
async fn ping_server(address: String) -> Result<u128, String> {
    let started = Instant::now();
    timeout(Duration::from_secs(3), TcpStream::connect(&address))
        .await
        .map_err(|_| "timeout".to_string())?
        .map_err(|error| error.to_string())?;
    Ok(started.elapsed().as_millis())
}

#[tauri::command]
async fn fetch_server_catalog(url: String) -> Result<ServerCatalog, String> {
    let http = updater::client().map_err(|error| error.to_string())?;
    remote_content::fetch_servers(&http, &url)
        .await
        .map_err(|error| error.to_string())
}

#[tauri::command]
async fn fetch_news_feed(url: String) -> Result<NewsFeed, String> {
    let http = updater::client().map_err(|error| error.to_string())?;
    remote_content::fetch_news(&http, &url)
        .await
        .map_err(|error| error.to_string())
}

#[tauri::command]
async fn check_launcher_update(app: AppHandle) -> Result<LauncherUpdateStatus, String> {
    launcher_update::check(&app).await
}

#[tauri::command]
async fn install_launcher_update(app: AppHandle) -> Result<bool, String> {
    launcher_update::install(&app).await
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
    let http = updater::client().map_err(|error| error.to_string())?;
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
    install_dir: String,
) -> Result<LocalInstallReport, String> {
    tokio::task::spawn_blocking(move || {
        ggo_local_install::install_local(&PathBuf::from(package_zip), &PathBuf::from(install_dir))
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
    let http = updater::client().map_err(|error| error.to_string())?;
    microsoft_auth::login(&http, store.inner())
        .await
        .map_err(|error| error.to_string())
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
async fn ggo_login(
    store: State<'_, GgoSessionStore>,
    api_url: String,
) -> Result<GgoAuthStatus, String> {
    let http = updater::client().map_err(|error| error.to_string())?;
    ggo_auth::login(&http, &api_url, store.inner()).await
}

#[tauri::command]
async fn ggo_auth_status(store: State<'_, GgoSessionStore>) -> Result<GgoAuthStatus, String> {
    Ok(ggo_auth::status(store.inner()).await)
}

#[tauri::command]
async fn ggo_logout(
    store: State<'_, GgoSessionStore>,
    api_url: String,
) -> Result<(), String> {
    let http = updater::client().map_err(|error| error.to_string())?;
    ggo_auth::logout(&http, &api_url, store.inner()).await
}

#[tauri::command]
async fn ggo_set_skin_source(
    store: State<'_, GgoSessionStore>,
    api_url: String,
    source: String,
) -> Result<GgoAuthStatus, String> {
    let http = updater::client().map_err(|error| error.to_string())?;
    ggo_auth::set_skin_source(&http, &api_url, &source, store.inner()).await
}

#[tauri::command]
async fn check_game(manifest_url: String, install_dir: String) -> Result<UpdatePlan, String> {
    let http = updater::client().map_err(|error| error.to_string())?;
    let manifest = updater::fetch_manifest(&http, &manifest_url)
        .await
        .map_err(|error| error.to_string())?;
    updater::build_plan(&manifest, &PathBuf::from(install_dir))
        .await
        .map_err(|error| error.to_string())
}

#[tauri::command]
async fn sync_game(
    app: AppHandle,
    manifest_url: String,
    install_dir: String,
) -> Result<SyncReport, String> {
    let root = PathBuf::from(&install_dir);
    let report = updater::sync(&app, &manifest_url, &root, false)
        .await
        .map_err(|error| error.to_string())?;
    ggo_remote_install::finalize_remote_install(&root).map_err(|error| error.to_string())?;
    Ok(report)
}

#[tauri::command]
async fn repair_game(
    app: AppHandle,
    manifest_url: String,
    install_dir: String,
) -> Result<SyncReport, String> {
    let root = PathBuf::from(&install_dir);
    let report = updater::sync(&app, &manifest_url, &root, true)
        .await
        .map_err(|error| error.to_string())?;
    ggo_remote_install::finalize_remote_install(&root).map_err(|error| error.to_string())?;
    Ok(report)
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_updater::Builder::new().build())
        .manage(MicrosoftSessionStore::default())
        .manage(GgoSessionStore::default())
        .invoke_handler(tauri::generate_handler![
            bootstrap_info,
            default_install_dir,
            pick_install_dir,
            pick_zip_file,
            open_game_folder,
            restart_launcher,
            local_game_installed,
            write_identity_bridge,
            ping_server,
            fetch_server_catalog,
            fetch_news_feed,
            check_launcher_update,
            install_launcher_update,
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
            ggo_login,
            ggo_auth_status,
            ggo_logout,
            ggo_set_skin_source,
            check_game,
            sync_game,
            repair_game
        ])
        .run(tauri::generate_context!())
        .expect("error while running GunGloryOnline launcher");
}
