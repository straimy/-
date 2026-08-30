import re
import uuid

import asyncpg
import httpx
from fastapi import Depends, HTTPException
from pydantic import BaseModel, Field
import app as base

app = base.app
minecraft_id_re = re.compile(r"^[0-9a-fA-F]{32}$")
MINECRAFT_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile"


class MinecraftLinkBody(BaseModel):
    minecraft_access_token: str = Field(min_length=32, max_length=4096)


def normalize_minecraft_id(value: str) -> str:
    compact = value.replace("-", "").strip().lower()
    if not minecraft_id_re.fullmatch(compact):
        raise HTTPException(400, "invalid Minecraft UUID")
    return compact


async def verify_minecraft_token(token: str) -> tuple[str, str]:
    try:
        async with httpx.AsyncClient(timeout=8.0, follow_redirects=False) as client:
            response = await client.get(
                MINECRAFT_PROFILE_URL,
                headers={"Authorization": f"Bearer {token}", "Accept": "application/json"},
            )
    except httpx.HTTPError:
        raise HTTPException(502, "Minecraft profile service unavailable")

    if response.status_code in {401, 403}:
        raise HTTPException(401, "Minecraft access token is invalid or expired")
    if response.status_code != 200:
        raise HTTPException(502, "Minecraft profile verification failed")

    try:
        payload = response.json()
        subject = normalize_minecraft_id(str(payload["id"]))
        name = str(payload["name"]).strip()
    except (KeyError, TypeError, ValueError):
        raise HTTPException(502, "Minecraft profile response is invalid")
    if not name or len(name) > 16:
        raise HTTPException(502, "Minecraft profile name is invalid")
    return subject, name


@app.put("/me/identities/minecraft")
async def link_minecraft_identity(
    body: MinecraftLinkBody,
    player_id: str = Depends(base.current_player),
) -> dict:
    """Link a Minecraft Java profile only after server-side ownership verification."""
    if base.db is None:
        raise HTTPException(503, "account database unavailable")

    subject, minecraft_name = await verify_minecraft_token(body.minecraft_access_token)
    owner = uuid.UUID(player_id)
    try:
        async with base.db.acquire() as conn:
            async with conn.transaction():
                existing_owner = await conn.fetchval(
                    "SELECT player_id FROM linked_identities WHERE provider='minecraft' AND LOWER(REPLACE(provider_subject,'-',''))=$1 LIMIT 1",
                    subject,
                )
                if existing_owner is not None and existing_owner != owner:
                    raise HTTPException(409, "this Minecraft profile is already linked to another GGO account")

                await conn.execute(
                    "DELETE FROM linked_identities WHERE player_id=$1 AND provider='minecraft'",
                    owner,
                )
                await conn.execute(
                    "INSERT INTO linked_identities(player_id,provider,provider_subject,display_name) VALUES($1,'minecraft',$2,$3)",
                    owner,
                    subject,
                    minecraft_name,
                )
    except asyncpg.UniqueViolationError:
        raise HTTPException(409, "this Minecraft profile is already linked to another GGO account")

    return {
        "linked": True,
        "provider": "minecraft",
        "minecraft_uuid": subject,
        "minecraft_name": minecraft_name,
    }


@app.delete("/me/identities/minecraft")
async def unlink_minecraft_identity(player_id: str = Depends(base.current_player)) -> dict:
    if base.db is None:
        raise HTTPException(503, "account database unavailable")
    await base.db.execute(
        "DELETE FROM linked_identities WHERE player_id=$1 AND provider='minecraft'",
        uuid.UUID(player_id),
    )
    return {"linked": False, "provider": "minecraft"}


@app.get("/minecraft/{minecraft_uuid}/profile")
async def public_profile_by_minecraft_uuid(minecraft_uuid: str) -> dict:
    """Resolve a linked Minecraft identity to public GGO cosmetic metadata only."""
    if base.db is None:
        raise HTTPException(503, "account database unavailable")

    subject = normalize_minecraft_id(minecraft_uuid)
    row = await base.db.fetchrow(
        """
        SELECT p.id,p.display_name,COALESCE(s.source,'default') AS source,s.skin_hash
        FROM linked_identities i
        JOIN players p ON p.id=i.player_id
        LEFT JOIN skin_preferences s ON s.player_id=p.id
        WHERE i.provider='minecraft' AND LOWER(REPLACE(i.provider_subject,'-',''))=$1
        LIMIT 1
        """,
        subject,
    )
    if row is None:
        raise HTTPException(404, "Minecraft identity is not linked to a GGO profile")

    return {
        "player_id": str(row["id"]),
        "display_name": row["display_name"],
        "source": row["source"],
        "skin_hash": row["skin_hash"],
        "skin_url": base.skin_url(row["skin_hash"]),
    }
