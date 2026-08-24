use crate::core::microsoft_auth::MicrosoftSession;
use serde_json::Value;
use std::{env, fs, path::Path};

use super::{
    minecraft::{check_runtime, MINECRAFT_VERSION},
    minecraft_launch::{self, LaunchError, LaunchOptions, LaunchResult},
    minecraft_natives,
};

const FORGE_JAVA_COMPAT: &str = "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED";
// Must match the StartupWMClass emitted by the Tauri AppImage desktop entry so GNOME groups
// the Java/Forge render window with the GunGloryOnline application instead of Minecraft/Java.
const GGO_LINUX_RESOURCE_NAME: &str = "gungloryonline-launcher";

pub fn launch_with_natives(
    install_dir: &Path,
    custom_java: Option<&str>,
    session: &MicrosoftSession,
    options: &LaunchOptions,
) -> Result<LaunchResult, LaunchError> {
    launch_with_natives_environment(install_dir, custom_java, session, options, &[])
}

pub fn launch_with_natives_environment(
    install_dir: &Path,
    custom_java: Option<&str>,
    session: &MicrosoftSession,
    options: &LaunchOptions,
    environment: &[(String, String)],
) -> Result<LaunchResult, LaunchError> {
    let runtime = check_runtime(install_dir, custom_java);
    if !runtime.ready {
        return Err(LaunchError::Runtime(runtime.missing.join(", ")));
    }

    let forge_id = runtime.version_profile;
    let forge_path = install_dir
        .join("versions")
        .join(&forge_id)
        .join(format!("{forge_id}.json"));
    let forge: Value = serde_json::from_slice(&fs::read(forge_path)?)?;
    let inherited = forge
        .get("inheritsFrom")
        .and_then(Value::as_str)
        .unwrap_or(MINECRAFT_VERSION);
    let vanilla_path = install_dir
        .join("versions")
        .join(inherited)
        .join(format!("{inherited}.json"));
    let vanilla: Value = serde_json::from_slice(&fs::read(vanilla_path)?)?;

    let natives_dir = install_dir.join("natives").join(&forge_id);
    minecraft_natives::prepare_natives(install_dir, &[&vanilla, &forge], &natives_dir)?;

    let previous = env::var("JDK_JAVA_OPTIONS").ok().unwrap_or_default();
    let mut combined = previous;
    if !combined.contains(FORGE_JAVA_COMPAT) {
        if !combined.trim().is_empty() {
            combined.push(' ');
        }
        combined.push_str(FORGE_JAVA_COMPAT);
    }

    let mut child_environment = environment.to_vec();
    child_environment.retain(|(key, _)| key != "JDK_JAVA_OPTIONS" && key != "RESOURCE_NAME");
    child_environment.push(("JDK_JAVA_OPTIONS".to_string(), combined));

    // GLFW creates Forge's early native window before normal GGO client hooks are loaded.
    // On X11 it uses RESOURCE_NAME as the WM_CLASS instance when present. The Tauri AppImage
    // advertises StartupWMClass=gungloryonline-launcher, so using the same value on the child
    // makes GNOME associate the render window with the existing GGO desktop/icon identity.
    #[cfg(target_os = "linux")]
    child_environment.push((
        "RESOURCE_NAME".to_string(),
        GGO_LINUX_RESOURCE_NAME.to_string(),
    ));

    minecraft_launch::launch_with_environment(
        install_dir,
        custom_java,
        session,
        options,
        &child_environment,
    )
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn forge_compat_opens_java_lang_invoke() {
        assert_eq!(
            FORGE_JAVA_COMPAT,
            "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED"
        );
    }

    #[test]
    fn launcher_no_longer_mutates_process_environment_for_forge() {
        let source = include_str!("minecraft_process.rs");
        let runtime_source = source.split("#[cfg(test)]").next().unwrap_or(source);
        assert!(!runtime_source.contains("env::set_var"));
        assert!(!runtime_source.contains("env::remove_var"));
        assert!(runtime_source.contains("launch_with_natives_environment"));
    }

    #[test]
    fn linux_game_identity_is_child_scoped_and_matches_appimage() {
        let source = include_str!("minecraft_process.rs");
        let runtime_source = source.split("#[cfg(test)]").next().unwrap_or(source);
        assert!(runtime_source.contains("RESOURCE_NAME"));
        assert!(runtime_source.contains("gungloryonline-launcher"));
        assert!(runtime_source.contains("cfg(target_os = \"linux\")"));
    }
}
