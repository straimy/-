#!/usr/bin/env python3
import base64
import concurrent.futures
import hashlib
import http.client
import json
import os
import subprocess
import sys
import tempfile
import time
import unittest
from pathlib import Path

PORT = 18787
BASE = f"127.0.0.1:{PORT}"
SERVER_KEY = "ci-ggo-server-key"


def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).decode().rstrip("=")


class AuthSmoke(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        env = os.environ.copy()
        env.update({
            "GGO_AUTH_PORT": str(PORT),
            "GGO_PUBLIC_URL": f"http://{BASE}",
            "GGO_AUTH_DB": str(Path(cls.temp.name) / "auth.db"),
            "GGO_SERVER_KEY": SERVER_KEY,
            "GGO_OWNER_USERNAMES": "kvi_nella",
        })
        cls.proc = subprocess.Popen([sys.executable, str(Path(__file__).with_name("server.py"))], env=env)
        for _ in range(50):
            try:
                status, _, _ = cls.req("GET", "/api/v1/health")
                if status == 200:
                    return
            except OSError:
                pass
            time.sleep(0.1)
        raise RuntimeError("auth service did not start")

    @classmethod
    def tearDownClass(cls):
        cls.proc.terminate()
        cls.proc.wait(timeout=5)
        cls.temp.cleanup()

    @staticmethod
    def req(method, path, data=None, headers=None):
        conn = http.client.HTTPConnection(BASE, timeout=5)
        body = None if data is None else json.dumps(data)
        h = {"Content-Type": "application/json"}
        if headers:
            h.update(headers)
        conn.request(method, path, body=body, headers=h)
        response = conn.getresponse()
        raw = response.read()
        parsed = json.loads(raw) if raw else None
        out_headers = dict(response.getheaders())
        conn.close()
        return response.status, parsed, out_headers

    def register_user(self, username):
        status, payload, headers = self.req("POST", "/api/v1/auth/register", {
            "username": username,
            "password": "correct-horse-123",
            "region": "eu",
            "language": "ru",
            "country": "SE",
        })
        self.assertEqual(status, 201)
        return payload, headers["Set-Cookie"].split(";", 1)[0]

    def test_registration_login_device_and_game_ticket_flow(self):
        status, reg, headers = self.req("POST", "/api/v1/auth/register", {
            "username": "Smoke_User",
            "password": "correct-horse-123",
            "region": "eu",
            "language": "ru",
            "country": "SE",
        })
        self.assertEqual(status, 201)
        self.assertEqual(reg["profile"]["display_name"], "Smoke_User")
        self.assertEqual(reg["profile"]["role"], "user")
        cookie = headers["Set-Cookie"].split(";", 1)[0]

        status, _, _ = self.req("POST", "/api/v1/auth/register", {"username": "Smoke_User", "password": "correct-horse-123"})
        self.assertEqual(status, 409)

        status, login, _ = self.req("POST", "/api/v1/auth/login", {"username": "smoke_user", "password": "correct-horse-123"})
        self.assertEqual(status, 200)
        self.assertTrue(login["access_token"])

        status, me, _ = self.req("GET", "/api/v1/me", headers={"Authorization": f"Bearer {login['access_token']}"})
        self.assertEqual(status, 200)
        self.assertEqual(me["country"], "SE")

        status, ticket, _ = self.req(
            "POST",
            "/api/v1/auth/game-ticket",
            {"audience": "official-online"},
            headers={"Authorization": f"Bearer {login['access_token']}"},
        )
        self.assertEqual(status, 201)
        self.assertTrue(ticket["ticket"])
        self.assertEqual(ticket["player_id"], me["id"])
        self.assertGreaterEqual(ticket["expires_in"], 175)
        self.assertLessEqual(ticket["expires_in"], 180)

        status, denied, _ = self.req(
            "POST",
            "/api/v1/auth/game-ticket/consume",
            {"ticket": ticket["ticket"], "audience": "official-online"},
            headers={"X-GGO-Server-Key": "wrong-key"},
        )
        self.assertEqual(status, 401)
        self.assertEqual(denied["error"], "server_auth_required")

        status, consumed, _ = self.req(
            "POST",
            "/api/v1/auth/game-ticket/consume",
            {"ticket": ticket["ticket"], "audience": "official-online"},
            headers={"X-GGO-Server-Key": SERVER_KEY},
        )
        self.assertEqual(status, 200)
        self.assertTrue(consumed["valid"])
        self.assertEqual(consumed["player"]["id"], me["id"])

        status, replay, _ = self.req(
            "POST",
            "/api/v1/auth/game-ticket/consume",
            {"ticket": ticket["ticket"], "audience": "official-online"},
            headers={"X-GGO-Server-Key": SERVER_KEY},
        )
        self.assertEqual(status, 401)
        self.assertEqual(replay["error"], "invalid_expired_or_consumed_ticket")

        status, race_ticket, _ = self.req(
            "POST",
            "/api/v1/auth/game-ticket",
            {"audience": "official-online"},
            headers={"Authorization": f"Bearer {login['access_token']}"},
        )
        self.assertEqual(status, 201)

        def consume_race_ticket():
            return self.req(
                "POST",
                "/api/v1/auth/game-ticket/consume",
                {"ticket": race_ticket["ticket"], "audience": "official-online"},
                headers={"X-GGO-Server-Key": SERVER_KEY},
            )

        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            race_results = list(pool.map(lambda _: consume_race_ticket(), range(2)))
        race_statuses = sorted(result[0] for result in race_results)
        self.assertEqual(race_statuses, [200, 401])
        winner = next(payload for code, payload, _ in race_results if code == 200)
        loser = next(payload for code, payload, _ in race_results if code == 401)
        self.assertTrue(winner["valid"])
        self.assertEqual(winner["player"]["id"], me["id"])
        self.assertEqual(loser["error"], "invalid_expired_or_consumed_ticket")

        verifier = "v" * 64
        challenge = b64url(hashlib.sha256(verifier.encode()).digest())
        status, start, _ = self.req("POST", "/api/v1/auth/device/start", {"code_challenge": challenge, "installation_id": "ci-smoke"})
        self.assertEqual(status, 200)
        device_id = start["device_id"]

        status, pending, _ = self.req("POST", "/api/v1/auth/device/token", {"device_id": device_id, "code_verifier": verifier})
        self.assertEqual(status, 428)
        self.assertEqual(pending["error"], "authorization_pending")

        status, approved, _ = self.req("POST", "/api/v1/auth/device/approve", {"device_id": device_id}, headers={"Cookie": cookie})
        self.assertEqual(status, 200)
        self.assertTrue(approved["approved"])

        status, tokens, _ = self.req("POST", "/api/v1/auth/device/token", {"device_id": device_id, "code_verifier": verifier})
        self.assertEqual(status, 200)
        self.assertTrue(tokens["access_token"])
        self.assertTrue(tokens["refresh_token"])

    def test_support_ticket_staff_and_admin_roles(self):
        owner, owner_cookie = self.register_user("kvi_nella")
        support, support_cookie = self.register_user("SupportGuy")
        player, player_cookie = self.register_user("TicketPlayer")
        self.assertEqual(owner["profile"]["role"], "admin")
        self.assertEqual(owner["profile"]["role_label"], "Администратор")

        support_id = support["profile"]["id"]
        status, promoted, _ = self.req(
            "PUT",
            f"/api/v1/admin/users/{support_id}/role",
            {"role": "support"},
            headers={"Cookie": owner_cookie},
        )
        self.assertEqual(status, 200)
        self.assertEqual(promoted["role"], "support")
        self.assertEqual(promoted["role_label"], "Тех. Поддержка")

        status, forbidden, _ = self.req(
            "PUT",
            f"/api/v1/admin/users/{player['profile']['id']}/role",
            {"role": "admin"},
            headers={"Cookie": support_cookie},
        )
        self.assertEqual(status, 403)
        self.assertEqual(forbidden["error"], "admin_required")

        status, created, _ = self.req(
            "POST",
            "/api/v1/support/tickets",
            {"subject": "Launcher error", "category": "technical", "body": "Launcher exits after pressing Play."},
            headers={"Cookie": player_cookie},
        )
        self.assertEqual(status, 201)
        ticket_id = created["id"]
        self.assertEqual(created["status"], "open")

        status, queue, _ = self.req("GET", "/api/v1/staff/tickets?status=open", headers={"Cookie": support_cookie})
        self.assertEqual(status, 200)
        self.assertEqual(queue["tickets"][0]["id"], ticket_id)

        status, answered, _ = self.req(
            "POST",
            f"/api/v1/support/tickets/{ticket_id}/messages",
            {"body": "Проверяем ваш лог. Ответим здесь."},
            headers={"Cookie": support_cookie},
        )
        self.assertEqual(status, 201)
        self.assertEqual(answered["status"], "pending")
        self.assertEqual(answered["messages"][-1]["author"]["role"], "support")
        self.assertEqual(answered["messages"][-1]["author"]["role_label"], "Тех. Поддержка")

        status, own, _ = self.req("GET", f"/api/v1/support/tickets/{ticket_id}", headers={"Cookie": player_cookie})
        self.assertEqual(status, 200)
        self.assertEqual(own["messages"][-1]["author"]["role"], "support")

        status, closed, _ = self.req(
            "PUT",
            f"/api/v1/staff/tickets/{ticket_id}/status",
            {"status": "closed"},
            headers={"Cookie": support_cookie},
        )
        self.assertEqual(status, 200)
        self.assertEqual(closed["status"], "closed")

        status, locked, _ = self.req(
            "PUT",
            f"/api/v1/admin/users/{owner['profile']['id']}/role",
            {"role": "user"},
            headers={"Cookie": owner_cookie},
        )
        self.assertEqual(status, 409)
        self.assertEqual(locked["error"], "owner_role_locked")


if __name__ == "__main__":
    unittest.main()
