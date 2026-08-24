use crate::core::manifest::{sha256_file, FileSide, GameManifest, ManifestFile};
use futures_util::{stream, StreamExt, TryStreamExt};
use reqwest::{Client, Url};
use serde::Serialize;
use sha2::{Digest, Sha256};
use std::{
    path::{Component, Path, PathBuf},
    sync::{
        atomic::{AtomicU64, Ordering},
        Arc,
    },
    time::{Duration, Instant},
};
use tauri::{AppHandle, Emitter};
use thiserror::Error;
use tokio::{fs, io::AsyncWriteExt};
use uuid::Uuid;

const MANIFEST_SCHEMA_VERSION: u32 = 1;
const DOWNLOAD_CONCURRENCY: usize = 4;

#[derive(Debug, Error)]
pub enum UpdateError {
    #[error("invalid manifest URL: {0}")]
    InvalidManifestUrl(String),
    #[error("manifest URL must use HTTPS (HTTP is allowed only for localhost development)")]
    InsecureManifestUrl,
    #[error("network error: {0}")]
    Network(#[from] reqwest::Error),
    #[error("filesystem error: {0}")]
    Io(#[from] std::io::Error),
    #[error("unsupported manifest schema {0}")]
    UnsupportedSchema(u32),
    #[error("unsafe manifest path: {0}")]
    UnsafePath(String),
    #[error("invalid SHA256 for {0}")]
    InvalidSha256(String),
    #[error("download size mismatch for {path}: expected {expected}, got {actual}")]
    SizeMismatch {
        path: String,
        expected: u64,
        actual: u64,
    },
    #[error("download checksum mismatch for {path}")]
    ChecksumMismatch { path: String },
    #[error("failed to replace {path}: {message}")]
    ReplaceFailed { path: String, message: String },
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct PlannedFile {
    pub path: String,
    pub url: String,
    pub size: u64,
    pub reason: &'static str,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct UpdatePlan {
    pub game_version: String,
    pub runtime: String,
    pub files: Vec<PlannedFile>,
    pub total_bytes: u64,
    pub checked_files: usize,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SyncReport {
    pub game_version: String,
    pub updated_files: usize,
    pub downloaded_bytes: u64,
    pub elapsed_ms: u128,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct UpdateProgress {
    pub stage: &'static str,
    pub current_file: String,
    pub downloaded_bytes: u64,
    pub total_bytes: u64,
    pub speed_bytes_per_second: u64,
}

pub fn client() -> Result<Client, UpdateError> {
    // Cloudflare's Browser Integrity Check can reject non-browser-looking HTTP signatures with
    // Error 1010 before the request reaches the GGO API. Keep an explicit stable desktop signature
    // while still identifying the GGO launcher in the product token.
    const DESKTOP_UA: &str = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 GunGloryOnline-Launcher/0.2.4";
    Ok(Client::builder()
        .connect_timeout(Duration::from_secs(15))
        .timeout(Duration::from_secs(300))
        .user_agent(DESKTOP_UA)
        .default_headers({
            let mut headers = reqwest::header::HeaderMap::new();
            headers.insert(
                reqwest::header::ACCEPT,
                reqwest::header::HeaderValue::from_static("application/json,text/plain,*/*"),
            );
            headers.insert(
                reqwest::header::ACCEPT_LANGUAGE,
                reqwest::header::HeaderValue::from_static("en-US,en;q=0.9"),
            );
            headers
        })
        .build()?)
}

pub async fn fetch_manifest(
    client: &Client,
    manifest_url: &str,
) -> Result<GameManifest, UpdateError> {
    let url = validate_remote_url(manifest_url)?;
    let manifest = client
        .get(url)
        .send()
        .await?
        .error_for_status()?
        .json::<GameManifest>()
        .await?;
    validate_manifest(&manifest)?;
    Ok(manifest)
}

pub async fn build_plan(
    manifest: &GameManifest,
    install_dir: &Path,
) -> Result<UpdatePlan, UpdateError> {
    fs::create_dir_all(install_dir).await?;
    let mut files = Vec::new();
    let mut total_bytes = 0_u64;
    let mut checked_files = 0_usize;

    for entry in manifest
        .files
        .iter()
        .filter(|f| f.required && matches!(f.side, FileSide::Client | FileSide::Both))
    {
        checked_files += 1;
        let target = resolve_target(install_dir, &entry.path)?;
        let reason = match fs::metadata(&target).await {
            Err(err) if err.kind() == std::io::ErrorKind::NotFound => Some("missing"),
            Err(err) => return Err(err.into()),
            Ok(metadata) if entry.size > 0 && metadata.len() != entry.size => Some("size-mismatch"),
            Ok(_) => {
                let path = target.clone();
                let actual = tokio::task::spawn_blocking(move || sha256_file(path))
                    .await
                    .map_err(|err| std::io::Error::other(err.to_string()))??;
                (actual != entry.sha256.to_ascii_lowercase()).then_some("checksum-mismatch")
            }
        };

        if let Some(reason) = reason {
            files.push(PlannedFile {
                path: entry.path.clone(),
                url: entry.url.clone(),
                size: entry.size,
                reason,
            });
            total_bytes = total_bytes.saturating_add(entry.size);
        }
    }

    Ok(UpdatePlan {
        game_version: manifest.game_version.clone(),
        runtime: runtime_name(manifest),
        files,
        total_bytes,
        checked_files,
    })
}

pub async fn sync(
    app: &AppHandle,
    manifest_url: &str,
    install_dir: &Path,
    repair: bool,
) -> Result<SyncReport, UpdateError> {
    let started = Instant::now();
    let http = client()?;
    let manifest = fetch_manifest(&http, manifest_url).await?;
    let plan = build_plan(&manifest, install_dir).await?;

    app.emit(
        "ggo-update-progress",
        UpdateProgress {
            stage: if repair {
                "repair-check-complete"
            } else {
                "check-complete"
            },
            current_file: String::new(),
            downloaded_bytes: 0,
            total_bytes: plan.total_bytes,
            speed_bytes_per_second: 0,
        },
    )
    .ok();

    if plan.files.is_empty() {
        return Ok(SyncReport {
            game_version: manifest.game_version,
            updated_files: 0,
            downloaded_bytes: 0,
            elapsed_ms: started.elapsed().as_millis(),
        });
    }

    let downloaded = Arc::new(AtomicU64::new(0));
    let total_bytes = plan.total_bytes;
    let entries: Vec<ManifestFile> = plan
        .files
        .iter()
        .filter_map(|planned| {
            manifest
                .files
                .iter()
                .find(|f| f.path == planned.path)
                .cloned()
        })
        .collect();

    stream::iter(entries.into_iter().map(|entry| {
        let app = app.clone();
        let http = http.clone();
        let root = install_dir.to_path_buf();
        let downloaded = downloaded.clone();
        async move {
            download_and_install(&app, &http, &root, &entry, total_bytes, downloaded, started).await
        }
    }))
    .buffer_unordered(DOWNLOAD_CONCURRENCY)
    .try_collect::<Vec<_>>()
    .await?;

    let downloaded_bytes = downloaded.load(Ordering::Relaxed);
    app.emit(
        "ggo-update-progress",
        UpdateProgress {
            stage: "complete",
            current_file: String::new(),
            downloaded_bytes,
            total_bytes,
            speed_bytes_per_second: average_speed(downloaded_bytes, started),
        },
    )
    .ok();

    Ok(SyncReport {
        game_version: manifest.game_version,
        updated_files: plan.files.len(),
        downloaded_bytes,
        elapsed_ms: started.elapsed().as_millis(),
    })
}

async fn download_and_install(
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
    let part = parent.join(format!(".{file_name}.ggo-part-{}", Uuid::new_v4()));

    let result = async {
        let response = http.get(url).send().await?.error_for_status()?;
        let mut stream = response.bytes_stream();
        let mut output = fs::File::create(&part).await?;
        let mut hasher = Sha256::new();
        let mut file_bytes = 0_u64;

        while let Some(chunk) = stream.next().await {
            let chunk = chunk?;
            output.write_all(&chunk).await?;
            hasher.update(&chunk);
            file_bytes = file_bytes.saturating_add(chunk.len() as u64);
            let aggregate =
                downloaded.fetch_add(chunk.len() as u64, Ordering::Relaxed) + chunk.len() as u64;
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

        if entry.size > 0 && file_bytes != entry.size {
            return Err(UpdateError::SizeMismatch {
                path: entry.path.clone(),
                expected: entry.size,
                actual: file_bytes,
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

    if result.is_err() {
        let _ = fs::remove_file(&part).await;
    }
    result
}

async fn replace_with_rollback(
    part: &Path,
    target: &Path,
    manifest_path: &str,
) -> Result<(), UpdateError> {
    if fs::metadata(target).await.is_err() {
        fs::rename(part, target).await?;
        return Ok(());
    }

    let file_name = target
        .file_name()
        .and_then(|v| v.to_str())
        .unwrap_or("file");
    let backup = target.with_file_name(format!(".{file_name}.ggo-backup-{}", Uuid::new_v4()));
    fs::rename(target, &backup).await?;
    match fs::rename(part, target).await {
        Ok(()) => {
            let _ = fs::remove_file(backup).await;
            Ok(())
        }
        Err(err) => {
            let _ = fs::rename(&backup, target).await;
            Err(UpdateError::ReplaceFailed {
                path: manifest_path.to_owned(),
                message: err.to_string(),
            })
        }
    }
}

fn validate_manifest(manifest: &GameManifest) -> Result<(), UpdateError> {
    if manifest.schema_version != MANIFEST_SCHEMA_VERSION {
        return Err(UpdateError::UnsupportedSchema(manifest.schema_version));
    }
    for entry in &manifest.files {
        resolve_target(Path::new("."), &entry.path)?;
        if entry.sha256.len() != 64 || !entry.sha256.bytes().all(|b| b.is_ascii_hexdigit()) {
            return Err(UpdateError::InvalidSha256(entry.path.clone()));
        }
        validate_remote_url(&entry.url)?;
    }
    Ok(())
}

fn validate_remote_url(raw: &str) -> Result<Url, UpdateError> {
    let url = Url::parse(raw).map_err(|_| UpdateError::InvalidManifestUrl(raw.to_owned()))?;
    let localhost = matches!(url.host_str(), Some("localhost" | "127.0.0.1" | "::1"));
    if url.scheme() != "https" && !(url.scheme() == "http" && localhost) {
        return Err(UpdateError::InsecureManifestUrl);
    }
    Ok(url)
}

fn resolve_target(root: &Path, manifest_path: &str) -> Result<PathBuf, UpdateError> {
    if manifest_path.is_empty() || manifest_path.contains('\\') || manifest_path.contains(':') {
        return Err(UpdateError::UnsafePath(manifest_path.to_owned()));
    }
    let relative = Path::new(manifest_path);
    if relative.is_absolute()
        || relative
            .components()
            .any(|part| !matches!(part, Component::Normal(_)))
    {
        return Err(UpdateError::UnsafePath(manifest_path.to_owned()));
    }
    Ok(root.join(relative))
}

fn runtime_name(manifest: &GameManifest) -> String {
    serde_json::to_value(&manifest.runtime)
        .ok()
        .and_then(|v| v.as_str().map(str::to_owned))
        .unwrap_or_else(|| "unknown".to_owned())
}

fn average_speed(bytes: u64, started: Instant) -> u64 {
    let seconds = started.elapsed().as_secs_f64().max(0.001);
    (bytes as f64 / seconds) as u64
}

#[cfg(test)]
mod tests {
    use super::resolve_target;
    use std::path::Path;

    #[test]
    fn rejects_path_traversal() {
        assert!(resolve_target(Path::new("/tmp/ggo"), "../escape.jar").is_err());
        assert!(resolve_target(Path::new("/tmp/ggo"), "/absolute.jar").is_err());
        assert!(resolve_target(Path::new("/tmp/ggo"), "C:\\evil.jar").is_err());
    }

    #[test]
    fn accepts_normal_game_paths() {
        assert!(resolve_target(Path::new("/tmp/ggo"), "mods/core.jar").is_ok());
    }
}
