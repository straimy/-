use serde::Serialize;

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct BootstrapInfo {
    pub launcher_version: &'static str,
    pub game_version: &'static str,
    pub channel: &'static str,
    pub runtime: &'static str,
    pub server: &'static str,
}

impl BootstrapInfo {
    pub fn current() -> Self {
        Self {
            launcher_version: env!("CARGO_PKG_VERSION"),
            game_version: "v0.4 Beta",
            channel: "beta",
            runtime: "minecraft-forge",
            server: "31.77.232.254:24842",
        }
    }
}
