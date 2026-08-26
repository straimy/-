#!/usr/bin/env python3
import runpy
from pathlib import Path

# CI invokes this patch in two contexts:
# - repository root: python3 launcher/hotfix/...
# - launcher working directory: python3 hotfix/...
# Resolve both without depending on the caller's cwd.
ROOT = Path(".") if Path("src/App.tsx").is_file() else Path("launcher")
RUST = ROOT / "src-tauri/src/lib.rs"
APP = ROOT / "src/App.tsx"

if not RUST.is_file() or not APP.is_file():
    raise SystemExit(f"launcher sources missing (resolved root: {ROOT.resolve()})")

rust = RUST.read_text(encoding="utf-8")
app = APP.read_text(encoding="utf-8")

# Remove public vanilla/Microsoft launch escape hatches. All player launches must flow through launch_game.
rust = rust.replace(
    "    minecraft_launch::{self, LaunchCommandPreview, LaunchOptions, LaunchResult},\n",
    "    minecraft_launch::{LaunchOptions, LaunchResult},\n",
)
rust = rust.replace(
    "use std::{path::PathBuf, time::Instant};",
    "use std::{\n    path::PathBuf,\n    time::{Instant, SystemTime, UNIX_EPOCH},\n};",
)

start = rust.find("#[tauri::command]\nasync fn preview_minecraft_launch(")
if start != -1:
    end = rust.find("#[tauri::command]\nasync fn launch_training(")
    if end == -1 or end <= start:
        raise SystemExit("legacy launch command block start found without valid end")
    rust = rust[:start] + rust[end:]
# Already-canonical source has no preview_minecraft_launch block; that is success, not failure.
rust = rust.replace("#[tauri::command]\nasync fn launch_training(", "async fn launch_training(", 1)

# launch_game is the only public game-start command. The official launch boots into the GGO menu first;
# the already-created short-lived ticket is held by the child until PLAY ONLINE connects.
rust = rust.replace("    _microsoft_store: State<'_, MicrosoftSessionStore>,\n", "", 1)
rust = rust.replace("    _server_address: Option<String>,\n", "", 1)
rust = rust.replace(
    "    options.connect_server = true;\n    options.launch_mode = \"online\".to_string();",
    "    options.connect_server = false;\n    options.launch_mode = \"online\".to_string();",
    1,
)
old_env = '    let child_environment = vec![("GGO_GAME_TICKET".to_string(), ticket.ticket)];'
new_env = '''    let expires_at = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map_err(|error| format!("system clock is invalid: {error}"))?
        .as_secs()
        .saturating_add(ticket.expires_in as u64);
    let child_environment = vec![
        ("GGO_GAME_TICKET".to_string(), ticket.ticket),
        (
            "GGO_GAME_TICKET_EXPIRES_AT".to_string(),
            expires_at.to_string(),
        ),
    ];'''
if old_env in rust:
    rust = rust.replace(old_env, new_env, 1)
elif "GGO_GAME_TICKET_EXPIRES_AT" not in rust:
    raise SystemExit("ticket environment block not found")
for entry in [
    "            preview_minecraft_launch,\n",
    "            launch_minecraft,\n",
    "            launch_training,\n",
]:
    rust = rust.replace(entry, "")

# One primary launcher action: INSTALL / UPDATE / PLAY. Mode choice happens in the GGO client.
for old, new in [
    ('play:"PLAY ONLINE"', 'play:"PLAY"'),
    ('play:"ИГРАТЬ ОНЛАЙН"', 'play:"ИГРАТЬ"'),
    ('play:"ГРАТИ ОНЛАЙН"', 'play:"ГРАТИ"'),
]:
    app = app.replace(old, new)

# A manifest with zero pending files is ready even when a remote/cache failure prevents
# checkedFiles from being populated. Requiring checkedFiles>0 caused a successful Stage96
# install to fall back to INSTALL after the final readiness pass.
old_ready = "setGameInstalled(plan.checkedFiles>0&&plan.files.length===0);"
new_ready = "setGameInstalled(plan.files.length===0);"
if old_ready in app:
    app = app.replace(old_ready, new_ready, 1)
elif new_ready not in app:
    raise SystemExit("launcher readiness expression not found")

old_launch = '''async function launch(training=false,server?:RemoteServer){if(!installDir){setStatus("Choose a GGO data folder");return;}if(!gameInstalled){setStatus(t.notInstalled);return;}setBusy(true);try{const runtimeCheck=await ensureRuntime();const profile=auth.minecraftProfile;const display=ggoAccount.connected?(ggoAccount.displayName||nickname.trim()||"GGOPlayer"):(profile?.name||nickname.trim()||"GGOPlayer");const provider=ggoAccount.connected?"ggo":profile?"microsoft":"guest";await invoke("write_identity_bridge",{installDir,ggoPlayerId:ggoAccount.connected?ggoAccount.playerId:null,displayName:display,skinSource:ggoAccount.skinSource,provider});const extraJvmArgs=extraJvmText.split(/\\r?\\n/).map(v=>v.trim()).filter(Boolean);const opts:LaunchOptions={ramMb,minRamMb:Math.min(minRamMb,ramMb),extraJvmArgs,width:resolution[0],height:resolution[1],fullscreen};const target=training?null:(server??selected??FALLBACK_SERVER);const launchProfile: MinecraftProfile = profile??{id:"guest",name:display};const result=await invoke<LaunchResult>("launch_game",{installDir,customJava:javaPath||runtimeCheck.java?.path||null,options:opts,serverAddress:target?.address||null,training,profile:launchProfile});setStatus(`Running · PID ${result.pid}`);}catch(error){setStatus(String(error));}finally{setBusy(false);}}'''
new_launch = '''async function launch(){if(!installDir){setStatus("Choose a GGO data folder");return;}if(!gameInstalled){setStatus(t.notInstalled);return;}if(!ggoAccount.connected){setStatus("GGO Account is required. Sign in first.");setPage("accounts");return;}setBusy(true);try{const runtimeCheck=await ensureRuntime();const profile=auth.minecraftProfile;const display=ggoAccount.displayName||nickname.trim()||"GGOPlayer";await invoke("write_identity_bridge",{installDir,ggoPlayerId:ggoAccount.playerId,displayName:display,skinSource:ggoAccount.skinSource,provider:"ggo"});const extraJvmArgs=extraJvmText.split(/\\r?\\n/).map(v=>v.trim()).filter(Boolean);const opts:LaunchOptions={ramMb,minRamMb:Math.min(minRamMb,ramMb),extraJvmArgs,width:resolution[0],height:resolution[1],fullscreen};const launchProfile:MinecraftProfile=profile??{id:"ggo",name:display};const result=await invoke<LaunchResult>("launch_game",{installDir,customJava:javaPath||runtimeCheck.java?.path||null,options:opts,training:false,profile:launchProfile});setStatus(`GGO Client · PID ${result.pid}`);}catch(error){setStatus(String(error));}finally{setBusy(false);}}'''
if old_launch in app:
    app = app.replace(old_launch, new_launch, 1)
else:
    # Stage106+ intentionally extends the canonical launch() with process lifecycle state,
    # readiness handoff, and launcher visibility control. Do not rewrite that newer function
    # back to the Stage76 byte-for-byte form. Validate its semantic contract instead.
    unified_launch_ok = all(token in app for token in [
        "async function launch(){",
        "if(!ggoAccount.connected)",
        'invoke<LaunchResult>("launch_game"',
        "training:false",
    ])
    if not unified_launch_ok:
        raise SystemExit("launcher launch() block not found")

old_home = '''<div className="home-actions"><button className="play-button" disabled={busy||!gameInstalled||updateAvailable} onClick={()=>void launch(false)}>{busy?t.preparing:t.play}</button><button className="training-button" disabled={busy||!gameInstalled} onClick={()=>void launch(true)}>{t.training}<small>{t.trainingHint}</small></button></div>'''
new_home = '''<div className="home-actions"><button className="play-button" disabled={busy||checkingGame} onClick={()=>void ((!gameInstalled||updateAvailable)?installGame():launch())}>{busy?t.preparing:!gameInstalled?t.install:updateAvailable?t.updateGame:t.play}</button></div>'''
if old_home in app:
    app = app.replace(old_home, new_home, 1)
elif new_home not in app:
    raise SystemExit("home action block not found")

old_card_actions = '''{(!gameInstalled||updateAvailable)&&<button disabled={busy} onClick={()=>void installGame()}>{installLabel}</button>}{gameInstalled&&!updateAvailable&&<button className="repair-link" disabled={busy} onClick={()=>void repairGame()}>{t.repair}</button>}'''
if old_card_actions in app:
    app = app.replace(old_card_actions, "", 1)
elif 'className="repair-link"' in app:
    raise SystemExit("unexpected repair action survived canonical home")

old_server_play = '''<button className="play-button compact" disabled={busy||!gameInstalled||updateAvailable} onClick={()=>void launch(false)}>{t.play}</button>'''
app = app.replace(old_server_play, "")

RUST.write_text(rust, encoding="utf-8")
APP.write_text(app, encoding="utf-8")

# Fail closed if any public launch bypass or old split-action home UI survives.
checks = {
    "preview_minecraft_launch": False,
    "async fn launch_minecraft(": False,
    "            launch_training,": False,
    "options.connect_server = true": False,
    "serverAddress:target": False,
    "onClick={()=>void launch(true)}": False,
    "className=\"repair-link\"": False,
    "setGameInstalled(plan.checkedFiles>0": False,
}
combined = rust + "\n" + app
for token, expected in checks.items():
    if (token in combined) != expected:
        raise SystemExit(f"stage76 launcher hardening failed for token: {token}")

for token in [
    'async fn launch_game(',
    'options.connect_server = false;',
    '("GGO_GAME_TICKET".to_string(), ticket.ticket)',
    'GGO_GAME_TICKET_EXPIRES_AT',
    'async function launch(){',
    'setGameInstalled(plan.files.length===0);',
    '?t.install:updateAvailable?t.updateGame:t.play',
]:
    if token not in combined:
        raise SystemExit(f"stage76 launcher requirement missing: {token}")

print("Applied GGO launcher Stage 76 menu-first beta hardening")
print(f" - resolved launcher root: {ROOT}")
print(" - idempotent on canonical and Stage106+ unified lifecycle launcher source")
print(" - one public game launch command")
print(" - official launch boots to GGO client menu before network connect")
print(" - absolute ticket expiry is passed to the child without exposing the ticket to UI")
print(" - launcher readiness is based on zero pending manifest files")
print(" - launcher home has one INSTALL / UPDATE / PLAY primary action")
print(" - Training and Repair removed from primary home surface")

# Stage76 is the canonical packaging entrypoint. Any workflow that applies menu-first hardening
# must also receive integrity metadata and ticket binding; building only Stage76 would create a
# launcher that looks correct but cannot complete the authenticated Online flow.
SCRIPT_DIR = Path(__file__).resolve().parent
for followup in ("apply_client_integrity_stage84.py", "apply_ticket_binding_stage90.py"):
    path = SCRIPT_DIR / followup
    if not path.is_file():
        raise SystemExit(f"required launcher hardening transform missing: {path}")
    runpy.run_path(str(path), run_name="__main__")

print("Applied canonical launcher chain: Stage76 -> Stage84 -> Stage90")
