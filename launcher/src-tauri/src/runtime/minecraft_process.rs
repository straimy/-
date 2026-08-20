use crate::core::microsoft_auth::MicrosoftSession;
use serde_json::Value;
use std::{env, fs, path::Path};

use super::{
    minecraft::{check_runtime, MINECRAFT_VERSION},
    minecraft_launch::{self, LaunchError, LaunchOptions, LaunchResult},
    minecraft_natives,
};

const FORGE_JAVA_COMPAT: &str = "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED";

pub fn launch_with_natives(
    install_dir: &Path,
    custom_java: Option<&str>,
    session: &MicrosoftSession,
    options: &LaunchOptions,
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
    minecraft_natives::prepare_natives(
        install_dir,
        &[&vanilla, &forge],
        &natives_dir,
    )?;

    let previous = env::var("JDK_JAVA_OPTIONS").ok();
    let mut combined = previous.clone().unwrap_or_default();
    if !combined.contains(FORGE_JAVA_COMPAT) {
        if !combined.trim().is_empty() {
            combined.push(' ');
        }
        combined.push_str(FORGE_JAVA_COMPAT);
    }
    env::set_var("JDK_JAVA_OPTIONS", &combined);

    let result = minecraft_launch::launch(install_dir, custom_java, session, options);

    match previous {
        Some(value) => env::set_var("JDK_JAVA_OPTIONS", value),
        None => env::remove_var("JDK_JAVA_OPTIONS"),
    }

    result
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn forge_compat_opens_java_lang_invoke() {
        assert_eq!(FORGE_JAVA_COMPAT, "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED");
    }
}
