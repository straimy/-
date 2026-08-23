#!/usr/bin/env python3
import http.client
import json
import os
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
        env = os.environ.copy()
        env.update({
            "GGO_AUTH_PORT": str(PORT),
            "GGO_PUBLIC_URL": f"http://{BASE}",
            "GGO_AUTH_DB": str(Path(cls.temp.name) / "auth.db"),
            "GGO_OWNER_USERNAMES": "kvi_nella",
        })
        env.pop("GGO_ALLOW_OWNER_BOOTSTRAP", None)
        cls.proc = subprocess.Popen(
            [sys.executable, str(Path(__file__).with_name("secure_server.py"))],
            env=env,
        )
        for _ in range(50):
            try:
                status, _ = cls.req("GET", "/api/v1/health")
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
    def req(method, path, data=None):
        conn = http.client.HTTPConnection(BASE, timeout=5)
        body = None if data is None else json.dumps(data)
        conn.request(method, path, body=body, headers={"Content-Type": "application/json"})
        response = conn.getresponse()
        raw = response.read()
        payload = json.loads(raw) if raw else {}
        code = response.status
        conn.close()
        return code, payload

    def test_reserved_owner_cannot_be_publicly_registered(self):
        status, payload = self.req("POST", "/api/v1/auth/register", {
            "username": "kvi_nella",
            "password": "correct-horse-123",
        })
        self.assertEqual(status, 403)
        self.assertEqual(payload["error"], "reserved_owner_username")

    def test_normal_registration_still_works(self):
        status, payload = self.req("POST", "/api/v1/auth/register", {
            "username": "SecurityUser",
            "password": "correct-horse-123",
            "region": "eu",
            "language": "ru",
            "country": "EE",
        })
        self.assertEqual(status, 201)
        self.assertEqual(payload["profile"]["role"], "user")


if __name__ == "__main__":
    unittest.main()
