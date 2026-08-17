use super::ggo_local_install::{CORE_FILE_NAME, RESOURCE_PACK_FILE_NAME, UI_FILE_NAME};
use std::{fs, io, path::Path};
use uuid::Uuid;

pub fn finalize_remote_install(install_dir: &Path) -> Result<(), io::Error> {
    remove_legacy_managed_jars(&install_dir.join("mods"))?;
    enable_required_resource_pack(install_dir)?;
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
        if managed && name != CORE_FILE_NAME && name != UI_FILE_NAME {
            fs::remove_file(path)?;
        }
    }
    Ok(())
}

fn enable_required_resource_pack(install_dir: &Path) -> Result<(), io::Error> {
    let options_path = install_dir.join("options.txt");
    let pack_ref = format!("file/{RESOURCE_PACK_FILE_NAME}");
    let desired = format!("resourcePacks:[\"{pack_ref}\"]");
    let original = fs::read_to_string(&options_path).unwrap_or_default();
    let mut lines: Vec<String> = original.lines().map(str::to_string).collect();
    let mut found = false;

    for line in &mut lines {
        if !line.starts_with("resourcePacks:") {
            continue;
        }
        found = true;
        if line.contains(&pack_ref) {
            return Ok(());
        }
        let prefix = "resourcePacks:[";
        if line.starts_with(prefix) && line.ends_with(']') {
            let inner = &line[prefix.len()..line.len() - 1];
            *line = if inner.trim().is_empty() {
                desired.clone()
            } else {
                format!("resourcePacks:[{inner},\"{pack_ref}\"]")
            };
        } else {
            *line = desired.clone();
        }
        break;
    }

    if !found {
        lines.push(desired);
    }
    let mut next = lines.join("\n");
    next.push('\n');
    fs::create_dir_all(install_dir)?;
    let temp = install_dir.join(format!(".options.txt.ggo-part-{}", Uuid::new_v4()));
    fs::write(&temp, next)?;
    if options_path.exists() {
        let backup = install_dir.join(format!(".options.txt.ggo-backup-{}", Uuid::new_v4()));
        fs::rename(&options_path, &backup)?;
        match fs::rename(&temp, &options_path) {
            Ok(()) => {
                let _ = fs::remove_file(backup);
            }
            Err(error) => {
                let _ = fs::rename(backup, &options_path);
                return Err(error);
            }
        }
    } else {
        fs::rename(temp, options_path)?;
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::finalize_remote_install;
    use crate::runtime::ggo_local_install::RESOURCE_PACK_FILE_NAME;

    #[test]
    fn finalizer_enables_required_pack() {
        let root = std::env::temp_dir().join(format!("ggo-remote-finalize-{}", uuid::Uuid::new_v4()));
        std::fs::create_dir_all(root.join("mods")).unwrap();
        std::fs::write(root.join("options.txt"), "resourcePacks:[]\n").unwrap();
        finalize_remote_install(&root).unwrap();
        let options = std::fs::read_to_string(root.join("options.txt")).unwrap();
        assert!(options.contains(RESOURCE_PACK_FILE_NAME));
        let _ = std::fs::remove_dir_all(root);
    }
}
