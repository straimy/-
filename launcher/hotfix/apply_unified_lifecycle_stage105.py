#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PROCESS = ROOT / "src-tauri/src/runtime/minecraft_launch.rs"
LIB = ROOT / "src-tauri/src/lib.rs"
APP = ROOT / "src/App.tsx"

for path in (PROCESS, LIB, APP):
    if not path.is_file():
        raise SystemExit(f"missing launcher source: {path}")

# --- Rust child lifecycle -------------------------------------------------
p = PROCESS.read_text(encoding="utf-8")
p = p.replace(
    "    process::{Child, Command, Stdio},\n};",
    "    process::{Child, Command, Stdio},\n    sync::{Mutex, OnceLock},\n};",
    1,
)

launch_result = '''#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct LaunchResult {
    pub started: bool,
    pub pid: u32,
    pub profile_name: String,
    pub profile_id: String,
}
'''
status_block = launch_result + '''
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct GameProcessStatus {
    pub running: bool,
    pub pid: Option<u32>,
    pub exit_code: Option<i32>,
}

static GAME_PROCESS_STATUS: OnceLock<Mutex<GameProcessStatus>> = OnceLock::new();

fn game_process_state() -> &'static Mutex<GameProcessStatus> {
    GAME_PROCESS_STATUS.get_or_init(|| Mutex::new(GameProcessStatus {
        running: false,
        pid: None,
        exit_code: None,
    }))
}

pub fn game_process_status() -> GameProcessStatus {
    game_process_state()
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner())
        .clone()
}
'''
if "pub struct GameProcessStatus" not in p:
    if launch_result not in p:
        raise SystemExit("Stage105 LaunchResult anchor not found")
    p = p.replace(launch_result, status_block, 1)

old_launch = '''pub fn launch_with_environment(
    install_dir: &Path,
    custom_java: Option<&str>,
    session: &MicrosoftSession,
    options: &LaunchOptions,
    environment: &[(String, String)],
) -> Result<LaunchResult, LaunchError> {
    let built = build_launch(install_dir, custom_java, session, options)?;
    let child = spawn(&built, environment)?;
    Ok(LaunchResult {
        started: true,
        pid: child.id(),
        profile_name: session.minecraft_profile.name.clone(),
        profile_id: session.minecraft_profile.id.clone(),
    })
}
'''
new_launch = '''pub fn launch_with_environment(
    install_dir: &Path,
    custom_java: Option<&str>,
    session: &MicrosoftSession,
    options: &LaunchOptions,
    environment: &[(String, String)],
) -> Result<LaunchResult, LaunchError> {
    {
        let status = game_process_state()
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner());
        if status.running {
            return Err(LaunchError::Spawn("GunGloryOnline is already running".to_string()));
        }
    }

    let built = build_launch(install_dir, custom_java, session, options)?;
    let mut child = spawn(&built, environment)?;
    let pid = child.id();
    {
        let mut status = game_process_state()
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner());
        *status = GameProcessStatus { running: true, pid: Some(pid), exit_code: None };
    }

    // Keep ownership of the Java child until it exits. This reaps the process and gives
    // the Tauri shell one authoritative lifecycle instead of leaving a detached game.
    std::thread::spawn(move || {
        let exit_code = child.wait().ok().and_then(|status| status.code());
        let mut status = game_process_state()
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner());
        status.running = false;
        status.pid = None;
        status.exit_code = exit_code;
    });

    Ok(LaunchResult {
        started: true,
        pid,
        profile_name: session.minecraft_profile.name.clone(),
        profile_id: session.minecraft_profile.id.clone(),
    })
}
'''
if old_launch in p:
    p = p.replace(old_launch, new_launch, 1)
elif "std::thread::spawn(move ||" not in p or "GunGloryOnline is already running" not in p:
    raise SystemExit("Stage105 launch_with_environment anchor not found")
PROCESS.write_text(p, encoding="utf-8")

# --- Tauri command --------------------------------------------------------
l = LIB.read_text(encoding="utf-8")
l = l.replace(
    "    minecraft_launch::{LaunchOptions, LaunchResult},",
    "    minecraft_launch::{self, GameProcessStatus, LaunchOptions, LaunchResult},",
    1,
)
command = '''
#[tauri::command]
fn game_process_status() -> GameProcessStatus {
    minecraft_launch::game_process_status()
}
'''
if "fn game_process_status() -> GameProcessStatus" not in l:
    anchor = "#[tauri::command]\nasync fn microsoft_login("
    if anchor not in l:
        raise SystemExit("Stage105 lib command anchor not found")
    l = l.replace(anchor, command + "\n" + anchor, 1)
if "            game_process_status,\n" not in l:
    anchor = "            launch_game,\n"
    if anchor not in l:
        raise SystemExit("Stage105 generate_handler anchor not found")
    l = l.replace(anchor, anchor + "            game_process_status,\n", 1)
LIB.write_text(l, encoding="utf-8")

# --- React/Tauri single-app presentation --------------------------------
a = APP.read_text(encoding="utf-8")
if 'from "@tauri-apps/api/window"' not in a:
    anchor = 'import { listen } from "@tauri-apps/api/event";\n'
    if anchor not in a:
        raise SystemExit("Stage105 App import anchor not found")
    a = a.replace(anchor, anchor + 'import { getCurrentWindow } from "@tauri-apps/api/window";\n', 1)

if "type GameProcessStatus" not in a:
    anchor = "type LaunchResult = { pid:number; profileName:string };\n"
    if anchor not in a:
        raise SystemExit("Stage105 App type anchor not found")
    a = a.replace(anchor, anchor + "type GameProcessStatus = { running:boolean; pid:number|null; exitCode:number|null };\n", 1)

state_anchor = 'const[logMode,setLogMode]=useState<LogMode>("latest");const[latestLog,setLatestLog]=useState<LogSnapshot|null>(null);const[crashLog,setCrashLog]=useState<LogSnapshot|null>(null);'
if "setClientRunning" not in a:
    if state_anchor not in a:
        raise SystemExit("Stage105 App state anchor not found")
    a = a.replace(state_anchor, state_anchor + 'const[clientRunning,setClientRunning]=useState(false);', 1)

lifecycle_effect = '''
  useEffect(()=>{if(!clientRunning)return;let disposed=false;const poll=async()=>{try{const process=await invoke<GameProcessStatus>("game_process_status");if(disposed||process.running)return;disposed=true;setClientRunning(false);const window=getCurrentWindow();await window.show().catch(()=>undefined);await window.setFocus().catch(()=>undefined);setStatus(process.exitCode===0?"GunGloryOnline closed":`GGO client exited (${process.exitCode??"signal"})`);}catch{/* keep the launcher hidden only while the known child is being tracked */}};const timer=window.setInterval(()=>void poll(),800);void poll();return()=>{disposed=true;window.clearInterval(timer);};},[clientRunning]);
'''
if 'invoke<GameProcessStatus>("game_process_status")' not in a:
    anchor = '  useEffect(()=>{if(page==="logs")void loadLogs();},[page,installDir]);\n'
    if anchor not in a:
        raise SystemExit("Stage105 App effect anchor not found")
    a = a.replace(anchor, anchor + lifecycle_effect, 1)

old_tail = 'const result=await invoke<LaunchResult>("launch_game",{installDir,customJava:javaPath||runtimeCheck.java?.path||null,options:opts,training:false,profile:launchProfile});setStatus(`GGO Client · PID ${result.pid}`);'
new_tail = 'const result=await invoke<LaunchResult>("launch_game",{installDir,customJava:javaPath||runtimeCheck.java?.path||null,options:opts,training:false,profile:launchProfile});setStatus(`GGO Client · PID ${result.pid}`);setClientRunning(true);await getCurrentWindow().hide().catch(()=>undefined);'
if old_tail in a:
    a = a.replace(old_tail, new_tail, 1)
elif 'setClientRunning(true);await getCurrentWindow().hide()' not in a:
    raise SystemExit("Stage105 App launch tail anchor not found")
APP.write_text(a, encoding="utf-8")

# --- Contract checks ------------------------------------------------------
checks = {
    PROCESS: ["pub struct GameProcessStatus", "std::thread::spawn(move ||", "child.wait()", "GunGloryOnline is already running"],
    LIB: ["minecraft_launch::{self, GameProcessStatus", "fn game_process_status() -> GameProcessStatus", "            game_process_status,"],
    APP: ['getCurrentWindow', 'invoke<GameProcessStatus>("game_process_status")', 'setClientRunning(true)', 'getCurrentWindow().hide()', 'window.show()', 'window.setFocus()'],
}
for path, needles in checks.items():
    text = path.read_text(encoding="utf-8")
    for needle in needles:
        if needle not in text:
            raise SystemExit(f"Stage105 missing {needle!r} in {path.name}")

print("Stage105 unified launcher/game lifecycle applied")
print(" - launcher owns and reaps the GGO Java child")
print(" - duplicate PLAY is rejected while the client is running")
print(" - launcher hides after PLAY and restores/focuses after client exit")
print(" - abnormal client exit returns to launcher instead of looking like a launcher crash")
