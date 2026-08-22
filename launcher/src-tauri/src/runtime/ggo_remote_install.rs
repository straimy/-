use super::{
    ggo_local_install::{CORE_FILE_NAME, UI_FILE_NAME},
    official_resource_pack, official_server,
};
use std::{fs, io, path::Path};

const REMOTE_CORE_FILE_NAME: &str = "gungloryonline-core-runtime-v1-stage68.jar";
const REMOTE_UI_FILE_NAME: &str = "gungloryonline-ui-runtime-v1-stage69.jar";

pub fn finalize_remote_install(install_dir: &Path) -> Result<(), io::Error> {
    remove_legacy_managed_jars(&install_dir.join("mods"))?;
    official_resource_pack::ensure_official_resource_pack(install_dir)?;
    official_server::ensure_official_server(install_dir)
        .map_err(|error| io::Error::new(io::ErrorKind::Other, error))?;
    Ok(())
}

fn remove_legacy_managed_jars(mods_dir: &Path) -> Result<(), io::Error> {
    if !mods_dir.is_dir() {
        return Ok(());
    }
    for entry in fs::read_dir(mods_dir)? {
        let entry = entry?;
        let path = entry.path();
        if !path.is_file() {
            continue;
        }
        let Some(name) = path.file_name().and_then(|value| value.to_str()) else {
            continue;
        };
        let lower = name.to_ascii_lowercase();
        let managed = lower.ends_with(".jar")
            && (lower.starts_with("gungloryonline-core-")
                || lower.starts_with("gungloryonline-ui-")
                || lower.starts_with("gunnerarena-core-")
                || lower.starts_with("gunnerarena-ui-"));
        let current = matches!(
            name,
            CORE_FILE_NAME | UI_FILE_NAME | REMOTE_CORE_FILE_NAME | REMOTE_UI_FILE_NAME
        );
        if managed && !current {
            fs::remove_file(path)?;
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::{finalize_remote_install, REMOTE_CORE_FILE_NAME, REMOTE_UI_FILE_NAME};
    use crate::runtime::official_resource_pack::OFFICIAL_PACK_FILE;

    #[test]
    fn finalizer_enables_official_resource_pack_and_preserves_user_pack() {
        let root =
            std::env::temp_dir().join(format!("ggo-remote-finalize-{}", uuid::Uuid::new_v4()));
        std::fs::create_dir_all(root.join("mods")).unwrap();
        std::fs::create_dir_all(root.join("resourcepacks")).unwrap();
        std::fs::write(root.join("resourcepacks").join(OFFICIAL_PACK_FILE), b"rp").unwrap();
        std::fs::write(
            root.join("options.txt"),
            "resourcePacks:[\"file/User.zip\"]\nincompatibleResourcePacks:[]\n",
        )
        .unwrap();

        finalize_remote_install(&root).unwrap();

        let options = std::fs::read_to_string(root.join("options.txt")).unwrap();
        assert!(options
            .contains("resourcePacks:[\"file/User.zip\",\"file/GunGloryOnline-Official.zip\"]"));
        assert!(root.join("servers.dat").is_file());
        let _ = std::fs::remove_dir_all(root);
    }

    #[test]
    fn finalizer_keeps_current_remote_runtime_and_removes_legacy_ggo_jars() {
        let root =
            std::env::temp_dir().join(format!("ggo-remote-runtime-{}", uuid::Uuid::new_v4()));
        let mods = root.join("mods");
        std::fs::create_dir_all(&mods).unwrap();
        std::fs::write(mods.join(REMOTE_CORE_FILE_NAME), b"core").unwrap();
        std::fs::write(mods.join(REMOTE_UI_FILE_NAME), b"ui").unwrap();
        std::fs::write(mods.join("gungloryonline-core-old.jar"), b"old").unwrap();

        super::remove_legacy_managed_jars(&mods).unwrap();

        assert!(mods.join(REMOTE_CORE_FILE_NAME).is_file());
        assert!(mods.join(REMOTE_UI_FILE_NAME).is_file());
        assert!(!mods.join("gungloryonline-core-old.jar").exists());
        let _ = std::fs::remove_dir_all(root);
    }
}
