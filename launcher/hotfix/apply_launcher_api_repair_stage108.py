#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LIB = ROOT / "src-tauri/src/lib.rs"
MOD = ROOT / "src-tauri/src/runtime/mod.rs"
UNIFIED = ROOT / "src-tauri/src/runtime/unified_surface.rs"

for path in (LIB, MOD, UNIFIED):
    if not path.is_file():
        raise SystemExit(f"missing Stage108 source: {path}")

lib = LIB.read_text(encoding="utf-8")

# The ready-file lifecycle lives in a dedicated runtime module so Stage76 cannot accidentally
# delete helper functions while canonicalizing launch_game.
if "    unified_surface,\n" not in lib:
    anchor = "    minecraft_process,\n"
    if anchor not in lib:
        raise SystemExit("Stage108 runtime import anchor missing")
    lib = lib.replace(anchor, anchor + "    unified_surface,\n", 1)
lib = lib.replace("let ready_file = unified_ready_file();", "let ready_file = unified_surface::ready_file();")
lib = lib.replace(
    "supervise_unified_surface(app, ready_file);",
    "unified_surface::supervise(app, ready_file);",
)

# Helpers from the first lifecycle prototype, if present, are obsolete after moving them into
# runtime/unified_surface.rs. Remove only that exact range and leave launch_game intact.
helper_start = lib.find("fn unified_ready_file() -> PathBuf {")
if helper_start != -1:
    helper_end = lib.find("#[tauri::command]\nasync fn launch_game(", helper_start)
    if helper_end == -1:
        raise SystemExit("Stage108 helper cleanup could not find launch_game")
    lib = lib[:helper_start] + lib[helper_end:]

# Restore the real auth/update API surface. An earlier experimental transform replaced this with
# calls to non-existent fetch_current_profile / remote plan/install functions. Keep the public
# Tauri command names that the existing React launcher already invokes.
start = lib.find("#[tauri::command]\nasync fn ggo_login(")
end = lib.find("pub fn run() {", start)
if start == -1 or end == -1 or end <= start:
    raise SystemExit("Stage108 auth/update command range missing")

commands = r'''#[tauri::command]
async fn ggo_login(
    store: State<'_, GgoSessionStore>,
    api_url: String,
    username: Option<String>,
    password: Option<String>,
) -> Result<GgoAuthStatus, String> {
    let http = updater::client().map_err(|error| error.to_string())?;
    match (
        username.as_deref().map(str::trim).filter(|value| !value.is_empty()),
        password.as_deref().filter(|value| !value.is_empty()),
    ) {
        (Some(username), Some(password)) => {
            ggo_auth::login_password(&http, &api_url, username, password, store.inner()).await
        }
        (None, None) => ggo_auth::login(&http, &api_url, store.inner()).await,
        _ => Err("Provide both GGO username and password, or neither for browser login".to_string()),
    }
}

#[tauri::command]
async fn ggo_auth_status(store: State<'_, GgoSessionStore>) -> Result<GgoAuthStatus, String> {
    let http = updater::client().map_err(|error| error.to_string())?;
    let api_url = BootstrapInfo::current().account_api_url;
    Ok(ggo_auth::status(&http, &api_url, store.inner()).await)
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

'''
lib = lib[:start] + commands + lib[end:]

# Keep generate_handler synchronized with the React surface.
for stale in ["            remote_content_status,\n", "            install_remote_content,\n"]:
    lib = lib.replace(stale, "")
if "            ggo_set_skin_source,\n" not in lib:
    lib = lib.replace(
        "            ggo_logout,\n",
        "            ggo_logout,\n            ggo_set_skin_source,\n",
        1,
    )
if "            check_game,\n" not in lib:
    lib = lib.replace(
        "            ggo_link_minecraft,\n",
        "            ggo_link_minecraft,\n            check_game,\n            sync_game,\n            repair_game,\n",
        1,
    )

# Remove the obsolete lib-level ready-file test; the dedicated module owns this test now.
test = r'''    #[test]
    fn unified_ready_file_is_unique_and_private_to_child_contract() {
        let first = unified_ready_file();
        let second = unified_ready_file();
        assert_ne!(first, second);
        assert!(first
            .file_name()
            .and_then(|v| v.to_str())
            .unwrap_or_default()
            .starts_with("ggo-ready-"));
    }
'''
lib = lib.replace(test, "")

# sleep moved into runtime/unified_surface.rs.
lib = lib.replace(
    "    time::{sleep, timeout, Duration},",
    "    time::{timeout, Duration},",
)

LIB.write_text(lib, encoding="utf-8")

mod = MOD.read_text(encoding="utf-8")
if "pub mod unified_surface;" not in mod:
    mod = mod.replace("pub mod official_server;\n", "pub mod official_server;\npub mod unified_surface;\n", 1)
MOD.write_text(mod, encoding="utf-8")

checks = [
    "unified_surface::ready_file()",
    "unified_surface::supervise(app, ready_file)",
    "ggo_auth::status(&http, &api_url, store.inner()).await",
    "ggo_auth::link_minecraft(",
    "async fn check_game(",
    "async fn sync_game(",
    "async fn repair_game(",
    "            ggo_set_skin_source,",
    "            check_game,",
    "            sync_game,",
    "            repair_game,",
]
final = LIB.read_text(encoding="utf-8")
for token in checks:
    if token not in final:
        raise SystemExit(f"Stage108 launcher API repair missing: {token}")
for forbidden in [
    "fetch_current_profile",
    "GgoAuthError",
    "ggo_remote_install::plan",
    "ggo_remote_install::install",
    "GgoAuthStatus::signed_out",
    "session.linked_minecraft",
]:
    if forbidden in final:
        raise SystemExit(f"Stage108 stale launcher API survived: {forbidden}")

print("Stage108 launcher API repair applied")
print(" - stale GGO session validation uses the real ggo_auth::status API")
print(" - Minecraft linking uses the Microsoft access token contract")
print(" - check/sync/repair commands use core::updater and finalize the managed runtime")
print(" - unified ready lifecycle moved to runtime/unified_surface.rs")
