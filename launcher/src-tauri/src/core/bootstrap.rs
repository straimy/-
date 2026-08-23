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
        // Closed-beta launchers must work out of the box. The old bootstrap only
        // exposed remote install when GGO_CONTENT_BASE_URL happened to be set at
        // build time, which made INSTALL silently fall back to a local v40 ZIP.
        // Keep env overrides for staging while providing the official beta
        // channel as a safe default.
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
            .unwrap_or_else(|| format!("{content_base_url}/manifests/beta-stage85-candidate.json"));

        Self {
            launcher_version: env!("CARGO_PKG_VERSION"),
            game_version: "v85-candidate",
            channel: "beta",
            runtime: "minecraft-forge",
            server: "play.kvicloud.ru:24842",
            content_base_url: Some(content_base_url),
            manifest_url: Some(manifest_url),
            servers_url: Some(format!("{content_base_url}/api/servers.json")),
            news_url: Some(format!("{content_base_url}/api/news.json")),
            site_url,
            account_api_url: format!("{site_url}/api/v1"),
        }
    }
}
