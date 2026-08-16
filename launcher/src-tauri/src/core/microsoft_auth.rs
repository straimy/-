use base64::{engine::general_purpose::URL_SAFE_NO_PAD, Engine};
use reqwest::Client;
use serde::{Deserialize, Serialize};
use serde_json::json;
use sha2::{Digest, Sha256};
use std::{env, sync::Arc, time::Duration};
use thiserror::Error;
use tokio::{
    io::{AsyncReadExt, AsyncWriteExt},
    net::TcpListener,
    sync::Mutex,
    time::timeout,
};
use url::Url;
use uuid::Uuid;

const AUTHORIZE_URL: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize";
const TOKEN_URL: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
const XBOX_USER_AUTH_URL: &str = "https://user.auth.xboxlive.com/user/authenticate";
const XSTS_AUTH_URL: &str = "https://xsts.auth.xboxlive.com/xsts/authorize";
const MINECRAFT_LOGIN_URL: &str = "https://api.minecraftservices.com/authentication/login_with_xbox";
const MINECRAFT_PROFILE_URL: &str = "https://api.minecraftservices.com/minecraft/profile";
const SCOPE: &str = "XboxLive.signin XboxLive.offline_access";

#[derive(Debug, Error)]
pub enum MicrosoftAuthError {
    #[error("GGO_MICROSOFT_CLIENT_ID is not configured")]
    MissingClientId,
    #[error("failed to bind OAuth callback: {0}")]
    CallbackBind(#[source] std::io::Error),
    #[error("failed to open system browser: {0}")]
    OpenBrowser(String),
    #[error("OAuth callback timed out")]
    CallbackTimeout,
    #[error("invalid OAuth callback")]
    InvalidCallback,
    #[error("OAuth state mismatch")]
    StateMismatch,
    #[error("Microsoft returned OAuth error: {0}")]
    OAuth(String),
    #[error("Xbox authentication failed: {0}")]
    Xbox(String),
    #[error("XSTS authentication failed: {0}")]
    Xsts(String),
    #[error("Minecraft authentication failed: {0}")]
    Minecraft(String),
    #[error("Minecraft profile is unavailable; the account may not own Minecraft Java Edition or may not have created a profile")]
    MinecraftProfileUnavailable,
    #[error("network error: {0}")]
    Network(#[from] reqwest::Error),
    #[error("I/O error: {0}")]
    Io(#[from] std::io::Error),
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct MicrosoftLoginResult {
    pub authenticated: bool,
    pub expires_in_seconds: u64,
    pub refresh_available: bool,
    pub minecraft_profile: Option<MinecraftProfile>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MinecraftProfile {
    pub id: String,
    pub name: String,
}

#[derive(Debug, Clone)]
pub struct MicrosoftSession {
    pub access_token: String,
    pub refresh_token: Option<String>,
    pub expires_in_seconds: u64,
    pub minecraft_access_token: String,
    pub minecraft_profile: MinecraftProfile,
}

#[derive(Debug, Default, Clone)]
pub struct MicrosoftSessionStore {
    inner: Arc<Mutex<Option<MicrosoftSession>>>,
}

impl MicrosoftSessionStore {
    pub async fn set(&self, session: MicrosoftSession) {
        *self.inner.lock().await = Some(session);
    }

    pub async fn snapshot(&self) -> Option<MicrosoftSession> {
        self.inner.lock().await.clone()
    }

    pub async fn clear(&self) {
        *self.inner.lock().await = None;
    }
}

#[derive(Debug, Deserialize)]
struct TokenResponse {
    access_token: String,
    #[serde(default)]
    refresh_token: Option<String>,
    #[serde(default)]
    expires_in: u64,
}

#[derive(Debug, Deserialize)]
struct TokenErrorResponse {
    error: String,
    #[serde(default)]
    error_description: String,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "PascalCase")]
struct XboxTokenResponse {
    token: String,
    display_claims: XboxDisplayClaims,
}

#[derive(Debug, Deserialize)]
struct XboxDisplayClaims {
    xui: Vec<XboxUserClaim>,
}

#[derive(Debug, Deserialize)]
struct XboxUserClaim {
    uhs: String,
}

#[derive(Debug, Deserialize)]
struct MinecraftTokenResponse {
    access_token: String,
    #[serde(default)]
    expires_in: u64,
}

#[derive(Debug, Deserialize)]
struct ServiceError {
    #[serde(default)]
    error: String,
    #[serde(default, rename = "errorMessage")]
    error_message: String,
    #[serde(default)]
    message: String,
}

pub async fn login(
    http: &Client,
    store: &MicrosoftSessionStore,
) -> Result<MicrosoftLoginResult, MicrosoftAuthError> {
    let client_id = env::var("GGO_MICROSOFT_CLIENT_ID")
        .ok()
        .filter(|value| !value.trim().is_empty())
        .ok_or(MicrosoftAuthError::MissingClientId)?;

    let listener = TcpListener::bind("127.0.0.1:0")
        .await
        .map_err(MicrosoftAuthError::CallbackBind)?;
    let port = listener.local_addr()?.port();
    let redirect_uri = format!("http://localhost:{port}/callback");

    let verifier = format!(
        "{}{}{}",
        Uuid::new_v4().simple(),
        Uuid::new_v4().simple(),
        Uuid::new_v4().simple()
    );
    let challenge = URL_SAFE_NO_PAD.encode(Sha256::digest(verifier.as_bytes()));
    let state = Uuid::new_v4().simple().to_string();

    let mut authorize =
        Url::parse(AUTHORIZE_URL).expect("static Microsoft authorize URL must parse");
    authorize
        .query_pairs_mut()
        .append_pair("client_id", &client_id)
        .append_pair("response_type", "code")
        .append_pair("redirect_uri", &redirect_uri)
        .append_pair("response_mode", "query")
        .append_pair("scope", SCOPE)
        .append_pair("state", &state)
        .append_pair("code_challenge", &challenge)
        .append_pair("code_challenge_method", "S256")
        .append_pair("prompt", "select_account");

    open::that(authorize.as_str())
        .map_err(|error| MicrosoftAuthError::OpenBrowser(error.to_string()))?;

    let (mut socket, _) = timeout(Duration::from_secs(180), listener.accept())
        .await
        .map_err(|_| MicrosoftAuthError::CallbackTimeout)??;

    let mut buffer = vec![0_u8; 8192];
    let read = socket.read(&mut buffer).await?;
    let request = String::from_utf8_lossy(&buffer[..read]);
    let request_line = request
        .lines()
        .next()
        .ok_or(MicrosoftAuthError::InvalidCallback)?;
    let target = request_line
        .split_whitespace()
        .nth(1)
        .ok_or(MicrosoftAuthError::InvalidCallback)?;
    let callback = Url::parse(&format!("http://localhost{target}"))
        .map_err(|_| MicrosoftAuthError::InvalidCallback)?;

    let mut code = None;
    let mut returned_state = None;
    let mut oauth_error = None;
    for (key, value) in callback.query_pairs() {
        match key.as_ref() {
            "code" => code = Some(value.into_owned()),
            "state" => returned_state = Some(value.into_owned()),
            "error" => oauth_error = Some(value.into_owned()),
            _ => {}
        }
    }

    let html = if oauth_error.is_some() {
        "GunGloryOnline: вход не выполнен. Можно закрыть эту вкладку."
    } else {
        "GunGloryOnline: вход принят. Можно закрыть эту вкладку и вернуться в лаунчер."
    };
    let response = format!(
        "HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
        html.len(),
        html
    );
    socket.write_all(response.as_bytes()).await?;
    socket.shutdown().await?;

    if let Some(error) = oauth_error {
        return Err(MicrosoftAuthError::OAuth(error));
    }
    if returned_state.as_deref() != Some(state.as_str()) {
        return Err(MicrosoftAuthError::StateMismatch);
    }
    let code = code.ok_or(MicrosoftAuthError::InvalidCallback)?;

    let response = http
        .post(TOKEN_URL)
        .form(&[
            ("client_id", client_id.as_str()),
            ("grant_type", "authorization_code"),
            ("code", code.as_str()),
            ("redirect_uri", redirect_uri.as_str()),
            ("code_verifier", verifier.as_str()),
            ("scope", SCOPE),
        ])
        .send()
        .await?;

    if !response.status().is_success() {
        let detail = service_error(response).await;
        return Err(MicrosoftAuthError::OAuth(detail));
    }

    let token = response.json::<TokenResponse>().await?;
    let (minecraft_access_token, minecraft_expires_in, profile) =
        exchange_for_minecraft(http, &token.access_token).await?;

    let result = MicrosoftLoginResult {
        authenticated: true,
        expires_in_seconds: minecraft_expires_in,
        refresh_available: token.refresh_token.is_some(),
        minecraft_profile: Some(profile.clone()),
    };

    store
        .set(MicrosoftSession {
            access_token: token.access_token,
            refresh_token: token.refresh_token,
            expires_in_seconds: token.expires_in,
            minecraft_access_token,
            minecraft_profile: profile,
        })
        .await;

    Ok(result)
}

async fn exchange_for_minecraft(
    http: &Client,
    microsoft_access_token: &str,
) -> Result<(String, u64, MinecraftProfile), MicrosoftAuthError> {
    let xbox_response = http
        .post(XBOX_USER_AUTH_URL)
        .header("x-xbl-contract-version", "1")
        .json(&json!({
            "RelyingParty": "http://auth.xboxlive.com",
            "TokenType": "JWT",
            "Properties": {
                "AuthMethod": "RPS",
                "SiteName": "user.auth.xboxlive.com",
                "RpsTicket": format!("d={microsoft_access_token}")
            }
        }))
        .send()
        .await?;

    if !xbox_response.status().is_success() {
        return Err(MicrosoftAuthError::Xbox(service_error(xbox_response).await));
    }
    let xbox = xbox_response.json::<XboxTokenResponse>().await?;

    let xsts_response = http
        .post(XSTS_AUTH_URL)
        .header("x-xbl-contract-version", "1")
        .json(&json!({
            "Properties": {
                "SandboxId": "RETAIL",
                "UserTokens": [xbox.token]
            },
            "RelyingParty": "rp://api.minecraftservices.com/",
            "TokenType": "JWT"
        }))
        .send()
        .await?;

    if !xsts_response.status().is_success() {
        return Err(MicrosoftAuthError::Xsts(service_error(xsts_response).await));
    }
    let xsts = xsts_response.json::<XboxTokenResponse>().await?;
    let user_hash = xsts
        .display_claims
        .xui
        .first()
        .map(|claim| claim.uhs.clone())
        .or_else(|| xbox.display_claims.xui.first().map(|claim| claim.uhs.clone()))
        .ok_or_else(|| MicrosoftAuthError::Xsts("missing user hash".to_string()))?;

    let minecraft_response = http
        .post(MINECRAFT_LOGIN_URL)
        .json(&json!({
            "identityToken": format!("XBL3.0 x={user_hash};{}", xsts.token)
        }))
        .send()
        .await?;

    if !minecraft_response.status().is_success() {
        return Err(MicrosoftAuthError::Minecraft(
            service_error(minecraft_response).await,
        ));
    }
    let minecraft = minecraft_response.json::<MinecraftTokenResponse>().await?;

    let profile_response = http
        .get(MINECRAFT_PROFILE_URL)
        .bearer_auth(&minecraft.access_token)
        .send()
        .await?;

    if profile_response.status() == reqwest::StatusCode::NOT_FOUND {
        return Err(MicrosoftAuthError::MinecraftProfileUnavailable);
    }
    if !profile_response.status().is_success() {
        return Err(MicrosoftAuthError::Minecraft(
            service_error(profile_response).await,
        ));
    }

    let profile = profile_response.json::<MinecraftProfile>().await?;
    Ok((minecraft.access_token, minecraft.expires_in, profile))
}

async fn service_error(response: reqwest::Response) -> String {
    let status = response.status();
    let body = response.text().await.unwrap_or_default();
    if let Ok(parsed) = serde_json::from_str::<TokenErrorResponse>(&body) {
        if !parsed.error.is_empty() {
            return if parsed.error_description.is_empty() {
                parsed.error
            } else {
                format!("{}: {}", parsed.error, parsed.error_description)
            };
        }
    }
    if let Ok(parsed) = serde_json::from_str::<ServiceError>(&body) {
        for value in [parsed.error_message, parsed.message, parsed.error] {
            if !value.is_empty() {
                return format!("HTTP {status}: {value}");
            }
        }
    }
    if body.trim().is_empty() {
        format!("HTTP {status}")
    } else {
        format!("HTTP {status}: {}", body.trim())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn pkce_challenge_is_url_safe_without_padding() {
        let verifier = "test-verifier";
        let challenge = URL_SAFE_NO_PAD.encode(Sha256::digest(verifier.as_bytes()));
        assert!(!challenge.contains('='));
        assert!(!challenge.contains('+'));
        assert!(!challenge.contains('/'));
    }

    #[test]
    fn minecraft_identity_header_has_expected_shape() {
        let uhs = "123";
        let token = "abc";
        assert_eq!(format!("XBL3.0 x={uhs};{token}"), "XBL3.0 x=123;abc");
    }
}
