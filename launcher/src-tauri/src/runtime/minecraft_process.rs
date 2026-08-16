use crate::core::microsoft_auth::MicrosoftSession;
use serde_json::Value;
use std::{fs, path::Path};

use super::{
    minecraft::{check_runtime, MINECRAFT_VERSION},
    minecraft_launch::{self, LaunchError, LaunchOptions, LaunchResult},
    minecraft_natives,
};

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

    minecraft_launch::launch(install_dir, custom_java, session, options)
}
