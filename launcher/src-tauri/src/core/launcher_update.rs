use serde::Serialize;
use tauri::{AppHandle, Emitter};
use tauri_plugin_updater::UpdaterExt;
use url::Url;

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct LauncherUpdateStatus {
    pub configured: bool,
    pub available: bool,
    pub current_version: String,
    pub version: Option<String>,
    pub notes: Option<String>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct LauncherUpdateProgress {
    pub downloaded_bytes: u64,
    pub total_bytes: Option<u64>,
}

fn configuration() -> Result<Option<(Url, &'static str)>, String> {
    let Some(base) = option_env!("GGO_CONTENT_BASE_URL")
        .map(str::trim)
        .filter(|value| !value.is_empty())
    else {
        return Ok(None);
    };
    let Some(pubkey) = option_env!("GGO_UPDATER_PUBKEY")
        .map(str::trim)
        .filter(|value| !value.is_empty())
    else {
        return Ok(None);
    };

    let endpoint = format!("{}/launcher/latest.json", base.trim_end_matches('/'));
    let endpoint = Url::parse(&endpoint).map_err(|error| error.to_string())?;
    if endpoint.scheme() != "https" {
        return Err("launcher updater endpoint must use HTTPS".to_string());
    }
    Ok(Some((endpoint, pubkey)))
}

async fn find_update(app: &AppHandle) -> Result<Option<tauri_plugin_updater::Update>, String> {
    let Some((endpoint, pubkey)) = configuration()? else {
        return Ok(None);
    };
    let updater = app
        .updater_builder()
        .pubkey(pubkey)
        .endpoints(vec![endpoint])
        .map_err(|error| error.to_string())?
        .build()
        .map_err(|error| error.to_string())?;
    updater.check().await.map_err(|error| error.to_string())
}

pub async fn check(app: &AppHandle) -> Result<LauncherUpdateStatus, String> {
    let configured = configuration()?.is_some();
    let update = if configured {
        find_update(app).await?
    } else {
        None
    };
    Ok(LauncherUpdateStatus {
        configured,
        available: update.is_some(),
        current_version: env!("CARGO_PKG_VERSION").to_string(),
        version: update.as_ref().map(|value| value.version.clone()),
        notes: update.and_then(|value| value.body),
    })
}

pub async fn install(app: &AppHandle) -> Result<bool, String> {
    let Some(update) = find_update(app).await? else {
        return Ok(false);
    };

    let mut downloaded = 0_u64;
    let progress_app = app.clone();
    update
        .download_and_install(
            move |chunk, total| {
                downloaded = downloaded.saturating_add(chunk as u64);
                let _ = progress_app.emit(
                    "ggo-launcher-update-progress",
                    LauncherUpdateProgress {
                        downloaded_bytes: downloaded,
                        total_bytes: total,
                    },
                );
            },
            || {},
        )
        .await
        .map_err(|error| error.to_string())?;

    app.restart();
}
