use std::{fs, io, path::Path};

pub const OFFICIAL_PACK_FILE: &str = "GunGloryOnline-Official.zip";
const OFFICIAL_PACK_ID: &str = "file/GunGloryOnline-Official.zip";

pub fn ensure_official_resource_pack(install_dir: &Path) -> Result<bool, io::Error> {
    let pack = install_dir.join("resourcepacks").join(OFFICIAL_PACK_FILE);
    if !pack.is_file() {
        return Ok(false);
    }

    fs::create_dir_all(install_dir)?;
    let options = install_dir.join("options.txt");
    let original = match fs::read_to_string(&options) {
        Ok(value) => value,
        Err(error) if error.kind() == io::ErrorKind::NotFound => String::new(),
        Err(error) => return Err(error),
    };
    let (updated, changed) = update_options(&original)?;
    if !changed {
        return Ok(false);
    }

    replace_options(&options, updated.as_bytes())?;
    Ok(true)
}

fn update_options(input: &str) -> Result<(String, bool), io::Error> {
    let mut lines = input.lines().map(str::to_owned).collect::<Vec<_>>();
    let mut resource_packs_found = false;
    let mut changed = false;

    for line in &mut lines {
        if let Some(raw) = line.strip_prefix("resourcePacks:") {
            resource_packs_found = true;
            let mut packs = parse_pack_list(raw, "resourcePacks")?;
            let original_len = packs.len();
            packs.retain(|value| value != OFFICIAL_PACK_ID);
            packs.push(OFFICIAL_PACK_ID.to_string());
            if packs.len() != original_len || packs.last().map(String::as_str) != Some(OFFICIAL_PACK_ID)
            {
                changed = true;
            }
            let encoded = serde_json::to_string(&packs).map_err(json_error)?;
            let replacement = format!("resourcePacks:{encoded}");
            if *line != replacement {
                *line = replacement;
                changed = true;
            }
            continue;
        }

        if let Some(raw) = line.strip_prefix("incompatibleResourcePacks:") {
            let mut packs = parse_pack_list(raw, "incompatibleResourcePacks")?;
            let before = packs.len();
            packs.retain(|value| value != OFFICIAL_PACK_ID);
            if packs.len() != before {
                let encoded = serde_json::to_string(&packs).map_err(json_error)?;
                *line = format!("incompatibleResourcePacks:{encoded}");
                changed = true;
            }
        }
    }

    if !resource_packs_found {
        lines.push(format!("resourcePacks:[\"{OFFICIAL_PACK_ID}\"]"));
        changed = true;
    }

    let mut output = lines.join("\n");
    if !output.is_empty() {
        output.push('\n');
    }
    Ok((output, changed))
}

fn parse_pack_list(raw: &str, key: &str) -> Result<Vec<String>, io::Error> {
    serde_json::from_str(raw).map_err(|error| {
        io::Error::new(
            io::ErrorKind::InvalidData,
            format!("invalid {key} entry in options.txt: {error}"),
        )
    })
}

fn json_error(error: serde_json::Error) -> io::Error {
    io::Error::new(io::ErrorKind::InvalidData, error)
}

fn replace_options(target: &Path, bytes: &[u8]) -> Result<(), io::Error> {
    let temp = target.with_extension("txt.ggo-part");
    let backup = target.with_extension("txt.ggo-backup");
    fs::write(&temp, bytes)?;

    if !target.exists() {
        return fs::rename(temp, target);
    }

    let _ = fs::remove_file(&backup);
    fs::rename(target, &backup)?;
    match fs::rename(&temp, target) {
        Ok(()) => {
            let _ = fs::remove_file(backup);
            Ok(())
        }
        Err(error) => {
            let _ = fs::rename(backup, target);
            let _ = fs::remove_file(temp);
            Err(error)
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn appends_official_pack_and_preserves_user_pack() {
        let input = "resourcePacks:[\"file/User.zip\"]\nincompatibleResourcePacks:[]\n";
        let (updated, changed) = update_options(input).unwrap();
        assert!(changed);
        assert!(updated.contains(
            "resourcePacks:[\"file/User.zip\",\"file/GunGloryOnline-Official.zip\"]"
        ));
    }

    #[test]
    fn activation_is_idempotent() {
        let input = "resourcePacks:[\"file/GunGloryOnline-Official.zip\"]\n";
        let (updated, changed) = update_options(input).unwrap();
        assert!(!changed);
        assert_eq!(updated, input);
    }

    #[test]
    fn invalid_resource_pack_json_fails_closed() {
        let input = "resourcePacks:[broken]\n";
        assert!(update_options(input).is_err());
    }
}
