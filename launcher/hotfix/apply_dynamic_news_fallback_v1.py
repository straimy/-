#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
lib = ROOT / "launcher/src-tauri/src/lib.rs"
admin = ROOT / "site/admin/admin.js"

lib_text = lib.read_text(encoding="utf-8")
old = '''#[tauri::command]\nasync fn fetch_news_feed(url: String) -> Result<NewsFeed, String> {\n    let http = updater::client().map_err(|error| error.to_string())?;\n    remote_content::fetch_news(&http, &url)\n        .await\n        .map_err(|error| error.to_string())\n}\n'''
new = '''#[tauri::command]\nasync fn fetch_news_feed(url: String) -> Result<NewsFeed, String> {\n    let http = updater::client().map_err(|error| error.to_string())?;\n    match remote_content::fetch_news(&http, &url).await {\n        Ok(feed) => Ok(feed),\n        Err(primary_error) => {\n            let Some(site_url) = url.strip_suffix("/api/v1/news") else {\n                return Err(primary_error.to_string());\n            };\n            let fallback_url = format!("{site_url}/content/api/news.json");\n            remote_content::fetch_news(&http, &fallback_url)\n                .await\n                .map_err(|fallback_error| {\n                    format!(\n                        "dynamic news failed ({primary_error}); static fallback failed ({fallback_error})"\n                    )\n                })\n        }\n    }\n}\n'''
if new not in lib_text:
    if old not in lib_text:
        raise SystemExit("dynamic-news patch: fetch_news_feed anchor missing")
    lib.write_text(lib_text.replace(old, new, 1), encoding="utf-8")

admin_text = admin.read_text(encoding="utf-8")
old_show = '''      $('#admin-users').classList.remove('hidden');\n      $('#admin-news').classList.remove('hidden');\n      $('#admin-audit').classList.remove('hidden');\n'''
new_show = '''      $('#admin-users').classList.remove('hidden');\n      $('#admin-audit').classList.remove('hidden');\n'''
if old_show in admin_text:
    admin_text = admin_text.replace(old_show, new_show, 1)

old_load = '''async function loadNews() {\n  const data = await api('/api/v1/news');\n  newsCache = data.items || [];\n'''
new_load = '''async function loadNews() {\n  let data;\n  try {\n    data = await api('/api/v1/admin/news');\n  } catch (error) {\n    if (error.status === 403) {\n      $('#admin-news').classList.add('hidden');\n      return;\n    }\n    throw error;\n  }\n  $('#admin-news').classList.remove('hidden');\n  newsCache = data.items || [];\n'''
if new_load not in admin_text:
    if old_load not in admin_text:
        raise SystemExit("dynamic-news patch: loadNews anchor missing")
    admin_text = admin_text.replace(old_load, new_load, 1)

admin.write_text(admin_text, encoding="utf-8")

checks = {
    "launcher dynamic primary": 'strip_suffix("/api/v1/news")' in lib.read_text(encoding="utf-8"),
    "launcher static fallback": '/content/api/news.json' in lib.read_text(encoding="utf-8"),
    "owner endpoint": "api('/api/v1/admin/news')" in admin.read_text(encoding="utf-8"),
    "owner-gated section": "$('#admin-news').classList.remove('hidden');" in admin.read_text(encoding="utf-8"),
}
for label, ok in checks.items():
    if not ok:
        raise SystemExit(f"dynamic-news patch failed: {label}")
print("GGO dynamic News fallback/owner UI patch applied")
