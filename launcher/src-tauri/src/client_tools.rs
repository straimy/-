use serde::Serialize;
use std::{
    fs,
    io::Read,
    path::{Path, PathBuf},
    time::UNIX_EPOCH,
};

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ClientFileEntry {
    pub name: String,
    pub path: String,
    pub size_bytes: u64,
    pub modified_unix_ms: u128,
    pub disabled: bool,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ClientFolderEntry {
    pub name: String,
    pub path: String,
    pub modified_unix_ms: u128,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct LogSnapshot {
    pub path: String,
    pub content: String,
    pub truncated: bool,
}

fn safe_child(root: &Path, kind: &str) -> Result<PathBuf, String> {
    let child = match kind {
        "mods" => "mods",
        "resourcepacks" => "resourcepacks",
        "shaderpacks" => "shaderpacks",
        "screenshots" => "screenshots",
        "logs" => "logs",
        "crash-reports" => "crash-reports",
        "config" => "config",
        "saves" => "saves",
        _ => return Err(format!("unsupported GGO folder: {kind}")),
    };
    Ok(root.join(child))
}

fn safe_file_name(name: &str) -> Result<&str, String> {
    let trimmed = name.trim();
    if trimmed.is_empty()
        || trimmed == "."
        || trimmed == ".."
        || trimmed.contains('/')
        || trimmed.contains('\\')
    {
        return Err("invalid client file name".to_string());
    }
    Ok(trimmed)
}

pub fn open_client_folder(install_dir: &Path, kind: &str) -> Result<(), String> {
    let path = safe_child(install_dir, kind)?;
    fs::create_dir_all(&path).map_err(|e| e.to_string())?;
    open::that(path).map_err(|e| e.to_string())
}

pub fn list_client_files(install_dir: &Path, kind: &str) -> Result<Vec<ClientFileEntry>, String> {
    let path = safe_child(install_dir, kind)?;
    fs::create_dir_all(&path).map_err(|e| e.to_string())?;
    let mut entries = Vec::new();
    for item in fs::read_dir(path).map_err(|e| e.to_string())? {
        let item = item.map_err(|e| e.to_string())?;
        let file_type = item.file_type().map_err(|e| e.to_string())?;
        if !file_type.is_file() {
            continue;
        }
        let meta = item.metadata().map_err(|e| e.to_string())?;
        let name = item.file_name().to_string_lossy().into_owned();
        let lower = name.to_ascii_lowercase();
        let disabled = lower.ends_with(".disabled") || lower.ends_with(".off");
        let modified_unix_ms = meta
            .modified()
            .ok()
            .and_then(|t| t.duration_since(UNIX_EPOCH).ok())
            .map(|d| d.as_millis())
            .unwrap_or(0);
        entries.push(ClientFileEntry {
            name,
            path: item.path().to_string_lossy().into_owned(),
            size_bytes: meta.len(),
            modified_unix_ms,
            disabled,
        });
    }
    entries.sort_by(|a, b| {
        a.name
            .to_ascii_lowercase()
            .cmp(&b.name.to_ascii_lowercase())
    });
    Ok(entries)
}

pub fn list_client_folders(
    install_dir: &Path,
    kind: &str,
) -> Result<Vec<ClientFolderEntry>, String> {
    let path = safe_child(install_dir, kind)?;
    fs::create_dir_all(&path).map_err(|e| e.to_string())?;
    let mut entries = Vec::new();
    for item in fs::read_dir(path).map_err(|e| e.to_string())? {
        let item = item.map_err(|e| e.to_string())?;
        if !item.file_type().map_err(|e| e.to_string())?.is_dir() {
            continue;
        }
        let meta = item.metadata().map_err(|e| e.to_string())?;
        entries.push(ClientFolderEntry {
            name: item.file_name().to_string_lossy().into_owned(),
            path: item.path().to_string_lossy().into_owned(),
            modified_unix_ms: meta
                .modified()
                .ok()
                .and_then(|t| t.duration_since(UNIX_EPOCH).ok())
                .map(|d| d.as_millis())
                .unwrap_or(0),
        });
    }
    entries.sort_by(|a, b| {
        b.modified_unix_ms.cmp(&a.modified_unix_ms).then_with(|| {
            a.name
                .to_ascii_lowercase()
                .cmp(&b.name.to_ascii_lowercase())
        })
    });
    Ok(entries)
}

pub fn set_mod_enabled(
    install_dir: &Path,
    file_name: &str,
    enabled: bool,
) -> Result<ClientFileEntry, String> {
    let file_name = safe_file_name(file_name)?;
    let mods = safe_child(install_dir, "mods")?;
    fs::create_dir_all(&mods).map_err(|e| e.to_string())?;
    let source = mods.join(file_name);
    if !source.is_file() {
        return Err(format!("mod file not found: {file_name}"));
    }

    let lower = file_name.to_ascii_lowercase();
    let target_name = if enabled {
        if lower.ends_with(".disabled") {
            file_name[..file_name.len() - ".disabled".len()].to_string()
        } else if lower.ends_with(".off") {
            file_name[..file_name.len() - ".off".len()].to_string()
        } else {
            return file_entry(&source);
        }
    } else {
        if lower.ends_with(".disabled") || lower.ends_with(".off") {
            return file_entry(&source);
        }
        if !lower.ends_with(".jar") {
            return Err("only .jar mods can be disabled from the launcher".to_string());
        }
        format!("{file_name}.disabled")
    };

    let target = mods.join(target_name);
    if target.exists() {
        return Err(format!("target already exists: {}", target.display()));
    }
    fs::rename(&source, &target).map_err(|e| e.to_string())?;
    file_entry(&target)
}

fn file_entry(path: &Path) -> Result<ClientFileEntry, String> {
    let meta = fs::metadata(path).map_err(|e| e.to_string())?;
    let name = path
        .file_name()
        .and_then(|v| v.to_str())
        .unwrap_or_default()
        .to_string();
    let lower = name.to_ascii_lowercase();
    Ok(ClientFileEntry {
        name,
        path: path.to_string_lossy().into_owned(),
        size_bytes: meta.len(),
        modified_unix_ms: meta
            .modified()
            .ok()
            .and_then(|t| t.duration_since(UNIX_EPOCH).ok())
            .map(|d| d.as_millis())
            .unwrap_or(0),
        disabled: lower.ends_with(".disabled") || lower.ends_with(".off"),
    })
}

fn read_tail(path: &Path, limit: usize) -> Result<LogSnapshot, String> {
    if !path.is_file() {
        return Ok(LogSnapshot {
            path: path.to_string_lossy().into_owned(),
            content: String::new(),
            truncated: false,
        });
    }
    let mut file = fs::File::open(path).map_err(|e| e.to_string())?;
    let size = file.metadata().map_err(|e| e.to_string())?.len() as usize;
    let mut bytes = Vec::with_capacity(size.min(limit));
    file.read_to_end(&mut bytes).map_err(|e| e.to_string())?;
    let truncated = bytes.len() > limit;
    if truncated {
        bytes = bytes[bytes.len() - limit..].to_vec();
    }
    Ok(LogSnapshot {
        path: path.to_string_lossy().into_owned(),
        content: String::from_utf8_lossy(&bytes).into_owned(),
        truncated,
    })
}

pub fn read_latest_log(install_dir: &Path) -> Result<LogSnapshot, String> {
    read_tail(&install_dir.join("logs").join("latest.log"), 512 * 1024)
}

pub fn read_launch_log(install_dir: &Path) -> Result<LogSnapshot, String> {
    read_tail(
        &install_dir.join("logs").join("ggo-launcher-minecraft.log"),
        1024 * 1024,
    )
}

pub fn read_latest_crash(install_dir: &Path) -> Result<LogSnapshot, String> {
    let dir = install_dir.join("crash-reports");
    if !dir.is_dir() {
        return Ok(LogSnapshot {
            path: dir.to_string_lossy().into_owned(),
            content: String::new(),
            truncated: false,
        });
    }
    let mut newest: Option<(std::time::SystemTime, PathBuf)> = None;
    for item in fs::read_dir(&dir).map_err(|e| e.to_string())? {
        let item = item.map_err(|e| e.to_string())?;
        if !item.file_type().map_err(|e| e.to_string())?.is_file() {
            continue;
        }
        let modified = item
            .metadata()
            .and_then(|m| m.modified())
            .unwrap_or(UNIX_EPOCH);
        if newest
            .as_ref()
            .map(|(time, _)| modified > *time)
            .unwrap_or(true)
        {
            newest = Some((modified, item.path()));
        }
    }
    match newest {
        Some((_, path)) => read_tail(&path, 512 * 1024),
        None => Ok(LogSnapshot {
            path: dir.to_string_lossy().into_owned(),
            content: String::new(),
            truncated: false,
        }),
    }
}
