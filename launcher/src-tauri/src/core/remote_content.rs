use reqwest::{Client, Url};
use serde::{Deserialize, Serialize};
use thiserror::Error;

#[derive(Debug, Error)]
pub enum RemoteContentError {
    #[error("invalid remote content URL: {0}")]
    InvalidUrl(String),
    #[error("remote content must use HTTPS (HTTP is allowed only for localhost development)")]
    InsecureUrl,
    #[error("network error: {0}")]
    Network(#[from] reqwest::Error),
    #[error("unsupported remote content schema {0}")]
    UnsupportedSchema(u32),
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ServerCatalog {
    pub schema_version: u32,
    pub servers: Vec<RemoteServer>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RemoteServer {
    pub id: String,
    pub name: String,
    pub address: String,
    pub enabled: bool,
    pub order: i32,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct NewsFeed {
    pub schema_version: u32,
    pub items: Vec<NewsItem>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct NewsItem {
    pub id: String,
    pub date: String,
    pub title: LocalizedText,
    pub body: LocalizedText,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LocalizedText {
    pub en: String,
    pub ru: String,
    pub uk: String,
}

pub async fn fetch_servers(
    client: &Client,
    url: &str,
) -> Result<ServerCatalog, RemoteContentError> {
    let catalog = fetch_json::<ServerCatalog>(client, url).await?;
    if catalog.schema_version != 1 {
        return Err(RemoteContentError::UnsupportedSchema(
            catalog.schema_version,
        ));
    }
    Ok(catalog)
}

pub async fn fetch_news(client: &Client, url: &str) -> Result<NewsFeed, RemoteContentError> {
    let feed = fetch_json::<NewsFeed>(client, url).await?;
    if feed.schema_version != 1 {
        return Err(RemoteContentError::UnsupportedSchema(feed.schema_version));
    }
    Ok(feed)
}

async fn fetch_json<T: for<'de> Deserialize<'de>>(
    client: &Client,
    raw_url: &str,
) -> Result<T, RemoteContentError> {
    let url = validate_url(raw_url)?;
    Ok(client
        .get(url)
        .send()
        .await?
        .error_for_status()?
        .json::<T>()
        .await?)
}

fn validate_url(raw: &str) -> Result<Url, RemoteContentError> {
    let url = Url::parse(raw).map_err(|_| RemoteContentError::InvalidUrl(raw.to_string()))?;
    let localhost = matches!(url.host_str(), Some("localhost" | "127.0.0.1" | "::1"));
    if url.scheme() != "https" && !(url.scheme() == "http" && localhost) {
        return Err(RemoteContentError::InsecureUrl);
    }
    Ok(url)
}
