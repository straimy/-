import base64
import hashlib
import os
import re
import secrets
import uuid
from datetime import datetime, timedelta, timezone
from typing import Literal

import asyncpg
import jwt
from argon2 import PasswordHasher
from argon2.exceptions import VerifyMismatchError
from fastapi import Depends, FastAPI, Header, HTTPException
from pydantic import BaseModel, EmailStr, Field
from redis.asyncio import Redis

app = FastAPI(title="GunGloryOnline Account API", version="0.1.0", docs_url=None, redoc_url=None)
ph = PasswordHasher(time_cost=3, memory_cost=65536, parallelism=2)
name_re = re.compile(r"^[A-Za-z0-9_]{3,16}$")

DB_URL = os.getenv("DATABASE_URL", "postgresql://ggo:ggo@postgres:5432/ggo")
REDIS_URL = os.getenv("REDIS_URL", "redis://redis:6379/0")
JWT_SECRET = os.getenv("GGO_JWT_SECRET", "")
JWT_ISSUER = os.getenv("GGO_JWT_ISSUER", "ggo.kvicloud.ru")
ACCESS_MINUTES = 15
REFRESH_DAYS = 30
DEVICE_TTL = 600

db: asyncpg.Pool | None = None
redis: Redis | None = None

SCHEMA = """
CREATE TABLE IF NOT EXISTS players (
    id UUID PRIMARY KEY,
    display_name VARCHAR(16) NOT NULL,
    is_guest BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS credentials (
    player_id UUID PRIMARY KEY REFERENCES players(id) ON DELETE CASCADE,
    email TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS linked_identities (
    id BIGSERIAL PRIMARY KEY,
    player_id UUID NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    provider TEXT NOT NULL,
    provider_subject TEXT NOT NULL,
    display_name TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(provider, provider_subject)
);
CREATE TABLE IF NOT EXISTS skin_preferences (
    player_id UUID PRIMARY KEY REFERENCES players(id) ON DELETE CASCADE,
    source TEXT NOT NULL DEFAULT 'default',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS refresh_sessions (
    id UUID PRIMARY KEY,
    player_id UUID NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    token_hash TEXT NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS refresh_sessions_player_idx ON refresh_sessions(player_id);
"""

class RegisterBody(BaseModel):
    email: EmailStr
    password: str = Field(min_length=10, max_length=128)
    display_name: str

class LoginBody(BaseModel):
    email: EmailStr
    password: str = Field(min_length=1, max_length=128)

class DeviceStartBody(BaseModel):
    code_challenge: str = Field(min_length=43, max_length=128)
    installation_id: str = Field(min_length=8, max_length=128)

class DeviceApproveBody(BaseModel):
    user_code: str = Field(min_length=6, max_length=16)

class DeviceTokenBody(BaseModel):
    device_id: str
    code_verifier: str = Field(min_length=43, max_length=128)

class RefreshBody(BaseModel):
    refresh_token: str = Field(min_length=32, max_length=256)

class SkinBody(BaseModel):
    source: Literal["ggo", "microsoft", "default"]


def now() -> datetime:
    return datetime.now(timezone.utc)


def sha256_text(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def pkce_s256(verifier: str) -> str:
    raw = hashlib.sha256(verifier.encode("ascii")).digest()
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode("ascii")


def validate_name(value: str) -> str:
    value = value.strip()
    if not name_re.fullmatch(value):
        raise HTTPException(400, "display_name must be 3-16 characters: A-Z, a-z, 0-9 or underscore")
    return value


def access_token(player_id: str) -> str:
    stamp = now()
    return jwt.encode({
        "sub": player_id,
        "typ": "access",
        "iss": JWT_ISSUER,
        "iat": int(stamp.timestamp()),
        "exp": int((stamp + timedelta(minutes=ACCESS_MINUTES)).timestamp()),
    }, JWT_SECRET, algorithm="HS256")


async def issue_session(player_id: str) -> dict:
    assert db is not None
    opaque = secrets.token_urlsafe(48)
    session_id = uuid.uuid4()
    expires = now() + timedelta(days=REFRESH_DAYS)
    await db.execute(
        "INSERT INTO refresh_sessions(id,player_id,token_hash,expires_at) VALUES($1,$2,$3,$4)",
        session_id, uuid.UUID(player_id), sha256_text(opaque), expires,
    )
    return {
        "access_token": access_token(player_id),
        "refresh_token": opaque,
        "token_type": "Bearer",
        "expires_in": ACCESS_MINUTES * 60,
    }


async def current_player(authorization: str | None = Header(default=None)) -> str:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(401, "missing bearer token")
    token = authorization[7:]
    try:
        payload = jwt.decode(token, JWT_SECRET, algorithms=["HS256"], issuer=JWT_ISSUER)
    except jwt.PyJWTError:
        raise HTTPException(401, "invalid or expired token")
    if payload.get("typ") != "access" or not payload.get("sub"):
        raise HTTPException(401, "invalid token type")
    return str(payload["sub"])


@app.on_event("startup")
async def startup() -> None:
    global db, redis
    if len(JWT_SECRET) < 32:
        raise RuntimeError("GGO_JWT_SECRET must contain at least 32 characters")
    db = await asyncpg.create_pool(DB_URL, min_size=1, max_size=6)
    redis = Redis.from_url(REDIS_URL, decode_responses=True)
    async with db.acquire() as conn:
        await conn.execute(SCHEMA)
    await redis.ping()


@app.on_event("shutdown")
async def shutdown() -> None:
    if redis is not None:
        await redis.aclose()
    if db is not None:
        await db.close()


@app.get("/health")
async def health() -> dict:
    assert db is not None and redis is not None
    await db.fetchval("SELECT 1")
    await redis.ping()
    return {"status": "ok", "service": "ggo-account-api"}


@app.post("/auth/register")
async def register(body: RegisterBody) -> dict:
    assert db is not None
    display_name = validate_name(body.display_name)
    player_id = uuid.uuid4()
    password_hash = ph.hash(body.password)
    try:
        async with db.acquire() as conn:
            async with conn.transaction():
                await conn.execute("INSERT INTO players(id,display_name) VALUES($1,$2)", player_id, display_name)
                await conn.execute("INSERT INTO credentials(player_id,email,password_hash) VALUES($1,$2,$3)", player_id, str(body.email).lower(), password_hash)
                await conn.execute("INSERT INTO skin_preferences(player_id,source) VALUES($1,'default')", player_id)
    except asyncpg.UniqueViolationError:
        raise HTTPException(409, "email is already registered")
    session = await issue_session(str(player_id))
    return {**session, "player": {"id": str(player_id), "display_name": display_name}}


@app.post("/auth/login")
async def login(body: LoginBody) -> dict:
    assert db is not None
    row = await db.fetchrow("SELECT c.player_id,c.password_hash,p.display_name FROM credentials c JOIN players p ON p.id=c.player_id WHERE c.email=$1", str(body.email).lower())
    if row is None:
        raise HTTPException(401, "invalid credentials")
    try:
        ph.verify(row["password_hash"], body.password)
    except VerifyMismatchError:
        raise HTTPException(401, "invalid credentials")
    if ph.check_needs_rehash(row["password_hash"]):
        await db.execute("UPDATE credentials SET password_hash=$1 WHERE player_id=$2", ph.hash(body.password), row["player_id"])
    session = await issue_session(str(row["player_id"]))
    return {**session, "player": {"id": str(row["player_id"]), "display_name": row["display_name"]}}


@app.post("/auth/device/start")
async def device_start(body: DeviceStartBody) -> dict:
    assert redis is not None
    device_id = secrets.token_urlsafe(32)
    user_code = "-".join([secrets.token_hex(2).upper(), secrets.token_hex(2).upper()])
    key = f"ggo:device:{device_id}"
    await redis.hset(key, mapping={
        "status": "pending",
        "user_code": user_code,
        "challenge": body.code_challenge,
        "installation_id": body.installation_id,
    })
    await redis.expire(key, DEVICE_TTL)
    await redis.setex(f"ggo:usercode:{user_code}", DEVICE_TTL, device_id)
    return {
        "device_id": device_id,
        "user_code": user_code,
        "verification_uri": f"https://ggo.kvicloud.ru/activate?code={user_code}",
        "expires_in": DEVICE_TTL,
        "interval": 3,
    }


@app.post("/auth/device/approve")
async def device_approve(body: DeviceApproveBody, player_id: str = Depends(current_player)) -> dict:
    assert redis is not None
    user_code = body.user_code.strip().upper()
    device_id = await redis.get(f"ggo:usercode:{user_code}")
    if not device_id:
        raise HTTPException(404, "device code not found or expired")
    key = f"ggo:device:{device_id}"
    if not await redis.exists(key):
        raise HTTPException(404, "device session expired")
    await redis.hset(key, mapping={"status": "approved", "player_id": player_id})
    return {"approved": True}


@app.post("/auth/device/token")
async def device_token(body: DeviceTokenBody) -> dict:
    assert redis is not None
    key = f"ggo:device:{body.device_id}"
    data = await redis.hgetall(key)
    if not data:
        raise HTTPException(404, "device session expired")
    if not secrets.compare_digest(pkce_s256(body.code_verifier), data.get("challenge", "")):
        raise HTTPException(401, "invalid PKCE verifier")
    if data.get("status") != "approved":
        raise HTTPException(428, "authorization pending")
    player_id = data.get("player_id")
    if not player_id:
        raise HTTPException(409, "approved session has no player")
    await redis.delete(key)
    await redis.delete(f"ggo:usercode:{data.get('user_code','')}")
    return await issue_session(player_id)


@app.post("/auth/refresh")
async def refresh(body: RefreshBody) -> dict:
    assert db is not None
    token_hash = sha256_text(body.refresh_token)
    row = await db.fetchrow("SELECT id,player_id,expires_at,revoked_at FROM refresh_sessions WHERE token_hash=$1", token_hash)
    if row is None or row["revoked_at"] is not None or row["expires_at"] <= now():
        raise HTTPException(401, "invalid refresh token")
    await db.execute("UPDATE refresh_sessions SET revoked_at=NOW() WHERE id=$1", row["id"])
    return await issue_session(str(row["player_id"]))


@app.post("/auth/logout")
async def logout(body: RefreshBody) -> dict:
    assert db is not None
    await db.execute("UPDATE refresh_sessions SET revoked_at=NOW() WHERE token_hash=$1 AND revoked_at IS NULL", sha256_text(body.refresh_token))
    return {"ok": True}


@app.get("/me")
async def me(player_id: str = Depends(current_player)) -> dict:
    assert db is not None
    row = await db.fetchrow("SELECT p.id,p.display_name,p.created_at,COALESCE(s.source,'default') AS skin_source FROM players p LEFT JOIN skin_preferences s ON s.player_id=p.id WHERE p.id=$1", uuid.UUID(player_id))
    if row is None:
        raise HTTPException(404, "player not found")
    identities = await db.fetch("SELECT provider,provider_subject,display_name FROM linked_identities WHERE player_id=$1 ORDER BY id", uuid.UUID(player_id))
    return {
        "id": str(row["id"]),
        "display_name": row["display_name"],
        "skin_source": row["skin_source"],
        "created_at": row["created_at"].isoformat(),
        "identities": [dict(item) for item in identities],
    }


@app.get("/me/skin")
async def get_skin(player_id: str = Depends(current_player)) -> dict:
    assert db is not None
    source = await db.fetchval("SELECT source FROM skin_preferences WHERE player_id=$1", uuid.UUID(player_id))
    return {"source": source or "default"}


@app.put("/me/skin/source")
async def set_skin(body: SkinBody, player_id: str = Depends(current_player)) -> dict:
    assert db is not None
    await db.execute("INSERT INTO skin_preferences(player_id,source,updated_at) VALUES($1,$2,NOW()) ON CONFLICT(player_id) DO UPDATE SET source=EXCLUDED.source,updated_at=NOW()", uuid.UUID(player_id), body.source)
    return {"source": body.source}
