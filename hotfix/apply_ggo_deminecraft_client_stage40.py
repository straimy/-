#!/usr/bin/env python3
from pathlib import Path
import shutil

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
JAVA = ROOT / "client-ui/src/main/java/arena/client/shell"
SOURCES = [
    Path("hotfix/GgoRespawnScreen.java"),
    Path("hotfix/GgoVanillaRuntimeFence.java"),
]

if not JAVA.is_dir():
    raise SystemExit("client-ui source tree is missing")
for source in SOURCES:
    if not source.is_file():
        raise SystemExit(f"missing {source}")
    shutil.copy2(source, JAVA / source.name)

fence = (JAVA / "GgoVanillaRuntimeFence.java").read_text(encoding="utf-8")
respawn = (JAVA / "GgoRespawnScreen.java").read_text(encoding="utf-8")
for required in ["DeathScreen", "AdvancementsScreen", "AbstractContainerScreen", "CreativeModeInventoryScreen"]:
    if required not in fence:
        raise SystemExit(f"stage40 client fence missing {required}")
for required in ["RESPAWN", "mc.player.respawn()", "shouldCloseOnEsc()"]:
    if required not in respawn:
        raise SystemExit(f"stage40 respawn screen missing {required}")

hooks = JAVA / "GgoShellHooks.java"
if not hooks.is_file() or "VanillaGuiOverlay.PLAYER_LIST" not in hooks.read_text(encoding="utf-8"):
    raise SystemExit("stage40 expected GGO TAB replacement is missing")

hud = JAVA / "GgoCombatHud.java"
if not hud.is_file():
    raise SystemExit("stage40 expected GGO HUD is missing")
hud_text = hud.read_text(encoding="utf-8")
for overlay in ["HOTBAR", "PLAYER_HEALTH", "ARMOR_LEVEL", "FOOD_LEVEL", "EXPERIENCE_BAR", "AIR_LEVEL"]:
    if f"VanillaGuiOverlay.{overlay}.id()" not in hud_text:
        raise SystemExit(f"stage40 vanilla HUD suppression missing {overlay}")

print("Applied GGO Stage 40 de-Minecraft client fence")
print(" - vanilla death screen -> GGO respawn")
print(" - vanilla advancements -> Activities")
print(" - vanilla container/crafting screens fenced for normal players")
print(" - creative inventory remains available for admin builders")
print(" - existing vanilla HUD and TAB suppression verified")
