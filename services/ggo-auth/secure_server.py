#!/usr/bin/env python3
"""Production entrypoint for the GGO Account API.

Keeps the core API implementation in server.py while enforcing bootstrap-only owner identities,
conservative per-client rate limits, hardened response headers, emergency session revocation and a
small append-only security audit trail for privileged account actions.
"""
import json
import os
import secrets
import threading
import time
from collections import defaultdict, deque
from http.server import ThreadingHTTPServer

import server as core

ALLOW_OWNER_BOOTSTRAP = os.environ.get("GGO_ALLOW_OWNER_BOOTSTRAP", "").strip() == "1"
TRUST_PROXY = os.environ.get("GGO_TRUST_LOCAL_PROXY", "1").strip() == "1"

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
        db.commit()


def _audit(db, actor_user_id, action, target_user_id=None, details=None):
    # Append-only by API design: there is intentionally no update/delete endpoint for this table.
    safe_details = json.dumps(details or {}, ensure_ascii=False, separators=(",", ":"))[:2000]
    db.execute(
        "INSERT INTO security_audit(id,actor_user_id,action,target_user_id,details,created_at) VALUES(?,?,?,?,?,?)",
        (secrets.token_hex(12), actor_user_id, action, target_user_id, safe_details, core.now()),
    )


class SecureHandler(core.Handler):
    server_version = "GGOAuth/1.4-secure"

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

    def do_GET(self):
        path = self.path.split("?", 1)[0]
        if path == "/api/v1/admin/audit":
            return self.get_security_audit()
        return super().do_GET()

    def do_POST(self):
        if self._rate_limit():
            return
        path = self.path.split("?", 1)[0]
        if path == "/api/v1/auth/logout-all":
            return self.logout_all_sessions()
        return super().do_POST()

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
    print(
        f"[ggo-auth] secure entrypoint on http://{core.HOST}:{core.PORT} "
        f"public={core.PUBLIC_URL} db={core.DB_PATH}",
        flush=True,
    )
    ThreadingHTTPServer((core.HOST, core.PORT), SecureHandler).serve_forever()
