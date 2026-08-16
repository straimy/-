pub mod minecraft;
pub mod minecraft_launch;
use std::path::Path;

pub trait GameRuntime {
    type Error;
    fn id(&self) -> &'static str;
    fn verify(&self, install_dir: &Path) -> Result<(), Self::Error>;
    fn launch(&self, install_dir: &Path) -> Result<(), Self::Error>;
}
