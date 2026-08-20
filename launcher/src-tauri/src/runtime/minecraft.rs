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
pub const DEFAULT_SERVER: &str = "2.26.100.125";
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
}

pub fn detect_java(custom_java: Option<&str>) -> Vec<JavaRuntimeInfo> {
    let mut candidates = Vec::new();
    let mut seen = HashSet::new();

    if let Some(path) = custom_java.map(str::trim).filter(|value| !value.is_empty()) {
        push_candidate(&mut candidates, &mut seen, PathBuf::from(path), "custom");
    }
    if let Ok(java_home) = env::var("JAVA_HOME") {
        push_candidate(
            &mut candidates,
            &mut seen,
            PathBuf::from(java_home).join("bin").join(java_binary()),
            "JAVA_HOME",
        );
    }
    if let Some(path) = find_on_path(java_binary()) {
        push_candidate(&mut candidates, &mut seen, path, "PATH");
    }

    candidates
        .into_iter()
        .filter_map(|candidate| inspect_java(candidate).ok())
        .collect()
}

pub fn check_runtime(install_dir: &Path, custom_java: Option<&str>) -> RuntimeCheck {
    let java = detect_java(custom_java)
        .into_iter()
        .find(|candidate| candidate.compatible);
    let version_profile = format!("1.20.1-forge-{FORGE_VERSION}");
    let mut missing = Vec::new();

    if java.is_none() {
        missing.push("Java 17".to_string());
    }
    let client_jar = install_dir
        .join("versions")
        .join(MINECRAFT_VERSION)
        .join(format!("{MINECRAFT_VERSION}.jar"));
    if !client_jar.is_file() {
        missing.push(format!("Minecraft {MINECRAFT_VERSION}"));
    }
    let forge_json = install_dir
        .join("versions")
        .join(&version_profile)
        .join(format!("{version_profile}.json"));
    if !forge_json.is_file() {
        missing.push(format!("Forge {FORGE_VERSION}"));
    }

    RuntimeCheck {
        ready: missing.is_empty(),
        minecraft_version: MINECRAFT_VERSION,
        forge_version: FORGE_VERSION,
        java,
        missing,
        version_profile,
        game_directory: install_dir.to_string_lossy().into_owned(),
    }
}

pub fn prepare_launch(install_dir: &Path, custom_java: Option<&str>) -> LaunchPreparation {
    let check = check_runtime(install_dir, custom_java);
    let mut extra_game_args = Vec::new();
    extra_game_args.push("--server".to_string());
    extra_game_args.push(DEFAULT_SERVER.to_string());
    extra_game_args.push("--port".to_string());
    extra_game_args.push(DEFAULT_SERVER_PORT.to_string());
    LaunchPreparation {
        ready: check.ready,
        java_path: check.java.as_ref().map(|java| java.path.clone()),
        game_directory: check.game_directory,
        version_profile: check.version_profile,
        server: DEFAULT_SERVER,
        port: DEFAULT_SERVER_PORT,
        extra_game_args,
        blockers: check.missing,
    }
}

fn java_binary() -> &'static str {
    if cfg!(windows) { "java.exe" } else { "java" }
}

fn push_candidate(
    candidates: &mut Vec<JavaCandidate>,
    seen: &mut HashSet<String>,
    path: PathBuf,
    source: &'static str,
) {
    let key = path.to_string_lossy().to_ascii_lowercase();
    if seen.insert(key) {
        candidates.push(JavaCandidate { path, source });
    }
}

fn inspect_java(candidate: JavaCandidate) -> Result<JavaRuntimeInfo, std::io::Error> {
    let output = Command::new(&candidate.path).arg("-version").output()?;
    let raw = format!(
        "{}{}",
        String::from_utf8_lossy(&output.stderr),
        String::from_utf8_lossy(&output.stdout)
    );
    let major = parse_java_major(&raw).unwrap_or_default();
    Ok(JavaRuntimeInfo {
        path: candidate.path.to_string_lossy().into_owned(),
        version: raw.lines().next().unwrap_or_default().trim().to_string(),
        major,
        compatible: major == REQUIRED_JAVA_MAJOR as u32,
        source: candidate.source,
    })
}

fn parse_java_major(raw: &str) -> Option<u32> {
    let quoted = raw.split('"').nth(1)?;
    let first = quoted.split('.').next()?;
    let value = first.parse::<u32>().ok()?;
    if value == 1 {
        quoted.split('.').nth(1)?.parse::<u32>().ok()
    } else {
        Some(value)
    }
}

fn find_on_path(binary: &str) -> Option<PathBuf> {
    let paths = env::var_os("PATH")?;
    env::split_paths(&paths)
        .map(|path| path.join(binary))
        .find(|path| path.is_file())
}

#[cfg(test)]
mod tests {
    use super::parse_java_major;

    #[test]
    fn parses_java_17() {
        assert_eq!(parse_java_major("openjdk version \"17.0.12\" 2024-07-16"), Some(17));
    }

    #[test]
    fn parses_legacy_java() {
        assert_eq!(parse_java_major("java version \"1.8.0_402\""), Some(8));
    }
}
