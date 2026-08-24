#!/usr/bin/env python3
from pathlib import Path

# This transform is intentionally rustfmt-clean; Stage84/81/87/88 all gate it.
ROOT = Path(".") if Path("src-tauri/src/lib.rs").is_file() else Path("launcher")
RUST = ROOT / "src-tauri/src/lib.rs"
if not RUST.is_file():
    raise SystemExit(f"launcher Rust source missing: {RUST}")

rust = RUST.read_text(encoding="utf-8")

anchor = "#[tauri::command]\nasync fn launch_game("
if anchor not in rust:
    raise SystemExit("launch_game anchor missing; apply Stage76 first")

helper = r'''fn ggo_integrity_pair(root: &std::path::Path) -> Result<(String, String, String), String> {
    let mods = root.join("mods");
    let candidates = [
        (
            "runtime-stage96",
            "gungloryonline-core-runtime-v1-stage96-channel-sync.jar",
            "gungloryonline-ui-runtime-v1-stage96.jar",
        ),
        (
            "runtime-stage85",
            "gungloryonline-core-runtime-v1-stage85.jar",
            "gungloryonline-ui-runtime-v1-stage85.jar",
        ),
        (
            "runtime-stage77",
            "gungloryonline-core-runtime-v1-stage77.jar",
            "gungloryonline-ui-runtime-v1-stage77.jar",
        ),
        (
            "runtime-stage68-69",
            "gungloryonline-core-runtime-v1-stage68.jar",
            "gungloryonline-ui-runtime-v1-stage69.jar",
        ),
    ];
    for (build_id, core_name, ui_name) in candidates {
        let core = mods.join(core_name);
        let ui = mods.join(ui_name);
        if core.is_file() && ui.is_file() {
            let core_bytes = std::fs::read(&core).map_err(|error| {
                format!("cannot read managed Core for integrity check: {error}")
            })?;
            let ui_bytes = std::fs::read(&ui)
                .map_err(|error| format!("cannot read managed UI for integrity check: {error}"))?;
            return Ok((
                build_id.to_string(),
                hex::encode(Sha256::digest(core_bytes)),
                hex::encode(Sha256::digest(ui_bytes)),
            ));
        }
    }
    Err("GGO managed Core/UI pair is incomplete. Repair the game before launching.".to_string())
}

'''

if "fn ggo_integrity_pair(" not in rust:
    rust = rust.replace(anchor, helper + anchor, 1)

old = '''    let child_environment = vec![
        ("GGO_GAME_TICKET".to_string(), ticket.ticket),
        (
            "GGO_GAME_TICKET_EXPIRES_AT".to_string(),
            expires_at.to_string(),
        ),
    ];'''
new = '''    let (build_id, core_sha256, ui_sha256) = ggo_integrity_pair(&root)?;
    let child_environment = vec![
        ("GGO_GAME_TICKET".to_string(), ticket.ticket),
        (
            "GGO_GAME_TICKET_EXPIRES_AT".to_string(),
            expires_at.to_string(),
        ),
        ("GGO_CLIENT_BUILD_ID".to_string(), build_id),
        ("GGO_CORE_SHA256".to_string(), core_sha256),
        ("GGO_UI_SHA256".to_string(), ui_sha256),
    ];'''
if old not in rust and new not in rust:
    raise SystemExit("Stage76 child environment block missing")
if old in rust:
    rust = rust.replace(old, new, 1)

for token in [
    "fn ggo_integrity_pair(",
    '"runtime-stage96"',
    '"runtime-stage85"',
    '"runtime-stage77"',
    '"runtime-stage68-69"',
    '"GGO_CLIENT_BUILD_ID"',
    '"GGO_CORE_SHA256"',
    '"GGO_UI_SHA256"',
    "Sha256::digest(core_bytes)",
    "Sha256::digest(ui_bytes)",
]:
    if token not in rust:
        raise SystemExit(f"Stage84 launcher integrity requirement missing: {token}")

RUST.write_text(rust, encoding="utf-8")
print("Applied GGO Stage84 launcher integrity metadata")
print(" - hashes installed managed Core/UI immediately before Java launch")
print(" - passes bounded build id + SHA-256 values to the child process")
print(" - supports Stage96 channel-sync, Stage85 candidate, Stage77 beta and Stage68/69 managed pairs")
print(" - fails closed on an incomplete managed GGO pair")
