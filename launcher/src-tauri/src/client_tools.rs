use serde::Serialize;
use std::{fs, io::Read, path::{Path, PathBuf}, time::UNIX_EPOCH};

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
        _ => return Err(format!("unsupported GGO folder: {kind}")),
    };
    Ok(root.join(child))
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
        if !file_type.is_file() { continue; }
        let meta = item.metadata().map_err(|e| e.to_string())?;
        let name = item.file_name().to_string_lossy().into_owned();
        let lower = name.to_ascii_lowercase();
        let disabled = lower.ends_with(".disabled") || lower.ends_with(".off");
        let modified_unix_ms = meta.modified().ok().and_then(|t| t.duration_since(UNIX_EPOCH).ok()).map(|d| d.as_millis()).unwrap_or(0);
        entries.push(ClientFileEntry {
            name,
            path: item.path().to_string_lossy().into_owned(),
            size_bytes: meta.len(),
            modified_unix_ms,
            disabled,
        });
    }
    entries.sort_by(|a,b| a.name.to_ascii_lowercase().cmp(&b.name.to_ascii_lowercase()));
    Ok(entries)
}

fn read_tail(path: &Path, limit: usize) -> Result<LogSnapshot, String> {
    if !path.is_file() {
        return Ok(LogSnapshot { path: path.to_string_lossy().into_owned(), content: String::new(), truncated: false });
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

pub fn read_latest_crash(install_dir: &Path) -> Result<LogSnapshot, String> {
    let dir = install_dir.join("crash-reports");
    if !dir.is_dir() {
        return Ok(LogSnapshot { path: dir.to_string_lossy().into_owned(), content: String::new(), truncated: false });
    }
    let mut newest: Option<(std::time::SystemTime, PathBuf)> = None;
    for item in fs::read_dir(&dir).map_err(|e| e.to_string())? {
        let item = item.map_err(|e| e.to_string())?;
        if !item.file_type().map_err(|e| e.to_string())?.is_file() { continue; }
        let modified = item.metadata().and_then(|m| m.modified()).unwrap_or(UNIX_EPOCH);
        if newest.as_ref().map(|(time,_)| modified > *time).unwrap_or(true) {
            newest = Some((modified, item.path()));
        }
    }
    match newest {
        Some((_, path)) => read_tail(&path, 512 * 1024),
        None => Ok(LogSnapshot { path: dir.to_string_lossy().into_owned(), content: String::new(), truncated: false }),
    }
}
