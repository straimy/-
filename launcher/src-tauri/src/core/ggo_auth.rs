use base64::{engine::general_purpose::URL_SAFE_NO_PAD, Engine as _};
use reqwest::{Client, StatusCode};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::time::Duration;
use tokio::sync::Mutex;
use uuid::Uuid;

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct GgoProfile {
    pub id: String,
    pub display_name: String,
    pub skin_source: String,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct GgoAuthStatus {
    pub authenticated: bool,
    pub profile: Option<GgoProfile>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct MinecraftLinkResult {
    pub linked: bool,
    pub provider: String,
    pub minecraft_uuid: String,
    pub minecraft_name: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct GameTicket {
    pub ticket: String,
    pub expires_in: u64,
    pub player_id: String,
    pub display_name: String,
}

#[derive(Debug, Clone)]
pub struct GgoSession {
    pub access_token: String,
    pub refresh_token: String,
    pub profile: GgoProfile,
}

#[derive(Default)]
pub struct GgoSessionStore {
    inner: Mutex<Option<GgoSession>>,
}

impl GgoSessionStore {
    pub async fn snapshot(&self) -> Option<GgoSession> {
        self.inner.lock().await.clone()
    }
    pub async fn replace(&self, session: GgoSession) {
        *self.inner.lock().await = Some(session);
    }
    pub async fn clear(&self) {
        *self.inner.lock().await = None;
    }
}

#[derive(Debug, Serialize)]
struct DeviceStartRequest<'a> {
    code_challenge: &'a str,
    installation_id: &'a str,
}
#[derive(Debug, Deserialize)]
struct DeviceStartResponse {
    device_id: String,
    verification_uri: String,
    expires_in: u64,
    interval: u64,
}
#[derive(Debug, Serialize)]
struct DeviceTokenRequest<'a> {
    device_id: &'a str,
    code_verifier: &'a str,
}
#[derive(Debug, Serialize)]
struct PasswordLoginRequest<'a> {
    username: &'a str,
    password: &'a str,
}
#[derive(Debug, Serialize)]
struct GameTicketRequest<'a> {
    audience: &'a str,
}
#[derive(Debug, Deserialize)]
struct SessionResponse {
    access_token: String,
    refresh_token: String,
}
#[derive(Debug, Deserialize)]
struct PasswordProfileResponse {
    id: String,
    display_name: String,
    skin_source: String,
}
#[derive(Debug, Deserialize)]
struct PasswordSessionResponse {
    access_token: String,
    refresh_token: String,
    profile: PasswordProfileResponse,
}
#[derive(Debug, Deserialize)]
struct MeResponse {
    id: String,
    display_name: String,
    skin_source: String,
}
#[derive(Debug, Serialize)]
struct SkinSourceRequest<'a> {
    source: &'a str,
}
#[derive(Debug, Serialize)]
struct MinecraftLinkRequest<'a> {
    minecraft_access_token: &'a str,
}

fn pkce_verifier() -> String {
    format!("{}{}", Uuid::new_v4().simple(), Uuid::new_v4().simple())
}
fn pkce_challenge(verifier: &str) -> String {
    URL_SAFE_NO_PAD.encode(Sha256::digest(verifier.as_bytes()))
}
fn endpoint(api_url: &str, path: &str) -> String {
    format!(
        "{}/{}",
        api_url.trim_end_matches('/'),
        path.trim_start_matches('/')
    )
}

async fn fetch_profile(
    http: &Client,
    api_url: &str,
    access_token: &str,
) -> Result<GgoProfile, String> {
    let response = http
        .get(endpoint(api_url, "/me"))
        .bearer_auth(access_token)
        .send()
        .await
        .map_err(|e| e.to_string())?;
    if !response.status().is_success() {
        return Err(format!(
            "GGO profile request failed: HTTP {}",
            response.status()
        ));
    }
    let p = response
        .json::<MeResponse>()
        .await
        .map_err(|e| e.to_string())?;
    Ok(GgoProfile {
        id: p.id,
        display_name: p.display_name,
        skin_source: p.skin_source,
    })
}

pub async fn login(
    http: &Client,
    api_url: &str,
    store: &GgoSessionStore,
) -> Result<GgoAuthStatus, String> {
    let verifier = pkce_verifier();
    let challenge = pkce_challenge(&verifier);
    let installation_id = Uuid::new_v4().to_string();
    let start = http
        .post(endpoint(api_url, "/auth/device/start"))
        .json(&DeviceStartRequest {
            code_challenge: &challenge,
            installation_id: &installation_id,
        })
        .send()
        .await
        .map_err(|e| e.to_string())?;
    if !start.status().is_success() {
        return Err(format!(
            "GGO device login start failed: HTTP {}",
            start.status()
        ));
    }
    let start = start
        .json::<DeviceStartResponse>()
        .await
        .map_err(|e| e.to_string())?;
    open::that(&start.verification_uri).map_err(|e| e.to_string())?;
    let poll_every = Duration::from_secs(start.interval.clamp(2, 10));
    let deadline = tokio::time::Instant::now() + Duration::from_secs(start.expires_in.min(900));
    loop {
        if tokio::time::Instant::now() >= deadline {
            return Err("GGO sign-in expired. Start it again.".into());
        }
        tokio::time::sleep(poll_every).await;
        let r = http
            .post(endpoint(api_url, "/auth/device/token"))
            .json(&DeviceTokenRequest {
                device_id: &start.device_id,
                code_verifier: &verifier,
            })
            .send()
            .await
            .map_err(|e| e.to_string())?;
        if r.status().as_u16() == 428 {
            continue;
        }
        if r.status() == StatusCode::NOT_FOUND {
            return Err("GGO sign-in expired. Start it again.".into());
        }
        if !r.status().is_success() {
            return Err(format!("GGO device login failed: HTTP {}", r.status()));
        }
        let s = r
            .json::<SessionResponse>()
            .await
            .map_err(|e| e.to_string())?;
        let p = fetch_profile(http, api_url, &s.access_token).await?;
        store
            .replace(GgoSession {
                access_token: s.access_token,
                refresh_token: s.refresh_token,
                profile: p.clone(),
            })
            .await;
        return Ok(GgoAuthStatus {
            authenticated: true,
            profile: Some(p),
        });
    }
}

pub async fn login_password(
    http: &Client,
    api_url: &str,
    username: &str,
    password: &str,
    store: &GgoSessionStore,
) -> Result<GgoAuthStatus, String> {
    let username = username.trim();
    if username.is_empty() || password.is_empty() {
        return Err("GGO username and password are required".to_string());
    }
    let response = http
        .post(endpoint(api_url, "/auth/login"))
        .json(&PasswordLoginRequest { username, password })
        .send()
        .await
        .map_err(|e| e.to_string())?;
    if !response.status().is_success() {
        let status = response.status();
        let body = response.text().await.unwrap_or_default();
        return Err(format!("GGO password login failed: HTTP {status} {body}"));
    }
    let session = response
        .json::<PasswordSessionResponse>()
        .await
        .map_err(|e| e.to_string())?;
    let profile = GgoProfile {
        id: session.profile.id,
        display_name: session.profile.display_name,
        skin_source: session.profile.skin_source,
    };
    store
        .replace(GgoSession {
            access_token: session.access_token,
            refresh_token: session.refresh_token,
            profile: profile.clone(),
        })
        .await;
    Ok(GgoAuthStatus {
        authenticated: true,
        profile: Some(profile),
    })
}

pub async fn issue_game_ticket(
    http: &Client,
    api_url: &str,
    audience: &str,
    store: &GgoSessionStore,
) -> Result<GameTicket, String> {
    let session = store
        .snapshot()
        .await
        .ok_or_else(|| "GGO account is not authenticated".to_string())?;
    let audience = audience.trim();
    if audience.is_empty() {
        return Err("GGO game ticket audience is required".to_string());
    }
    let response = http
        .post(endpoint(api_url, "/auth/game-ticket"))
        .bearer_auth(&session.access_token)
        .json(&GameTicketRequest { audience })
        .send()
        .await
        .map_err(|e| e.to_string())?;
    if !response.status().is_success() {
        let status = response.status();
        let body = response.text().await.unwrap_or_default();
        return Err(format!(
            "GGO game ticket request failed: HTTP {status} {body}"
        ));
    }
    response
        .json::<GameTicket>()
        .await
        .map_err(|e| e.to_string())
}

pub async fn status(store: &GgoSessionStore) -> GgoAuthStatus {
    match store.snapshot().await {
        Some(s) => GgoAuthStatus {
            authenticated: true,
            profile: Some(s.profile),
        },
        None => GgoAuthStatus {
            authenticated: false,
            profile: None,
        },
    }
}

pub async fn logout(http: &Client, api_url: &str, store: &GgoSessionStore) -> Result<(), String> {
    if let Some(s) = store.snapshot().await {
        let _ = http
            .post(endpoint(api_url, "/auth/logout"))
            .bearer_auth(&s.access_token)
            .json(&serde_json::json!({"refresh_token": s.refresh_token}))
            .send()
            .await;
    }
    store.clear().await;
    Ok(())
}

pub async fn set_skin_source(
    http: &Client,
    api_url: &str,
    source: &str,
    store: &GgoSessionStore,
) -> Result<GgoAuthStatus, String> {
    if !matches!(source, "ggo" | "microsoft" | "default") {
        return Err("unsupported GGO skin source".into());
    }
    let s = store
        .snapshot()
        .await
        .ok_or_else(|| "GGO account is not authenticated".to_string())?;
    let r = http
        .put(endpoint(api_url, "/me/skin/source"))
        .bearer_auth(&s.access_token)
        .json(&SkinSourceRequest { source })
        .send()
        .await
        .map_err(|e| e.to_string())?;
    if !r.status().is_success() {
        return Err(format!(
            "GGO skin source update failed: HTTP {}",
            r.status()
        ));
    }
    let mut n = s;
    n.profile.skin_source = source.to_string();
    let p = n.profile.clone();
    store.replace(n).await;
    Ok(GgoAuthStatus {
        authenticated: true,
        profile: Some(p),
    })
}

pub async fn link_minecraft(
    http: &Client,
    api_url: &str,
    minecraft_access_token: &str,
    store: &GgoSessionStore,
) -> Result<MinecraftLinkResult, String> {
    let s = store
        .snapshot()
        .await
        .ok_or_else(|| "GGO account is not authenticated".to_string())?;
    let r = http
        .put(endpoint(api_url, "/me/identities/minecraft"))
        .bearer_auth(&s.access_token)
        .json(&MinecraftLinkRequest {
            minecraft_access_token,
        })
        .send()
        .await
        .map_err(|e| e.to_string())?;
    if !r.status().is_success() {
        let st = r.status();
        let d = r.text().await.unwrap_or_default();
        return Err(format!("Minecraft link failed: HTTP {st} {d}"));
    }
    r.json::<MinecraftLinkResult>()
        .await
        .map_err(|e| e.to_string())
}
