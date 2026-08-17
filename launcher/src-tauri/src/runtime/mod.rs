pub mod ggo_local_install;
pub mod ggo_remote_install;
pub mod minecraft;
pub mod minecraft_install;
pub mod minecraft_launch;
pub mod minecraft_natives;
pub mod minecraft_process;
use std::path::Path;

pub trait GameRuntime {
    type Error;
    fn id(&self) -> &'static str;
    fn verify(&self, install_dir: &Path) -> Result<(), Self::Error>;
    fn launch(&self, install_dir: &Path) -> Result<(), Self::Error>;
}
