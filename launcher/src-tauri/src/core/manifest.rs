use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::{fs::File, io::Read, path::Path};

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct GameManifest { pub schema_version: u32, pub game_version: String, pub runtime: RuntimeKind, pub channel: ReleaseChannel, pub files: Vec<ManifestFile> }

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "kebab-case")]
pub enum RuntimeKind { MinecraftForge, Native }

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum ReleaseChannel { Stable, Beta }

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ManifestFile { pub path: String, pub url: String, pub sha256: String, pub size: u64, pub required: bool, pub side: FileSide, pub version: String, pub kind: FileKind }

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum FileSide { Client, Server, Both }

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum FileKind { Mod, Config, Resourcepack, Runtime, Client, Asset, Library }

pub fn sha256_file(path: impl AsRef<Path>) -> std::io::Result<String> {
    let mut file = File::open(path)?;
    let mut digest = Sha256::new();
    let mut buffer = [0_u8; 128 * 1024];
    loop { let read = file.read(&mut buffer)?; if read == 0 { break; } digest.update(&buffer[..read]); }
    Ok(hex::encode(digest.finalize()))
}
