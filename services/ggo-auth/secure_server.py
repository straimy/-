#!/usr/bin/env python3
"""Production entrypoint for the GGO Account API.

Keeps the core API implementation in server.py while enforcing bootstrap-only owner identities.
A public registration must never be able to obtain administrator privileges merely by choosing a
reserved username. Existing owner accounts remain protected/promoted by the core migration/login
logic. Fresh installations should provision the owner account out-of-band.
"""
import os
from http.server import ThreadingHTTPServer

import server as core

ALLOW_OWNER_BOOTSTRAP = os.environ.get("GGO_ALLOW_OWNER_BOOTSTRAP", "").strip() == "1"


class SecureHandler(core.Handler):
    server_version = "GGOAuth/1.2-secure"

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
