#!/usr/bin/env python3
"""Production entrypoint for the GGO Account API.

Keeps the core API implementation in server.py while enforcing bootstrap-only owner identities,
conservative per-client rate limits, hardened response headers, emergency session revocation and a
small append-only security audit trail for privileged account actions.
"""
import json
import os
import re
import secrets
import threading
import time
from collections import defaultdict, deque
from http.server import ThreadingHTTPServer
from pathlib import Path

import server as core

ALLOW_OWNER_BOOTSTRAP = os.environ.get("GGO_ALLOW_OWNER_BOOTSTRAP", "").strip() == "1"
TRUST_PROXY = os.environ.get("GGO_TRUST_LOCAL_PROXY", "1").strip() == "1"
BUILD_ID_RE = re.compile(r"^[A-Za-z0-9._:-]{1,96}$")
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")
NEWS_ID_RE = re.compile(r"^[a-z0-9][a-z0-9._-]{2,63}$")
NEWS_DATE_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")
NEWS_SEED_PATH = Path(os.environ.get("GGO_NEWS_SEED", "/opt/ggo-auth/news_seed.json"))

# Sliding-window limits. These protect the Python service even if an upstream proxy is misconfigured.
# They intentionally target credential/session creation, not ordinary read-only API traffic.
RATE_RULES = {
    "/api/v1/auth/login": (8, 60),
    "/api/v1/auth/register": (5, 300),
    "/api/v1/auth/device/start": (12, 60),
    "/api/v1/auth/device/token": (30, 60),
    "/api/v1/auth/game-ticket": (20, 60),
    "/api/v1/auth/logout-all": (3, 300),
}
_RATE_LOCK = threading.Lock()
_RATE_BUCKETS = defaultdict(deque)


def _client_ip(handler) -> str:
    peer = handler.client_address[0] if handler.client_address else "unknown"
    # Production binds ggo-auth to loopback. Only trust forwarding headers from a loopback proxy.
    if TRUST_PROXY and peer in {"127.0.0.1", "::1"}:
        forwarded = handler.headers.get("X-Forwarded-For", "")
        if forwarded:
            candidate = forwarded.split(",", 1)[0].strip()
            if candidate and len(candidate) <= 64:
                return candidate
    return peer


def _rate_allowed(client_ip: str, path: str):
    rule = RATE_RULES.get(path)
    if not rule:
        return True, 0
    limit, window = rule
    now = time.monotonic()
    key = (client_ip, path)
    with _RATE_LOCK:
        bucket = _RATE_BUCKETS[key]
        cutoff = now - window
        while bucket and bucket[0] <= cutoff:
            bucket.popleft()
        if len(bucket) >= limit:
            retry = max(1, int(window - (now - bucket[0])) + 1)
            return False, retry
        bucket.append(now)
        # Opportunistic bounded cleanup to avoid retaining inactive keys forever.
        if len(_RATE_BUCKETS) > 4096:
            stale = []
            for k, values in list(_RATE_BUCKETS.items())[:512]:
                if not values or values[-1] <= now - max(300, window):
                    stale.append(k)
            for k in stale:
                _RATE_BUCKETS.pop(k, None)
    return True, 0


def _ticket_build(data):
    """Return normalized optional build binding; partial/invalid metadata is rejected."""
    build_id = str(data.get("build_id", "")).strip()
    core_sha256 = str(data.get("core_sha256", "")).strip().lower()
    ui_sha256 = str(data.get("ui_sha256", "")).strip().lower()
    values = (build_id, core_sha256, ui_sha256)
    if not any(values):
        return None
    if not all(values):
        raise ValueError("incomplete_build_metadata")
    if not BUILD_ID_RE.fullmatch(build_id):
        raise ValueError("invalid_build_id")
    if not SHA256_RE.fullmatch(core_sha256) or not SHA256_RE.fullmatch(ui_sha256):
        raise ValueError("invalid_build_hash")
    return build_id, core_sha256, ui_sha256


def _news_input(data):
    if not isinstance(data, dict):
        raise ValueError("invalid_news")
    date = str(data.get("date", "")).strip()
    if not NEWS_DATE_RE.fullmatch(date):
        raise ValueError("invalid_news_date")
    title = data.get("title") if isinstance(data.get("title"), dict) else {}
    body = data.get("body") if isinstance(data.get("body"), dict) else {}
    values = {
        "title_en": str(title.get("en", "")).strip(),
        "title_ru": str(title.get("ru", "")).strip(),
        "title_uk": str(title.get("uk", "")).strip(),
        "body_en": str(body.get("en", "")).strip(),
        "body_ru": str(body.get("ru", "")).strip(),
        "body_uk": str(body.get("uk", "")).strip(),
    }
    if any(not values[key] for key in ("title_en", "title_ru", "title_uk")):
        raise ValueError("missing_news_title")
    if any(not values[key] for key in ("body_en", "body_ru", "body_uk")):
        raise ValueError("missing_news_body")
    if any(len(values[key]) > 160 for key in ("title_en", "title_ru", "title_uk")):
        raise ValueError("news_title_too_long")
    if any(len(values[key]) > 6000 for key in ("body_en", "body_ru", "body_uk")):
        raise ValueError("news_body_too_long")
    return date, values


def _news_payload(row):
    return {
        "id": row["id"],
        "date": row["date"],
        "title": {"en": row["title_en"], "ru": row["title_ru"], "uk": row["title_uk"]},
        "body": {"en": row["body_en"], "ru": row["body_ru"], "uk": row["body_uk"]},
    }


def init_security_db():
    with core.connect() as db:
        db.execute(
            """
            CREATE TABLE IF NOT EXISTS security_audit (
              id TEXT PRIMARY KEY,
              actor_user_id TEXT,
              action TEXT NOT NULL,
              target_user_id TEXT,
              details TEXT NOT NULL DEFAULT '{}',
              created_at INTEGER NOT NULL
            )
            """
        )
        db.execute(
            "CREATE INDEX IF NOT EXISTS idx_security_audit_created ON security_audit(created_at DESC)"
        )
        db.execute(
            "CREATE INDEX IF NOT EXISTS idx_security_audit_actor ON security_audit(actor_user_id,created_at DESC)"
        )
        # Additive migration: old launchers may still create unbound tickets during the beta rollout.
        # New launchers bind the exact managed Core/UI identity into the one-shot ticket at issuance.
        ticket_columns = {row["name"] for row in db.execute("PRAGMA table_info(game_tickets)")}
        for name in ("build_id", "core_sha256", "ui_sha256"):
            if name not in ticket_columns:
                db.execute(f"ALTER TABLE game_tickets ADD COLUMN {name} TEXT")
        db.commit()


def init_news_db():
    with core.connect() as db:
        db.executescript(
            """
            CREATE TABLE IF NOT EXISTS news_items (
              id TEXT PRIMARY KEY,
              date TEXT NOT NULL,
              title_en TEXT NOT NULL,
              title_ru TEXT NOT NULL,
              title_uk TEXT NOT NULL,
              body_en TEXT NOT NULL,
              body_ru TEXT NOT NULL,
              body_uk TEXT NOT NULL,
              author_user_id TEXT REFERENCES users(id) ON DELETE SET NULL,
              created_at INTEGER NOT NULL,
              updated_at INTEGER NOT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_news_date ON news_items(date DESC,created_at DESC);
            """
        )
        seed_path = NEWS_SEED_PATH if NEWS_SEED_PATH.is_file() else Path(__file__).with_name("news_seed.json")
        if seed_path.is_file():
            try:
                seed = json.loads(seed_path.read_text(encoding="utf-8"))
                for item in seed.get("items", []):
                    item_id = str(item.get("id", "")).strip().lower()
                    if not NEWS_ID_RE.fullmatch(item_id):
                        continue
                    try:
                        date, values = _news_input(item)
                    except ValueError:
                        continue
                    ts = core.now()
                    db.execute(
                        "INSERT OR IGNORE INTO news_items("
                        "id,date,title_en,title_ru,title_uk,body_en,body_ru,body_uk,author_user_id,created_at,updated_at"
                        ") VALUES(?,?,?,?,?,?,?,?,NULL,?,?)",
                        (
                            item_id,
                            date,
                            values["title_en"],
                            values["title_ru"],
                            values["title_uk"],
                            values["body_en"],
                            values["body_ru"],
                            values["body_uk"],
                            ts,
                            ts,
                        ),
                    )
            except Exception as error:
                print(f"[ggo-auth] news seed skipped: {error}", flush=True)
        db.commit()


def _audit(db, actor_user_id, action, target_user_id=None, details=None):
    # Append-only by API design: there is intentionally no update/delete endpoint for this table.
    safe_details = json.dumps(details or {}, ensure_ascii=False, separators=(",", ":"))[:2000]
    db.execute(
        "INSERT INTO security_audit(id,actor_user_id,action,target_user_id,details,created_at) VALUES(?,?,?,?,?,?)",
        (secrets.token_hex(12), actor_user_id, action, target_user_id, safe_details, core.now()),
    )


class SecureHandler(core.Handler):
    server_version = "GGOAuth/1.6-secure"

    def json(self, status, payload, headers=None):
        hardened = {
            "X-Frame-Options": "DENY",
            "Referrer-Policy": "no-referrer",
            "Permissions-Policy": "camera=(), microphone=(), geolocation=()",
        }
        if headers:
            hardened.update(headers)
        return super().json(status, payload, hardened)

    def _rate_limit(self):
        path = self.path.split("?", 1)[0]
        allowed, retry = _rate_allowed(_client_ip(self), path)
        if allowed:
            return False
        self.json(
            429,
            {"error": "rate_limited", "retry_after": retry},
            {"Retry-After": str(retry)},
        )
        return True

    def require_owner(self, db):
        actor = self.require_admin(db)
        if not actor:
            return None
        if actor["username_norm"] not in core.OWNER_USERNAMES:
            self.json(403, {"error": "owner_required"})
            return None
        return actor

    def do_GET(self):
        path = self.path.split("?", 1)[0]
        if path == "/api/v1/news":
            return self.get_news()
        if path == "/api/v1/admin/audit":
            return self.get_security_audit()
        return super().do_GET()

    def do_POST(self):
        if self._rate_limit():
            return
        path = self.path.split("?", 1)[0]
        if path == "/api/v1/auth/logout-all":
            return self.logout_all_sessions()
        if path == "/api/v1/admin/news":
            data = self.read_json()
            if data is None:
                return self.json(400, {"error": "invalid_json"})
            return self.create_news(data)
        return super().do_POST()

    def do_PUT(self):
        path = self.path.split("?", 1)[0]
        if path.startswith("/api/v1/admin/news/"):
            data = self.read_json()
            if data is None:
                return self.json(400, {"error": "invalid_json"})
            return self.update_news(path.rsplit("/", 1)[-1], data)
        return super().do_PUT()

    def do_DELETE(self):
        path = self.path.split("?", 1)[0]
        if path.startswith("/api/v1/admin/news/"):
            return self.delete_news(path.rsplit("/", 1)[-1])
        return self.json(404, {"error": "not_found"})

    def register(self, data):
        username = str(data.get("username", "")).strip().lower()
        if username in core.OWNER_USERNAMES and not ALLOW_OWNER_BOOTSTRAP:
            return self.json(
                403,
                {
                    "error": "reserved_owner_username",
                    "message": "This GGO identity is reserved and cannot be created through public registration.",
                },
            )
        return super().register(data)

    def game_ticket(self, data):
        audience = str(data.get("audience", "official-online")).strip().lower()
        if audience not in core.ALLOWED_GAME_AUDIENCES:
            return self.json(400, {"error": "invalid_audience"})
        try:
            build = _ticket_build(data)
        except ValueError as error:
            return self.json(400, {"error": str(error)})
        with core.connect() as db:
            core.cleanup(db)
            user = self.auth_user(db)
            if not user:
                return self.json(401, {"error": "not_authenticated"})
            raw_ticket = core.token()
            ts = core.now()
            build_id, core_sha256, ui_sha256 = build or (None, None, None)
            db.execute(
                "INSERT INTO game_tickets(token_hash,user_id,audience,expires_at,created_at,consumed_at,build_id,core_sha256,ui_sha256) "
                "VALUES(?,?,?,?,?,NULL,?,?,?)",
                (
                    core.token_hash(raw_ticket),
                    user["id"],
                    audience,
                    ts + core.GAME_TICKET_TTL,
                    ts,
                    build_id,
                    core_sha256,
                    ui_sha256,
                ),
            )
            db.commit()
        return self.json(
            201,
            {
                "ticket": raw_ticket,
                "expires_in": core.GAME_TICKET_TTL,
                "player_id": user["id"],
                "display_name": user["display_name"],
                "build_bound": build is not None,
            },
        )

    def consume_game_ticket(self, data):
        if not self.server_key_ok():
            return self.json(401, {"error": "server_auth_required"})
        raw_ticket = str(data.get("ticket", "")).strip()
        audience = str(data.get("audience", "official-online")).strip().lower()
        if not raw_ticket or audience not in core.ALLOWED_GAME_AUDIENCES:
            return self.json(400, {"error": "invalid_ticket_request"})
        ts = core.now()
        with core.connect() as db:
            core.cleanup(db)
            db.commit()
            db.execute("BEGIN IMMEDIATE")
            row = db.execute(
                "SELECT t.*,u.* FROM game_tickets t JOIN users u ON u.id=t.user_id "
                "WHERE t.token_hash=? AND t.audience=? AND t.expires_at>? AND t.consumed_at IS NULL",
                (core.token_hash(raw_ticket), audience, ts),
            ).fetchone()
            if not row:
                db.rollback()
                return self.json(401, {"error": "invalid_expired_or_consumed_ticket"})
            changed = db.execute(
                "UPDATE game_tickets SET consumed_at=? WHERE token_hash=? AND consumed_at IS NULL",
                (ts, core.token_hash(raw_ticket)),
            ).rowcount
            if changed != 1:
                db.rollback()
                return self.json(409, {"error": "ticket_already_consumed"})
            db.commit()
        bound = bool(row["build_id"] and row["core_sha256"] and row["ui_sha256"])
        return self.json(
            200,
            {
                "valid": True,
                "audience": audience,
                "player": core.profile(row),
                "ticket_build": {
                    "bound": bound,
                    "build_id": row["build_id"] if bound else "",
                    "core_sha256": row["core_sha256"] if bound else "",
                    "ui_sha256": row["ui_sha256"] if bound else "",
                },
            },
        )

    def get_news(self):
        with core.connect() as db:
            rows = db.execute(
                "SELECT * FROM news_items ORDER BY date DESC, created_at DESC, id DESC LIMIT 200"
            ).fetchall()
            return self.json(200, {"schemaVersion": 1, "items": [_news_payload(row) for row in rows]})

    def create_news(self, data):
        if not self.same_origin_ok():
            return self.json(403, {"error": "origin_rejected"})
        try:
            date, values = _news_input(data)
        except ValueError as error:
            return self.json(400, {"error": str(error)})
        item_id = str(data.get("id", "")).strip().lower() or f"news-{date}-{secrets.token_hex(4)}"
        if not NEWS_ID_RE.fullmatch(item_id):
            return self.json(400, {"error": "invalid_news_id"})
        with core.connect() as db:
            actor = self.require_owner(db)
            if not actor:
                return
            ts = core.now()
            try:
                db.execute(
                    "INSERT INTO news_items("
                    "id,date,title_en,title_ru,title_uk,body_en,body_ru,body_uk,author_user_id,created_at,updated_at"
                    ") VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                    (
                        item_id,
                        date,
                        values["title_en"],
                        values["title_ru"],
                        values["title_uk"],
                        values["body_en"],
                        values["body_ru"],
                        values["body_uk"],
                        actor["id"],
                        ts,
                        ts,
                    ),
                )
            except core.sqlite3.IntegrityError:
                return self.json(409, {"error": "news_id_exists"})
            _audit(db, actor["id"], "news_created", None, {"news_id": item_id, "date": date})
            db.commit()
            row = db.execute("SELECT * FROM news_items WHERE id=?", (item_id,)).fetchone()
            return self.json(201, _news_payload(row))

    def update_news(self, item_id, data):
        if not self.same_origin_ok():
            return self.json(403, {"error": "origin_rejected"})
        item_id = str(item_id).strip().lower()
        if not NEWS_ID_RE.fullmatch(item_id):
            return self.json(400, {"error": "invalid_news_id"})
        try:
            date, values = _news_input(data)
        except ValueError as error:
            return self.json(400, {"error": str(error)})
        with core.connect() as db:
            actor = self.require_owner(db)
            if not actor:
                return
            if not db.execute("SELECT 1 FROM news_items WHERE id=?", (item_id,)).fetchone():
                return self.json(404, {"error": "news_not_found"})
            ts = core.now()
            db.execute(
                "UPDATE news_items SET date=?,title_en=?,title_ru=?,title_uk=?,body_en=?,body_ru=?,body_uk=?,"
                "author_user_id=?,updated_at=? WHERE id=?",
                (
                    date,
                    values["title_en"],
                    values["title_ru"],
                    values["title_uk"],
                    values["body_en"],
                    values["body_ru"],
                    values["body_uk"],
                    actor["id"],
                    ts,
                    item_id,
                ),
            )
            _audit(db, actor["id"], "news_updated", None, {"news_id": item_id, "date": date})
            db.commit()
            row = db.execute("SELECT * FROM news_items WHERE id=?", (item_id,)).fetchone()
            return self.json(200, _news_payload(row))

    def delete_news(self, item_id):
        if not self.same_origin_ok():
            return self.json(403, {"error": "origin_rejected"})
        item_id = str(item_id).strip().lower()
        if not NEWS_ID_RE.fullmatch(item_id):
            return self.json(400, {"error": "invalid_news_id"})
        with core.connect() as db:
            actor = self.require_owner(db)
            if not actor:
                return
            changed = db.execute("DELETE FROM news_items WHERE id=?", (item_id,)).rowcount
            if changed != 1:
                return self.json(404, {"error": "news_not_found"})
            _audit(db, actor["id"], "news_deleted", None, {"news_id": item_id})
            db.commit()
            return self.json(200, {"ok": True, "id": item_id})

    def get_security_audit(self):
        with core.connect() as db:
            actor = self.require_admin(db)
            if not actor:
                return
            rows = db.execute(
                "SELECT id,actor_user_id,action,target_user_id,details,created_at "
                "FROM security_audit ORDER BY created_at DESC LIMIT 200"
            ).fetchall()
            events = []
            for row in rows:
                try:
                    details = json.loads(row["details"] or "{}")
                except Exception:
                    details = {}
                events.append({
                    "id": row["id"],
                    "actor_user_id": row["actor_user_id"],
                    "action": row["action"],
                    "target_user_id": row["target_user_id"],
                    "details": details,
                    "created_at": row["created_at"],
                })
            return self.json(200, {"events": events})

    def update_role(self, user_id, data):
        if not self.same_origin_ok():
            return self.json(403, {"error": "origin_rejected"})
        role = str(data.get("role", "")).strip().lower()
        if role not in core.ALLOWED_ROLES:
            return self.json(400, {"error": "invalid_role"})
        with core.connect() as db:
            actor = self.require_admin(db)
            if not actor:
                return
            target = db.execute("SELECT * FROM users WHERE id=?", (user_id,)).fetchone()
            if not target:
                return self.json(404, {"error": "user_not_found"})
            if target["username_norm"] in core.OWNER_USERNAMES and role != "admin":
                _audit(db, actor["id"], "role_change_rejected_owner_lock", user_id, {
                    "requested_role": role,
                })
                db.commit()
                return self.json(409, {"error": "owner_role_locked"})
            old_role = target["role"]
            db.execute("UPDATE users SET role=? WHERE id=?", (role, user_id))
            _audit(db, actor["id"], "role_changed", user_id, {
                "old_role": old_role,
                "new_role": role,
            })
            db.commit()
            updated = db.execute("SELECT * FROM users WHERE id=?", (user_id,)).fetchone()
            return self.json(200, core.profile(updated))

    def logout_all_sessions(self):
        # Emergency containment for a stolen browser/launcher session. Bearer auth is accepted for
        # launcher recovery; cookie-based browser calls must come from the configured GGO origin.
        if self.cookie_token() and not self.bearer():
            origin = self.headers.get("Origin", "").rstrip("/")
            if not origin or origin != core.PUBLIC_URL:
                return self.json(403, {"error": "origin_rejected"})
        with core.connect() as db:
            user = self.auth_user(db)
            if not user:
                return self.json(401, {"error": "not_authenticated"})
            user_id = user["id"]
            access = db.execute("DELETE FROM access_sessions WHERE user_id=?", (user_id,)).rowcount
            refresh = db.execute("DELETE FROM refresh_tokens WHERE user_id=?", (user_id,)).rowcount
            tickets = db.execute(
                "DELETE FROM game_tickets WHERE user_id=? AND consumed_at IS NULL",
                (user_id,),
            ).rowcount
            devices = db.execute("DELETE FROM device_flows WHERE user_id=?", (user_id,)).rowcount
            _audit(db, user_id, "logout_all", user_id, {
                "access_sessions": access,
                "refresh_tokens": refresh,
                "game_tickets": tickets,
                "device_flows": devices,
            })
            db.commit()
        cookie = "ggo_session=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0" + (
            "; Secure" if core.PUBLIC_URL.startswith("https://") else ""
        )
        return self.json(
            200,
            {
                "ok": True,
                "revoked": {
                    "access_sessions": access,
                    "refresh_tokens": refresh,
                    "game_tickets": tickets,
                    "device_flows": devices,
                },
            },
            {"Set-Cookie": cookie},
        )


if __name__ == "__main__":
    core.init_db()
    init_security_db()
    init_news_db()
    print(
        f"[ggo-auth] secure entrypoint on http://{core.HOST}:{core.PORT} "
        f"public={core.PUBLIC_URL} db={core.DB_PATH}",
        flush=True,
    )
    ThreadingHTTPServer((core.HOST, core.PORT), SecureHandler).serve_forever()
