use serde::Serialize;

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct BootstrapInfo {
    pub launcher_version: &'static str,
    pub game_version: &'static str,
    pub channel: &'static str,
    pub runtime: &'static str,
    pub server: &'static str,
    pub content_base_url: Option<&'static str>,
    pub manifest_url: Option<String>,
    pub servers_url: Option<String>,
    pub news_url: Option<String>,
    pub site_url: &'static str,
    pub account_api_url: String,
}

impl BootstrapInfo {
    pub fn current() -> Self {
        // Stage114 is the current launcher-supervised unified GGO runtime candidate.
        // The Java/Forge engine remains an implementation detail while player-facing
        // navigation, startup branding and exit flow are owned by GunGloryOnline.
        let content_base_url = option_env!("GGO_CONTENT_BASE_URL")
            .map(str::trim)
            .filter(|value| !value.is_empty())
            .map(|value| value.trim_end_matches('/'))
            .unwrap_or("https://ggo.kvicloud.ru/content");
        let site_url = option_env!("GGO_SITE_URL")
            .map(str::trim)
            .filter(|value| !value.is_empty())
            .map(|value| value.trim_end_matches('/'))
            .unwrap_or("https://ggo.kvicloud.ru");
        let manifest_url = option_env!("GGO_MANIFEST_URL")
            .map(str::trim)
            .filter(|value| !value.is_empty())
            .map(ToOwned::to_owned)
            .unwrap_or_else(|| {
                format!("{content_base_url}/manifests/beta-stage114-candidate.json")
            });

        Self {
            launcher_version: env!("CARGO_PKG_VERSION"),
            game_version: "v114-candidate",
            channel: "beta",
            runtime: "minecraft-forge",
            server: "play.kvicloud.ru:24842",
            content_base_url: Some(content_base_url),
            manifest_url: Some(manifest_url),
            servers_url: Some(format!("{content_base_url}/api/servers.json")),
            news_url: Some(format!("{site_url}/api/v1/news")),
            site_url,
            account_api_url: format!("{site_url}/api/v1"),
        }
    }
}
