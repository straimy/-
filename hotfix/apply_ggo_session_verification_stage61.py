#!/usr/bin/env python3
from pathlib import Path
import shutil

ROOT = Path("ga-build/client-ui/src/main/java/arena/client/shell")
SRC = Path("hotfix/GgoSessionVerificationOverlay.java")
DST = ROOT / SRC.name

if not ROOT.is_dir():
    raise SystemExit("client-ui source tree missing")
if not SRC.is_file():
    raise SystemExit("GgoSessionVerificationOverlay.java missing")
shutil.copy2(SRC, DST)

client = ROOT / "GgoLaunchTicketClient.java"
if not client.is_file():
    raise SystemExit("GgoLaunchTicketClient.java missing; apply Stage 55 first")
client_text = client.read_text(encoding="utf-8")
overlay_text = DST.read_text(encoding="utf-8")

for required in [
    "verificationPending()",
    "isClientVerificationExpected",
    "isClientVerificationComplete",
    "OFFICIAL_LAUNCH",
]:
    if required not in client_text:
        raise SystemExit(f"stage61 ticket client missing: {required}")
for required in [
    "RenderGuiEvent.Post",
    "GgoLaunchTicketClient.verificationPending()",
    "VERIFYING GGO ACCOUNT",
    "GAMEPLAY UNLOCKS AFTER VERIFIED ENTRY",
]:
    if required not in overlay_text:
        raise SystemExit(f"stage61 overlay missing: {required}")

print("Applied GGO Stage 61 verified-entry overlay")
print(" - official world view stays covered while server verification is pending")
print(" - UI observes only boolean verification state")
