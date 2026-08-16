use super::GameRuntime;
use serde::Serialize;
use std::{
    collections::HashSet,
    env,
    path::{Path, PathBuf},
    process::Command,
};
use thiserror::Error;

pub const MINECRAFT_VERSION: &str = "1.20.1";
pub const FORGE_VERSION: &str = "47.4.10";
pub const REQUIRED_JAVA_MAJOR: u8 = 17;

#[derive(Debug, Error)]
pub enum MinecraftRuntimeError {
    #[error("minecraft runtime installation is not implemented yet")]
    NotInstalled,
    #[error("minecraft runtime launch is not implemented yet")]
    NotImplemented,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct JavaRuntimeInfo {
    pub path: String,
    pub version: String,
    pub major: u32,
    pub compatible: bool,
    pub source: &'static str,
}

#[derive(Debug, Clone)]
struct JavaCandidate {
    path: PathBuf,
    source: &'static str,
}

pub struct MinecraftForgeRuntime;

impl GameRuntime for MinecraftForgeRuntime {
    type Error = MinecraftRuntimeError;

    fn id(&self) -> &'static str {
        "minecraft-forge"
    }

    fn verify(&self, _install_dir: &Path) -> Result<(), Self::Error> {
        Err(MinecraftRuntimeError::NotInstalled)
    }

    fn launch(&self, _install_dir: &Path) -> Result<(), Self::Error> {
        Err(MinecraftRuntimeError::NotImplemented)
    }
}

pub fn detect_java(custom_path: Option<&str>) -> Vec<JavaRuntimeInfo> {
    let mut candidates = Vec::new();
    if let Some(path) = custom_path.map(str::trim).filter(|path| !path.is_empty()) {
        candidates.push(JavaCandidate {
            path: PathBuf::from(path),
            source: "custom",
        });
    }

    if let Ok(java_home) = env::var("JAVA_HOME") {
        let executable = if cfg!(windows) { "java.exe" } else { "java" };
        candidates.push(JavaCandidate {
            path: PathBuf::from(java_home).join("bin").join(executable),
            source: "JAVA_HOME",
        });
    }

    candidates.push(JavaCandidate {
        path: PathBuf::from(if cfg!(windows) { "java.exe" } else { "java" }),
        source: "PATH",
    });

    let mut seen = HashSet::new();
    candidates
        .into_iter()
        .filter(|candidate| seen.insert(candidate.path.clone()))
        .filter_map(probe_java)
        .collect()
}

fn probe_java(candidate: JavaCandidate) -> Option<JavaRuntimeInfo> {
    let output = Command::new(&candidate.path).arg("-version").output().ok()?;
    if !output.status.success() {
        return None;
    }

    let mut text = String::from_utf8_lossy(&output.stderr).to_string();
    if text.trim().is_empty() {
        text = String::from_utf8_lossy(&output.stdout).to_string();
    }
    let version = extract_java_version(&text)?;
    let major = java_major(&version)?;

    Some(JavaRuntimeInfo {
        path: candidate.path.to_string_lossy().into_owned(),
        version,
        major,
        compatible: major == REQUIRED_JAVA_MAJOR as u32,
        source: candidate.source,
    })
}

fn extract_java_version(text: &str) -> Option<String> {
    let version_marker = "version \"";
    if let Some(start) = text.find(version_marker) {
        let rest = &text[start + version_marker.len()..];
        return rest.find('"').map(|end| rest[..end].to_string());
    }

    text.split_whitespace()
        .find(|part| part.chars().next().is_some_and(|ch| ch.is_ascii_digit()) && part.contains('.'))
        .map(|part| part.trim_matches('"').to_string())
}

fn java_major(version: &str) -> Option<u32> {
    let mut parts = version.split('.');
    let first = parts.next()?.parse::<u32>().ok()?;
    if first == 1 {
        parts.next()?.parse::<u32>().ok()
    } else {
        Some(first)
    }
}

#[cfg(test)]
mod tests {
    use super::{extract_java_version, java_major};

    #[test]
    fn parses_modern_openjdk_version() {
        let text = "openjdk version \"17.0.12\" 2024-07-16\nOpenJDK Runtime Environment";
        assert_eq!(extract_java_version(text).as_deref(), Some("17.0.12"));
        assert_eq!(java_major("17.0.12"), Some(17));
    }

    #[test]
    fn parses_legacy_java_major() {
        assert_eq!(java_major("1.8.0_412"), Some(8));
    }

    #[test]
    fn rejects_non_version_text() {
        assert_eq!(extract_java_version("java command failed"), None);
    }
}
