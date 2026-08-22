#!/usr/bin/env python3
from pathlib import Path
import shutil

ROOT = Path("ga-build/src/main/java/arena/forge")
SRC = Path("hotfix/GgoPreAuthQuarantine.java")
DST = ROOT / "GgoPreAuthQuarantine.java"

if not ROOT.is_dir():
    raise SystemExit("server source tree missing")
if not SRC.is_file():
    raise SystemExit("GgoPreAuthQuarantine.java missing")
shutil.copy2(SRC, DST)

text = DST.read_text(encoding="utf-8")
for required in [
    "GgoOfficialAuthState.required()",
    "!GgoOfficialAuthState.isAuthenticated(player)",
    "PlayerInteractEvent",
    "BlockEvent.BreakEvent",
    "BlockEvent.EntityPlaceEvent",
    "EntityItemPickupEvent",
    "LivingAttackEvent",
    "CommandEvent",
    "player.teleportTo",
    "player.setDeltaMovement(Vec3.ZERO)",
    "MAX_QUARANTINE_TICKS",
    "20 * 15",
    "QUARANTINE_TICKS.merge",
    "secure session verification timed out",
    "GgoOfficialAuthState.verificationFailed(player)",
    "GgoOfficialAuthState.clear(player)",
]:
    if required not in text:
        raise SystemExit(f"stage59 quarantine missing: {required}")

for forbidden in ["System.out", "println", "GGO_GAME_TICKET", "serverKey()"]:
    if forbidden in text:
        raise SystemExit(f"stage59 quarantine must not expose auth secret/logging: {forbidden}")

print("Applied GGO Stage 59 pre-auth quarantine")
print(" - official players are frozen until launcher-ticket verification succeeds")
print(" - pre-auth world interaction, combat, pickup and commands are blocked")
print(" - stalled verification is disconnected after 15 seconds")
print(" - disconnect cleanup clears transient auth/quarantine state")
