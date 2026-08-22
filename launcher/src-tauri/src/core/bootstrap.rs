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
        let content_base_url = option_env!("GGO_CONTENT_BASE_URL")
            .map(str::trim)
            .filter(|value| !value.is_empty())
            .map(|value| value.trim_end_matches('/'));
        let site_url = option_env!("GGO_SITE_URL")
            .map(str::trim)
            .filter(|value| !value.is_empty())
            .map(|value| value.trim_end_matches('/'))
            .unwrap_or("https://ggo.kvicloud.ru");

        Self {
            launcher_version: env!("CARGO_PKG_VERSION"),
            game_version: "v40",
            channel: "beta",
            runtime: "minecraft-forge",
            server: "play.kvicloud.ru:24842",
            content_base_url,
            manifest_url: content_base_url.map(|base| format!("{base}/manifests/beta.json")),
            servers_url: content_base_url.map(|base| format!("{base}/api/servers.json")),
            news_url: content_base_url.map(|base| format!("{base}/api/news.json")),
            site_url,
            account_api_url: format!("{site_url}/api/v1"),
        }
    }
}
