#!/usr/bin/env python3
"""Production entrypoint for the GGO Account API.

Keeps the core API implementation in server.py while enforcing bootstrap-only owner identities
and conservative per-client rate limits on authentication/session issuance endpoints.
"""
import os
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


class SecureHandler(core.Handler):
    server_version = "GGOAuth/1.3-secure"

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

    def do_POST(self):
        if self._rate_limit():
            return
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


if __name__ == "__main__":
    core.init_db()
    print(
        f"[ggo-auth] secure entrypoint on http://{core.HOST}:{core.PORT} "
        f"public={core.PUBLIC_URL} db={core.DB_PATH}",
        flush=True,
    )
    ThreadingHTTPServer((core.HOST, core.PORT), SecureHandler).serve_forever()
