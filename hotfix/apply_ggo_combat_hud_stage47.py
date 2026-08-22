#!/usr/bin/env python3
from pathlib import Path
import shutil

ROOT=Path("ga-build") if Path("ga-build").exists() else Path(".")
JAVA=ROOT/"client-ui/src/main/java/arena/client/shell"
SRC=Path("hotfix/GgoCombatHud.java")
DST=JAVA/"GgoCombatHud.java"
if not JAVA.is_dir(): raise SystemExit("client-ui source tree is missing")
if not SRC.is_file(): raise SystemExit("GgoCombatHud.java is missing")
shutil.copy2(SRC,DST)
text=DST.read_text(encoding="utf-8")
for required in ["GLFW_KEY_H","ggomed use","medicineSelection","for(int i=0;i<3;i++)","VanillaGuiOverlay.HOTBAR","GgoWeaponTelemetry.current"]:
    if required not in text: raise SystemExit(f"stage47 HUD behavior missing: {required}")
print("Applied GGO Stage 47 combat HUD")
print(" - vanilla hotbar remains hidden")
print(" - three compact combat slots replace Minecraft hotbar")
print(" - weapon magazine/reserve/fire-mode stays on authoritative telemetry")
print(" - hold H opens radial field-medicine wheel")
