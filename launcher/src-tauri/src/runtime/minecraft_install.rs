use super::minecraft::{self, RuntimeCheck, FORGE_VERSION, MINECRAFT_VERSION};
use futures_util::{stream, StreamExt};
use reqwest::Client;
use serde::{Deserialize, Serialize};
use sha1::{Digest, Sha1};
use std::{collections::HashMap, path::Path, process::Command, sync::Arc};
use tauri::{AppHandle, Emitter};
use thiserror::Error;
use tokio::{fs, sync::Mutex};

const VERSION_MANIFEST_URL: &str =
    "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
const ASSET_OBJECT_BASE: &str = "https://resources.download.minecraft.net";
const FORGE_MAVEN_BASE: &str = "https://maven.minecraftforge.net/net/minecraftforge/forge";
const DOWNLOAD_CONCURRENCY: usize = 12;

#[derive(Debug, Error)]
pub enum RuntimeInstallError {
    #[error("network error: {0}")]
    Network(#[from] reqwest::Error),
    #[error("filesystem error: {0}")]
    Io(#[from] std::io::Error),
    #[error("metadata error: {0}")]
    Metadata(String),
    #[error("checksum mismatch for {0}")]
    Checksum(String),
    #[error("Java 17 is required to install Forge")]
    Java17Required,
    #[error("Forge installer failed with exit code {0}")]
    ForgeInstaller(i32),
    #[error("runtime is still incomplete after installation: {0}")]
    Incomplete(String),
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct RuntimeInstallProgress {
    pub stage: String,
    pub current_file: String,
    pub downloaded_bytes: u64,
    pub total_bytes: u64,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct RuntimeInstallReport {
    pub installed: bool,
    pub downloaded_bytes: u64,
    pub minecraft_version: &'static str,
    pub forge_version: &'static str,
    pub runtime: RuntimeCheck,
}

#[derive(Debug, Deserialize)]
struct VersionManifest {
    versions: Vec<ManifestVersion>,
}

#[derive(Debug, Deserialize)]
struct ManifestVersion {
    id: String,
    url: String,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct VersionMeta {
    id: String,
    asset_index: DownloadRef,
    assets: String,
    downloads: VersionDownloads,
    libraries: Vec<Library>,
}

#[derive(Debug, Deserialize)]
struct VersionDownloads {
    client: DownloadRef,
}

#[derive(Debug, Clone, Deserialize)]
struct DownloadRef {
    sha1: String,
    size: u64,
    url: String,
}

#[derive(Debug, Deserialize)]
struct Library {
    downloads: LibraryDownloads,
    rules: Option<Vec<Rule>>,
    natives: Option<HashMap<String, String>>,
}

#[derive(Debug, Deserialize)]
struct LibraryDownloads {
    artifact: Option<LibraryArtifact>,
    classifiers: Option<HashMap<String, LibraryArtifact>>,
}

#[derive(Debug, Clone, Deserialize)]
struct LibraryArtifact {
    path: String,
    sha1: String,
    size: u64,
    url: String,
}

#[derive(Debug, Deserialize)]
struct Rule {
    action: String,
    os: Option<RuleOs>,
}

#[derive(Debug, Deserialize)]
struct RuleOs {
    name: Option<String>,
    arch: Option<String>,
}

#[derive(Debug, Deserialize)]
struct AssetIndex {
    objects: HashMap<String, AssetObject>,
}

#[derive(Debug, Clone, Deserialize)]
struct AssetObject {
    hash: String,
    size: u64,
}

#[derive(Debug, Clone)]
struct InstallDownload {
    label: String,
    url: String,
    sha1: String,
    size: u64,
    relative_path: String,
}

pub async fn install_runtime(
    app: &AppHandle,
    http: &Client,
    install_dir: &Path,
    custom_java: Option<&str>,
) -> Result<RuntimeInstallReport, RuntimeInstallError> {
    fs::create_dir_all(install_dir).await?;
    emit(app, "metadata", "version_manifest_v2.json", 0, 0);

    let manifest: VersionManifest = http
        .get(VERSION_MANIFEST_URL)
        .send()
        .await?
        .error_for_status()?
        .json()
        .await?;
    let version = manifest
        .versions
        .iter()
        .find(|entry| entry.id == MINECRAFT_VERSION)
        .ok_or_else(|| RuntimeInstallError::Metadata("Minecraft 1.20.1 not found".to_string()))?;

    let version_bytes = http
        .get(&version.url)
        .send()
        .await?
        .error_for_status()?
        .bytes()
        .await?;
    let version_meta: VersionMeta = serde_json::from_slice(&version_bytes)
        .map_err(|error| RuntimeInstallError::Metadata(error.to_string()))?;
    if version_meta.id != MINECRAFT_VERSION {
        return Err(RuntimeInstallError::Metadata(format!(
            "unexpected version metadata: {}",
            version_meta.id
        )));
    }

    let version_dir = install_dir.join("versions").join(MINECRAFT_VERSION);
    fs::create_dir_all(&version_dir).await?;
    fs::write(
        version_dir.join(format!("{MINECRAFT_VERSION}.json")),
        &version_bytes,
    )
    .await?;

    let mut downloads = Vec::new();
    downloads.push(InstallDownload {
        label: format!("Minecraft {MINECRAFT_VERSION} client"),
        url: version_meta.downloads.client.url.clone(),
        sha1: version_meta.downloads.client.sha1.clone(),
        size: version_meta.downloads.client.size,
        relative_path: format!("versions/{MINECRAFT_VERSION}/{MINECRAFT_VERSION}.jar"),
    });

    for library in &version_meta.libraries {
        if !library_allowed(library) {
            continue;
        }
        if let Some(artifact) = &library.downloads.artifact {
            downloads.push(from_library_artifact("library", artifact));
        }
        if let Some(classifier) = native_classifier(library) {
            if let Some(artifact) = library
                .downloads
                .classifiers
                .as_ref()
                .and_then(|items| items.get(&classifier))
            {
                downloads.push(from_library_artifact("native", artifact));
            }
        }
    }

    let asset_index_bytes = http
        .get(&version_meta.asset_index.url)
        .send()
        .await?
        .error_for_status()?
        .bytes()
        .await?;
    verify_sha1_bytes(
        &asset_index_bytes,
        &version_meta.asset_index.sha1,
        "asset index",
    )?;
    let asset_index: AssetIndex = serde_json::from_slice(&asset_index_bytes)
        .map_err(|error| RuntimeInstallError::Metadata(error.to_string()))?;
    let indexes = install_dir.join("assets").join("indexes");
    fs::create_dir_all(&indexes).await?;
    fs::write(
        indexes.join(format!("{}.json", version_meta.assets)),
        &asset_index_bytes,
    )
    .await?;

    for (name, object) in asset_index.objects {
        if object.hash.len() < 2 {
            return Err(RuntimeInstallError::Metadata(format!(
                "invalid asset hash for {name}"
            )));
        }
        let prefix = &object.hash[..2];
        downloads.push(InstallDownload {
            label: name,
            url: format!("{ASSET_OBJECT_BASE}/{prefix}/{}", object.hash),
            sha1: object.hash.clone(),
            size: object.size,
            relative_path: format!("assets/objects/{prefix}/{}", object.hash),
        });
    }

    let total_bytes = downloads.iter().map(|item| item.size).sum::<u64>();
    let downloaded = Arc::new(Mutex::new(0_u64));
    emit(app, "minecraft", "Preparing Minecraft files", 0, total_bytes);

    let root = install_dir.to_path_buf();
    let app_handle = app.clone();
    let client = http.clone();
    stream::iter(downloads.into_iter().map(|item| {
        let root = root.clone();
        let app_handle = app_handle.clone();
        let client = client.clone();
        let downloaded = downloaded.clone();
        async move {
            ensure_download(
                &app_handle,
                &client,
                &root,
                item,
                downloaded,
                total_bytes,
            )
            .await
        }
    }))
    .buffer_unordered(DOWNLOAD_CONCURRENCY)
    .collect::<Vec<Result<(), RuntimeInstallError>>>()
    .await
    .into_iter()
    .collect::<Result<Vec<_>, _>>()?;

    let java = minecraft::detect_java(custom_java)
        .into_iter()
        .find(|candidate| candidate.compatible)
        .ok_or(RuntimeInstallError::Java17Required)?;
    install_forge(app, http, install_dir, &java.path).await?;

    let final_check = minecraft::check_runtime(install_dir, custom_java);
    if !final_check.ready {
        return Err(RuntimeInstallError::Incomplete(final_check.missing.join(", ")));
    }
    let downloaded_bytes = *downloaded.lock().await;
    emit(
        app,
        "complete",
        "GunGlory Runtime v1 ready",
        downloaded_bytes,
        total_bytes,
    );

    Ok(RuntimeInstallReport {
        installed: true,
        downloaded_bytes,
        minecraft_version: MINECRAFT_VERSION,
        forge_version: FORGE_VERSION,
        runtime: final_check,
    })
}

fn from_library_artifact(label: &str, artifact: &LibraryArtifact) -> InstallDownload {
    InstallDownload {
        label: label.to_string(),
        url: artifact.url.clone(),
        sha1: artifact.sha1.clone(),
        size: artifact.size,
        relative_path: format!("libraries/{}", artifact.path),
    }
}

async fn ensure_download(
    app: &AppHandle,
    http: &Client,
    root: &Path,
    item: InstallDownload,
    downloaded: Arc<Mutex<u64>>,
    total_bytes: u64,
) -> Result<(), RuntimeInstallError> {
    let target = root.join(&item.relative_path);
    if let Ok(existing) = fs::read(&target).await {
        if existing.len() as u64 == item.size
            && sha1_hex(&existing).eq_ignore_ascii_case(&item.sha1)
        {
            return Ok(());
        }
    }

    if let Some(parent) = target.parent() {
        fs::create_dir_all(parent).await?;
    }
    let bytes = http
        .get(&item.url)
        .send()
        .await?
        .error_for_status()?
        .bytes()
        .await?;
    if bytes.len() as u64 != item.size {
        return Err(RuntimeInstallError::Metadata(format!(
            "size mismatch for {}",
            item.relative_path
        )));
    }
    verify_sha1_bytes(&bytes, &item.sha1, &item.relative_path)?;
    let part = target.with_extension("ggo-part");
    fs::write(&part, &bytes).await?;
    if fs::metadata(&target).await.is_ok() {
        fs::remove_file(&target).await?;
    }
    fs::rename(&part, &target).await?;

    let mut aggregate = downloaded.lock().await;
    *aggregate = aggregate.saturating_add(bytes.len() as u64);
    emit(
        app,
        "minecraft",
        &item.label,
        *aggregate,
        total_bytes,
    );
    Ok(())
}

async fn install_forge(
    app: &AppHandle,
    http: &Client,
    install_dir: &Path,
    java_path: &str,
) -> Result<(), RuntimeInstallError> {
    let forge_coordinate = format!("{MINECRAFT_VERSION}-{FORGE_VERSION}");
    let forge_dir = install_dir.join(".ggo").join("installers");
    fs::create_dir_all(&forge_dir).await?;
    let installer = forge_dir.join(format!("forge-{forge_coordinate}-installer.jar"));
    let url = format!(
        "{FORGE_MAVEN_BASE}/{forge_coordinate}/forge-{forge_coordinate}-installer.jar"
    );
    let sha1_url = format!("{url}.sha1");

    emit(app, "forge", "Downloading Forge installer", 0, 0);
    let expected_sha1 = http
        .get(&sha1_url)
        .send()
        .await?
        .error_for_status()?
        .text()
        .await?
        .trim()
        .split_whitespace()
        .next()
        .unwrap_or_default()
        .to_string();
    if expected_sha1.len() != 40 {
        return Err(RuntimeInstallError::Metadata(
            "Forge installer SHA1 is invalid".to_string(),
        ));
    }

    let needs_installer = match fs::read(&installer).await {
        Ok(existing) => !sha1_hex(&existing).eq_ignore_ascii_case(&expected_sha1),
        Err(_) => true,
    };
    if needs_installer {
        let bytes = http.get(&url).send().await?.error_for_status()?.bytes().await?;
        verify_sha1_bytes(&bytes, &expected_sha1, "Forge installer")?;
        fs::write(&installer, bytes).await?;
    }

    let launcher_profiles = install_dir.join("launcher_profiles.json");
    if fs::metadata(&launcher_profiles).await.is_err() {
        fs::write(
            &launcher_profiles,
            br#"{"profiles":{},"settings":{},"version":3}"#,
        )
        .await?;
    }

    emit(app, "forge", "Installing Forge 47.4.10", 0, 0);
    let java = java_path.to_string();
    let installer_path = installer.clone();
    let target = install_dir.to_path_buf();
    let status = tokio::task::spawn_blocking(move || {
        Command::new(java)
            .arg("-jar")
            .arg(installer_path)
            .arg("--installClient")
            .arg(target)
            .status()
    })
    .await
    .map_err(|error| RuntimeInstallError::Metadata(error.to_string()))??;
    if !status.success() {
        return Err(RuntimeInstallError::ForgeInstaller(
            status.code().unwrap_or(-1),
        ));
    }
    Ok(())
}

fn verify_sha1_bytes(
    bytes: &[u8],
    expected: &str,
    label: &str,
) -> Result<(), RuntimeInstallError> {
    if sha1_hex(bytes).eq_ignore_ascii_case(expected) {
        Ok(())
    } else {
        Err(RuntimeInstallError::Checksum(label.to_string()))
    }
}

fn sha1_hex(bytes: &[u8]) -> String {
    let mut hasher = Sha1::new();
    hasher.update(bytes);
    hex::encode(hasher.finalize())
}

fn library_allowed(library: &Library) -> bool {
    let Some(rules) = &library.rules else {
        return true;
    };
    let mut allowed = false;
    for rule in rules {
        if rule_matches(rule) {
            allowed = rule.action == "allow";
        }
    }
    allowed
}

fn rule_matches(rule: &Rule) -> bool {
    let Some(os) = &rule.os else {
        return true;
    };
    if let Some(name) = &os.name {
        if name != current_os_name() {
            return false;
        }
    }
    if let Some(arch) = &os.arch {
        let current_arch = if cfg!(target_pointer_width = "64") {
            "x86_64"
        } else {
            "x86"
        };
        if arch != current_arch && !(arch == "x86" && current_arch == "x86_64") {
            return false;
        }
    }
    true
}

fn native_classifier(library: &Library) -> Option<String> {
    let template = library.natives.as_ref()?.get(current_os_name())?;
    let arch = if cfg!(target_pointer_width = "64") {
        "64"
    } else {
        "32"
    };
    Some(template.replace("${arch}", arch))
}

fn current_os_name() -> &'static str {
    if cfg!(target_os = "windows") {
        "windows"
    } else if cfg!(target_os = "macos") {
        "osx"
    } else {
        "linux"
    }
}

fn emit(app: &AppHandle, stage: &str, current_file: &str, downloaded: u64, total: u64) {
    let _ = app.emit(
        "ggo-runtime-install-progress",
        RuntimeInstallProgress {
            stage: stage.to_string(),
            current_file: current_file.to_string(),
            downloaded_bytes: downloaded,
            total_bytes: total,
        },
    );
}

#[cfg(test)]
mod tests {
    use super::{current_os_name, sha1_hex};

    #[test]
    fn hashes_sha1() {
        assert_eq!(
            sha1_hex(b"GunGloryOnline"),
            "03a482568f8687d0e3c0d7796ef9534c2d0f9940"
        );
    }

    #[test]
    fn maps_supported_os() {
        assert!(matches!(current_os_name(), "windows" | "linux" | "osx"));
    }
}
