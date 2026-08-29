use super::minecraft_launch;
use std::path::PathBuf;
use tauri::{AppHandle, Manager};
use tokio::time::{sleep, Duration, Instant};

// The launcher owns the visible startup surface. The Java/Forge client is an internal engine:
// keep the Tauri window in front until the child explicitly confirms that a first-party GGO
// surface is renderable, then yield the desktop. This prevents Mojang/Forge bootstrap flashes.
pub fn ready_file() -> PathBuf {
    std::env::temp_dir().join(format!("ggo-ready-{}.flag", uuid::Uuid::new_v4()))
}

pub fn supervise(app: AppHandle, ready_file: PathBuf) {
    tauri::async_runtime::spawn(async move {
        let main = app.get_webview_window("main");
        let started = Instant::now();
        let startup_timeout = Duration::from_secs(120);
        let mut launcher_hidden = false;

        // launch_game already placed the launcher above the engine. Keep it there until the
        // private marker written by GgoUnifiedSurfaceBridge appears. If the engine dies or never
        // reaches GGO readiness, fail visually closed and leave the launcher visible.
        loop {
            let status = minecraft_launch::game_process_status();
            if !status.running {
                break;
            }

            if ready_file.is_file() {
                if let Some(window) = main.as_ref() {
                    let _ = window.set_always_on_top(false);
                    let _ = window.hide();
                    launcher_hidden = true;
                }
                break;
            }

            if started.elapsed() >= startup_timeout {
                break;
            }
            sleep(Duration::from_millis(125)).await;
        }

        // Once the GGO engine is visible, supervise it until process exit. EXIT TO GGO therefore
        // behaves like returning to the same application instead of leaving two independent apps.
        if launcher_hidden {
            loop {
                let status = minecraft_launch::game_process_status();
                if !status.running {
                    break;
                }
                sleep(Duration::from_millis(250)).await;
            }
        }

        let _ = std::fs::remove_file(&ready_file);
        if let Some(window) = main {
            let _ = window.set_always_on_top(false);
            let _ = window.show();
            let _ = window.unminimize();
            let _ = window.set_focus();
        }
    });
}

#[cfg(test)]
mod tests {
    use super::ready_file;

    #[test]
    fn ready_file_is_unique_and_private_to_child_contract() {
        let first = ready_file();
        let second = ready_file();
        assert_ne!(first, second);
        assert!(first
            .file_name()
            .and_then(|value| value.to_str())
            .unwrap_or_default()
            .starts_with("ggo-ready-"));
    }
}
