use base64::{engine::general_purpose::URL_SAFE_NO_PAD, Engine};
use reqwest::Client;
use serde::{Deserialize, Serialize};
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
}

#[derive(Debug, Clone)]
pub struct MicrosoftSession {
    pub access_token: String,
    pub refresh_token: Option<String>,
    pub expires_in_seconds: u64,
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

pub async fn login(http: &Client, store: &MicrosoftSessionStore) -> Result<MicrosoftLoginResult, MicrosoftAuthError> {
    let client_id = env::var("GGO_MICROSOFT_CLIENT_ID")
        .ok()
        .filter(|value| !value.trim().is_empty())
        .ok_or(MicrosoftAuthError::MissingClientId)?;

    let listener = TcpListener::bind("127.0.0.1:0")
        .await
        .map_err(MicrosoftAuthError::CallbackBind)?;
    let port = listener.local_addr()?.port();
    let redirect_uri = format!("http://localhost:{port}/callback");

    let verifier = format!("{}{}{}", Uuid::new_v4().simple(), Uuid::new_v4().simple(), Uuid::new_v4().simple());
    let challenge = URL_SAFE_NO_PAD.encode(Sha256::digest(verifier.as_bytes()));
    let state = Uuid::new_v4().simple().to_string();

    let mut authorize = Url::parse(AUTHORIZE_URL).expect("static Microsoft authorize URL must parse");
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

    open::that(authorize.as_str()).map_err(|error| MicrosoftAuthError::OpenBrowser(error.to_string()))?;

    let (mut socket, _) = timeout(Duration::from_secs(180), listener.accept())
        .await
        .map_err(|_| MicrosoftAuthError::CallbackTimeout)??;

    let mut buffer = vec![0_u8; 8192];
    let read = socket.read(&mut buffer).await?;
    let request = String::from_utf8_lossy(&buffer[..read]);
    let request_line = request.lines().next().ok_or(MicrosoftAuthError::InvalidCallback)?;
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
        html.as_bytes().len(),
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
        let status = response.status();
        let body = response.text().await.unwrap_or_default();
        if let Ok(parsed) = serde_json::from_str::<TokenErrorResponse>(&body) {
            let detail = if parsed.error_description.is_empty() {
                parsed.error
            } else {
                format!("{}: {}", parsed.error, parsed.error_description)
            };
            return Err(MicrosoftAuthError::OAuth(detail));
        }
        return Err(MicrosoftAuthError::OAuth(format!("HTTP {status}")));
    }

    let token = response.json::<TokenResponse>().await?;
    let result = MicrosoftLoginResult {
        authenticated: true,
        expires_in_seconds: token.expires_in,
        refresh_available: token.refresh_token.is_some(),
    };
    store
        .set(MicrosoftSession {
            access_token: token.access_token,
            refresh_token: token.refresh_token,
            expires_in_seconds: token.expires_in,
        })
        .await;

    Ok(result)
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
}
