#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "src/App.tsx"
CSS = ROOT / "src/polish.css"
LIB = ROOT / "src-tauri/src/lib.rs"
CARGO = ROOT / "src-tauri/Cargo.toml"
TAURI = ROOT / "src-tauri/tauri.conf.json"
PACKAGE = ROOT / "package.json"

for p in (APP, CSS, LIB, CARGO, TAURI, PACKAGE):
    if not p.is_file():
        raise SystemExit(f"Stage106 launcher file missing: {p}")

# Rust: issue a local, non-secret readiness marker path to the Java child.
lib = LIB.read_text(encoding="utf-8")
if '"GGO_READY_FILE".to_string()' not in lib:
    needle = "    let child_environment = vec![\n"
    if needle not in lib:
        raise SystemExit("Stage106 launcher could not locate child environment")
    replacement = '''    let ready_file = root.join(".ggo-client-ready");
    let _ = std::fs::remove_file(&ready_file);
    let child_environment = vec![
'''
    lib = lib.replace(needle, replacement, 1)
    needle = '        ("GGO_UI_SHA256".to_string(), ui_sha256),\n    ];'
    replacement = '''        ("GGO_UI_SHA256".to_string(), ui_sha256),
        (
            "GGO_READY_FILE".to_string(),
            ready_file.to_string_lossy().into_owned(),
        ),
    ];'''
    if needle not in lib:
        raise SystemExit("Stage106 launcher could not append ready environment")
    lib = lib.replace(needle, replacement, 1)

if "fn game_surface_ready(" not in lib:
    needle = '''#[tauri::command]
fn game_process_status() -> GameProcessStatus {
    minecraft_launch::game_process_status()
}
'''
    replacement = needle + '''
#[tauri::command]
fn game_surface_ready(install_dir: String) -> bool {
    PathBuf::from(install_dir).join(".ggo-client-ready").is_file()
}
'''
    if needle not in lib:
        raise SystemExit("Stage106 launcher could not locate game_process_status command")
    lib = lib.replace(needle, replacement, 1)

if "            game_surface_ready,\n" not in lib:
    needle = "            game_process_status,\n"
    if needle not in lib:
        raise SystemExit("Stage106 launcher could not register readiness command")
    lib = lib.replace(needle, needle + "            game_surface_ready,\n", 1)
LIB.write_text(lib, encoding="utf-8")

# Frontend: keep the launcher visible and branded until the Java client confirms GGO UI readiness.
app = APP.read_text(encoding="utf-8")
if "const[clientReady,setClientReady]=useState(false);" not in app:
    needle = "const[clientRunning,setClientRunning]=useState(false);"
    if needle not in app:
        raise SystemExit("Stage106 launcher could not locate clientRunning state")
    app = app.replace(needle, needle + "const[clientReady,setClientReady]=useState(false);", 1)

old_effect = '''  useEffect(()=>{if(!clientRunning)return;let disposed=false;const poll=async()=>{try{const process=await invoke<GameProcessStatus>("game_process_status");if(disposed||process.running)return;disposed=true;setClientRunning(false);const window=getCurrentWindow();await window.show().catch(()=>undefined);await window.setFocus().catch(()=>undefined);setStatus(process.exitCode===0?"GunGloryOnline closed":`GGO client exited (${process.exitCode??"signal"})`);}catch{/* keep the launcher hidden only while the known child is being tracked */}};const timer=window.setInterval(()=>void poll(),800);void poll();return()=>{disposed=true;window.clearInterval(timer);};},[clientRunning]);'''
new_effect = '''  useEffect(()=>{if(!clientRunning)return;let disposed=false;const poll=async()=>{try{const process=await invoke<GameProcessStatus>("game_process_status");if(disposed)return;const window=getCurrentWindow();if(process.running){if(!clientReady){const ready=await invoke<boolean>("game_surface_ready",{installDir}).catch(()=>false);if(ready&&!disposed){setClientReady(true);setStatus("GunGloryOnline running");await window.setAlwaysOnTop(false).catch(()=>undefined);await window.hide().catch(()=>undefined);}}return;}disposed=true;setClientRunning(false);setClientReady(false);await window.setAlwaysOnTop(false).catch(()=>undefined);await window.show().catch(()=>undefined);await window.setFocus().catch(()=>undefined);setStatus(process.exitCode===0?"GunGloryOnline closed":`GGO client exited (${process.exitCode??"signal"})`);}catch{/* keep the GGO startup surface visible if process state cannot be confirmed */}};const timer=window.setInterval(()=>void poll(),400);void poll();return()=>{disposed=true;window.clearInterval(timer);};},[clientRunning,clientReady,installDir]);'''
if old_effect in app:
    app = app.replace(old_effect, new_effect, 1)
elif 'invoke<boolean>("game_surface_ready"' not in app:
    raise SystemExit("Stage106 launcher could not locate lifecycle polling effect")

old_launch_tail = '''setStatus(`GGO Client · PID ${result.pid}`);setClientRunning(true);await getCurrentWindow().hide().catch(()=>undefined);'''
new_launch_tail = '''setStatus("STARTING GUNGLORYONLINE…");setClientReady(false);setClientRunning(true);const window=getCurrentWindow();await window.show().catch(()=>undefined);await window.setAlwaysOnTop(true).catch(()=>undefined);await window.setFocus().catch(()=>undefined);'''
if old_launch_tail in app:
    app = app.replace(old_launch_tail, new_launch_tail, 1)
elif 'setStatus("STARTING GUNGLORYONLINE…")' not in app:
    raise SystemExit("Stage106 launcher could not locate launch visibility handoff")

if 'className="ggo-boot-cover"' not in app:
    needle = '  return <div className="app-shell">\n'
    overlay = '''  return <div className="app-shell">
    {clientRunning&&!clientReady&&<div className="ggo-boot-cover" role="status" aria-live="polite"><div className="ggo-boot-mark">G</div><span>GUNGLORYONLINE</span><h2>INITIALIZING GGO</h2><div className="ggo-boot-line"><i></i></div><small>Preparing your session · engine startup is hidden behind this GGO surface</small></div>}
'''
    if needle not in app:
        raise SystemExit("Stage106 launcher could not locate app shell root")
    app = app.replace(needle, overlay, 1)
APP.write_text(app, encoding="utf-8")

css = CSS.read_text(encoding="utf-8")
marker = "/* GGO Stage106 unified startup cover */"
if marker not in css:
    css += '''

/* GGO Stage106 unified startup cover */
.ggo-boot-cover{position:fixed;inset:0;z-index:100000;display:flex;flex-direction:column;align-items:center;justify-content:center;background:radial-gradient(circle at 62% 42%,rgba(130,24,43,.16),transparent 38%),#06080c;color:#f3f5f8;letter-spacing:.08em;overflow:hidden}
.ggo-boot-cover:before{content:"";position:absolute;inset:0;background:linear-gradient(120deg,transparent 10%,rgba(211,54,72,.035) 46%,transparent 70%);pointer-events:none}
.ggo-boot-mark{width:58px;height:58px;border:1px solid #7d2631;border-radius:16px;display:grid;place-items:center;margin-bottom:20px;background:#11141b;color:#ef4657;font-size:28px;font-weight:900;box-shadow:0 0 50px rgba(214,52,73,.12)}
.ggo-boot-cover>span{font-size:12px;font-weight:800;color:#d7dbe2}.ggo-boot-cover h2{margin:12px 0 24px;font-size:25px;letter-spacing:.13em;color:#ef4a59}.ggo-boot-cover small{margin-top:15px;color:#667082;font-size:10px;letter-spacing:.04em;text-transform:none}
.ggo-boot-line{position:relative;width:min(360px,44vw);height:3px;background:#202631;overflow:hidden}.ggo-boot-line i{position:absolute;top:0;bottom:0;width:25%;background:#f0f2f5;animation:ggoBootSweep 1.25s ease-in-out infinite}
@keyframes ggoBootSweep{0%{left:-28%}50%{left:76%}100%{left:-28%}}
@media(prefers-reduced-motion:reduce){.ggo-boot-line i{animation:none;left:38%}}
'''
CSS.write_text(css, encoding="utf-8")

# This launcher build will be distinguishable from the Stage103 0.2.7 binary.
for path in (PACKAGE, TAURI):
    text = path.read_text(encoding="utf-8")
    text = text.replace('"version": "0.2.7"', '"version": "0.2.8"', 1)
    path.write_text(text, encoding="utf-8")
text = CARGO.read_text(encoding="utf-8")
text = text.replace('version = "0.2.7"', 'version = "0.2.8"', 1)
CARGO.write_text(text, encoding="utf-8")

# Fail closed if any part of the visible handoff was not applied.
checks = {
    "ready environment": '"GGO_READY_FILE".to_string()' in LIB.read_text(encoding="utf-8"),
    "ready command": "fn game_surface_ready(" in LIB.read_text(encoding="utf-8"),
    "ready polling": 'invoke<boolean>("game_surface_ready"' in APP.read_text(encoding="utf-8"),
    "launcher stays visible": 'setStatus("STARTING GUNGLORYONLINE…")' in APP.read_text(encoding="utf-8"),
    "old immediate hide removed": 'setClientRunning(true);await getCurrentWindow().hide()' not in APP.read_text(encoding="utf-8"),
    "startup cover": 'className="ggo-boot-cover"' in APP.read_text(encoding="utf-8"),
    "0.2.8 cargo": 'version = "0.2.8"' in CARGO.read_text(encoding="utf-8"),
}
for label, ok in checks.items():
    if not ok:
        raise SystemExit(f"Stage106 launcher failed check: {label}")

print("Applied GGO Stage106 unified launcher lifecycle")
for label in checks:
    print(f" - {label}: ok")
