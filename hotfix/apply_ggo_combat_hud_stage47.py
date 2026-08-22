#!/usr/bin/env python3
from pathlib import Path
import shutil

ROOT=Path("ga-build") if Path("ga-build").exists() else Path(".")
JAVA=ROOT/"client-ui/src/main/java/arena/client/shell"
for name in ["GgoCombatHud.java","GgoKeyMappings.java"]:
    src=Path("hotfix")/name
    dst=JAVA/name
    if not JAVA.is_dir(): raise SystemExit("client-ui source tree is missing")
    if not src.is_file(): raise SystemExit(f"{name} is missing")
    shutil.copy2(src,dst)
hud=(JAVA/"GgoCombatHud.java").read_text(encoding="utf-8")
keys=(JAVA/"GgoKeyMappings.java").read_text(encoding="utf-8")
for required in ["GgoKeyMappings.MEDICAL_WHEEL.isDown()","ggomed use","medicineSelection","for(int i=0;i<3;i++)","VanillaGuiOverlay.HOTBAR","GgoWeaponTelemetry.current","renderKillFeed"]:
    if required not in hud: raise SystemExit(f"stage47 HUD behavior missing: {required}")
for required in ["RegisterKeyMappingsEvent","GLFW_KEY_H","key.gungloryonline.medical_wheel"]:
    if required not in keys: raise SystemExit(f"stage47 key mapping behavior missing: {required}")
print("Applied GGO Stage 47 combat HUD")
print(" - vanilla hotbar remains hidden")
print(" - three compact combat slots replace Minecraft hotbar")
print(" - weapon magazine/reserve/fire-mode stays on authoritative telemetry")
print(" - medical wheel is rebindable and defaults to H")
