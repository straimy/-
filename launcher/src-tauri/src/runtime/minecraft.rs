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
pub const DEFAULT_SERVER: &str = "31.77.232.254";
pub const DEFAULT_SERVER_PORT: u16 = 24842;

#[derive(Debug, Error)]
pub enum MinecraftRuntimeError {
    #[error("minecraft runtime is incomplete: {0}")]
    Incomplete(String),
    #[error("minecraft runtime launch is not implemented until identity is available")]
    IdentityRequired,
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

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct RuntimeCheck {
    pub ready: bool,
    pub minecraft_version: &'static str,
    pub forge_version: &'static str,
    pub java: Option<JavaRuntimeInfo>,
    pub missing: Vec<String>,
    pub version_profile: String,
    pub game_directory: String,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct LaunchPreparation {
    pub ready: bool,
    pub java_path: Option<String>,
    pub game_directory: String,
    pub version_profile: String,
    pub server: &'static str,
    pub port: u16,
    pub extra_game_args: Vec<String>,
    pub blockers: Vec<String>,
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

    fn verify(&self, install_dir: &Path) -> Result<(), Self::Error> {
        let check = check_runtime(install_dir, None);
        if check.ready {
            Ok(())
        } else {
            Err(MinecraftRuntimeError::Incomplete(check.missing.join(", ")))
        }
    }

    fn launch(&self, _install_dir: &Path) -> Result<(), Self::Error> {
        Err(MinecraftRuntimeError::IdentityRequired)
    }
}

pub fn check_runtime(install_dir: &Path, custom_java: Option<&str>) -> RuntimeCheck {
    let forge_profile = format!("{MINECRAFT_VERSION}-forge-{FORGE_VERSION}");
    let version_profile_path = install_dir
        .join("versions")
        .join(&forge_profile)
        .join(format!("{forge_profile}.json"));
    let vanilla_profile_path = install_dir
        .join("versions")
        .join(MINECRAFT_VERSION)
        .join(format!("{MINECRAFT_VERSION}.json"));
    let forge_library = install_dir
        .join("libraries")
        .join("net")
        .join("minecraftforge")
        .join("forge")
        .join(format!("{MINECRAFT_VERSION}-{FORGE_VERSION}"));
    let assets = install_dir.join("assets");
    let libraries = install_dir.join("libraries");

    let mut missing = Vec::new();
    for (label, path) in [
        ("Forge version profile", &version_profile_path),
        ("Minecraft 1.20.1 profile", &vanilla_profile_path),
        ("Forge libraries", &forge_library),
        ("Minecraft libraries", &libraries),
        ("Minecraft assets", &assets),
    ] {
        if !path.exists() {
            missing.push(format!("{label}: {}", path.display()));
        }
    }

    let java = detect_java(custom_java)
        .into_iter()
        .find(|candidate| candidate.compatible);
    if java.is_none() {
        missing.push("Java 17 runtime".to_string());
    }

    RuntimeCheck {
        ready: missing.is_empty(),
        minecraft_version: MINECRAFT_VERSION,
        forge_version: FORGE_VERSION,
        java,
        missing,
        version_profile: forge_profile,
        game_directory: install_dir.to_string_lossy().into_owned(),
    }
}

pub fn prepare_launch(install_dir: &Path, custom_java: Option<&str>) -> LaunchPreparation {
    let check = check_runtime(install_dir, custom_java);
    let java_path = check.java.as_ref().map(|java| java.path.clone());
    let blockers = check.missing.clone();

    LaunchPreparation {
        ready: check.ready,
        java_path,
        game_directory: check.game_directory,
        version_profile: check.version_profile,
        server: DEFAULT_SERVER,
        port: DEFAULT_SERVER_PORT,
        extra_game_args: vec![
            "--server".to_string(),
            DEFAULT_SERVER.to_string(),
            "--port".to_string(),
            DEFAULT_SERVER_PORT.to_string(),
        ],
        blockers,
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
        .find(|part| {
            part.chars()
                .next()
                .is_some_and(|ch| ch.is_ascii_digit())
                && part.contains('.')
        })
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
