#!/usr/bin/env python3
from pathlib import Path
import shutil

ROOT=Path("ga-build/src/main/java/arena/forge")
SOURCES=[
    Path("hotfix/GgoUiActionNetwork.java"),
    Path("hotfix/GgoMedicineService.java"),
    Path("hotfix/InventoryUtilityCommands.java"),
]
if not ROOT.is_dir(): raise SystemExit("ga-build source tree is missing")
for source in SOURCES:
    if not source.is_file(): raise SystemExit(f"missing {source}")
    shutil.copy2(source,ROOT/source.name)

network=(ROOT/"GgoUiActionNetwork.java").read_text(encoding="utf-8")
medicine=(ROOT/"GgoMedicineService.java").read_text(encoding="utf-8")
inventory=(ROOT/"InventoryUtilityCommands.java").read_text(encoding="utf-8")
for required in [
    'new ResourceLocation("gunnerarena","ggo_ui_action")',
    'MEDICINE_USE=0',
    'InventoryUtilityCommands.uiSwap',
    'GgoMedicineService.useFromUi',
    'authorizedAndRateLimited',
    'Unknown opcodes are ignored fail-closed',
]:
    if required not in network: raise SystemExit(f"stage65 packet contract missing: {required}")
for text,label in [(medicine,"medicine"),(inventory,"inventory")]:
    if '.requires(s->s.hasPermission(2))' not in text:
        raise SystemExit(f"stage65 {label} debug command is not permission-gated")
for required in ['public static int useFromUi','ArenaPlayerState.ALIVE']:
    if required not in medicine: raise SystemExit(f"stage65 medicine authority missing: {required}")
for required in ['public static int uiAmmo','public static int uiDrop','public static int uiSwap','runtime.auth().isAuthenticated(p)']:
    if required not in inventory: raise SystemExit(f"stage65 inventory authority missing: {required}")
print("Applied GGO Stage 65 UI packet actions")
print(" - first-party inventory and medicine use a bounded C2S opcode channel")
print(" - every packet is re-authorized and rate-limited server-side")
print(" - slash commands remain permission-2 debug fallbacks only")
