#!/usr/bin/env python3
from pathlib import Path
import shutil

ROOT = Path("ga-build/src/main/java/arena/forge")
if not ROOT.is_dir():
    raise SystemExit("GGO Core arena/forge source tree missing")

for name in ["GgoAntiCheatEvidence.java", "GgoMovementAntiCheat.java"]:
    src = Path("hotfix") / name
    dst = ROOT / name
    if not src.is_file():
        raise SystemExit(f"missing anti-cheat source: {src}")
    shutil.copy2(src, dst)

movement = (ROOT / "GgoMovementAntiCheat.java").read_text(encoding="utf-8")
evidence = (ROOT / "GgoAntiCheatEvidence.java").read_text(encoding="utf-8")

for required in [
    "REPORT ONLY",
    "HORIZONTAL_SPEED",
    "VERTICAL_SPEED",
    "TELEPORT_LIKE_MOVE",
    "IMPOSSIBLE_AIR_CHAIN",
    "GgoOfficialAuthState.isAuthenticated(player)",
    "PlayerTickEvent",
]:
    if required not in movement + evidence:
        raise SystemExit(f"Stage 82 anti-cheat requirement missing: {required}")

for forbidden in [
    ".disconnect(",
    "banPlayer",
    "banList",
    "kickPlayer",
    "System.exit",
    "GGO_GAME_TICKET",
    "Authorization",
]:
    if forbidden in movement + evidence:
        raise SystemExit(f"Stage 82 report-only boundary violated: {forbidden}")

print("Applied GGO Stage 82 report-only anti-cheat")
print(" - server-authoritative movement telemetry")
print(" - bounded in-memory evidence scoring")
print(" - authenticated players only")
print(" - zero automatic kick/ban enforcement")
