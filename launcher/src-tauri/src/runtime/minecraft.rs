use super::GameRuntime;
use std::path::Path;
use thiserror::Error;

pub const MINECRAFT_VERSION: &str = "1.20.1";
pub const FORGE_VERSION: &str = "47.4.10";
pub const REQUIRED_JAVA_MAJOR: u8 = 17;

#[derive(Debug, Error)]
pub enum MinecraftRuntimeError {
    #[error("minecraft runtime installation is not implemented yet")]
    NotInstalled,
    #[error("minecraft runtime launch is not implemented yet")]
    NotImplemented,
}

pub struct MinecraftForgeRuntime;

impl GameRuntime for MinecraftForgeRuntime {
    type Error = MinecraftRuntimeError;

    fn id(&self) -> &'static str {
        "minecraft-forge"
    }

    fn verify(&self, _install_dir: &Path) -> Result<(), Self::Error> {
        Err(MinecraftRuntimeError::NotInstalled)
    }

    fn launch(&self, _install_dir: &Path) -> Result<(), Self::Error> {
        Err(MinecraftRuntimeError::NotImplemented)
    }
}
