#!/usr/bin/env python3
from pathlib import Path

TARGET = Path(__file__).with_name("server.py")
text = TARGET.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str):
    global text
    if new in text:
        return
    if old not in text:
        raise SystemExit(f"news-api patch: missing anchor: {label}")
    text = text.replace(old, new, 1)


replace_once(
    'USERNAME_RE = re.compile(r"^[A-Za-z0-9_]{3,16}$")\n',
    'USERNAME_RE = re.compile(r"^[A-Za-z0-9_]{3,16}$")\n'
    'NEWS_ID_RE = re.compile(r"^[a-z0-9][a-z0-9._-]{2,63}$")\n'
    'NEWS_DATE_RE = re.compile(r"^\\d{4}-\\d{2}-\\d{2}$")\n'
    'NEWS_SEED_PATH = Path(os.environ.get("GGO_NEWS_SEED_PATH", str(Path(__file__).resolve().parents[2] / "site/content/api/news.json")))\n',
    "news constants",
)

replace_once(
    '''            CREATE TABLE IF NOT EXISTS support_messages (\n              id TEXT PRIMARY KEY,\n              ticket_id TEXT NOT NULL REFERENCES support_tickets(id) ON DELETE CASCADE,\n              user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,\n              body TEXT NOT NULL,\n              created_at INTEGER NOT NULL\n            );\n''',
    '''            CREATE TABLE IF NOT EXISTS support_messages (\n              id TEXT PRIMARY KEY,\n              ticket_id TEXT NOT NULL REFERENCES support_tickets(id) ON DELETE CASCADE,\n              user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,\n              body TEXT NOT NULL,\n              created_at INTEGER NOT NULL\n            );\n            CREATE TABLE IF NOT EXISTS news_items (\n              id TEXT PRIMARY KEY,\n              date TEXT NOT NULL,\n              title_en TEXT NOT NULL,\n              title_ru TEXT NOT NULL,\n              title_uk TEXT NOT NULL,\n              body_en TEXT NOT NULL,\n              body_ru TEXT NOT NULL,\n              body_uk TEXT NOT NULL,\n              created_at INTEGER NOT NULL,\n              updated_at INTEGER NOT NULL\n            );\n''',
    "news table",
)

replace_once(
    '            CREATE INDEX IF NOT EXISTS idx_support_message_ticket ON support_messages(ticket_id,created_at);\n',
    '            CREATE INDEX IF NOT EXISTS idx_support_message_ticket ON support_messages(ticket_id,created_at);\n'
    '            CREATE INDEX IF NOT EXISTS idx_news_date ON news_items(date DESC,created_at DESC);\n',
    "news index",
)

replace_once(
    '''        for owner in OWNER_USERNAMES:\n            db.execute("UPDATE users SET role='admin' WHERE username_norm=?", (owner,))\n        db.commit()\n''',
    '''        for owner in OWNER_USERNAMES:\n            db.execute("UPDATE users SET role='admin' WHERE username_norm=?", (owner,))\n        if NEWS_SEED_PATH.is_file():\n            try:\n                seed = json.loads(NEWS_SEED_PATH.read_text(encoding="utf-8"))\n                ts = now()\n                for item in seed.get("items", []):\n                    title = item.get("title") or {}\n                    body = item.get("body") or {}\n                    item_id = str(item.get("id", "")).strip().lower()\n                    item_date = str(item.get("date", "")).strip()\n                    if not NEWS_ID_RE.fullmatch(item_id) or not NEWS_DATE_RE.fullmatch(item_date):\n                        continue\n                    values = [str(title.get(lang, "")).strip() for lang in ("en", "ru", "uk")]\n                    values += [str(body.get(lang, "")).strip() for lang in ("en", "ru", "uk")]\n                    if not all(values):\n                        continue\n                    db.execute(\n                        "INSERT OR IGNORE INTO news_items(id,date,title_en,title_ru,title_uk,body_en,body_ru,body_uk,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?)",\n                        (item_id, item_date, *values, ts, ts),\n                    )\n            except Exception as exc:\n                print(f"[ggo-auth] news seed skipped: {exc}", flush=True)\n        db.commit()\n''',
    "seed history",
)

replace_once(
    '''    def require_admin(self, db):\n        user = self.require_user(db)\n        if not user:\n            return None\n        if user["role"] != "admin":\n            self.json(403, {"error": "admin_required"})\n            return None\n        return user\n\n''',
    '''    def require_admin(self, db):\n        user = self.require_user(db)\n        if not user:\n            return None\n        if user["role"] != "admin":\n            self.json(403, {"error": "admin_required"})\n            return None\n        return user\n\n    def require_owner(self, db):\n        user = self.require_admin(db)\n        if not user:\n            return None\n        if user["username_norm"] not in OWNER_USERNAMES:\n            self.json(403, {"error": "owner_required"})\n            return None\n        return user\n\n    def news_payload(self, row):\n        return {\n            "id": row["id"],\n            "date": row["date"],\n            "title": {"en": row["title_en"], "ru": row["title_ru"], "uk": row["title_uk"]},\n            "body": {"en": row["body_en"], "ru": row["body_ru"], "uk": row["body_uk"]},\n        }\n\n''',
    "owner/news helpers",
)

replace_once(
    '''        if path == "/api/v1/health":\n            return self.json(200, {"ok": True, "service": "ggo-auth", "version": 3, "game_tickets": True, "support_tickets": True, "staff_roles": True})\n''',
    '''        if path == "/api/v1/health":\n            return self.json(200, {"ok": True, "service": "ggo-auth", "version": 4, "game_tickets": True, "support_tickets": True, "staff_roles": True, "news": True})\n        if path == "/api/v1/news":\n            with connect() as db:\n                rows = db.execute("SELECT * FROM news_items ORDER BY date DESC,created_at DESC LIMIT 100").fetchall()\n                return self.json(200, {"schemaVersion": 1, "items": [self.news_payload(row) for row in rows]})\n        if path == "/api/v1/admin/news":\n            with connect() as db:\n                if not self.require_owner(db):\n                    return\n                rows = db.execute("SELECT * FROM news_items ORDER BY date DESC,created_at DESC LIMIT 100").fetchall()\n                return self.json(200, {"schemaVersion": 1, "items": [self.news_payload(row) for row in rows]})\n''',
    "GET news routes",
)

replace_once(
    '''        if path == "/api/v1/support/tickets":\n            return self.create_ticket(data)\n''',
    '''        if path == "/api/v1/admin/news":\n            return self.create_news(data)\n        if path == "/api/v1/support/tickets":\n            return self.create_ticket(data)\n''',
    "POST news route",
)

replace_once(
    '''        if path.startswith("/api/v1/admin/users/") and path.endswith("/role"):\n            return self.update_role(path.split("/")[-2], data)\n''',
    '''        if path.startswith("/api/v1/admin/news/"):\n            return self.update_news(path.rsplit("/", 1)[-1], data)\n        if path.startswith("/api/v1/admin/users/") and path.endswith("/role"):\n            return self.update_role(path.split("/")[-2], data)\n''',
    "PUT news route",
)

replace_once(
    '''    def register(self, data):\n''',
    '''    def do_DELETE(self):\n        path = urllib.parse.urlparse(self.path).path\n        if path.startswith("/api/v1/admin/news/"):\n            return self.delete_news(path.rsplit("/", 1)[-1])\n        return self.json(404, {"error": "not_found"})\n\n    def register(self, data):\n''',
    "DELETE route",
)

replace_once(
    '''    def update_role(self, user_id, data):\n''',
    '''    def parse_news(self, data, forced_id=None):\n        item_id = str(forced_id or data.get("id", "")).strip().lower()\n        item_date = str(data.get("date", "")).strip()\n        title = data.get("title") if isinstance(data.get("title"), dict) else {}\n        body = data.get("body") if isinstance(data.get("body"), dict) else {}\n        if not NEWS_ID_RE.fullmatch(item_id):\n            return None, "invalid_news_id"\n        if not NEWS_DATE_RE.fullmatch(item_date):\n            return None, "invalid_news_date"\n        titles = {lang: str(title.get(lang, "")).strip() for lang in ("en", "ru", "uk")}\n        bodies = {lang: str(body.get(lang, "")).strip() for lang in ("en", "ru", "uk")}\n        if any(not value or len(value) > 160 for value in titles.values()):\n            return None, "invalid_news_title"\n        if any(not value or len(value) > 8000 for value in bodies.values()):\n            return None, "invalid_news_body"\n        return (item_id, item_date, titles, bodies), None\n\n    def create_news(self, data):\n        if not self.same_origin_ok():\n            return self.json(403, {"error": "origin_rejected"})\n        parsed, error = self.parse_news(data)\n        if error:\n            return self.json(400, {"error": error})\n        item_id, item_date, title, body = parsed\n        with connect() as db:\n            if not self.require_owner(db):\n                return\n            ts = now()\n            try:\n                db.execute(\n                    "INSERT INTO news_items(id,date,title_en,title_ru,title_uk,body_en,body_ru,body_uk,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?)",\n                    (item_id, item_date, title["en"], title["ru"], title["uk"], body["en"], body["ru"], body["uk"], ts, ts),\n                )\n            except sqlite3.IntegrityError:\n                return self.json(409, {"error": "news_id_exists"})\n            db.commit()\n            row = db.execute("SELECT * FROM news_items WHERE id=?", (item_id,)).fetchone()\n            return self.json(201, self.news_payload(row))\n\n    def update_news(self, item_id, data):\n        if not self.same_origin_ok():\n            return self.json(403, {"error": "origin_rejected"})\n        parsed, error = self.parse_news(data, item_id)\n        if error:\n            return self.json(400, {"error": error})\n        item_id, item_date, title, body = parsed\n        with connect() as db:\n            if not self.require_owner(db):\n                return\n            if not db.execute("SELECT 1 FROM news_items WHERE id=?", (item_id,)).fetchone():\n                return self.json(404, {"error": "news_not_found"})\n            db.execute(\n                "UPDATE news_items SET date=?,title_en=?,title_ru=?,title_uk=?,body_en=?,body_ru=?,body_uk=?,updated_at=? WHERE id=?",\n                (item_date, title["en"], title["ru"], title["uk"], body["en"], body["ru"], body["uk"], now(), item_id),\n            )\n            db.commit()\n            row = db.execute("SELECT * FROM news_items WHERE id=?", (item_id,)).fetchone()\n            return self.json(200, self.news_payload(row))\n\n    def delete_news(self, item_id):\n        if not self.same_origin_ok():\n            return self.json(403, {"error": "origin_rejected"})\n        item_id = str(item_id).strip().lower()\n        if not NEWS_ID_RE.fullmatch(item_id):\n            return self.json(400, {"error": "invalid_news_id"})\n        with connect() as db:\n            if not self.require_owner(db):\n                return\n            cursor = db.execute("DELETE FROM news_items WHERE id=?", (item_id,))\n            if cursor.rowcount == 0:\n                return self.json(404, {"error": "news_not_found"})\n            db.commit()\n            return self.json(200, {"ok": True, "id": item_id})\n\n    def update_role(self, user_id, data):\n''',
    "news CRUD methods",
)

TARGET.write_text(text, encoding="utf-8")
print("GGO News API v1 patch applied")
