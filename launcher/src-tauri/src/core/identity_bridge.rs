use serde::{Deserialize, Serialize};
use std::{fs, path::Path};
use uuid::Uuid;

const FILE_NAME: &str = ".ggo-profile.json";

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RuntimeIdentity {
    pub schema_version: u32,
    pub ggo_player_id: Option<String>,
    pub display_name: String,
    pub skin_source: String,
    pub provider: String,
}

pub fn write(
    install_dir: &Path,
    ggo_player_id: Option<&str>,
    display_name: &str,
    skin_source: &str,
    provider: &str,
) -> Result<(), String> {
    if let Some(id) = ggo_player_id {
        Uuid::parse_str(id).map_err(|_| "invalid ggo_player_id".to_string())?;
    }
    let display_name = display_name.trim();
    if display_name.is_empty() || display_name.chars().count() > 32 {
        return Err("invalid GGO display name".to_string());
    }
    if !matches!(skin_source, "ggo" | "microsoft" | "default") {
        return Err("invalid GGO skin source".to_string());
    }
    if !matches!(provider, "ggo" | "microsoft" | "guest") {
        return Err("invalid GGO identity provider".to_string());
    }
    if provider == "ggo" && ggo_player_id.is_none() {
        return Err("GGO provider requires ggo_player_id".to_string());
    }

    fs::create_dir_all(install_dir).map_err(|error| error.to_string())?;
    let identity = RuntimeIdentity {
        schema_version: 1,
        ggo_player_id: ggo_player_id.map(ToOwned::to_owned),
        display_name: display_name.to_string(),
        skin_source: skin_source.to_string(),
        provider: provider.to_string(),
    };
    let bytes = serde_json::to_vec_pretty(&identity).map_err(|error| error.to_string())?;
    let target = install_dir.join(FILE_NAME);
    let temp = install_dir.join(format!("{FILE_NAME}.tmp"));
    fs::write(&temp, bytes).map_err(|error| error.to_string())?;
    fs::rename(&temp, &target).or_else(|_| {
        let _ = fs::remove_file(&target);
        fs::rename(&temp, &target)
    }).map_err(|error| error.to_string())?;
    Ok(())
}
