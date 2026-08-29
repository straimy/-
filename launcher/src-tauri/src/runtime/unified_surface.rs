use super::minecraft_launch;
use std::path::PathBuf;
use tauri::{AppHandle, Manager};
use tokio::time::{sleep, Duration};

// Stage111 lifecycle: once the Java engine has spawned successfully, the launcher immediately
// yields the desktop. The private ready marker is still passed to the child for deferred
// fullscreen/readiness, but launcher visibility no longer waits for Mojang/Forge bootstrap.
pub fn ready_file() -> PathBuf {
    std::env::temp_dir().join(format!("ggo-ready-{}.flag", uuid::Uuid::new_v4()))
}

pub fn supervise(app: AppHandle, ready_file: PathBuf) {
    tauri::async_runtime::spawn(async move {
        let main = app.get_webview_window("main");

        // supervise() is called only after launch_with_natives succeeded. Hide immediately so
        // PLAY feels like one application transition rather than two windows fighting for focus.
        if let Some(window) = main.as_ref() {
            let _ = window.set_always_on_top(false);
            let _ = window.hide();
        }

        loop {
            let status = minecraft_launch::game_process_status();
            if !status.running {
                break;
            }
            sleep(Duration::from_millis(125)).await;
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
