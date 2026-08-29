#!/usr/bin/env python3
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
UPDATER = ROOT / "src-tauri/src/core/updater.rs"
APP = ROOT / "src/App.tsx"
CARGO = ROOT / "src-tauri/Cargo.toml"

if not UPDATER.is_file() or not APP.is_file() or not CARGO.is_file():
    raise SystemExit(f"launcher sources missing under {ROOT}")

updater = UPDATER.read_text(encoding="utf-8")

# Stage111 is a canonical source transform and may be run repeatedly by multiple launcher
# workflows. Accept both the legacy and already-final forms instead of stacking declarations.
legacy_tokio = 'use tokio::{fs, io::AsyncWriteExt};'
final_tokio = 'time::{sleep, timeout}'
if legacy_tokio in updater:
    updater = updater.replace(
        legacy_tokio,
        'use tokio::{fs, io::AsyncWriteExt, time::{sleep, timeout}};',
        1,
    )
elif final_tokio not in updater:
    raise SystemExit("unable to locate tokio import for Stage111 timeout support")

concurrency = 'const DOWNLOAD_CONCURRENCY: usize = 4;'
attempts = 'const DOWNLOAD_ATTEMPTS: usize = 3;'
stall_timeout = 'const DOWNLOAD_STALL_TIMEOUT: Duration = Duration::from_secs(30);'
if attempts not in updater and stall_timeout not in updater:
    if concurrency not in updater:
        raise SystemExit("download concurrency constant missing")
    updater = updater.replace(
        concurrency,
        concurrency + '\n' + attempts + '\n' + stall_timeout,
        1,
    )
elif attempts not in updater or stall_timeout not in updater:
    raise SystemExit("partial Stage111 download retry constants found")
if updater.count(attempts) != 1 or updater.count(stall_timeout) != 1:
    raise SystemExit("duplicate Stage111 download retry constants found")

replace_variant = '    #[error("failed to replace {path}: {message}")]\n    ReplaceFailed { path: String, message: String },'
stalled_variant = '    #[error("download stalled while receiving {0}")]\n    DownloadStalled(String),'
if stalled_variant not in updater:
    if replace_variant not in updater:
        raise SystemExit("UpdateError replacement marker missing")
    updater = updater.replace(
        replace_variant,
        replace_variant + '\n' + stalled_variant,
        1,
    )
if updater.count('DownloadStalled(String),') != 1:
    raise SystemExit("duplicate Stage111 DownloadStalled variant found")

old_ua = '    const DESKTOP_UA: &str = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 GunGloryOnline-Launcher/0.2.4";'
new_ua = '    const DESKTOP_UA: &str = concat!("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 GunGloryOnline-Launcher/", env!("CARGO_PKG_VERSION"));'
if old_ua in updater:
    updater = updater.replace(old_ua, new_ua, 1)
elif new_ua not in updater:
    raise SystemExit("desktop user-agent marker missing")

start = updater.index('async fn download_and_install(')
end = updater.index('\nasync fn replace_with_rollback(', start)
current_download = updater[start:end]

# If the final Stage111 body is already present, do not rewrite it. This keeps the transform
# stable across generic Linux verification, unified launcher packaging, and manual dispatches.
final_markers = [
    'for attempt in 1..=DOWNLOAD_ATTEMPTS',
    'timeout(DOWNLOAD_STALL_TIMEOUT, http.get(url.clone()).send())',
    'stage: "retrying"',
    'downloaded.fetch_sub(attempt_bytes',
]
if not all(marker in current_download for marker in final_markers):
    replacement = r'''async fn download_and_install(
    app: &AppHandle,
    http: &Client,
    install_dir: &Path,
    entry: &ManifestFile,
    total_bytes: u64,
    downloaded: Arc<AtomicU64>,
    started: Instant,
) -> Result<(), UpdateError> {
    let url = validate_remote_url(&entry.url)?;
    let target = resolve_target(install_dir, &entry.path)?;
    let parent = target
        .parent()
        .ok_or_else(|| UpdateError::UnsafePath(entry.path.clone()))?;
    fs::create_dir_all(parent).await?;

    let file_name = target
        .file_name()
        .and_then(|v| v.to_str())
        .ok_or_else(|| UpdateError::UnsafePath(entry.path.clone()))?;

    let mut last_error = None;
    for attempt in 1..=DOWNLOAD_ATTEMPTS {
        let part = parent.join(format!(".{file_name}.ggo-part-{}", Uuid::new_v4()));
        let mut attempt_bytes = 0_u64;

        if attempt > 1 {
            app.emit(
                "ggo-update-progress",
                UpdateProgress {
                    stage: "retrying",
                    current_file: format!("{} · attempt {attempt}/{DOWNLOAD_ATTEMPTS}", entry.path),
                    downloaded_bytes: downloaded.load(Ordering::Relaxed),
                    total_bytes,
                    speed_bytes_per_second: average_speed(downloaded.load(Ordering::Relaxed), started),
                },
            )
            .ok();
            sleep(Duration::from_millis(750 * attempt as u64)).await;
        }

        let result: Result<(), UpdateError> = async {
            let response = timeout(DOWNLOAD_STALL_TIMEOUT, http.get(url.clone()).send())
                .await
                .map_err(|_| UpdateError::DownloadStalled(entry.path.clone()))??
                .error_for_status()?;
            let mut stream = response.bytes_stream();
            let mut output = fs::File::create(&part).await?;
            let mut hasher = Sha256::new();

            loop {
                let next = timeout(DOWNLOAD_STALL_TIMEOUT, stream.next())
                    .await
                    .map_err(|_| UpdateError::DownloadStalled(entry.path.clone()))?;
                let Some(chunk) = next else { break };
                let chunk = chunk?;
                output.write_all(&chunk).await?;
                hasher.update(&chunk);
                attempt_bytes = attempt_bytes.saturating_add(chunk.len() as u64);
                let aggregate = downloaded.fetch_add(chunk.len() as u64, Ordering::Relaxed)
                    + chunk.len() as u64;
                app.emit(
                    "ggo-update-progress",
                    UpdateProgress {
                        stage: "downloading",
                        current_file: entry.path.clone(),
                        downloaded_bytes: aggregate,
                        total_bytes,
                        speed_bytes_per_second: average_speed(aggregate, started),
                    },
                )
                .ok();
            }
            output.flush().await?;
            output.sync_all().await?;
            drop(output);

            if entry.size > 0 && attempt_bytes != entry.size {
                return Err(UpdateError::SizeMismatch {
                    path: entry.path.clone(),
                    expected: entry.size,
                    actual: attempt_bytes,
                });
            }
            let actual_hash = hex::encode(hasher.finalize());
            if actual_hash != entry.sha256.to_ascii_lowercase() {
                return Err(UpdateError::ChecksumMismatch {
                    path: entry.path.clone(),
                });
            }
            replace_with_rollback(&part, &target, &entry.path).await
        }
        .await;

        match result {
            Ok(()) => return Ok(()),
            Err(error) => {
                if attempt_bytes > 0 {
                    downloaded.fetch_sub(attempt_bytes, Ordering::Relaxed);
                }
                let _ = fs::remove_file(&part).await;
                last_error = Some(error);
            }
        }
    }

    Err(last_error.expect("at least one download attempt must run"))
}
'''
    updater = updater[:start] + replacement + updater[end:]

for required in [
    'DOWNLOAD_ATTEMPTS: usize = 3',
    'DOWNLOAD_STALL_TIMEOUT',
    'stage: "retrying"',
    'fetch_sub(attempt_bytes',
    'env!("CARGO_PKG_VERSION")',
]:
    if required not in updater:
        raise SystemExit(f"updater patch missing {required}")
if updater.count('const DOWNLOAD_ATTEMPTS: usize = 3;') != 1:
    raise SystemExit("Stage111 retry attempt constant is not unique")
if updater.count('const DOWNLOAD_STALL_TIMEOUT: Duration = Duration::from_secs(30);') != 1:
    raise SystemExit("Stage111 stall timeout constant is not unique")
if updater.count('DownloadStalled(String),') != 1:
    raise SystemExit("Stage111 stalled error variant is not unique")
UPDATER.write_text(updater, encoding="utf-8")

app = APP.read_text(encoding="utf-8")
old_listener = 'void listen<UpdateProgress>("ggo-update-progress",e=>{setProgress(e.payload);setStatus(e.payload.stage);}).then(v=>u1=v);'
new_listener = 'void listen<UpdateProgress>("ggo-update-progress",e=>{setProgress(e.payload);const file=e.payload.currentFile?.split("/").pop();setStatus(file?`${e.payload.stage} · ${file}`:e.payload.stage);}).then(v=>u1=v);'
if old_listener in app:
    app = app.replace(old_listener, new_listener, 1)
elif new_listener not in app:
    raise SystemExit("update progress listener is neither legacy nor Stage111 final form")

# React only records that the child exists. Rust unified_surface is the single owner of launcher
# visibility, so repeated application can never stack hide() calls in the canonical source.
hide_block = 'setClientRunning(true);await getCurrentWindow().hide().catch(()=>undefined);'
count = app.count(hide_block)
if count:
    app = app.replace(hide_block, '', count)
    marker = 'setStatus(`GGO Client · PID ${result.pid}`);'
    if marker not in app:
        raise SystemExit("launch status marker missing")
    if 'setClientRunning(true);' not in app:
        app = app.replace(marker, marker + 'setClientRunning(true);', 1)

if 'getCurrentWindow().hide()' in app:
    raise SystemExit("React still hides launcher directly; unified supervisor must own visibility")
if app.count('setClientRunning(true);') != 1:
    raise SystemExit("expected exactly one clientRunning transition")
if 'e.payload.currentFile?.split("/").pop()' not in app:
    raise SystemExit("per-file progress label missing")
APP.write_text(app, encoding="utf-8")

# The patch intentionally rewrites Rust structurally. Normalize it before immutable --check gates.
subprocess.run(
    ["cargo", "fmt", "--manifest-path", str(CARGO), "--all"],
    cwd=ROOT,
    check=True,
)

print("Applied Stage111 resilient install + unified visibility patch")
print(f" - launcher root: {ROOT}")
print(" - stalled downloads timeout and retry up to 3 times")
print(" - progress identifies the active file")
print(" - failed retry bytes are rolled back from aggregate progress")
print(" - exactly one clientRunning transition remains")
print(" - launcher visibility is owned only by unified_surface supervisor")
print(" - transform is idempotent on already-canonical Stage111 source")
