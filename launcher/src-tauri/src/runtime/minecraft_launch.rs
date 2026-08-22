use crate::core::microsoft_auth::MicrosoftSession;
use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::{
    collections::HashSet,
    env,
    fs::{self, File, OpenOptions},
    io::Write,
    path::{Path, PathBuf},
    process::{Child, Command, Stdio},
};
use thiserror::Error;

use super::minecraft::{check_runtime, DEFAULT_SERVER, DEFAULT_SERVER_PORT};

#[derive(Debug, Error)]
pub enum LaunchError {
    #[error("runtime is not ready: {0}")]
    Runtime(String),
    #[error("failed to read version metadata: {0}")]
    Io(#[from] std::io::Error),
    #[error("invalid version metadata: {0}")]
    Json(#[from] serde_json::Error),
    #[error("version metadata is missing mainClass")]
    MissingMainClass,
    #[error("version metadata is missing assets index")]
    MissingAssets,
    #[error("library is missing from disk: {0}")]
    MissingLibrary(String),
    #[error("Minecraft client jar is missing: {0}")]
    MissingClientJar(String),
    #[error("unresolved launcher placeholder: {0}")]
    UnresolvedPlaceholder(String),
    #[error("unsafe custom JVM argument: {0}")]
    UnsafeJvmArgument(String),
    #[error("failed to start Java: {0}")]
    Spawn(String),
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LaunchOptions {
    #[serde(default = "default_ram")]
    pub ram_mb: u32,
    #[serde(default = "default_min_ram")]
    pub min_ram_mb: u32,
    #[serde(default)]
    pub extra_jvm_args: Vec<String>,
    #[serde(default = "default_width")]
    pub width: u32,
    #[serde(default = "default_height")]
    pub height: u32,
    #[serde(default)]
    pub fullscreen: bool,
    #[serde(default = "default_connect_server")]
    pub connect_server: bool,
    #[serde(default = "default_launch_mode")]
    pub launch_mode: String,
}

fn default_ram() -> u32 { 4096 }
fn default_min_ram() -> u32 { 512 }
fn default_width() -> u32 { 1280 }
fn default_height() -> u32 { 720 }
fn default_connect_server() -> bool { true }
fn default_launch_mode() -> String { "online".to_string() }

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct LaunchCommandPreview {
    pub java_path: String,
    pub main_class: String,
    pub game_directory: String,
    pub classpath_entries: usize,
    pub jvm_args: Vec<String>,
    pub game_args: Vec<String>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct LaunchResult {
    pub started: bool,
    pub pid: u32,
    pub profile_name: String,
    pub profile_id: String,
}

#[derive(Debug)]
struct BuiltLaunch {
    java_path: String,
    main_class: String,
    game_directory: PathBuf,
    jvm_args: Vec<String>,
    game_args: Vec<String>,
    classpath_entries: usize,
}

pub fn preview(
    install_dir: &Path,
    custom_java: Option<&str>,
    session: &MicrosoftSession,
    options: &LaunchOptions,
) -> Result<LaunchCommandPreview, LaunchError> {
    let built = build_launch(install_dir, custom_java, session, options)?;
    Ok(LaunchCommandPreview {
        java_path: built.java_path,
        main_class: built.main_class,
        game_directory: built.game_directory.to_string_lossy().into_owned(),
        classpath_entries: built.classpath_entries,
        jvm_args: redact_token_args(built.jvm_args),
        game_args: redact_token_args(built.game_args),
    })
}

pub fn launch(
    install_dir: &Path,
    custom_java: Option<&str>,
    session: &MicrosoftSession,
    options: &LaunchOptions,
) -> Result<LaunchResult, LaunchError> {
    launch_with_environment(install_dir, custom_java, session, options, &[])
}

pub fn launch_with_environment(
    install_dir: &Path,
    custom_java: Option<&str>,
    session: &MicrosoftSession,
    options: &LaunchOptions,
    environment: &[(String, String)],
) -> Result<LaunchResult, LaunchError> {
    let built = build_launch(install_dir, custom_java, session, options)?;
    let child = spawn(&built, environment)?;
    Ok(LaunchResult {
        started: true,
        pid: child.id(),
        profile_name: session.minecraft_profile.name.clone(),
        profile_id: session.minecraft_profile.id.clone(),
    })
}

fn spawn(built: &BuiltLaunch, environment: &[(String, String)]) -> Result<Child, LaunchError> {
    let log_dir = built.game_directory.join("logs");
    fs::create_dir_all(&log_dir)?;
    let log_path = log_dir.join("ggo-launcher-minecraft.log");
    let mut stdout_log = OpenOptions::new().create(true).write(true).truncate(true).open(&log_path)?;
    writeln!(stdout_log, "=== GunGloryOnline client launch ===")?;
    writeln!(stdout_log, "Java: {}", built.java_path)?;
    writeln!(stdout_log, "Main class: {}", built.main_class)?;
    writeln!(stdout_log, "Classpath entries: {}", built.classpath_entries)?;
    writeln!(stdout_log, "Working directory: {}", built.game_directory.display())?;
    writeln!(stdout_log, "====================================")?;
    stdout_log.flush()?;
    let stderr_log = stdout_log.try_clone()?;

    let mut command = Command::new(&built.java_path);
    command
        .args(&built.jvm_args)
        .arg(&built.main_class)
        .args(&built.game_args)
        .current_dir(&built.game_directory)
        .stdin(Stdio::null())
        .stdout(Stdio::from(stdout_log))
        .stderr(Stdio::from(stderr_log));
    for (key, value) in environment {
        command.env(key, value);
    }
    command
        .spawn()
        .map_err(|error| LaunchError::Spawn(format!("{} (log: {})", error, log_path.display())))
}

fn build_launch(
    install_dir: &Path,
    custom_java: Option<&str>,
    session: &MicrosoftSession,
    options: &LaunchOptions,
) -> Result<BuiltLaunch, LaunchError> {
    let runtime = check_runtime(install_dir, custom_java);
    if !runtime.ready {
        return Err(LaunchError::Runtime(runtime.missing.join(", ")));
    }

    let java_path = runtime.java.expect("ready runtime has Java").path;
    let forge_id = runtime.version_profile;
    let forge_path = install_dir.join("versions").join(&forge_id).join(format!("{forge_id}.json"));
    let forge: Value = serde_json::from_slice(&fs::read(forge_path)?)?;
    let inherited = forge.get("inheritsFrom").and_then(Value::as_str).unwrap_or("1.20.1");
    let vanilla_path = install_dir.join("versions").join(inherited).join(format!("{inherited}.json"));
    let vanilla: Value = serde_json::from_slice(&fs::read(vanilla_path)?)?;

    let main_class = forge.get("mainClass").or_else(|| vanilla.get("mainClass")).and_then(Value::as_str).ok_or(LaunchError::MissingMainClass)?.to_string();
    let assets = vanilla.get("assets").and_then(Value::as_str).or_else(|| vanilla.get("assetIndex").and_then(|v| v.get("id")).and_then(Value::as_str)).ok_or(LaunchError::MissingAssets)?.to_string();

    let natives_dir = install_dir.join("natives").join(&forge_id);
    fs::create_dir_all(&natives_dir)?;

    let mut classpath = Vec::new();
    let mut seen = HashSet::new();
    collect_libraries(install_dir, &vanilla, &mut classpath, &mut seen)?;
    collect_libraries(install_dir, &forge, &mut classpath, &mut seen)?;

    filter_duplicate_minecraft_runtime_jars(&mut classpath);

    let client_jar = install_dir.join("versions").join(inherited).join(format!("{inherited}.jar"));
    if !client_jar.exists() { return Err(LaunchError::MissingClientJar(client_jar.display().to_string())); }

    let classpath_string = env::join_paths(&classpath).map_err(|e| LaunchError::Runtime(format!("invalid classpath: {e}")))?.to_string_lossy().into_owned();

    let game_dir_owned = install_dir.to_string_lossy().into_owned();
    let assets_root_owned = install_dir.join("assets").to_string_lossy().into_owned();
    let natives_owned = natives_dir.to_string_lossy().into_owned();
    let library_directory_owned = install_dir.join("libraries").to_string_lossy().into_owned();
    let classpath_separator_owned = if cfg!(windows) { ";".to_string() } else { ":".to_string() };
    let empty = "".to_string();
    let vars = vec![
        ("${auth_player_name}", session.minecraft_profile.name.as_str()),
        ("${auth_uuid}", session.minecraft_profile.id.as_str()),
        ("${auth_access_token}", session.minecraft_access_token.as_str()),
        ("${auth_xuid}", empty.as_str()),
        ("${clientid}", empty.as_str()),
        ("${user_type}", "msa"),
        ("${version_name}", forge_id.as_str()),
        ("${game_directory}", game_dir_owned.as_str()),
        ("${assets_root}", assets_root_owned.as_str()),
        ("${assets_index_name}", assets.as_str()),
        ("${version_type}", "release"),
        ("${natives_directory}", natives_owned.as_str()),
        ("${library_directory}", library_directory_owned.as_str()),
        ("${classpath_separator}", classpath_separator_owned.as_str()),
        ("${launcher_name}", "GunGloryOnline"),
        ("${launcher_version}", env!("CARGO_PKG_VERSION")),
        ("${classpath}", classpath_string.as_str()),
    ];

    let launch_mode = if options.launch_mode == "training" { "training" } else { "online" };
    let mut jvm_args = Vec::new();
    collect_arguments(&vanilla, "jvm", &mut jvm_args);
    collect_arguments(&forge, "jvm", &mut jvm_args);
    if jvm_args.is_empty() {
        jvm_args.push("-Djava.library.path=${natives_directory}".into());
        jvm_args.push("-cp".into());
        jvm_args.push("${classpath}".into());
    }
    jvm_args.retain(|arg| !arg.starts_with("-Xmx") && !arg.starts_with("-Xms"));
    let max_ram = options.ram_mb.clamp(1024, 32768);
    let min_ram = options.min_ram_mb.clamp(256, max_ram);
    jvm_args.insert(0, format!("-Dggo.launch.mode={launch_mode}"));
    jvm_args.insert(0, format!("-Xmx{max_ram}M"));
    jvm_args.insert(0, format!("-Xms{min_ram}M"));
    for arg in &options.extra_jvm_args {
        let arg = arg.trim();
        if arg.is_empty() { continue; }
        validate_custom_jvm_arg(arg)?;
        jvm_args.push(arg.to_string());
    }
    substitute_all(&mut jvm_args, &vars);
    ensure_no_placeholders(&jvm_args)?;

    let mut game_args = Vec::new();
    collect_arguments(&vanilla, "game", &mut game_args);
    collect_arguments(&forge, "game", &mut game_args);
    if game_args.is_empty() {
        if let Some(legacy) = vanilla.get("minecraftArguments").and_then(Value::as_str) { game_args.extend(split_legacy_args(legacy)); }
        if let Some(legacy) = forge.get("minecraftArguments").and_then(Value::as_str) { game_args.extend(split_legacy_args(legacy)); }
    }
    substitute_all(&mut game_args, &vars);
    ensure_no_placeholders(&game_args)?;
    game_args.extend(["--width".into(), options.width.clamp(640, 7680).to_string(), "--height".into(), options.height.clamp(480, 4320).to_string()]);
    if options.fullscreen { game_args.push("--fullscreen".into()); }
    if options.connect_server {
        game_args.extend(["--server".into(), DEFAULT_SERVER.into(), "--port".into(), DEFAULT_SERVER_PORT.to_string()]);
    }

    Ok(BuiltLaunch { java_path, main_class, game_directory: install_dir.to_path_buf(), classpath_entries: classpath.len(), jvm_args, game_args })
}

fn validate_custom_jvm_arg(arg: &str) -> Result<(), LaunchError> {
    let lower = arg.to_ascii_lowercase();
    let reserved = lower.starts_with("-xmx")
        || lower.starts_with("-xms")
        || lower == "-cp"
        || lower == "-classpath"
        || lower == "--class-path"
        || lower == "-p"
        || lower == "--module-path"
        || lower.starts_with("--module-path=")
        || lower.starts_with("-djava.library.path=")
        || arg.contains("${");
    if reserved { return Err(LaunchError::UnsafeJvmArgument(arg.to_string())); }
    Ok(())
}

fn collect_libraries(install_dir: &Path, version: &Value, output: &mut Vec<PathBuf>, seen: &mut HashSet<PathBuf>) -> Result<(), LaunchError> {
    let Some(libraries) = version.get("libraries").and_then(Value::as_array) else { return Ok(()); };
    for library in libraries {
        if !rules_allow(library.get("rules")) { continue; }
        let path = library.get("downloads").and_then(|d| d.get("artifact")).and_then(|a| a.get("path")).and_then(Value::as_str).map(PathBuf::from)
            .or_else(|| library.get("name").and_then(Value::as_str).and_then(maven_path));
        let Some(relative) = path else { continue; };
        let full = install_dir.join("libraries").join(relative);
        if !full.exists() { return Err(LaunchError::MissingLibrary(full.display().to_string())); }
        if seen.insert(full.clone()) { output.push(full); }
    }
    Ok(())
}

fn filter_duplicate_minecraft_runtime_jars(classpath: &mut Vec<PathBuf>) {
    classpath.retain(|path| {
        if !jar_contains_minecraft_runtime(path) {
            return true;
        }
        path.file_name()
            .and_then(|name| name.to_str())
            .is_some_and(|name| name.ends_with("-srg.jar"))
    });
}

fn jar_contains_minecraft_runtime(path: &Path) -> bool {
    let Ok(file) = File::open(path) else { return false; };
    let Ok(mut archive) = zip::ZipArchive::new(file) else { return false; };
    for marker in [
        "net/minecraft/client/Minecraft.class",
        "net/minecraft/server/MinecraftServer.class",
        "com/mojang/blaze3d/systems/RenderSystem.class",
    ] {
        if archive.by_name(marker).is_ok() {
            return true;
        }
    }
    false
}

fn maven_path(name: &str) -> Option<PathBuf> {
    let mut parts = name.split(':');
    let group = parts.next()?; let artifact = parts.next()?; let version = parts.next()?; let classifier = parts.next();
    let mut file = format!("{artifact}-{version}");
    if let Some(classifier) = classifier { file.push('-'); file.push_str(classifier); }
    file.push_str(".jar");
    Some(PathBuf::from(group.replace('.', "/")).join(artifact).join(version).join(file))
}

fn collect_arguments(version: &Value, kind: &str, output: &mut Vec<String>) {
    let Some(args) = version.get("arguments").and_then(|a| a.get(kind)).and_then(Value::as_array) else { return; };
    for arg in args {
        if let Some(text) = arg.as_str() { output.push(text.to_string()); continue; }
        if !rules_allow(arg.get("rules")) { continue; }
        match arg.get("value") {
            Some(Value::String(value)) => output.push(value.clone()),
            Some(Value::Array(values)) => output.extend(values.iter().filter_map(Value::as_str).map(ToOwned::to_owned)),
            _ => {}
        }
    }
}

fn rules_allow(rules: Option<&Value>) -> bool {
    let Some(rules) = rules.and_then(Value::as_array) else { return true; };
    let mut allowed = false;
    for rule in rules {
        if !rule_matches(rule) { continue; }
        allowed = rule.get("action").and_then(Value::as_str).unwrap_or("disallow") == "allow";
    }
    allowed
}

fn rule_matches(rule: &Value) -> bool {
    if let Some(os) = rule.get("os") {
        if let Some(name) = os.get("name").and_then(Value::as_str) {
            let current = if cfg!(windows) { "windows" } else if cfg!(target_os = "macos") { "osx" } else { "linux" };
            if name != current { return false; }
        }
        if let Some(arch) = os.get("arch").and_then(Value::as_str) {
            let current = if cfg!(target_arch = "x86_64") { "x86_64" } else { env::consts::ARCH };
            if arch != current { return false; }
        }
    }
    rule.get("features").is_none()
}

fn substitute_all(args: &mut [String], vars: &[(&str, &str)]) {
    for arg in args { for (key, value) in vars { *arg = arg.replace(key, value); } }
}

fn ensure_no_placeholders(args: &[String]) -> Result<(), LaunchError> {
    if let Some(arg) = args.iter().find(|arg| arg.contains("${")) {
        return Err(LaunchError::UnresolvedPlaceholder(arg.clone()));
    }
    Ok(())
}

fn split_legacy_args(input: &str) -> Vec<String> { input.split_whitespace().map(ToOwned::to_owned).collect() }
fn redact_token_args(mut args: Vec<String>) -> Vec<String> {
    for index in 0..args.len() { if index > 0 && args[index - 1] == "--accessToken" { args[index] = "<redacted>".to_string(); } }
    args
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn converts_maven_coordinate_to_library_path() {
        assert_eq!(maven_path("net.minecraftforge:forge:1.20.1-47.4.10:universal").unwrap(), PathBuf::from("net/minecraftforge/forge/1.20.1-47.4.10/forge-1.20.1-47.4.10-universal.jar"));
    }

    #[test]
    fn substitutes_placeholders() {
        let mut args = vec!["--username".into(), "${auth_player_name}".into(), "${library_directory}".into()];
        substitute_all(&mut args, &[("${auth_player_name}", "Player"), ("${library_directory}", "/tmp/libs")]);
        assert_eq!(args[1], "Player");
        assert_eq!(args[2], "/tmp/libs");
        assert!(ensure_no_placeholders(&args).is_ok());
    }

    #[test]
    fn unresolved_placeholders_fail_closed() {
        let args = vec!["--module-path".to_string(), "${library_directory}/mods".to_string()];
        assert!(matches!(ensure_no_placeholders(&args), Err(LaunchError::UnresolvedPlaceholder(_))));
    }

    #[test]
    fn custom_jvm_args_cannot_override_launcher_critical_args() {
        assert!(validate_custom_jvm_arg("-XX:+UseG1GC").is_ok());
        assert!(matches!(validate_custom_jvm_arg("-Xmx8G"), Err(LaunchError::UnsafeJvmArgument(_))));
        assert!(matches!(validate_custom_jvm_arg("--module-path=/tmp/x"), Err(LaunchError::UnsafeJvmArgument(_))));
        assert!(matches!(validate_custom_jvm_arg("-Djava.library.path=/tmp/x"), Err(LaunchError::UnsafeJvmArgument(_))));
    }

    #[test]
    fn launch_options_default_to_online_connection() {
        let options: LaunchOptions = serde_json::from_value(serde_json::json!({"ramMb":4096,"width":1280,"height":720,"fullscreen":false})).unwrap();
        assert!(options.connect_server);
        assert_eq!(options.launch_mode, "online");
        assert_eq!(options.min_ram_mb, 512);
        assert!(options.extra_jvm_args.is_empty());
    }

    #[test]
    fn child_environment_is_not_part_of_preview_or_arguments() {
        let secret = vec![("GGO_GAME_TICKET".to_string(), "secret".to_string())];
        assert_eq!(secret[0].0, "GGO_GAME_TICKET");
        assert!(!secret[0].1.is_empty());
    }
}
