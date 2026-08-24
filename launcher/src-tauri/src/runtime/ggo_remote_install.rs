use super::{
    ggo_local_install::{CORE_FILE_NAME, UI_FILE_NAME},
    official_resource_pack, official_server,
};
use std::{collections::BTreeSet, fs, io, path::Path};

const REMOTE_CORE_STAGE96: &str = "gungloryonline-core-runtime-v1-stage96-channel-sync.jar";
const REMOTE_UI_STAGE96: &str = "gungloryonline-ui-runtime-v1-stage96.jar";
const REMOTE_CORE_STAGE85: &str = "gungloryonline-core-runtime-v1-stage85.jar";
const REMOTE_UI_STAGE85: &str = "gungloryonline-ui-runtime-v1-stage85.jar";
const REMOTE_CORE_STAGE77: &str = "gungloryonline-core-runtime-v1-stage77.jar";
const REMOTE_UI_STAGE77: &str = "gungloryonline-ui-runtime-v1-stage77.jar";
const REMOTE_CORE_STAGE68: &str = "gungloryonline-core-runtime-v1-stage68.jar";
const REMOTE_UI_STAGE69: &str = "gungloryonline-ui-runtime-v1-stage69.jar";

pub fn finalize_remote_install(install_dir: &Path) -> Result<(), io::Error> {
    remove_legacy_managed_jars(&install_dir.join("mods"))?;
    official_resource_pack::ensure_official_resource_pack(install_dir)?;
    official_server::ensure_official_server(install_dir)
        .map_err(|error| io::Error::new(io::ErrorKind::Other, error))?;
    Ok(())
}

fn pair_exists(mods_dir: &Path, core: &str, ui: &str) -> bool {
    mods_dir.join(core).is_file() && mods_dir.join(ui).is_file()
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum ManagedRuntimeKind {
    Core,
    Ui,
}

fn staged_runtime(name: &str) -> Option<(ManagedRuntimeKind, u32)> {
    let lower = name.to_ascii_lowercase();
    if !lower.ends_with(".jar") {
        return None;
    }
    let kind = if lower.starts_with("gungloryonline-core-") {
        ManagedRuntimeKind::Core
    } else if lower.starts_with("gungloryonline-ui-") {
        ManagedRuntimeKind::Ui
    } else {
        return None;
    };
    let marker = lower.rfind("stage")?;
    let digits = lower[marker + "stage".len()..]
        .chars()
        .take_while(|value| value.is_ascii_digit())
        .collect::<String>();
    if digits.is_empty() {
        return None;
    }
    digits.parse::<u32>().ok().map(|stage| (kind, stage))
}

fn highest_complete_dynamic_stage(mods_dir: &Path) -> Result<Option<u32>, io::Error> {
    let mut core_stages = BTreeSet::new();
    let mut ui_stages = BTreeSet::new();
    for entry in fs::read_dir(mods_dir)? {
        let entry = entry?;
        let path = entry.path();
        if !path.is_file() {
            continue;
        }
        let Some(name) = path.file_name().and_then(|value| value.to_str()) else {
            continue;
        };
        match staged_runtime(name) {
            Some((ManagedRuntimeKind::Core, stage)) => {
                core_stages.insert(stage);
            }
            Some((ManagedRuntimeKind::Ui, stage)) => {
                ui_stages.insert(stage);
            }
            None => {}
        }
    }
    Ok(core_stages.intersection(&ui_stages).last().copied())
}

fn remove_legacy_managed_jars(mods_dir: &Path) -> Result<(), io::Error> {
    if !mods_dir.is_dir() {
        return Ok(());
    }

    // Atomic managed-runtime preference. Keep exact known production pairs first, but also
    // understand a complete GGO `stageNN` Core/UI pair dynamically. The dynamic fallback is
    // important for candidate artifacts whose filename gains a suffix (for example channel-sync)
    // without changing its stage number. A partial newer download must never destroy the last
    // complete working pair.
    let stage96_ready = pair_exists(mods_dir, REMOTE_CORE_STAGE96, REMOTE_UI_STAGE96);
    let stage85_ready = pair_exists(mods_dir, REMOTE_CORE_STAGE85, REMOTE_UI_STAGE85);
    let stage77_ready = pair_exists(mods_dir, REMOTE_CORE_STAGE77, REMOTE_UI_STAGE77);
    let stage68_ready = pair_exists(mods_dir, REMOTE_CORE_STAGE68, REMOTE_UI_STAGE69);
    let dynamic_stage = highest_complete_dynamic_stage(mods_dir)?;

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
        if !managed {
            continue;
        }

        let current = if stage96_ready {
            matches!(name, REMOTE_CORE_STAGE96 | REMOTE_UI_STAGE96)
        } else if let Some(stage) = dynamic_stage {
            staged_runtime(name)
                .map(|(_, file_stage)| file_stage == stage)
                .unwrap_or(false)
        } else if stage85_ready {
            matches!(name, REMOTE_CORE_STAGE85 | REMOTE_UI_STAGE85)
        } else if stage77_ready {
            matches!(name, REMOTE_CORE_STAGE77 | REMOTE_UI_STAGE77)
        } else if stage68_ready {
            matches!(name, REMOTE_CORE_STAGE68 | REMOTE_UI_STAGE69)
        } else {
            matches!(name, CORE_FILE_NAME | UI_FILE_NAME)
        };
        if !current {
            fs::remove_file(path)?;
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::{
        finalize_remote_install, REMOTE_CORE_STAGE68, REMOTE_CORE_STAGE77, REMOTE_CORE_STAGE85,
        REMOTE_CORE_STAGE96, REMOTE_UI_STAGE69, REMOTE_UI_STAGE77, REMOTE_UI_STAGE85,
        REMOTE_UI_STAGE96,
    };
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
    fn finalizer_preserves_stage68_69_until_stage77_pair_is_complete() {
        let root =
            std::env::temp_dir().join(format!("ggo-remote-transition-{}", uuid::Uuid::new_v4()));
        let mods = root.join("mods");
        std::fs::create_dir_all(&mods).unwrap();
        std::fs::write(mods.join(REMOTE_CORE_STAGE68), b"old-core").unwrap();
        std::fs::write(mods.join(REMOTE_UI_STAGE69), b"old-ui").unwrap();
        std::fs::write(mods.join(REMOTE_CORE_STAGE77), b"new-core").unwrap();

        super::remove_legacy_managed_jars(&mods).unwrap();

        assert!(mods.join(REMOTE_CORE_STAGE68).is_file());
        assert!(mods.join(REMOTE_UI_STAGE69).is_file());
        assert!(!mods.join(REMOTE_CORE_STAGE77).exists());
        let _ = std::fs::remove_dir_all(root);
    }

    #[test]
    fn finalizer_switches_to_stage77_and_removes_old_pair() {
        let root =
            std::env::temp_dir().join(format!("ggo-remote-stage77-{}", uuid::Uuid::new_v4()));
        let mods = root.join("mods");
        std::fs::create_dir_all(&mods).unwrap();
        std::fs::write(mods.join(REMOTE_CORE_STAGE68), b"old-core").unwrap();
        std::fs::write(mods.join(REMOTE_UI_STAGE69), b"old-ui").unwrap();
        std::fs::write(mods.join(REMOTE_CORE_STAGE77), b"new-core").unwrap();
        std::fs::write(mods.join(REMOTE_UI_STAGE77), b"new-ui").unwrap();
        std::fs::write(mods.join("gungloryonline-core-old.jar"), b"older").unwrap();

        super::remove_legacy_managed_jars(&mods).unwrap();

        assert!(mods.join(REMOTE_CORE_STAGE77).is_file());
        assert!(mods.join(REMOTE_UI_STAGE77).is_file());
        assert!(!mods.join(REMOTE_CORE_STAGE68).exists());
        assert!(!mods.join(REMOTE_UI_STAGE69).exists());
        assert!(!mods.join("gungloryonline-core-old.jar").exists());
        let _ = std::fs::remove_dir_all(root);
    }

    #[test]
    fn finalizer_preserves_stage77_until_stage85_pair_is_complete() {
        let root = std::env::temp_dir().join(format!(
            "ggo-remote-stage85-partial-{}",
            uuid::Uuid::new_v4()
        ));
        let mods = root.join("mods");
        std::fs::create_dir_all(&mods).unwrap();
        std::fs::write(mods.join(REMOTE_CORE_STAGE77), b"old-core").unwrap();
        std::fs::write(mods.join(REMOTE_UI_STAGE77), b"old-ui").unwrap();
        std::fs::write(mods.join(REMOTE_CORE_STAGE85), b"new-core").unwrap();

        super::remove_legacy_managed_jars(&mods).unwrap();

        assert!(mods.join(REMOTE_CORE_STAGE77).is_file());
        assert!(mods.join(REMOTE_UI_STAGE77).is_file());
        assert!(!mods.join(REMOTE_CORE_STAGE85).exists());
        let _ = std::fs::remove_dir_all(root);
    }

    #[test]
    fn finalizer_switches_to_stage85_atomically() {
        let root =
            std::env::temp_dir().join(format!("ggo-remote-stage85-{}", uuid::Uuid::new_v4()));
        let mods = root.join("mods");
        std::fs::create_dir_all(&mods).unwrap();
        std::fs::write(mods.join(REMOTE_CORE_STAGE77), b"old-core").unwrap();
        std::fs::write(mods.join(REMOTE_UI_STAGE77), b"old-ui").unwrap();
        std::fs::write(mods.join(REMOTE_CORE_STAGE85), b"new-core").unwrap();
        std::fs::write(mods.join(REMOTE_UI_STAGE85), b"new-ui").unwrap();

        super::remove_legacy_managed_jars(&mods).unwrap();

        assert!(mods.join(REMOTE_CORE_STAGE85).is_file());
        assert!(mods.join(REMOTE_UI_STAGE85).is_file());
        assert!(!mods.join(REMOTE_CORE_STAGE77).exists());
        assert!(!mods.join(REMOTE_UI_STAGE77).exists());
        let _ = std::fs::remove_dir_all(root);
    }

    #[test]
    fn finalizer_preserves_stage85_until_stage96_pair_is_complete() {
        let root = std::env::temp_dir().join(format!(
            "ggo-remote-stage96-partial-{}",
            uuid::Uuid::new_v4()
        ));
        let mods = root.join("mods");
        std::fs::create_dir_all(&mods).unwrap();
        std::fs::write(mods.join(REMOTE_CORE_STAGE85), b"old-core").unwrap();
        std::fs::write(mods.join(REMOTE_UI_STAGE85), b"old-ui").unwrap();
        std::fs::write(mods.join(REMOTE_CORE_STAGE96), b"new-core").unwrap();

        super::remove_legacy_managed_jars(&mods).unwrap();

        assert!(mods.join(REMOTE_CORE_STAGE85).is_file());
        assert!(mods.join(REMOTE_UI_STAGE85).is_file());
        assert!(!mods.join(REMOTE_CORE_STAGE96).exists());
        let _ = std::fs::remove_dir_all(root);
    }

    #[test]
    fn finalizer_switches_to_stage96_atomically() {
        let root =
            std::env::temp_dir().join(format!("ggo-remote-stage96-{}", uuid::Uuid::new_v4()));
        let mods = root.join("mods");
        std::fs::create_dir_all(&mods).unwrap();
        std::fs::write(mods.join(REMOTE_CORE_STAGE85), b"old-core").unwrap();
        std::fs::write(mods.join(REMOTE_UI_STAGE85), b"old-ui").unwrap();
        std::fs::write(mods.join(REMOTE_CORE_STAGE96), b"new-core").unwrap();
        std::fs::write(mods.join(REMOTE_UI_STAGE96), b"new-ui").unwrap();

        super::remove_legacy_managed_jars(&mods).unwrap();

        assert!(mods.join(REMOTE_CORE_STAGE96).is_file());
        assert!(mods.join(REMOTE_UI_STAGE96).is_file());
        assert!(!mods.join(REMOTE_CORE_STAGE85).exists());
        assert!(!mods.join(REMOTE_UI_STAGE85).exists());
        let _ = std::fs::remove_dir_all(root);
    }

    #[test]
    fn finalizer_preserves_complete_stage96_pair_with_candidate_suffixes() {
        let root = std::env::temp_dir().join(format!(
            "ggo-remote-stage96-dynamic-{}",
            uuid::Uuid::new_v4()
        ));
        let mods = root.join("mods");
        std::fs::create_dir_all(&mods).unwrap();
        std::fs::write(mods.join(REMOTE_CORE_STAGE85), b"old-core").unwrap();
        std::fs::write(mods.join(REMOTE_UI_STAGE85), b"old-ui").unwrap();
        let candidate_core = "gungloryonline-core-runtime-v1-stage96-candidate.jar";
        let candidate_ui = "gungloryonline-ui-runtime-v1-stage96-candidate.jar";
        std::fs::write(mods.join(candidate_core), b"new-core").unwrap();
        std::fs::write(mods.join(candidate_ui), b"new-ui").unwrap();

        super::remove_legacy_managed_jars(&mods).unwrap();

        assert!(mods.join(candidate_core).is_file());
        assert!(mods.join(candidate_ui).is_file());
        assert!(!mods.join(REMOTE_CORE_STAGE85).exists());
        assert!(!mods.join(REMOTE_UI_STAGE85).exists());
        let _ = std::fs::remove_dir_all(root);
    }
}
