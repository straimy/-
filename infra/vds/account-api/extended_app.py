import re
import uuid

from fastapi import HTTPException
import app as base

app = base.app
minecraft_id_re = re.compile(r"^[0-9a-fA-F]{32}$")


def normalize_minecraft_id(value: str) -> str:
    compact = value.replace("-", "").strip().lower()
    if not minecraft_id_re.fullmatch(compact):
        raise HTTPException(400, "invalid Minecraft UUID")
    return compact


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
