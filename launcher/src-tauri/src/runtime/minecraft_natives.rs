use serde_json::Value;
use std::{
    env,
    fs::{self, File},
    io::{self, copy},
    path::{Path, PathBuf},
};
use zip::ZipArchive;

pub fn prepare_natives(
    install_dir: &Path,
    versions: &[&Value],
    natives_dir: &Path,
) -> io::Result<usize> {
    if natives_dir.exists() {
        fs::remove_dir_all(natives_dir)?;
    }
    fs::create_dir_all(natives_dir)?;

    let mut extracted = 0;
    for version in versions {
        let Some(libraries) = version.get("libraries").and_then(Value::as_array) else {
            continue;
        };
        for library in libraries {
            if !rules_allow(library.get("rules")) {
                continue;
            }
            let Some(classifier) = native_classifier(library) else {
                continue;
            };
            let Some(relative) = library
                .get("downloads")
                .and_then(|value| value.get("classifiers"))
                .and_then(|value| value.get(&classifier))
                .and_then(|value| value.get("path"))
                .and_then(Value::as_str)
                .map(PathBuf::from)
            else {
                continue;
            };
            let jar = install_dir.join("libraries").join(relative);
            if !jar.exists() {
                return Err(io::Error::new(
                    io::ErrorKind::NotFound,
                    format!("native library missing: {}", jar.display()),
                ));
            }
            extracted += extract_native_jar(&jar, library, natives_dir)?;
        }
    }
    Ok(extracted)
}

fn native_classifier(library: &Value) -> Option<String> {
    let os_key = if cfg!(windows) {
        "windows"
    } else if cfg!(target_os = "macos") {
        "osx"
    } else {
        "linux"
    };
    let template = library
        .get("natives")?
        .get(os_key)?
        .as_str()?;
    let arch = if cfg!(target_pointer_width = "64") { "64" } else { "32" };
    Some(template.replace("${arch}", arch))
}

fn extract_native_jar(jar: &Path, library: &Value, natives_dir: &Path) -> io::Result<usize> {
    let file = File::open(jar)?;
    let mut archive = ZipArchive::new(file).map_err(zip_error)?;
    let excludes = library
        .get("extract")
        .and_then(|value| value.get("exclude"))
        .and_then(Value::as_array)
        .map(|values| {
            values
                .iter()
                .filter_map(Value::as_str)
                .map(ToOwned::to_owned)
                .collect::<Vec<_>>()
        })
        .unwrap_or_else(|| vec!["META-INF/".to_string()]);

    let mut count = 0;
    for index in 0..archive.len() {
        let mut entry = archive.by_index(index).map_err(zip_error)?;
        let name = entry.name().replace('\\', "/");
        if entry.is_dir() || excludes.iter().any(|prefix| name.starts_with(prefix)) {
            continue;
        }
        let Some(relative) = entry.enclosed_name().map(PathBuf::from) else {
            continue;
        };
        let destination = natives_dir.join(relative);
        if let Some(parent) = destination.parent() {
            fs::create_dir_all(parent)?;
        }
        let mut output = File::create(&destination)?;
        copy(&mut entry, &mut output)?;
        count += 1;
    }
    Ok(count)
}

fn zip_error(error: zip::result::ZipError) -> io::Error {
    io::Error::other(error.to_string())
}

fn rules_allow(rules: Option<&Value>) -> bool {
    let Some(rules) = rules.and_then(Value::as_array) else {
        return true;
    };
    let mut allowed = false;
    for rule in rules {
        if !rule_matches(rule) {
            continue;
        }
        allowed = rule
            .get("action")
            .and_then(Value::as_str)
            .unwrap_or("disallow")
            == "allow";
    }
    allowed
}

fn rule_matches(rule: &Value) -> bool {
    if let Some(os) = rule.get("os") {
        if let Some(name) = os.get("name").and_then(Value::as_str) {
            let current = if cfg!(windows) {
                "windows"
            } else if cfg!(target_os = "macos") {
                "osx"
            } else {
                "linux"
            };
            if name != current {
                return false;
            }
        }
        if let Some(arch) = os.get("arch").and_then(Value::as_str) {
            let current = if cfg!(target_arch = "x86_64") {
                "x86_64"
            } else {
                env::consts::ARCH
            };
            if arch != current {
                return false;
            }
        }
    }
    rule.get("features").is_none()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn native_classifier_replaces_arch() {
        let os_key = if cfg!(windows) {
            "windows"
        } else if cfg!(target_os = "macos") {
            "osx"
        } else {
            "linux"
        };
        let value: Value = serde_json::json!({
            "natives": { os_key: "natives-${arch}" }
        });
        let classifier = native_classifier(&value).unwrap();
        assert!(classifier == "natives-64" || classifier == "natives-32");
    }
}
