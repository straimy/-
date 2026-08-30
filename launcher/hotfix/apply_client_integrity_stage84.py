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

helper = r'''fn managed_stage_from_name(name: &str, prefix: &str) -> Option<u32> {
    let rest = name.strip_prefix(prefix)?;
    let rest = rest.strip_prefix("stage")?;
    let digits: String = rest.chars().take_while(|ch| ch.is_ascii_digit()).collect();
    if digits.is_empty() {
        return None;
    }
    digits.parse().ok()
}

fn ggo_integrity_pair(root: &std::path::Path) -> Result<(String, String, String), String> {
    use std::collections::{BTreeMap, BTreeSet};

    let mods = root.join("mods");
    let entries = std::fs::read_dir(&mods)
        .map_err(|error| format!("cannot inspect managed GGO mods: {error}"))?;
    let mut cores = BTreeMap::<u32, std::path::PathBuf>::new();
    let mut uis = BTreeMap::<u32, std::path::PathBuf>::new();

    for entry in entries {
        let entry = entry.map_err(|error| format!("cannot inspect managed GGO mod: {error}"))?;
        let path = entry.path();
        if !path.is_file() {
            continue;
        }
        let Some(name) = path.file_name().and_then(|value| value.to_str()) else {
            continue;
        };
        if let Some(stage) = managed_stage_from_name(name, "gungloryonline-core-runtime-v1-") {
            cores.insert(stage, path.clone());
        }
        if let Some(stage) = managed_stage_from_name(name, "gungloryonline-ui-runtime-v1-") {
            uis.insert(stage, path);
        }
    }

    let core_stages: BTreeSet<u32> = cores.keys().copied().collect();
    let ui_stages: BTreeSet<u32> = uis.keys().copied().collect();
    if let Some(stage) = core_stages.intersection(&ui_stages).last().copied() {
        let core = cores.get(&stage).expect("complete Core stage must exist");
        let ui = uis.get(&stage).expect("complete UI stage must exist");
        let core_bytes = std::fs::read(core)
            .map_err(|error| format!("cannot read managed Core for integrity check: {error}"))?;
        let ui_bytes = std::fs::read(ui)
            .map_err(|error| format!("cannot read managed UI for integrity check: {error}"))?;
        return Ok((
            format!("runtime-stage{stage}"),
            hex::encode(Sha256::digest(core_bytes)),
            hex::encode(Sha256::digest(ui_bytes)),
        ));
    }

    Err("GGO managed Core/UI pair is incomplete. Repair the game before launching.".to_string())
}

'''

start = rust.find("fn ggo_integrity_pair(")
if start != -1:
    end = rust.find(anchor, start)
    if end == -1:
        raise SystemExit("launch_game anchor missing after integrity helper")
    helper_start = rust.rfind("fn managed_stage_from_name(", 0, start)
    if helper_start != -1:
        start = helper_start
    rust = rust[:start] + helper + rust[end:]
else:
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

already_applied = all(
    token in rust
    for token in (
        "fn ggo_integrity_pair(",
        '"GGO_CLIENT_BUILD_ID"',
        '"GGO_CORE_SHA256"',
        '"GGO_UI_SHA256"',
    )
)
if old in rust:
    rust = rust.replace(old, new, 1)
elif new not in rust and not already_applied:
    raise SystemExit("Stage76 child environment block missing")

for token in [
    "fn managed_stage_from_name(",
    "fn ggo_integrity_pair(",
    'format!("runtime-stage{stage}")',
    '"GGO_CLIENT_BUILD_ID"',
    '"GGO_CORE_SHA256"',
    '"GGO_UI_SHA256"',
    "Sha256::digest(core_bytes)",
    "Sha256::digest(ui_bytes)",
    "intersection(&ui_stages).last().copied()",
]:
    if token not in rust:
        raise SystemExit(f"Stage84 launcher integrity requirement missing: {token}")

RUST.write_text(rust, encoding="utf-8")
print("Applied GGO Stage84 launcher integrity metadata")
print(" - dynamically selects the highest complete same-stage managed Core/UI pair")
print(" - supports Stage100 and future stageNN runtime names without hard-coded ceilings")
print(" - hashes installed managed Core/UI immediately before ticket issuance")
print(" - fails closed on incomplete managed GGO runtime")
