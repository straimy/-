#!/usr/bin/env python3
import http.client
import json
import os
import sqlite3
import subprocess
import sys
import tempfile
import time
import unittest
from pathlib import Path

PORT = 18788
BASE = f"127.0.0.1:{PORT}"


class SecuritySmoke(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.db_path = Path(cls.temp.name) / "auth.db"
        env = os.environ.copy()
        env.update({
            "GGO_AUTH_PORT": str(PORT),
            "GGO_PUBLIC_URL": f"http://{BASE}",
            "GGO_AUTH_DB": str(cls.db_path),
            "GGO_OWNER_USERNAMES": "kvi_nella",
            "GGO_TRUST_LOCAL_PROXY": "1",
        })
        env.pop("GGO_ALLOW_OWNER_BOOTSTRAP", None)
        cls.proc = subprocess.Popen(
            [sys.executable, str(Path(__file__).with_name("secure_server.py"))],
            env=env,
        )
        for _ in range(50):
            try:
                status, _, _ = cls.req("GET", "/api/v1/health")
                if status == 200:
                    return
            except OSError:
                pass
            time.sleep(0.1)
        raise RuntimeError("secure auth service did not start")

    @classmethod
    def tearDownClass(cls):
        cls.proc.terminate()
        cls.proc.wait(timeout=5)
        cls.temp.cleanup()

    @staticmethod
    def req(method, path, data=None, headers=None):
        conn = http.client.HTTPConnection(BASE, timeout=5)
        body = None if data is None else json.dumps(data)
        request_headers = {"Content-Type": "application/json"}
        if headers:
            request_headers.update(headers)
        conn.request(method, path, body=body, headers=request_headers)
        response = conn.getresponse()
        raw = response.read()
        payload = json.loads(raw) if raw else {}
        code = response.status
        response_headers = {k.lower(): v for k, v in response.getheaders()}
        conn.close()
        return code, payload, response_headers

    @classmethod
    def register(cls, username, ip):
        status, payload, headers = cls.req("POST", "/api/v1/auth/register", {
            "username": username,
            "password": "correct-horse-123",
            "region": "eu",
            "language": "ru",
            "country": "EE",
        }, {"X-Forwarded-For": ip})
        return status, payload, headers

    def test_reserved_owner_cannot_be_publicly_registered(self):
        status, payload, _ = self.req("POST", "/api/v1/auth/register", {
            "username": "kvi_nella",
            "password": "correct-horse-123",
        }, {"X-Forwarded-For": "198.51.100.10"})
        self.assertEqual(status, 403)
        self.assertEqual(payload["error"], "reserved_owner_username")

    def test_normal_registration_still_works(self):
        status, payload, _ = self.register("SecurityUser", "198.51.100.11")
        self.assertEqual(status, 201)
        self.assertEqual(payload["profile"]["role"], "user")

    def test_security_headers_present(self):
        status, _, headers = self.req("GET", "/api/v1/health")
        self.assertEqual(status, 200)
        self.assertEqual(headers.get("x-frame-options"), "DENY")
        self.assertEqual(headers.get("referrer-policy"), "no-referrer")
        self.assertIn("camera=()", headers.get("permissions-policy", ""))

    def test_login_rate_limit_uses_forwarded_client_from_loopback_proxy(self):
        ip = "198.51.100.50"
        payload = {"username": "nobody", "password": "wrong-password"}
        for _ in range(8):
            status, body, _ = self.req("POST", "/api/v1/auth/login", payload, {"X-Forwarded-For": ip})
            self.assertEqual(status, 401)
            self.assertEqual(body["error"], "invalid_credentials")
        status, body, headers = self.req("POST", "/api/v1/auth/login", payload, {"X-Forwarded-For": ip})
        self.assertEqual(status, 429)
        self.assertEqual(body["error"], "rate_limited")
        self.assertGreaterEqual(int(headers.get("retry-after", "0")), 1)

    def test_rate_limit_isolated_by_client_ip(self):
        payload = {"username": "nobody", "password": "wrong-password"}
        for _ in range(8):
            self.req("POST", "/api/v1/auth/login", payload, {"X-Forwarded-For": "198.51.100.60"})
        status, _, _ = self.req("POST", "/api/v1/auth/login", payload, {"X-Forwarded-For": "198.51.100.61"})
        self.assertEqual(status, 401)

    def test_logout_all_revokes_access_and_refresh_sessions(self):
        status, payload, _ = self.register("RevokeUser", "198.51.100.70")
        self.assertEqual(status, 201)
        access = payload["access_token"]
        headers = {
            "Authorization": f"Bearer {access}",
            "X-Forwarded-For": "198.51.100.70",
        }
        status, body, _ = self.req("POST", "/api/v1/auth/logout-all", {}, headers)
        self.assertEqual(status, 200)
        self.assertTrue(body["ok"])
        self.assertGreaterEqual(body["revoked"]["access_sessions"], 1)
        self.assertGreaterEqual(body["revoked"]["refresh_tokens"], 1)
        status, body, _ = self.req("GET", "/api/v1/me", headers=headers)
        self.assertEqual(status, 401)
        self.assertEqual(body["error"], "not_authenticated")

    def test_cookie_logout_all_requires_origin(self):
        status, _, response_headers = self.register("CookieRevoke", "198.51.100.71")
        self.assertEqual(status, 201)
        cookie = response_headers.get("set-cookie", "").split(";", 1)[0]
        status, body, _ = self.req(
            "POST",
            "/api/v1/auth/logout-all",
            {},
            {"Cookie": cookie, "X-Forwarded-For": "198.51.100.71"},
        )
        self.assertEqual(status, 403)
        self.assertEqual(body["error"], "origin_rejected")

    def test_admin_role_changes_are_append_only_audited(self):
        status, admin_payload, _ = self.register("AuditAdmin", "198.51.100.80")
        self.assertEqual(status, 201)
        status, target_payload, _ = self.register("AuditTarget", "198.51.100.81")
        self.assertEqual(status, 201)
        admin_id = admin_payload["profile"]["id"]
        target_id = target_payload["profile"]["id"]
        with sqlite3.connect(self.db_path) as db:
            db.execute("UPDATE users SET role='admin' WHERE id=?", (admin_id,))
            db.commit()
        headers = {
            "Authorization": f"Bearer {admin_payload['access_token']}",
            "Origin": f"http://{BASE}",
            "X-Forwarded-For": "198.51.100.80",
        }
        status, body, _ = self.req(
            "PUT", f"/api/v1/admin/users/{target_id}/role", {"role": "support"}, headers
        )
        self.assertEqual(status, 200)
        self.assertEqual(body["role"], "support")
        status, audit, _ = self.req("GET", "/api/v1/admin/audit", headers=headers)
        self.assertEqual(status, 200)
        events = [e for e in audit["events"] if e["action"] == "role_changed" and e["target_user_id"] == target_id]
        self.assertTrue(events)
        self.assertEqual(events[0]["actor_user_id"], admin_id)
        self.assertEqual(events[0]["details"]["old_role"], "user")
        self.assertEqual(events[0]["details"]["new_role"], "support")
        with sqlite3.connect(self.db_path) as db:
            count = db.execute(
                "SELECT COUNT(*) FROM security_audit WHERE action='role_changed' AND target_user_id=?",
                (target_id,),
            ).fetchone()[0]
        self.assertGreaterEqual(count, 1)

    def test_non_admin_cannot_read_security_audit(self):
        status, payload, _ = self.register("AuditViewer", "198.51.100.82")
        self.assertEqual(status, 201)
        status, body, _ = self.req("GET", "/api/v1/admin/audit", headers={
            "Authorization": f"Bearer {payload['access_token']}",
        })
        self.assertEqual(status, 403)
        self.assertEqual(body["error"], "admin_required")


if __name__ == "__main__":
    unittest.main()
