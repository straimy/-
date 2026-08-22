use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::path::PathBuf;
use tauri::{AppHandle, Emitter, Manager};
use tauri_plugin_updater::UpdaterExt;
use url::Url;

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct LauncherUpdateStatus {
    pub configured: bool,
    pub available: bool,
    pub current_version: String,
    pub version: Option<String>,
    pub notes: Option<String>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct LauncherUpdateProgress {
    pub downloaded_bytes: u64,
    pub total_bytes: Option<u64>,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
struct BetaManifest {
    version: String,
    notes: Option<String>,
    linux_deb: Option<BetaPackage>,
    linux_app_image: Option<BetaPackage>,
    windows_exe: Option<BetaPackage>,
}

#[derive(Debug, Clone, Deserialize)]
struct BetaPackage {
    url: String,
    sha256: String,
}

fn base_url() -> Result<Option<Url>, String> {
    let Some(base) = option_env!("GGO_CONTENT_BASE_URL")
        .map(str::trim)
        .filter(|value| !value.is_empty())
    else {
        return Ok(None);
    };
    let url = Url::parse(base).map_err(|error| error.to_string())?;
    if url.scheme() != "https" || url.host_str() != Some("ggo.kvicloud.ru") {
        return Err("launcher updater must use official GGO HTTPS content host".to_string());
    }
    Ok(Some(url))
}

fn signed_configuration() -> Result<Option<(Url, &'static str)>, String> {
    let Some(base) = base_url()? else {
        return Ok(None);
    };
    let Some(pubkey) = option_env!("GGO_UPDATER_PUBKEY")
        .map(str::trim)
        .filter(|value| !value.is_empty())
    else {
        return Ok(None);
    };
    let endpoint = base
        .join("launcher/latest.json")
        .map_err(|error| error.to_string())?;
    Ok(Some((endpoint, pubkey)))
}

async fn find_signed_update(
    app: &AppHandle,
) -> Result<Option<tauri_plugin_updater::Update>, String> {
    let Some((endpoint, pubkey)) = signed_configuration()? else {
        return Ok(None);
    };
    let updater = app
        .updater_builder()
        .pubkey(pubkey)
        .endpoints(vec![endpoint])
        .map_err(|error| error.to_string())?
        .build()
        .map_err(|error| error.to_string())?;
    updater.check().await.map_err(|error| error.to_string())
}

fn version_is_newer(remote: &str, current: &str) -> bool {
    fn parts(value: &str) -> Vec<u64> {
        value
            .trim_start_matches('v')
            .split('.')
            .map(|part| part.parse::<u64>().unwrap_or(0))
            .collect()
    }
    let mut remote_parts = parts(remote);
    let mut current_parts = parts(current);
    let len = remote_parts.len().max(current_parts.len());
    remote_parts.resize(len, 0);
    current_parts.resize(len, 0);
    remote_parts > current_parts
}

async fn beta_manifest() -> Result<Option<BetaManifest>, String> {
    let Some(base) = base_url()? else {
        return Ok(None);
    };
    let url = base
        .join("launcher/latest-beta.json")
        .map_err(|error| error.to_string())?;
    let response = reqwest::Client::new()
        .get(url)
        .send()
        .await
        .map_err(|error| error.to_string())?;
    if response.status() == reqwest::StatusCode::NOT_FOUND {
        return Ok(None);
    }
    if !response.status().is_success() {
        return Err(format!(
            "launcher update manifest returned {}",
            response.status()
        ));
    }
    response
        .json::<BetaManifest>()
        .await
        .map(Some)
        .map_err(|error| error.to_string())
}

pub async fn check(app: &AppHandle) -> Result<LauncherUpdateStatus, String> {
    let current = env!("CARGO_PKG_VERSION").to_string();
    if signed_configuration()?.is_some() {
        let update = find_signed_update(app).await?;
        return Ok(LauncherUpdateStatus {
            configured: true,
            available: update.is_some(),
            current_version: current,
            version: update.as_ref().map(|value| value.version.clone()),
            notes: update.and_then(|value| value.body),
        });
    }

    let manifest = beta_manifest().await?;
    Ok(match manifest {
        Some(manifest) => LauncherUpdateStatus {
            configured: true,
            available: version_is_newer(&manifest.version, &current),
            current_version: current,
            version: Some(manifest.version),
            notes: manifest.notes,
        },
        None => LauncherUpdateStatus {
            configured: false,
            available: false,
            current_version: current,
            version: None,
            notes: None,
        },
    })
}

fn beta_package(manifest: &BetaManifest) -> Result<BetaPackage, String> {
    #[cfg(target_os = "windows")]
    let package = manifest.windows_exe.clone();
    #[cfg(target_os = "linux")]
    let package = if std::path::Path::new("/etc/debian_version").exists() {
        manifest
            .linux_deb
            .clone()
            .or_else(|| manifest.linux_app_image.clone())
    } else {
        manifest
            .linux_app_image
            .clone()
            .or_else(|| manifest.linux_deb.clone())
    };
    #[cfg(not(any(target_os = "windows", target_os = "linux")))]
    let package: Option<BetaPackage> = None;
    package.ok_or_else(|| "no launcher update package for this platform".to_string())
}

async fn download_beta_package(app: &AppHandle, package: &BetaPackage) -> Result<PathBuf, String> {
    let url = Url::parse(&package.url).map_err(|error| error.to_string())?;
    if url.scheme() != "https" || url.host_str() != Some("ggo.kvicloud.ru") {
        return Err("refusing launcher update outside official GGO HTTPS host".to_string());
    }
    let response = reqwest::Client::new()
        .get(url)
        .send()
        .await
        .map_err(|error| error.to_string())?;
    if !response.status().is_success() {
        return Err(format!("launcher package returned {}", response.status()));
    }
    let total = response.content_length();
    let bytes = response.bytes().await.map_err(|error| error.to_string())?;
    let digest = hex::encode(Sha256::digest(&bytes));
    if !digest.eq_ignore_ascii_case(package.sha256.trim()) {
        return Err("launcher update SHA-256 verification failed".to_string());
    }
    let downloads = app
        .path()
        .download_dir()
        .or_else(|_| app.path().temp_dir())
        .map_err(|error| error.to_string())?;
    let file_name = package
        .url
        .rsplit('/')
        .next()
        .filter(|value| !value.is_empty())
        .unwrap_or("GunGloryOnline-Launcher-Update.bin");
    let destination = downloads.join(file_name);
    tokio::fs::write(&destination, &bytes)
        .await
        .map_err(|error| error.to_string())?;
    let _ = app.emit(
        "ggo-launcher-update-progress",
        LauncherUpdateProgress {
            downloaded_bytes: bytes.len() as u64,
            total_bytes: total,
        },
    );
    Ok(destination)
}

pub async fn install(app: &AppHandle) -> Result<bool, String> {
    if signed_configuration()?.is_some() {
        let Some(update) = find_signed_update(app).await? else {
            return Ok(false);
        };
        let mut downloaded = 0_u64;
        let progress_app = app.clone();
        update
            .download_and_install(
                move |chunk, total| {
                    downloaded = downloaded.saturating_add(chunk as u64);
                    let _ = progress_app.emit(
                        "ggo-launcher-update-progress",
                        LauncherUpdateProgress {
                            downloaded_bytes: downloaded,
                            total_bytes: total,
                        },
                    );
                },
                || {},
            )
            .await
            .map_err(|error| error.to_string())?;
        app.restart();
    }

    let Some(manifest) = beta_manifest().await? else {
        return Ok(false);
    };
    if !version_is_newer(&manifest.version, env!("CARGO_PKG_VERSION")) {
        return Ok(false);
    }
    let package = beta_package(&manifest)?;
    let path = download_beta_package(app, &package).await?;
    open::that(&path).map_err(|error| error.to_string())?;
    Ok(true)
}
