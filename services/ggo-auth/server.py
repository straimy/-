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
GAME_TICKET_TTL = 3 * 60
USERNAME_RE = re.compile(r"^[A-Za-z0-9_]{3,16}$")
ALLOWED_SKINS = {"ggo", "microsoft", "default"}
ALLOWED_LANGS = {"ru", "en", "uk"}
ALLOWED_REGIONS = {"eu", "eeu", "na", "sa", "apac", "other"}
ALLOWED_GAME_AUDIENCES = {"official-online", "play.kvicloud.ru"}
ALLOWED_ROLES = {"user", "support", "admin"}
ALLOWED_TICKET_STATUS = {"open", "pending", "closed"}
ALLOWED_TICKET_CATEGORIES = {"technical", "account", "server", "moderation", "bug", "other"}
OWNER_USERNAMES = {
    value.strip().lower()
    for value in os.environ.get("GGO_OWNER_USERNAMES", "kvi_nella").split(",")
    if value.strip()
}


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
              role TEXT NOT NULL DEFAULT 'user',
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
            CREATE TABLE IF NOT EXISTS support_tickets (
              id TEXT PRIMARY KEY,
              user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
              subject TEXT NOT NULL,
              category TEXT NOT NULL,
              status TEXT NOT NULL DEFAULT 'open',
              created_at INTEGER NOT NULL,
              updated_at INTEGER NOT NULL,
              closed_at INTEGER
            );
            CREATE TABLE IF NOT EXISTS support_messages (
              id TEXT PRIMARY KEY,
              ticket_id TEXT NOT NULL REFERENCES support_tickets(id) ON DELETE CASCADE,
              user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
              body TEXT NOT NULL,
              created_at INTEGER NOT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_access_user ON access_sessions(user_id);
            CREATE INDEX IF NOT EXISTS idx_refresh_user ON refresh_tokens(user_id);
            CREATE INDEX IF NOT EXISTS idx_device_expiry ON device_flows(expires_at);
            CREATE INDEX IF NOT EXISTS idx_game_ticket_expiry ON game_tickets(expires_at);
            CREATE INDEX IF NOT EXISTS idx_game_ticket_user ON game_tickets(user_id);
            CREATE INDEX IF NOT EXISTS idx_support_ticket_user ON support_tickets(user_id);
            CREATE INDEX IF NOT EXISTS idx_support_ticket_status ON support_tickets(status,updated_at);
            CREATE INDEX IF NOT EXISTS idx_support_message_ticket ON support_messages(ticket_id,created_at);
            """
        )
        columns = {row["name"] for row in db.execute("PRAGMA table_info(users)")}
        if "role" not in columns:
            db.execute("ALTER TABLE users ADD COLUMN role TEXT NOT NULL DEFAULT 'user'")
        for owner in OWNER_USERNAMES:
            db.execute("UPDATE users SET role='admin' WHERE username_norm=?", (owner,))
        db.commit()


def role_label(role: str) -> str:
    return {"admin": "Администратор", "support": "Тех. Поддержка"}.get(role, "Игрок")


def profile(row):
    role = row["role"] if "role" in row.keys() else "user"
    return {
        "id": row["id"],
        "display_name": row["display_name"],
        "skin_source": row["skin_source"],
        "region": row["region"],
        "language": row["language"],
        "country": row["country"],
        "role": role,
        "role_label": role_label(role),
        "created_at": row["created_at"],
    }


def public_user(row):
    role = row["role"] if "role" in row.keys() else "user"
    return {
        "id": row["id"],
        "display_name": row["display_name"],
        "role": role,
        "role_label": role_label(role),
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
    db.execute(
        "DELETE FROM game_tickets WHERE expires_at <= ? OR (consumed_at IS NOT NULL AND consumed_at <= ?)",
        (ts, ts - 300),
    )


class Handler(BaseHTTPRequestHandler):
    server_version = "GGOAuth/1.2"

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
        cookie = SimpleCookie()
        cookie.load(raw)
        item = cookie.get("ggo_session")
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

    def require_user(self, db):
        user = self.auth_user(db)
        if not user:
            self.json(401, {"error": "not_authenticated"})
            return None
        return user

    def require_staff(self, db):
        user = self.require_user(db)
        if not user:
            return None
        if user["role"] not in ("support", "admin"):
            self.json(403, {"error": "staff_required"})
            return None
        return user

    def require_admin(self, db):
        user = self.require_user(db)
        if not user:
            return None
        if user["role"] != "admin":
            self.json(403, {"error": "admin_required"})
            return None
        return user

    def ticket_payload(self, db, ticket):
        owner = db.execute("SELECT * FROM users WHERE id=?", (ticket["user_id"],)).fetchone()
        messages = db.execute(
            "SELECT m.*,u.display_name,u.role FROM support_messages m JOIN users u ON u.id=m.user_id WHERE m.ticket_id=? ORDER BY m.created_at ASC",
            (ticket["id"],),
        ).fetchall()
        return {
            "id": ticket["id"],
            "subject": ticket["subject"],
            "category": ticket["category"],
            "status": ticket["status"],
            "created_at": ticket["created_at"],
            "updated_at": ticket["updated_at"],
            "closed_at": ticket["closed_at"],
            "owner": public_user(owner),
            "messages": [
                {
                    "id": row["id"],
                    "body": row["body"],
                    "created_at": row["created_at"],
                    "author": {
                        "id": row["user_id"],
                        "display_name": row["display_name"],
                        "role": row["role"],
                        "role_label": role_label(row["role"]),
                    },
                }
                for row in messages
            ],
        }

    def visible_ticket(self, db, user, ticket_id):
        ticket = db.execute("SELECT * FROM support_tickets WHERE id=?", (ticket_id,)).fetchone()
        if not ticket:
            self.json(404, {"error": "ticket_not_found"})
            return None
        if user["role"] not in ("support", "admin") and ticket["user_id"] != user["id"]:
            self.json(403, {"error": "ticket_forbidden"})
            return None
        return ticket

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path
        query = urllib.parse.parse_qs(parsed.query)
        if path == "/api/v1/health":
            return self.json(200, {"ok": True, "service": "ggo-auth", "version": 3, "game_tickets": True, "support_tickets": True, "staff_roles": True})
        if path in ("/api/v1/me", "/api/v1/auth/session"):
            with connect() as db:
                cleanup(db)
                user = self.auth_user(db)
                if not user:
                    return self.json(401, {"error": "not_authenticated"})
                return self.json(200, profile(user))
        if path == "/api/v1/support/tickets":
            with connect() as db:
                user = self.require_user(db)
                if not user:
                    return
                rows = db.execute(
                    "SELECT * FROM support_tickets WHERE user_id=? ORDER BY updated_at DESC LIMIT 50",
                    (user["id"],),
                ).fetchall()
                return self.json(200, {"tickets": [self.ticket_payload(db, row) for row in rows]})
        if path.startswith("/api/v1/support/tickets/"):
            ticket_id = path.rsplit("/", 1)[-1]
            with connect() as db:
                user = self.require_user(db)
                if not user:
                    return
                ticket = self.visible_ticket(db, user, ticket_id)
                if not ticket:
                    return
                return self.json(200, self.ticket_payload(db, ticket))
        if path == "/api/v1/staff/tickets":
            with connect() as db:
                user = self.require_staff(db)
                if not user:
                    return
                status = str(query.get("status", [""])[0])
                if status in ALLOWED_TICKET_STATUS:
                    rows = db.execute(
                        "SELECT * FROM support_tickets WHERE status=? ORDER BY updated_at DESC LIMIT 100",
                        (status,),
                    ).fetchall()
                else:
                    rows = db.execute("SELECT * FROM support_tickets ORDER BY updated_at DESC LIMIT 100").fetchall()
                return self.json(200, {"tickets": [self.ticket_payload(db, row) for row in rows]})
        if path == "/api/v1/staff/stats":
            with connect() as db:
                user = self.require_staff(db)
                if not user:
                    return
                counts = {
                    status: db.execute("SELECT COUNT(*) FROM support_tickets WHERE status=?", (status,)).fetchone()[0]
                    for status in ALLOWED_TICKET_STATUS
                }
                return self.json(200, {"tickets": counts})
        if path == "/api/v1/admin/users":
            with connect() as db:
                user = self.require_admin(db)
                if not user:
                    return
                needle = str(query.get("q", [""])[0]).strip().lower()[:32]
                if needle:
                    rows = db.execute(
                        "SELECT * FROM users WHERE username_norm LIKE ? OR lower(display_name) LIKE ? ORDER BY created_at DESC LIMIT 50",
                        (f"%{needle}%", f"%{needle}%"),
                    ).fetchall()
                else:
                    rows = db.execute("SELECT * FROM users ORDER BY created_at DESC LIMIT 50").fetchall()
                return self.json(200, {"users": [profile(row) for row in rows]})
        return self.json(404, {"error": "not_found"})

    def do_POST(self):
        path = urllib.parse.urlparse(self.path).path
        data = self.read_json()
        if data is None:
            return self.json(400, {"error": "invalid_json"})
        if path == "/api/v1/auth/register":
            return self.register(data)
        if path == "/api/v1/auth/login":
            return self.login(data)
        if path == "/api/v1/auth/logout":
            return self.logout(data)
        if path == "/api/v1/auth/device/start":
            return self.device_start(data)
        if path == "/api/v1/auth/device/approve":
            return self.device_approve(data)
        if path == "/api/v1/auth/device/token":
            return self.device_token(data)
        if path == "/api/v1/auth/game-ticket":
            return self.game_ticket(data)
        if path == "/api/v1/auth/game-ticket/consume":
            return self.consume_game_ticket(data)
        if path == "/api/v1/support/tickets":
            return self.create_ticket(data)
        if path.startswith("/api/v1/support/tickets/") and path.endswith("/messages"):
            return self.add_ticket_message(path.split("/")[-2], data)
        if path.startswith("/api/v1/support/tickets/") and path.endswith("/close"):
            return self.change_ticket_status(path.split("/")[-2], "closed")
        if path.startswith("/api/v1/support/tickets/") and path.endswith("/reopen"):
            return self.change_ticket_status(path.split("/")[-2], "open")
        return self.json(404, {"error": "not_found"})

    def do_PUT(self):
        path = urllib.parse.urlparse(self.path).path
        data = self.read_json()
        if data is None:
            return self.json(400, {"error": "invalid_json"})
        if path == "/api/v1/me/skin/source":
            return self.update_skin(data)
        if path == "/api/v1/me/profile":
            return self.update_profile(data)
        if path == "/api/v1/me/identities/minecraft":
            return self.json(501, {"error": "minecraft_link_not_enabled", "message": "Minecraft identity verification is not enabled yet."})
        if path.startswith("/api/v1/admin/users/") and path.endswith("/role"):
            return self.update_role(path.split("/")[-2], data)
        if path.startswith("/api/v1/staff/tickets/") and path.endswith("/status"):
            return self.staff_ticket_status(path.split("/")[-2], data)
        return self.json(404, {"error": "not_found"})

    def register(self, data):
        if not self.same_origin_ok():
            return self.json(403, {"error": "origin_rejected"})
        username = str(data.get("username", "")).strip()
        password = str(data.get("password", ""))
        region = str(data.get("region", "eu"))
        language = str(data.get("language", "ru"))
        country = str(data.get("country", "other"))[:32]
        if not USERNAME_RE.fullmatch(username):
            return self.json(400, {"error": "invalid_username", "message": "Use 3-16 latin letters, numbers or underscore."})
        if len(password) < 8 or len(password) > 128:
            return self.json(400, {"error": "invalid_password", "message": "Password must be 8-128 characters."})
        if region not in ALLOWED_REGIONS:
            region = "other"
        if language not in ALLOWED_LANGS:
            language = "en"
        salt = secrets.token_bytes(16)
        digest = password_hash(password, salt)
        user_id = secrets.token_hex(16)
        username_norm = username.lower()
        role = "admin" if username_norm in OWNER_USERNAMES else "user"
        with connect() as db:
            cleanup(db)
            try:
                db.execute(
                    "INSERT INTO users(id,username_norm,display_name,password_salt,password_hash,skin_source,region,language,country,role,created_at) VALUES(?,?,?,?,?,'default',?,?,?,?,?)",
                    (user_id, username_norm, username, salt, digest, region, language, country, role, now()),
                )
            except sqlite3.IntegrityError:
                return self.json(409, {"error": "username_taken"})
            access, refresh = issue_session(db, user_id)
            db.commit()
            user = db.execute("SELECT * FROM users WHERE id=?", (user_id,)).fetchone()
        return self.json(201, {"access_token": access, "refresh_token": refresh, "profile": profile(user)}, {"Set-Cookie": self.set_session_cookie(access)})

    def login(self, data):
        if not self.same_origin_ok():
            return self.json(403, {"error": "origin_rejected"})
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
            if user["username_norm"] in OWNER_USERNAMES and user["role"] != "admin":
                db.execute("UPDATE users SET role='admin' WHERE id=?", (user["id"],))
                user = db.execute("SELECT * FROM users WHERE id=?", (user["id"],)).fetchone()
            access, refresh = issue_session(db, user["id"])
            db.commit()
        return self.json(200, {"access_token": access, "refresh_token": refresh, "profile": profile(user)}, {"Set-Cookie": self.set_session_cookie(access)})

    def logout(self, data):
        access = self.bearer() or self.cookie_token()
        refresh = str(data.get("refresh_token", ""))
        with connect() as db:
            if access:
                db.execute("DELETE FROM access_sessions WHERE token_hash=?", (token_hash(access),))
            if refresh:
                db.execute("DELETE FROM refresh_tokens WHERE token_hash=?", (token_hash(refresh),))
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
            db.execute(
                "INSERT INTO device_flows(device_id,code_challenge,installation_id,expires_at,created_at) VALUES(?,?,?,?,?)",
                (device_id, challenge, installation_id, now() + DEVICE_TTL, now()),
            )
            db.commit()
        uri = f"{PUBLIC_URL}/account/device.html?device_id={urllib.parse.quote(device_id)}"
        return self.json(200, {"device_id": device_id, "verification_uri": uri, "expires_in": DEVICE_TTL, "interval": 3})

    def device_approve(self, data):
        if not self.same_origin_ok():
            return self.json(403, {"error": "origin_rejected"})
        device_id = str(data.get("device_id", ""))
        with connect() as db:
            cleanup(db)
            user = self.auth_user(db)
            if not user:
                return self.json(401, {"error": "not_authenticated"})
            flow = db.execute("SELECT * FROM device_flows WHERE device_id=? AND expires_at>?", (device_id, now())).fetchone()
            if not flow:
                return self.json(404, {"error": "device_flow_not_found"})
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
            if not flow:
                return self.json(404, {"error": "device_flow_not_found"})
            if not hmac.compare_digest(challenge, flow["code_challenge"]):
                return self.json(401, {"error": "pkce_failed"})
            if not flow["approved"] or not flow["user_id"]:
                return self.json(428, {"error": "authorization_pending"})
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
        return self.json(201, {"ticket": raw_ticket, "expires_in": GAME_TICKET_TTL, "player_id": user["id"], "display_name": user["display_name"]})

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
            db.commit()
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
        return self.json(200, {"valid": True, "audience": audience, "player": profile(row)})

    def update_skin(self, data):
        source = str(data.get("source", ""))
        if source not in ALLOWED_SKINS:
            return self.json(400, {"error": "invalid_skin_source"})
        with connect() as db:
            user = self.auth_user(db)
            if not user:
                return self.json(401, {"error": "not_authenticated"})
            db.execute("UPDATE users SET skin_source=? WHERE id=?", (source, user["id"]))
            db.commit()
            updated = db.execute("SELECT * FROM users WHERE id=?", (user["id"],)).fetchone()
        return self.json(200, profile(updated))

    def update_profile(self, data):
        if not self.same_origin_ok():
            return self.json(403, {"error": "origin_rejected"})
        language = str(data.get("language", ""))
        region = str(data.get("region", ""))
        country = str(data.get("country", ""))[:32]
        with connect() as db:
            user = self.auth_user(db)
            if not user:
                return self.json(401, {"error": "not_authenticated"})
            if language not in ALLOWED_LANGS:
                language = user["language"]
            if region not in ALLOWED_REGIONS:
                region = user["region"]
            if not country:
                country = user["country"]
            db.execute("UPDATE users SET language=?, region=?, country=? WHERE id=?", (language, region, country, user["id"]))
            db.commit()
            updated = db.execute("SELECT * FROM users WHERE id=?", (user["id"],)).fetchone()
        return self.json(200, profile(updated))

    def create_ticket(self, data):
        if not self.same_origin_ok():
            return self.json(403, {"error": "origin_rejected"})
        subject = str(data.get("subject", "")).strip()
        body = str(data.get("body", "")).strip()
        category = str(data.get("category", "technical")).strip().lower()
        if not 3 <= len(subject) <= 120:
            return self.json(400, {"error": "invalid_subject"})
        if not 5 <= len(body) <= 4000:
            return self.json(400, {"error": "invalid_message"})
        if category not in ALLOWED_TICKET_CATEGORIES:
            category = "other"
        with connect() as db:
            user = self.require_user(db)
            if not user:
                return
            open_count = db.execute(
                "SELECT COUNT(*) FROM support_tickets WHERE user_id=? AND status!='closed'",
                (user["id"],),
            ).fetchone()[0]
            if open_count >= 10:
                return self.json(429, {"error": "too_many_open_tickets"})
            ts = now()
            ticket_id = secrets.token_hex(8)
            message_id = secrets.token_hex(12)
            db.execute(
                "INSERT INTO support_tickets(id,user_id,subject,category,status,created_at,updated_at) VALUES(?,?,?,?, 'open',?,?)",
                (ticket_id, user["id"], subject, category, ts, ts),
            )
            db.execute(
                "INSERT INTO support_messages(id,ticket_id,user_id,body,created_at) VALUES(?,?,?,?,?)",
                (message_id, ticket_id, user["id"], body, ts),
            )
            db.commit()
            ticket = db.execute("SELECT * FROM support_tickets WHERE id=?", (ticket_id,)).fetchone()
            return self.json(201, self.ticket_payload(db, ticket))

    def add_ticket_message(self, ticket_id, data):
        if not self.same_origin_ok():
            return self.json(403, {"error": "origin_rejected"})
        body = str(data.get("body", "")).strip()
        if not 1 <= len(body) <= 8000:
            return self.json(400, {"error": "invalid_message"})
        with connect() as db:
            user = self.require_user(db)
            if not user:
                return
            ticket = self.visible_ticket(db, user, ticket_id)
            if not ticket:
                return
            if ticket["status"] == "closed" and user["role"] not in ("support", "admin"):
                return self.json(409, {"error": "ticket_closed"})
            ts = now()
            db.execute(
                "INSERT INTO support_messages(id,ticket_id,user_id,body,created_at) VALUES(?,?,?,?,?)",
                (secrets.token_hex(12), ticket_id, user["id"], body, ts),
            )
            next_status = "pending" if user["role"] in ("support", "admin") else "open"
            db.execute(
                "UPDATE support_tickets SET status=?,updated_at=?,closed_at=NULL WHERE id=?",
                (next_status, ts, ticket_id),
            )
            db.commit()
            updated = db.execute("SELECT * FROM support_tickets WHERE id=?", (ticket_id,)).fetchone()
            return self.json(201, self.ticket_payload(db, updated))

    def change_ticket_status(self, ticket_id, status):
        if not self.same_origin_ok():
            return self.json(403, {"error": "origin_rejected"})
        with connect() as db:
            user = self.require_user(db)
            if not user:
                return
            ticket = self.visible_ticket(db, user, ticket_id)
            if not ticket:
                return
            ts = now()
            closed_at = ts if status == "closed" else None
            db.execute(
                "UPDATE support_tickets SET status=?,updated_at=?,closed_at=? WHERE id=?",
                (status, ts, closed_at, ticket_id),
            )
            db.commit()
            updated = db.execute("SELECT * FROM support_tickets WHERE id=?", (ticket_id,)).fetchone()
            return self.json(200, self.ticket_payload(db, updated))

    def staff_ticket_status(self, ticket_id, data):
        if not self.same_origin_ok():
            return self.json(403, {"error": "origin_rejected"})
        status = str(data.get("status", "")).strip().lower()
        if status not in ALLOWED_TICKET_STATUS:
            return self.json(400, {"error": "invalid_ticket_status"})
        with connect() as db:
            user = self.require_staff(db)
            if not user:
                return
            ticket = db.execute("SELECT * FROM support_tickets WHERE id=?", (ticket_id,)).fetchone()
            if not ticket:
                return self.json(404, {"error": "ticket_not_found"})
            ts = now()
            db.execute(
                "UPDATE support_tickets SET status=?,updated_at=?,closed_at=? WHERE id=?",
                (status, ts, ts if status == "closed" else None, ticket_id),
            )
            db.commit()
            updated = db.execute("SELECT * FROM support_tickets WHERE id=?", (ticket_id,)).fetchone()
            return self.json(200, self.ticket_payload(db, updated))

    def update_role(self, user_id, data):
        if not self.same_origin_ok():
            return self.json(403, {"error": "origin_rejected"})
        role = str(data.get("role", "")).strip().lower()
        if role not in ALLOWED_ROLES:
            return self.json(400, {"error": "invalid_role"})
        with connect() as db:
            actor = self.require_admin(db)
            if not actor:
                return
            target = db.execute("SELECT * FROM users WHERE id=?", (user_id,)).fetchone()
            if not target:
                return self.json(404, {"error": "user_not_found"})
            if target["username_norm"] in OWNER_USERNAMES and role != "admin":
                return self.json(409, {"error": "owner_role_locked"})
            db.execute("UPDATE users SET role=? WHERE id=?", (role, user_id))
            db.commit()
            updated = db.execute("SELECT * FROM users WHERE id=?", (user_id,)).fetchone()
            return self.json(200, profile(updated))


if __name__ == "__main__":
    init_db()
    print(f"[ggo-auth] listening on http://{HOST}:{PORT} public={PUBLIC_URL} db={DB_PATH}", flush=True)
    ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()
