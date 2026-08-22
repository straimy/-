use fastnbt::Value;
use std::{collections::HashMap, fs, path::Path};

const SERVER_NAME: &str = "GunGloryOnline";
const SERVER_ADDRESS: &str = "play.kvicloud.ru:24842";

pub fn ensure_official_server(install_dir: &Path) -> Result<bool, String> {
    fs::create_dir_all(install_dir).map_err(|e| e.to_string())?;
    let path = install_dir.join("servers.dat");

    let mut root = if path.is_file() {
        let bytes = fs::read(&path).map_err(|e| e.to_string())?;
        fastnbt::from_bytes::<Value>(&bytes).unwrap_or_else(|_| empty_root())
    } else {
        empty_root()
    };

    let Value::Compound(root_map) = &mut root else {
        root = empty_root();
        return write_with_server(path.as_path(), root);
    };

    let servers = root_map
        .entry("servers".to_string())
        .or_insert_with(|| Value::List(Vec::new()));

    let Value::List(entries) = servers else {
        *servers = Value::List(Vec::new());
        let Value::List(entries) = servers else {
            unreachable!()
        };
        insert_or_update(entries);
        return write_root(&path, &root).map(|_| true);
    };

    let changed = insert_or_update(entries);
    if changed {
        write_root(&path, &root)?;
    }
    Ok(changed)
}

fn empty_root() -> Value {
    let mut root = HashMap::new();
    root.insert("servers".to_string(), Value::List(Vec::new()));
    Value::Compound(root)
}

fn insert_or_update(entries: &mut Vec<Value>) -> bool {
    let mut found_index = None;
    let mut changed = false;

    for (index, entry) in entries.iter_mut().enumerate() {
        let Value::Compound(server) = entry else {
            continue;
        };
        let ip_matches = matches!(server.get("ip"), Some(Value::String(ip)) if ip.eq_ignore_ascii_case(SERVER_ADDRESS));
        if !ip_matches {
            continue;
        }

        found_index = Some(index);
        if !matches!(server.get("name"), Some(Value::String(name)) if name == SERVER_NAME) {
            server.insert("name".to_string(), Value::String(SERVER_NAME.to_string()));
            changed = true;
        }
        if !matches!(server.get("acceptTextures"), Some(Value::Byte(1))) {
            server.insert("acceptTextures".to_string(), Value::Byte(1));
            changed = true;
        }
        break;
    }

    if let Some(index) = found_index {
        if index != 0 {
            let entry = entries.remove(index);
            entries.insert(0, entry);
            changed = true;
        }
        return changed;
    }

    let mut server = HashMap::new();
    server.insert("name".to_string(), Value::String(SERVER_NAME.to_string()));
    server.insert("ip".to_string(), Value::String(SERVER_ADDRESS.to_string()));
    server.insert("acceptTextures".to_string(), Value::Byte(1));
    entries.insert(0, Value::Compound(server));
    true
}

fn write_with_server(path: &Path, mut root: Value) -> Result<bool, String> {
    if let Value::Compound(map) = &mut root {
        map.insert("servers".to_string(), Value::List(Vec::new()));
        if let Some(Value::List(entries)) = map.get_mut("servers") {
            insert_or_update(entries);
        }
    }
    write_root(path, &root)?;
    Ok(true)
}

fn write_root(path: &Path, root: &Value) -> Result<(), String> {
    let bytes = fastnbt::to_bytes(root).map_err(|e| e.to_string())?;
    let tmp = path.with_extension("dat.tmp");
    fs::write(&tmp, bytes).map_err(|e| e.to_string())?;
    fs::rename(&tmp, path).map_err(|e| e.to_string())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn inserts_official_server_first() {
        let mut entries = Vec::new();
        assert!(insert_or_update(&mut entries));
        let Value::Compound(server) = &entries[0] else {
            panic!("expected compound")
        };
        assert_eq!(
            server.get("name"),
            Some(&Value::String("GunGloryOnline".into()))
        );
        assert_eq!(
            server.get("ip"),
            Some(&Value::String("play.kvicloud.ru:24842".into()))
        );
        assert_eq!(server.get("acceptTextures"), Some(&Value::Byte(1)));
    }

    #[test]
    fn repairs_existing_server_resource_pack_policy() {
        let mut server = HashMap::new();
        server.insert("name".to_string(), Value::String("GunGloryOnline".into()));
        server.insert(
            "ip".to_string(),
            Value::String("play.kvicloud.ru:24842".into()),
        );
        let mut entries = vec![Value::Compound(server)];

        assert!(insert_or_update(&mut entries));
        let Value::Compound(server) = &entries[0] else {
            panic!("expected compound")
        };
        assert_eq!(server.get("acceptTextures"), Some(&Value::Byte(1)));
        assert!(!insert_or_update(&mut entries));
    }
}
