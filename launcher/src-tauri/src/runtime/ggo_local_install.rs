use serde::Serialize;
use sha2::{Digest, Sha256};
use std::{
    fs::{self, File},
    io::{self, Read, Write},
    path::{Path, PathBuf},
};
use thiserror::Error;
use uuid::Uuid;
use zip::ZipArchive;

pub const GGO_VERSION: &str = "v34";
pub const CORE_ARCHIVE_PATH: &str = "client/mods/gungloryonline-core-0.9.1-v34.jar";
pub const UI_ARCHIVE_PATH: &str = "client/mods/gungloryonline-ui-0.9.1-v34.jar";
pub const RESOURCE_PACK_ARCHIVE_PATH: &str =
    "client/resourcepacks/GunGloryOnline-ResourcePack-1.20.1-v5-swittie-social.zip";
pub const CORE_FILE_NAME: &str = "gungloryonline-core-0.9.1-v34.jar";
pub const UI_FILE_NAME: &str = "gungloryonline-ui-0.9.1-v34.jar";
pub const RESOURCE_PACK_FILE_NAME: &str =
    "GunGloryOnline-ResourcePack-1.20.1-v5-swittie-social.zip";

pub const CORE_SHA256: &str =
    "2fb1d083a9d79a1dfcc166cc9c4dc0c3b5b214648244911d19c1c3253b213304";
pub const UI_SHA256: &str =
    "af4fdad2a6330e134701e1b0ee20ab7c9860b4042adc3974bbab74454b470baf";
pub const RESOURCE_PACK_SHA256: &str =
    "33c40c492ce5db2b8312dc09326a64bdec4f11006ace07ab09a41a213a9308b7";

pub const CORE_SIZE: u64 = 439_260;
pub const UI_SIZE: u64 = 103_459;
pub const RESOURCE_PACK_SIZE: u64 = 12_668_450;

#[derive(Debug, Error)]
pub enum LocalInstallError {
    #[error("I/O error: {0}")]
    Io(#[from] io::Error),
    #[error("invalid GGO full-install zip: {0}")]
    Zip(#[from] zip::result::ZipError),
    #[error("missing expected file in GGO full-install zip: {0}")]
    Missing(String),
    #[error("size mismatch for {name}: expected {expected}, got {actual}")]
    SizeMismatch { name: String, expected: u64, actual: u64 },
    #[error("SHA256 mismatch for {0}")]
    Checksum(String),
    #[error("local package path does not point to a file: {0}")]
    InvalidSource(String),
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct LocalInstallReport {
    pub version: &'static str,
    pub installed_files: Vec<String>,
    pub skipped_files: Vec<String>,
    pub removed_legacy_files: Vec<String>,
    pub resource_pack_enabled: bool,
}

pub fn install_local(package_zip: &Path, install_dir: &Path) -> Result<LocalInstallReport, LocalInstallError> {
    require_file(package_zip)?;
    fs::create_dir_all(install_dir.join("mods"))?;
    fs::create_dir_all(install_dir.join("resourcepacks"))?;

    let removed_legacy_files = remove_legacy_ggo_jars(&install_dir.join("mods"))?;
    let mut installed_files = Vec::new();
    let mut skipped_files = Vec::new();
    let package = File::open(package_zip)?;
    let mut archive = ZipArchive::new(package)?;

    install_archive_entry(&mut archive, CORE_ARCHIVE_PATH, &install_dir.join("mods").join(CORE_FILE_NAME), CORE_SIZE, CORE_SHA256, &mut installed_files, &mut skipped_files)?;
    install_archive_entry(&mut archive, UI_ARCHIVE_PATH, &install_dir.join("mods").join(UI_FILE_NAME), UI_SIZE, UI_SHA256, &mut installed_files, &mut skipped_files)?;
    install_archive_entry(&mut archive, RESOURCE_PACK_ARCHIVE_PATH, &install_dir.join("resourcepacks").join(RESOURCE_PACK_FILE_NAME), RESOURCE_PACK_SIZE, RESOURCE_PACK_SHA256, &mut installed_files, &mut skipped_files)?;

    let resource_pack_enabled = enable_resource_pack(install_dir)?;
    write_local_state(install_dir)?;

    Ok(LocalInstallReport { version: GGO_VERSION, installed_files, skipped_files, removed_legacy_files, resource_pack_enabled })
}

fn require_file(path: &Path) -> Result<(), LocalInstallError> {
    if path.is_file() { Ok(()) } else { Err(LocalInstallError::InvalidSource(path.to_string_lossy().into_owned())) }
}

fn remove_legacy_ggo_jars(mods_dir: &Path) -> Result<Vec<String>, io::Error> {
    let mut removed = Vec::new();
    if !mods_dir.is_dir() { return Ok(removed); }
    for entry in fs::read_dir(mods_dir)? {
        let entry = entry?;
        let path = entry.path();
        if !path.is_file() { continue; }
        let Some(name) = path.file_name().and_then(|value| value.to_str()) else { continue; };
        let lower = name.to_ascii_lowercase();
        let managed = lower.ends_with(".jar") && (
            lower.starts_with("gungloryonline-core-") ||
            lower.starts_with("gungloryonline-ui-") ||
            lower.starts_with("gunnerarena-core-") ||
            lower.starts_with("gunnerarena-ui-")
        );
        if managed && name != CORE_FILE_NAME && name != UI_FILE_NAME {
            fs::remove_file(&path)?;
            removed.push(name.to_string());
        }
    }
    Ok(removed)
}

#[allow(clippy::too_many_arguments)]
fn install_archive_entry(
    archive: &mut ZipArchive<File>, archive_path: &str, target: &Path,
    expected_size: u64, expected_sha256: &str,
    installed: &mut Vec<String>, skipped: &mut Vec<String>,
) -> Result<(), LocalInstallError> {
    if valid_existing(target, expected_size, expected_sha256)? {
        skipped.push(relative_label(target));
        return Ok(());
    }
    let mut entry = archive.by_name(archive_path).map_err(|_| LocalInstallError::Missing(archive_path.to_string()))?;
    if entry.size() != expected_size {
        return Err(LocalInstallError::SizeMismatch { name: archive_path.to_string(), expected: expected_size, actual: entry.size() });
    }
    let parent = target.parent().ok_or_else(|| LocalInstallError::InvalidSource(target.to_string_lossy().into_owned()))?;
    fs::create_dir_all(parent)?;
    let temp = temp_path(target);
    let mut output = File::create(&temp)?;
    let mut hasher = Sha256::new();
    let mut written = 0_u64;
    let mut buffer = [0_u8; 64 * 1024];
    loop {
        let count = entry.read(&mut buffer)?;
        if count == 0 { break; }
        output.write_all(&buffer[..count])?;
        hasher.update(&buffer[..count]);
        written += count as u64;
    }
    output.sync_all()?;
    if written != expected_size {
        let _ = fs::remove_file(&temp);
        return Err(LocalInstallError::SizeMismatch { name: archive_path.to_string(), expected: expected_size, actual: written });
    }
    let digest = hex::encode(hasher.finalize());
    if !digest.eq_ignore_ascii_case(expected_sha256) {
        let _ = fs::remove_file(&temp);
        return Err(LocalInstallError::Checksum(archive_path.to_string()));
    }
    atomic_replace(&temp, target)?;
    installed.push(relative_label(target));
    Ok(())
}

fn valid_existing(path: &Path, expected_size: u64, expected_sha256: &str) -> Result<bool, LocalInstallError> {
    let Ok(metadata) = fs::metadata(path) else { return Ok(false); };
    if !metadata.is_file() || metadata.len() != expected_size { return Ok(false); }
    Ok(sha256_file(path)?.eq_ignore_ascii_case(expected_sha256))
}

fn sha256_file(path: &Path) -> Result<String, io::Error> {
    let mut file = File::open(path)?;
    let mut hasher = Sha256::new();
    let mut buffer = [0_u8; 64 * 1024];
    loop {
        let count = file.read(&mut buffer)?;
        if count == 0 { break; }
        hasher.update(&buffer[..count]);
    }
    Ok(hex::encode(hasher.finalize()))
}

fn atomic_replace(temp: &Path, target: &Path) -> Result<(), io::Error> {
    if !target.exists() { return fs::rename(temp, target); }
    let backup = target.with_extension(format!("ggo-backup-{}", Uuid::new_v4()));
    fs::rename(target, &backup)?;
    match fs::rename(temp, target) {
        Ok(()) => { let _ = fs::remove_file(backup); Ok(()) }
        Err(error) => { let _ = fs::rename(&backup, target); Err(error) }
    }
}

fn temp_path(target: &Path) -> PathBuf {
    let file_name = target.file_name().and_then(|value| value.to_str()).unwrap_or("file");
    target.with_file_name(format!(".{file_name}.ggo-part-{}", Uuid::new_v4()))
}

fn enable_resource_pack(install_dir: &Path) -> Result<bool, io::Error> {
    let options_path = install_dir.join("options.txt");
    let pack_ref = format!("file/{RESOURCE_PACK_FILE_NAME}");
    let desired = format!("resourcePacks:[\"{pack_ref}\"]");
    let original = fs::read_to_string(&options_path).unwrap_or_default();
    let mut lines: Vec<String> = original.lines().map(str::to_string).collect();
    let mut found = false;
    for line in &mut lines {
        if line.starts_with("resourcePacks:") {
            found = true;
            if line.contains(&pack_ref) { return Ok(true); }
            let prefix = "resourcePacks:[";
            if line.starts_with(prefix) && line.ends_with(']') {
                let inner = &line[prefix.len()..line.len() - 1];
                *line = if inner.trim().is_empty() { desired.clone() } else { format!("resourcePacks:[{inner},\"{pack_ref}\"]") };
            } else { *line = desired.clone(); }
            break;
        }
    }
    if !found { lines.push(desired); }
    let mut next = lines.join("\n");
    next.push('\n');
    let temp = install_dir.join(format!(".options.txt.ggo-part-{}", Uuid::new_v4()));
    fs::write(&temp, next)?;
    atomic_replace(&temp, &options_path)?;
    Ok(true)
}

fn write_local_state(install_dir: &Path) -> Result<(), io::Error> {
    let state = serde_json::json!({
        "schema": 1,
        "source": "local-full-install",
        "gameVersion": GGO_VERSION,
        "files": [
            {"path": format!("mods/{CORE_FILE_NAME}"), "sha256": CORE_SHA256, "size": CORE_SIZE},
            {"path": format!("mods/{UI_FILE_NAME}"), "sha256": UI_SHA256, "size": UI_SIZE},
            {"path": format!("resourcepacks/{RESOURCE_PACK_FILE_NAME}"), "sha256": RESOURCE_PACK_SHA256, "size": RESOURCE_PACK_SIZE}
        ]
    });
    let bytes = serde_json::to_vec_pretty(&state).map_err(io::Error::other)?;
    fs::write(install_dir.join(".ggo-local-state.json"), bytes)
}

fn relative_label(path: &Path) -> String { path.to_string_lossy().into_owned() }

#[cfg(test)]
mod tests {
    use super::{CORE_SHA256, RESOURCE_PACK_FILE_NAME, UI_SHA256};
    #[test]
    fn pinned_hashes_are_sha256() {
        assert_eq!(CORE_SHA256.len(), 64);
        assert_eq!(UI_SHA256.len(), 64);
    }
    #[test]
    fn resource_pack_name_is_normalized() {
        assert!(RESOURCE_PACK_FILE_NAME.ends_with(".zip"));
    }
}
