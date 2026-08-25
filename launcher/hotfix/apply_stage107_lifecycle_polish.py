#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "src/App.tsx"
LIB = ROOT / "src-tauri/src/lib.rs"
CARGO = ROOT / "src-tauri/Cargo.toml"
TAURI = ROOT / "src-tauri/tauri.conf.json"
PACKAGE = ROOT / "package.json"

for p in (APP, LIB, CARGO, TAURI, PACKAGE):
    if not p.is_file():
        raise SystemExit(f"Stage107 launcher file missing: {p}")

app = APP.read_text(encoding="utf-8")

# Returning from the Java engine is a normal lifecycle transition, not a terminal launcher state.
# Restore the ordinary PLAY surface instead of leaving the user at "GunGloryOnline closed".
app = app.replace(
    'setStatus(process.exitCode===0?"GunGloryOnline closed":`GGO client exited (${process.exitCode??"signal"})`);',
    'setStatus(process.exitCode===0?"Ready":`GGO client exited (${process.exitCode??"signal"})`);',
)

# Be defensive if a previous canonicalization changed whitespace but preserved the old text.
app = app.replace('setStatus("GunGloryOnline closed")', 'setStatus("Ready")')
APP.write_text(app, encoding="utf-8")

lib = LIB.read_text(encoding="utf-8")

# Do not let a user-requested fullscreen Java window cover the Tauri startup surface while Forge
# is still showing its early Mojang/FML window. Start the child windowed, pass the preference only
# to the child environment, and let GgoUnifiedSurfaceBridge apply fullscreen after GGO is ready.
needle = '''    let child_environment = vec![
'''
if 'GGO_FULLSCREEN_AFTER_READY' not in lib:
    marker = '    let child_environment = vec![\n'
    idx = lib.find(marker, lib.find('async fn launch_game('))
    if idx < 0:
        raise SystemExit("Stage107 launcher could not locate online child environment")
    prefix = '''    let requested_fullscreen = options.fullscreen;
    options.fullscreen = false;
'''
    lib = lib[:idx] + prefix + lib[idx:]

    close = '''        (
            "GGO_READY_FILE".to_string(),
            ready_file.to_string_lossy().into_owned(),
        ),
    ];'''
    replacement = '''        (
            "GGO_READY_FILE".to_string(),
            ready_file.to_string_lossy().into_owned(),
        ),
        (
            "GGO_FULLSCREEN_AFTER_READY".to_string(),
            if requested_fullscreen { "1" } else { "0" }.to_string(),
        ),
    ];'''
    if close not in lib:
        raise SystemExit("Stage107 launcher could not append deferred fullscreen env")
    lib = lib.replace(close, replacement, 1)

LIB.write_text(lib, encoding="utf-8")

# Distinguish the fixed launcher from the Stage106 prototype.
for path in (PACKAGE, TAURI):
    text = path.read_text(encoding="utf-8")
    text = text.replace('"version": "0.2.8"', '"version": "0.2.9"', 1)
    path.write_text(text, encoding="utf-8")
text = CARGO.read_text(encoding="utf-8")
text = text.replace('version = "0.2.8"', 'version = "0.2.9"', 1)
CARGO.write_text(text, encoding="utf-8")

app = APP.read_text(encoding="utf-8")
lib = LIB.read_text(encoding="utf-8")
checks = {
    "normal exit restores ready": 'process.exitCode===0?"Ready"' in app or 'setStatus("Ready")' in app,
    "deferred fullscreen env": '"GGO_FULLSCREEN_AFTER_READY".to_string()' in lib,
    "fullscreen forced windowed before spawn": 'options.fullscreen = false;' in lib,
    "fullscreen preference preserved": 'let requested_fullscreen = options.fullscreen;' in lib,
    "0.2.9 cargo": 'version = "0.2.9"' in CARGO.read_text(encoding="utf-8"),
    "0.2.9 tauri": '"version": "0.2.9"' in TAURI.read_text(encoding="utf-8"),
    "0.2.9 package": '"version": "0.2.9"' in PACKAGE.read_text(encoding="utf-8"),
}
for label, ok in checks.items():
    if not ok:
        raise SystemExit(f"Stage107 launcher failed check: {label}")

print("Applied GGO Stage107 launcher lifecycle polish")
for label in checks:
    print(f" - {label}: ok")
