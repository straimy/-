#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(".") if Path("src-tauri/src/lib.rs").is_file() else Path("launcher")
RUST = ROOT / "src-tauri/src/lib.rs"
AUTH = ROOT / "src-tauri/src/core/ggo_auth.rs"
for path in (RUST, AUTH):
    if not path.is_file():
        raise SystemExit(f"missing launcher source: {path}")

rust = RUST.read_text(encoding="utf-8")
auth = AUTH.read_text(encoding="utf-8")

old_request = '''struct GameTicketRequest<'a> {
    audience: &'a str,
}'''
new_request = '''struct GameTicketRequest<'a> {
    audience: &'a str,
    build_id: &'a str,
    core_sha256: &'a str,
    ui_sha256: &'a str,
}'''
if old_request in auth:
    auth = auth.replace(old_request, new_request, 1)
elif new_request not in auth:
    raise SystemExit("GameTicketRequest shape not found")

old_sig = '''pub async fn issue_game_ticket(
    http: &Client,
    api_url: &str,
    audience: &str,
    store: &GgoSessionStore,
) -> Result<GameTicket, String> {'''
new_sig = '''pub async fn issue_game_ticket(
    http: &Client,
    api_url: &str,
    audience: &str,
    build_id: &str,
    core_sha256: &str,
    ui_sha256: &str,
    store: &GgoSessionStore,
) -> Result<GameTicket, String> {'''
if old_sig in auth:
    auth = auth.replace(old_sig, new_sig, 1)
elif new_sig not in auth:
    raise SystemExit("issue_game_ticket signature not found")

old_json = '.json(&GameTicketRequest { audience })'
new_json = '''.json(&GameTicketRequest {
            audience,
            build_id,
            core_sha256,
            ui_sha256,
        })'''
if old_json in auth:
    auth = auth.replace(old_json, new_json, 1)
elif new_json not in auth:
    raise SystemExit("game ticket request serialization not found")

old_issue = '''    let ticket =
        ggo_auth::issue_game_ticket(&http, &api_url, "official-online", ggo_store.inner()).await?;'''
new_issue = '''    let (build_id, core_sha256, ui_sha256) = ggo_integrity_pair(&root)?;
    let ticket = ggo_auth::issue_game_ticket(
        &http,
        &api_url,
        "official-online",
        &build_id,
        &core_sha256,
        &ui_sha256,
        ggo_store.inner(),
    )
    .await?;'''
if old_issue in rust:
    rust = rust.replace(old_issue, new_issue, 1)
elif new_issue not in rust:
    raise SystemExit("launcher ticket issue call not found")

old_late = '''    let (build_id, core_sha256, ui_sha256) = ggo_integrity_pair(&root)?;
    let child_environment = vec!['''
new_late = '''    let child_environment = vec!['''
# Remove only the second occurrence introduced by Stage84. The first one is now before ticket issuance.
if rust.count('let (build_id, core_sha256, ui_sha256) = ggo_integrity_pair(&root)?;') == 2:
    pos = rust.rfind(old_late)
    if pos == -1:
        raise SystemExit("late integrity calculation block not found")
    rust = rust[:pos] + rust[pos:].replace(old_late, new_late, 1)

required = [
    "build_id: &'a str",
    "core_sha256: &'a str",
    "ui_sha256: &'a str",
    '"official-online",\n        &build_id,\n        &core_sha256,\n        &ui_sha256,',
    '("GGO_CLIENT_BUILD_ID".to_string(), build_id)',
]
combined = auth + "\n" + rust
for token in required:
    if token not in combined:
        raise SystemExit(f"Stage90 ticket binding requirement missing: {token}")
if rust.count('let (build_id, core_sha256, ui_sha256) = ggo_integrity_pair(&root)?;') != 1:
    raise SystemExit("integrity metadata must be calculated exactly once before ticket issuance")

AUTH.write_text(auth, encoding="utf-8")
RUST.write_text(rust, encoding="utf-8")
print("Applied GGO Stage90 ticket-bound build identity")
print(" - hashes managed Core/UI before requesting a game ticket")
print(" - sends build id + Core/UI SHA-256 to trusted GGO Auth")
print(" - passes the same bound metadata to the Java child for server comparison")
