#!/usr/bin/env python3
from pathlib import Path
import re
import subprocess

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
# Stage106 has changed this block a few times, so match the semantic expression rather than one
# historical whitespace/minification layout. The transform is intentionally idempotent.
if 'process.exitCode===0?"Ready"' not in app and 'process.exitCode === 0 ? "Ready"' not in app:
    patterns = [
        r'setStatus\(process\.exitCode===0\?"GunGloryOnline closed":`GGO client exited \(\$\{process\.exitCode\?\?"signal"\}\)`\);',
        r'setStatus\(process\.exitCode\s*===\s*0\s*\?\s*"GunGloryOnline closed"\s*:\s*`GGO client exited \(\$\{process\.exitCode\s*\?\?\s*"signal"\}\)`\s*\);',
    ]
    replacement = 'setStatus(process.exitCode===0?"Ready":`GGO client exited (${process.exitCode??"signal"})`);'
    changed = False
    for pattern in patterns:
        app, count = re.subn(pattern, replacement, app, count=1)
        if count:
            changed = True
            break
    if not changed:
        old = 'setStatus("GunGloryOnline closed")'
        if old in app:
            app = app.replace(old, 'setStatus("Ready")', 1)
            changed = True
    if not changed:
        raise SystemExit("Stage107 App patch: normal-exit status anchor not found")

# The engine is an implementation detail. Launcher-visible copy must describe GGO, not the
# Minecraft/Forge substrate that the player should never need to interact with.
for old, new in [
    ('runtimeInstalling:"Installing client runtime 1.20.1 + Forge 47.4.10…"', 'runtimeInstalling:"Installing GGO runtime…"'),
    ('runtimeInstalling:"Устанавливаю runtime клиента 1.20.1 + Forge 47.4.10…"', 'runtimeInstalling:"Устанавливаю GGO runtime…"'),
    ('runtimeInstalling:"Встановлюю runtime клієнта 1.20.1 + Forge 47.4.10…"', 'runtimeInstalling:"Встановлюю GGO runtime…"'),
    ('<div><span>Client runtime</span><b>1.20.1 · Forge 47.4.10</b></div>', '<div><span>GGO Runtime</span><b>Managed automatically</b></div>'),
]:
    app = app.replace(old, new)
APP.write_text(app, encoding="utf-8")

lib = LIB.read_text(encoding="utf-8")

# Do not let a user-requested fullscreen Java window cover the Tauri startup surface while the
# engine is still bootstrapping. Start the child windowed, pass the preference only to the child
# environment, and let the GGO surface bridge apply fullscreen after GGO is ready.
if 'GGO_FULLSCREEN_AFTER_READY' not in lib:
    marker = '    let child_environment = vec![\n'
    launch_idx = lib.find('async fn launch_game(')
    idx = lib.find(marker, launch_idx)
    if idx < 0:
        raise SystemExit("Stage107 launcher could not locate online child environment")
    prefix = '''    let requested_fullscreen = options.fullscreen;
    options.fullscreen = false;
'''
    lib = lib[:idx] + prefix + lib[idx:]

    ready_block = '''        (
            "GGO_READY_FILE".to_string(),
            ready_file.to_string_lossy().into_owned(),
        ),
'''
    replacement = ready_block + '''        (
            "GGO_FULLSCREEN_AFTER_READY".to_string(),
            if requested_fullscreen { "1" } else { "0" }.to_string(),
        ),
'''
    env_idx = lib.find('    let child_environment = vec![', launch_idx)
    block_idx = lib.find(ready_block, env_idx)
    if block_idx < 0:
        raise SystemExit("Stage107 launcher could not append deferred fullscreen env")
    lib = lib[:block_idx] + replacement + lib[block_idx + len(ready_block):]
else:
    if 'let requested_fullscreen = options.fullscreen;' not in lib or 'options.fullscreen = false;' not in lib:
        raise SystemExit("Stage107 deferred fullscreen exists without windowed-start contract")

LIB.write_text(lib, encoding="utf-8")

# Distinguish the fixed launcher from Stage106. Accept already-upgraded sources on repeated runs.
def bump_json(path: Path) -> None:
    text = path.read_text(encoding="utf-8")
    if '"version": "0.2.9"' not in text:
        if '"version": "0.2.8"' not in text:
            raise SystemExit(f"Stage107 cannot bump version in {path}")
        text = text.replace('"version": "0.2.8"', '"version": "0.2.9"', 1)
        path.write_text(text, encoding="utf-8")

for path in (PACKAGE, TAURI):
    bump_json(path)

text = CARGO.read_text(encoding="utf-8")
if 'version = "0.2.9"' not in text:
    if 'version = "0.2.8"' not in text:
        raise SystemExit("Stage107 cannot bump Cargo version")
    text = text.replace('version = "0.2.8"', 'version = "0.2.9"', 1)
    CARGO.write_text(text, encoding="utf-8")

# Stage106/107 synthesize Rust directly into the temporary build tree. Normalize it here so every
# caller receives the same rustfmt-clean lifecycle source before cargo check/test/package.
subprocess.run(
    ["cargo", "fmt", "--manifest-path", str(CARGO), "--all"],
    cwd=ROOT,
    check=True,
)

app = APP.read_text(encoding="utf-8")
lib = LIB.read_text(encoding="utf-8")
checks = {
    "normal exit restores ready": 'process.exitCode===0?"Ready"' in app or 'process.exitCode === 0 ? "Ready"' in app or 'setStatus("Ready")' in app,
    "deferred fullscreen env": '"GGO_FULLSCREEN_AFTER_READY".to_string()' in lib,
    "fullscreen forced windowed before spawn": 'options.fullscreen = false;' in lib,
    "fullscreen preference preserved": 'let requested_fullscreen = options.fullscreen;' in lib,
    "launcher runtime copy is GGO-owned": '<span>GGO Runtime</span><b>Managed automatically</b>' in app,
    "no visible Forge fingerprint": '1.20.1 · Forge 47.4.10' not in app and 'runtime 1.20.1 + Forge 47.4.10' not in app,
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
print(" - generated Rust: rustfmt clean")
