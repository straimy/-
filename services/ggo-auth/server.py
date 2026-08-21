#!/usr/bin/env python3
import base64
import hashlib
import hmac
import json
import os
import re
import secrets
import sqlite3
import time
import urllib.parse
from http.cookies import SimpleCookie
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

HOST = os.environ.get("GGO_AUTH_HOST", "127.0.0.1")
PORT = int(os.environ.get("GGO_AUTH_PORT", "8787"))
PUBLIC_URL = os.environ.get("GGO_PUBLIC_URL", "https://ggo.kvicloud.ru").rstrip("/")
DB_PATH = Path(os.environ.get("GGO_AUTH_DB", "/var/lib/ggo-auth/auth.db"))
SERVER_KEY = os.environ.get("GGO_SERVER_KEY", "")
ACCESS_TTL = 12 * 60 * 60
REFRESH_TTL = 30 * 24 * 60 * 60
DEVICE_TTL = 10 * 60
GAME_TICKET_TTL = 75
USERNAME_RE = re.compile(r"^[A-Za-z0-9_]{3,16}$")
ALLOWED_SKINS = {"ggo", "microsoft", "default"}
ALLOWED_LANGS = {"ru", "en", "uk"}
ALLOWED_REGIONS = {"eu", "eeu", "na", "sa", "apac", "other"}
ALLOWED_GAME_AUDIENCES = {"official-online", "play.kvicloud.ru"}


def now() -> int:
    return int(time.time())


def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).decode().rstrip("=")


def token() -> str:
    return b64url(secrets.token_bytes(32))


def token_hash(value: str) -> str:
    return hashlib.sha256(value.encode()).hexdigest()


def password_hash(password: str, salt: bytes) -> bytes:
    return hashlib.scrypt(password.encode("utf-8"), salt=salt, n=2**14, r=8, p=1, dklen=32)


def connect():
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    db = sqlite3.connect(DB_PATH, timeout=15)
    db.row_factory = sqlite3.Row
    db.execute("PRAGMA journal_mode=WAL")
    db.execute("PRAGMA foreign_keys=ON")
    return db


def init_db():
    with connect() as db:
        db.executescript(
            """
            CREATE TABLE IF NOT EXISTS users (
              id TEXT PRIMARY KEY,
              username_norm TEXT NOT NULL UNIQUE,
              display_name TEXT NOT NULL,
              password_salt BLOB NOT NULL,
              password_hash BLOB NOT NULL,
              skin_source TEXT NOT NULL DEFAULT 'default',
              region TEXT NOT NULL DEFAULT 'eu',
              language TEXT NOT NULL DEFAULT 'ru',
              country TEXT NOT NULL DEFAULT 'other',
              created_at INTEGER NOT NULL
            );
            CREATE TABLE IF NOT EXISTS access_sessions (
              token_hash TEXT PRIMARY KEY,
              user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
              expires_at INTEGER NOT NULL,
              created_at INTEGER NOT NULL
            );
            CREATE TABLE IF NOT EXISTS refresh_tokens (
              token_hash TEXT PRIMARY KEY,
              user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
              expires_at INTEGER NOT NULL,
              created_at INTEGER NOT NULL
            );
            CREATE TABLE IF NOT EXISTS device_flows (
              device_id TEXT PRIMARY KEY,
              code_challenge TEXT NOT NULL,
              installation_id TEXT NOT NULL,
              user_id TEXT REFERENCES users(id) ON DELETE CASCADE,
              approved INTEGER NOT NULL DEFAULT 0,
              expires_at INTEGER NOT NULL,
              created_at INTEGER NOT NULL
            );
            CREATE TABLE IF NOT EXISTS game_tickets (
              token_hash TEXT PRIMARY KEY,
              user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
              audience TEXT NOT NULL,
              expires_at INTEGER NOT NULL,
              created_at INTEGER NOT NULL,
              consumed_at INTEGER
            );
            CREATE INDEX IF NOT EXISTS idx_access_user ON access_sessions(user_id);
            CREATE INDEX IF NOT EXISTS idx_refresh_user ON refresh_tokens(user_id);
            CREATE INDEX IF NOT EXISTS idx_device_expiry ON device_flows(expires_at);
            CREATE INDEX IF NOT EXISTS idx_game_ticket_expiry ON game_tickets(expires_at);
            CREATE INDEX IF NOT EXISTS idx_game_ticket_user ON game_tickets(user_id);
            """
        )


def profile(row):
    return {
        "id": row["id"],
        "display_name": row["display_name"],
        "skin_source": row["skin_source"],
        "region": row["region"],
        "language": row["language"],
        "country": row["country"],
        "created_at": row["created_at"],
    }


def issue_session(db, user_id: str):
    access = token()
    refresh = token()
    ts = now()
    db.execute(
        "INSERT INTO access_sessions(token_hash,user_id,expires_at,created_at) VALUES(?,?,?,?)",
        (token_hash(access), user_id, ts + ACCESS_TTL, ts),
    )
    db.execute(
        "INSERT INTO refresh_tokens(token_hash,user_id,expires_at,created_at) VALUES(?,?,?,?)",
        (token_hash(refresh), user_id, ts + REFRESH_TTL, ts),
    )
    return access, refresh


def cleanup(db):
    ts = now()
    db.execute("DELETE FROM access_sessions WHERE expires_at <= ?", (ts,))
    db.execute("DELETE FROM refresh_tokens WHERE expires_at <= ?", (ts,))
    db.execute("DELETE FROM device_flows WHERE expires_at <= ?", (ts,))
    db.execute("DELETE FROM game_tickets WHERE expires_at <= ? OR (consumed_at IS NOT NULL AND consumed_at <= ?)", (ts, ts - 300))


class Handler(BaseHTTPRequestHandler):
    server_version = "GGOAuth/1.1"

    def log_message(self, fmt, *args):
        print(f"[ggo-auth] {self.address_string()} {fmt % args}", flush=True)

    def json(self, status, payload, headers=None):
        body = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        if headers:
            for k, v in headers.items():
                self.send_header(k, v)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def read_json(self):
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if length <= 0 or length > 64 * 1024:
                return {}
            return json.loads(self.rfile.read(length))
        except Exception:
            return None

    def bearer(self):
        value = self.headers.get("Authorization", "")
        return value[7:].strip() if value.lower().startswith("bearer ") else None

    def cookie_token(self):
        raw = self.headers.get("Cookie")
        if not raw:
            return None
        c = SimpleCookie(); c.load(raw)
        item = c.get("ggo_session")
        return item.value if item else None

    def auth_user(self, db):
        access = self.bearer() or self.cookie_token()
        if not access:
            return None
        return db.execute(
            "SELECT u.* FROM access_sessions s JOIN users u ON u.id=s.user_id WHERE s.token_hash=? AND s.expires_at>?",
            (token_hash(access), now()),
        ).fetchone()

    def same_origin_ok(self):
        origin = self.headers.get("Origin")
        return not origin or origin.rstrip("/") == PUBLIC_URL

    def server_key_ok(self):
        candidate = self.headers.get("X-GGO-Server-Key", "")
        return bool(SERVER_KEY) and bool(candidate) and hmac.compare_digest(candidate, SERVER_KEY)

    def set_session_cookie(self, access):
        secure = PUBLIC_URL.startswith("https://")
        return f"ggo_session={access}; Path=/; HttpOnly; SameSite=Lax; Max-Age={ACCESS_TTL}" + ("; Secure" if secure else "")

    def do_GET(self):
        path = urllib.parse.urlparse(self.path).path
        if path == "/api/v1/health":
            return self.json(200, {"ok": True, "service": "ggo-auth", "version": 2, "game_tickets": True})
        if path in ("/api/v1/me", "/api/v1/auth/session"):
            with connect() as db:
                cleanup(db)
                user = self.auth_user(db)
                if not user:
                    return self.json(401, {"error": "not_authenticated"})
                return self.json(200, profile(user))
        return self.json(404, {"error": "not_found"})

    def do_POST(self):
        path = urllib.parse.urlparse(self.path).path
        data = self.read_json()
        if data is None:
            return self.json(400, {"error": "invalid_json"})
        if path == "/api/v1/auth/register": return self.register(data)
        if path == "/api/v1/auth/login": return self.login(data)
        if path == "/api/v1/auth/logout": return self.logout(data)
        if path == "/api/v1/auth/device/start": return self.device_start(data)
        if path == "/api/v1/auth/device/approve": return self.device_approve(data)
        if path == "/api/v1/auth/device/token": return self.device_token(data)
        if path == "/api/v1/auth/game-ticket": return self.game_ticket(data)
        if path == "/api/v1/auth/game-ticket/consume": return self.consume_game_ticket(data)
        return self.json(404, {"error": "not_found"})

    def do_PUT(self):
        path = urllib.parse.urlparse(self.path).path
        data = self.read_json()
        if data is None:
            return self.json(400, {"error": "invalid_json"})
        if path == "/api/v1/me/skin/source": return self.update_skin(data)
        if path == "/api/v1/me/profile": return self.update_profile(data)
        if path == "/api/v1/me/identities/minecraft":
            return self.json(501, {"error": "minecraft_link_not_enabled", "message": "Minecraft identity verification is not enabled yet."})
        return self.json(404, {"error": "not_found"})

    def register(self, data):
        if not self.same_origin_ok(): return self.json(403, {"error": "origin_rejected"})
        username = str(data.get("username", "")).strip()
        password = str(data.get("password", ""))
        region = str(data.get("region", "eu"))
        language = str(data.get("language", "ru"))
        country = str(data.get("country", "other"))[:32]
        if not USERNAME_RE.fullmatch(username):
            return self.json(400, {"error": "invalid_username", "message": "Use 3-16 latin letters, numbers or underscore."})
        if len(password) < 8 or len(password) > 128:
            return self.json(400, {"error": "invalid_password", "message": "Password must be 8-128 characters."})
        if region not in ALLOWED_REGIONS: region = "other"
        if language not in ALLOWED_LANGS: language = "en"
        salt = secrets.token_bytes(16)
        digest = password_hash(password, salt)
        user_id = secrets.token_hex(16)
        with connect() as db:
            cleanup(db)
            try:
                db.execute(
                    "INSERT INTO users(id,username_norm,display_name,password_salt,password_hash,skin_source,region,language,country,created_at) VALUES(?,?,?,?,?,'default',?,?,?,?)",
                    (user_id, username.lower(), username, salt, digest, region, language, country, now()),
                )
            except sqlite3.IntegrityError:
                return self.json(409, {"error": "username_taken"})
            access, refresh = issue_session(db, user_id)
            db.commit()
            user = db.execute("SELECT * FROM users WHERE id=?", (user_id,)).fetchone()
        return self.json(201, {"access_token": access, "refresh_token": refresh, "profile": profile(user)}, {"Set-Cookie": self.set_session_cookie(access)})

    def login(self, data):
        if not self.same_origin_ok(): return self.json(403, {"error": "origin_rejected"})
        username = str(data.get("username", "")).strip().lower()
        password = str(data.get("password", ""))
        with connect() as db:
            cleanup(db)
            user = db.execute("SELECT * FROM users WHERE username_norm=?", (username,)).fetchone()
            valid = False
            if user is not None:
                try:
                    valid = hmac.compare_digest(user["password_hash"], password_hash(password, user["password_salt"]))
                except Exception:
                    valid = False
            if not valid:
                time.sleep(0.15)
                return self.json(401, {"error": "invalid_credentials"})
            access, refresh = issue_session(db, user["id"])
            db.commit()
        return self.json(200, {"access_token": access, "refresh_token": refresh, "profile": profile(user)}, {"Set-Cookie": self.set_session_cookie(access)})

    def logout(self, data):
        access = self.bearer() or self.cookie_token()
        refresh = str(data.get("refresh_token", ""))
        with connect() as db:
            if access: db.execute("DELETE FROM access_sessions WHERE token_hash=?", (token_hash(access),))
            if refresh: db.execute("DELETE FROM refresh_tokens WHERE token_hash=?", (token_hash(refresh),))
            db.commit()
        cookie = "ggo_session=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0" + ("; Secure" if PUBLIC_URL.startswith("https://") else "")
        return self.json(200, {"ok": True}, {"Set-Cookie": cookie})

    def device_start(self, data):
        challenge = str(data.get("code_challenge", ""))
        installation_id = str(data.get("installation_id", ""))[:128]
        if len(challenge) < 32 or len(challenge) > 128 or not installation_id:
            return self.json(400, {"error": "invalid_device_request"})
        device_id = token()
        with connect() as db:
            cleanup(db)
            db.execute("INSERT INTO device_flows(device_id,code_challenge,installation_id,expires_at,created_at) VALUES(?,?,?,?,?)", (device_id, challenge, installation_id, now()+DEVICE_TTL, now()))
            db.commit()
        uri = f"{PUBLIC_URL}/account/device.html?device_id={urllib.parse.quote(device_id)}"
        return self.json(200, {"device_id": device_id, "verification_uri": uri, "expires_in": DEVICE_TTL, "interval": 3})

    def device_approve(self, data):
        if not self.same_origin_ok(): return self.json(403, {"error": "origin_rejected"})
        device_id = str(data.get("device_id", ""))
        with connect() as db:
            cleanup(db)
            user = self.auth_user(db)
            if not user: return self.json(401, {"error": "not_authenticated"})
            flow = db.execute("SELECT * FROM device_flows WHERE device_id=? AND expires_at>?", (device_id, now())).fetchone()
            if not flow: return self.json(404, {"error": "device_flow_not_found"})
            db.execute("UPDATE device_flows SET user_id=?, approved=1 WHERE device_id=?", (user["id"], device_id))
            db.commit()
        return self.json(200, {"approved": True})

    def device_token(self, data):
        device_id = str(data.get("device_id", ""))
        verifier = str(data.get("code_verifier", ""))
        challenge = b64url(hashlib.sha256(verifier.encode()).digest()) if verifier else ""
        with connect() as db:
            cleanup(db)
            flow = db.execute("SELECT * FROM device_flows WHERE device_id=? AND expires_at>?", (device_id, now())).fetchone()
            if not flow: return self.json(404, {"error": "device_flow_not_found"})
            if not hmac.compare_digest(challenge, flow["code_challenge"]): return self.json(401, {"error": "pkce_failed"})
            if not flow["approved"] or not flow["user_id"]: return self.json(428, {"error": "authorization_pending"})
            access, refresh = issue_session(db, flow["user_id"])
            db.execute("DELETE FROM device_flows WHERE device_id=?", (device_id,))
            db.commit()
        return self.json(200, {"access_token": access, "refresh_token": refresh})

    def game_ticket(self, data):
        audience = str(data.get("audience", "official-online")).strip().lower()
        if audience not in ALLOWED_GAME_AUDIENCES:
            return self.json(400, {"error": "invalid_audience"})
        with connect() as db:
            cleanup(db)
            user = self.auth_user(db)
            if not user:
                return self.json(401, {"error": "not_authenticated"})
            raw_ticket = token()
            ts = now()
            db.execute(
                "INSERT INTO game_tickets(token_hash,user_id,audience,expires_at,created_at,consumed_at) VALUES(?,?,?,?,?,NULL)",
                (token_hash(raw_ticket), user["id"], audience, ts + GAME_TICKET_TTL, ts),
            )
            db.commit()
        return self.json(201, {
            "ticket": raw_ticket,
            "expires_in": GAME_TICKET_TTL,
            "player_id": user["id"],
            "display_name": user["display_name"],
        })

    def consume_game_ticket(self, data):
        if not self.server_key_ok():
            return self.json(401, {"error": "server_auth_required"})
        raw_ticket = str(data.get("ticket", "")).strip()
        audience = str(data.get("audience", "official-online")).strip().lower()
        if not raw_ticket or audience not in ALLOWED_GAME_AUDIENCES:
            return self.json(400, {"error": "invalid_ticket_request"})
        ts = now()
        with connect() as db:
            cleanup(db)
            db.execute("BEGIN IMMEDIATE")
            row = db.execute(
                "SELECT t.*,u.* FROM game_tickets t JOIN users u ON u.id=t.user_id WHERE t.token_hash=? AND t.audience=? AND t.expires_at>? AND t.consumed_at IS NULL",
                (token_hash(raw_ticket), audience, ts),
            ).fetchone()
            if not row:
                db.rollback()
                return self.json(401, {"error": "invalid_expired_or_consumed_ticket"})
            changed = db.execute(
                "UPDATE game_tickets SET consumed_at=? WHERE token_hash=? AND consumed_at IS NULL",
                (ts, token_hash(raw_ticket)),
            ).rowcount
            if changed != 1:
                db.rollback()
                return self.json(409, {"error": "ticket_already_consumed"})
            db.commit()
        return self.json(200, {
            "valid": True,
            "audience": audience,
            "player": profile(row),
        })

    def update_skin(self, data):
        source = str(data.get("source", ""))
        if source not in ALLOWED_SKINS: return self.json(400, {"error": "invalid_skin_source"})
        with connect() as db:
            user = self.auth_user(db)
            if not user: return self.json(401, {"error": "not_authenticated"})
            db.execute("UPDATE users SET skin_source=? WHERE id=?", (source, user["id"]))
            db.commit()
            updated = db.execute("SELECT * FROM users WHERE id=?", (user["id"],)).fetchone()
        return self.json(200, profile(updated))

    def update_profile(self, data):
        if not self.same_origin_ok(): return self.json(403, {"error": "origin_rejected"})
        language = str(data.get("language", "")); region = str(data.get("region", "")); country = str(data.get("country", ""))[:32]
        with connect() as db:
            user = self.auth_user(db)
            if not user: return self.json(401, {"error": "not_authenticated"})
            if language not in ALLOWED_LANGS: language = user["language"]
            if region not in ALLOWED_REGIONS: region = user["region"]
            if not country: country = user["country"]
            db.execute("UPDATE users SET language=?, region=?, country=? WHERE id=?", (language, region, country, user["id"]))
            db.commit()
            updated = db.execute("SELECT * FROM users WHERE id=?", (user["id"],)).fetchone()
        return self.json(200, profile(updated))


if __name__ == "__main__":
    init_db()
    print(f"[ggo-auth] listening on http://{HOST}:{PORT} public={PUBLIC_URL} db={DB_PATH}", flush=True)
    ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()
