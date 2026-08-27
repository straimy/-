use super::minecraft_launch;
use std::path::PathBuf;
use tauri::{AppHandle, Manager};
use tokio::time::{sleep, Duration};

pub fn ready_file() -> PathBuf {
    std::env::temp_dir().join(format!("ggo-ready-{}.flag", uuid::Uuid::new_v4()))
}

pub fn supervise(app: AppHandle, ready_file: PathBuf) {
    tauri::async_runtime::spawn(async move {
        let main = app.get_webview_window("main");
        if let Some(window) = main.as_ref() {
            let _ = window.set_always_on_top(true);
            let _ = window.show();
            let _ = window.set_focus();
        }

        let mut game_revealed = false;
        loop {
            let status = minecraft_launch::game_process_status();
            if !status.running {
                break;
            }

            let ready = std::fs::read_to_string(&ready_file)
                .is_ok_and(|value| value.trim() == "ready");
            if !game_revealed && ready {
                if let Some(window) = main.as_ref() {
                    let _ = window.set_always_on_top(false);
                    let _ = window.hide();
                }
                game_revealed = true;
            } else if !game_revealed {
                // Stage105 React builds used to hide immediately after spawn. Keep the launcher
                // authoritative and visible throughout Forge bootstrap until the GGO client writes
                // its explicit ready signal; the first-party game surface then replaces it.
                if let Some(window) = main.as_ref() {
                    let _ = window.set_always_on_top(true);
                    let _ = window.show();
                }
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
