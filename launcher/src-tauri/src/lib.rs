mod client_tools;
mod core;
mod runtime;

use core::{
    bootstrap::BootstrapInfo,
    ggo_auth::{self, GgoAuthStatus, GgoSessionStore, MinecraftLinkResult},
    identity_bridge,
    launcher_update::{self, LauncherUpdateStatus},
    microsoft_auth::{
        self, MicrosoftLoginResult, MicrosoftSession, MicrosoftSessionStore, MinecraftProfile,
    },
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
use sha2::{Digest, Sha256};
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
fn open_client_folder(install_dir: String, kind: String) -> Result<(), String> {
    client_tools::open_client_folder(&PathBuf::from(install_dir), &kind)
}

#[tauri::command]
fn list_client_files(
    install_dir: String,
    kind: String,
) -> Result<Vec<client_tools::ClientFileEntry>, String> {
    client_tools::list_client_files(&PathBuf::from(install_dir), &kind)
}

#[tauri::command]
fn set_mod_enabled(
    install_dir: String,
    file_name: String,
    enabled: bool,
) -> Result<client_tools::ClientFileEntry, String> {
    client_tools::set_mod_enabled(&PathBuf::from(install_dir), &file_name, enabled)
}

#[tauri::command]
fn read_latest_log(install_dir: String) -> Result<client_tools::LogSnapshot, String> {
    client_tools::read_latest_log(&PathBuf::from(install_dir))
}

#[tauri::command]
fn read_latest_crash(install_dir: String) -> Result<client_tools::LogSnapshot, String> {
    client_tools::read_latest_crash(&PathBuf::from(install_dir))
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
    microsoft_store: State<'_, MicrosoftSessionStore>,
    ggo_store: State<'_, GgoSessionStore>,
    install_dir: String,
    custom_java: Option<String>,
    options: LaunchOptions,
) -> Result<LaunchResult, String> {
    let session = microsoft_store
        .snapshot()
        .await
        .ok_or_else(|| "Minecraft account is not authenticated".to_string())?;
    let root = PathBuf::from(&install_dir);
    if let Some(ggo) = ggo_store.snapshot().await {
        identity_bridge::write(
            &root,
            Some(&ggo.profile.id),
            &ggo.profile.display_name,
            &ggo.profile.skin_source,
            "ggo",
        )?;
    } else {
        identity_bridge::write(
            &root,
            None,
            &session.minecraft_profile.name,
            "microsoft",
            "microsoft",
        )?;
    }
    minecraft_process::launch_with_natives(&root, custom_java.as_deref(), &session, &options)
        .map_err(|error| error.to_string())
}

#[tauri::command]
async fn launch_training(
    ggo_store: State<'_, GgoSessionStore>,
    install_dir: String,
    custom_java: Option<String>,
    display_name: String,
    mut options: LaunchOptions,
) -> Result<LaunchResult, String> {
    let root = PathBuf::from(&install_dir);
    let guest_name = {
        let value = display_name.trim();
        if value.is_empty() {
            "Guest".to_string()
        } else {
            value.chars().take(16).collect::<String>()
        }
    };
    let (runtime_name, runtime_id) = if let Some(ggo) = ggo_store.snapshot().await {
        identity_bridge::write(
            &root,
            Some(&ggo.profile.id),
            &ggo.profile.display_name,
            &ggo.profile.skin_source,
            "ggo",
        )?;
        (ggo.profile.display_name, ggo.profile.id.replace('-', ""))
    } else {
        identity_bridge::write(&root, None, &guest_name, "default", "guest")?;
        (guest_name.clone(), stable_local_profile_id(&guest_name))
    };
    options.connect_server = false;
    options.launch_mode = "training".to_string();
    let local_session = local_session(runtime_name, runtime_id);
    minecraft_process::launch_with_natives(&root, custom_java.as_deref(), &local_session, &options)
        .map_err(|error| error.to_string())
}

fn stable_local_profile_id(display_name: &str) -> String {
    let digest = Sha256::digest(format!("GunGloryOnline:{display_name}").as_bytes());
    hex::encode(&digest[..16])
}

fn local_session(name: String, id: String) -> MicrosoftSession {
    MicrosoftSession {
        access_token: String::new(),
        refresh_token: None,
        expires_in_seconds: 0,
        minecraft_access_token: "0".to_string(),
        minecraft_profile: MinecraftProfile { id, name },
    }
}

#[tauri::command]
async fn launch_game(
    _microsoft_store: State<'_, MicrosoftSessionStore>,
    ggo_store: State<'_, GgoSessionStore>,
    install_dir: String,
    custom_java: Option<String>,
    mut options: LaunchOptions,
    _server_address: Option<String>,
    training: bool,
    profile: Option<MinecraftProfile>,
) -> Result<LaunchResult, String> {
    if training {
        let display_name = profile
            .as_ref()
            .map(|value| value.name.clone())
            .unwrap_or_else(|| "Guest".to_string());
        return launch_training(ggo_store, install_dir, custom_java, display_name, options).await;
    }

    let ggo = ggo_store.snapshot().await.ok_or_else(|| {
        "GGO Account is required for official online play. Sign in through the GGO launcher first."
            .to_string()
    })?;
    options.connect_server = true;
    options.launch_mode = "online".to_string();

    let root = PathBuf::from(&install_dir);
    identity_bridge::write(
        &root,
        Some(&ggo.profile.id),
        &ggo.profile.display_name,
        &ggo.profile.skin_source,
        "ggo",
    )?;
    let runtime_id = ggo.profile.id.replace('-', "");
    let session = local_session(ggo.profile.display_name.clone(), runtime_id);

    // The frontend never selects the authentication endpoint and never receives the raw ticket.
    // Use the trusted launcher bootstrap endpoint, issue as late as possible, then give the ticket
    // only to the Java child process environment. Official network routing remains hard-coded in
    // minecraft_launch, so an untrusted UI catalog cannot redirect the credential elsewhere.
    let api_url = BootstrapInfo::current().account_api_url;
    let http = updater::client().map_err(|error| error.to_string())?;
    let ticket =
        ggo_auth::issue_game_ticket(&http, &api_url, "official-online", ggo_store.inner()).await?;
    if ticket.player_id != ggo.profile.id {
        return Err(
            "GGO launcher session changed while preparing the game. Sign in again.".to_string(),
        );
    }
    if ticket.expires_in < 15 {
        return Err(
            "GGO launcher session ticket lifetime is too short. Try PLAY ONLINE again.".to_string(),
        );
    }
    let child_environment = vec![("GGO_GAME_TICKET".to_string(), ticket.ticket)];
    minecraft_process::launch_with_natives_environment(
        &root,
        custom_java.as_deref(),
        &session,
        &options,
        &child_environment,
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
    username: Option<String>,
    password: Option<String>,
) -> Result<GgoAuthStatus, String> {
    let http = updater::client().map_err(|error| error.to_string())?;
    match (
        username.as_deref().map(str::trim).filter(|v| !v.is_empty()),
        password.as_deref().filter(|v| !v.is_empty()),
    ) {
        (Some(username), Some(password)) => {
            ggo_auth::login_password(&http, &api_url, username, password, store.inner()).await
        }
        (None, None) => ggo_auth::login(&http, &api_url, store.inner()).await,
        _ => {
            Err("Provide both GGO username and password, or neither for browser login".to_string())
        }
    }
}

#[tauri::command]
async fn ggo_auth_status(store: State<'_, GgoSessionStore>) -> Result<GgoAuthStatus, String> {
    Ok(ggo_auth::status(store.inner()).await)
}

#[tauri::command]
async fn ggo_logout(store: State<'_, GgoSessionStore>, api_url: String) -> Result<(), String> {
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
async fn ggo_link_minecraft(
    ggo_store: State<'_, GgoSessionStore>,
    microsoft_store: State<'_, MicrosoftSessionStore>,
    api_url: String,
) -> Result<MinecraftLinkResult, String> {
    let microsoft = microsoft_store
        .snapshot()
        .await
        .ok_or_else(|| "Microsoft/Minecraft account is not authenticated".to_string())?;
    let http = updater::client().map_err(|error| error.to_string())?;
    ggo_auth::link_minecraft(
        &http,
        &api_url,
        &microsoft.minecraft_access_token,
        ggo_store.inner(),
    )
    .await
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
            open_client_folder,
            list_client_files,
            set_mod_enabled,
            read_latest_log,
            read_latest_crash,
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
            launch_training,
            launch_game,
            microsoft_login,
            microsoft_auth_status,
            microsoft_logout,
            ggo_login,
            ggo_auth_status,
            ggo_logout,
            ggo_set_skin_source,
            ggo_link_minecraft,
            check_game,
            sync_game,
            repair_game
        ])
        .run(tauri::generate_context!())
        .expect("error while running GunGloryOnline launcher");
}
