#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
AUTH = ROOT / "src-tauri/src/core/ggo_auth.rs"
LIB = ROOT / "src-tauri/src/lib.rs"

a = AUTH.read_text()
old = '''pub async fn status(store: &GgoSessionStore) -> GgoAuthStatus {
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
'''
new = '''pub async fn status(
    http: &Client,
    api_url: &str,
    store: &GgoSessionStore,
) -> GgoAuthStatus {
    let Some(session) = store.snapshot().await else {
        return GgoAuthStatus { authenticated: false, profile: None };
    };

    match fetch_profile(http, api_url, &session.access_token).await {
        Ok(profile) => {
            if profile.id != session.profile.id
                || profile.display_name != session.profile.display_name
                || profile.skin_source != session.profile.skin_source
            {
                store.replace(GgoSession {
                    access_token: session.access_token,
                    refresh_token: session.refresh_token,
                    profile: profile.clone(),
                }).await;
            }
            GgoAuthStatus { authenticated: true, profile: Some(profile) }
        }
        Err(_) => {
            // A persisted file is not proof of authentication.  Fail closed and clear
            // stale credentials so Home cannot display a false Game-ready PLAY state.
            store.clear().await;
            GgoAuthStatus { authenticated: false, profile: None }
        }
    }
}
'''
if old not in a:
    if 'A persisted file is not proof of authentication' not in a:
        raise SystemExit('ggo_auth status block not found')
else:
    a = a.replace(old, new, 1)
AUTH.write_text(a)

l = LIB.read_text()
old_cmd = '''#[tauri::command]
async fn ggo_auth_status(store: State<'_, GgoSessionStore>) -> Result<GgoAuthStatus, String> {
    Ok(ggo_auth::status(store.inner()).await)
}
'''
new_cmd = '''#[tauri::command]
async fn ggo_auth_status(store: State<'_, GgoSessionStore>) -> Result<GgoAuthStatus, String> {
    let http = updater::client().map_err(|error| error.to_string())?;
    let api_url = BootstrapInfo::current().account_api_url;
    Ok(ggo_auth::status(&http, &api_url, store.inner()).await)
}
'''
if old_cmd not in l:
    if 'ggo_auth::status(&http, &api_url, store.inner()).await' not in l:
        raise SystemExit('ggo_auth_status command block not found')
else:
    l = l.replace(old_cmd, new_cmd, 1)
LIB.write_text(l)

assert 'fetch_profile(http, api_url, &session.access_token)' in AUTH.read_text()
assert 'store.clear().await' in AUTH.read_text()
assert 'ggo_auth::status(&http, &api_url, store.inner()).await' in LIB.read_text()
print('Stage104 launcher session validation applied')
