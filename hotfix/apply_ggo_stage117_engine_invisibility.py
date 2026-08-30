#!/usr/bin/env python3
from pathlib import Path

ROOT = Path("ga-build/client-ui/src/main/java/arena/client/shell")
FENCE = ROOT / "GgoVanillaRuntimeFence.java"
BRIDGE = ROOT / "GgoUnifiedSurfaceBridge.java"

if not FENCE.is_file():
    raise SystemExit("Stage117 requires generated GgoVanillaRuntimeFence.java")
if not BRIDGE.is_file():
    raise SystemExit("Stage117 requires generated GgoUnifiedSurfaceBridge.java")

fence = FENCE.read_text(encoding="utf-8")
bridge = BRIDGE.read_text(encoding="utf-8")

for required in [
    'className.startsWith("net.minecraft.client.gui.screens.options.")',
    'className.equals("net.minecraft.client.gui.screens.PackSelectionScreen")',
    'className.startsWith("net.minecraft.client.gui.screens.telemetry.")',
    'className.startsWith("net.minecraft.client.gui.screens.worldselection.")',
    'className.startsWith("net.minecraft.client.gui.screens.multiplayer.")',
    'className.startsWith("net.minecraft.client.gui.screens.realms.")',
    'className.equals("net.minecraftforge.client.gui.ModListScreen")',
    'screen instanceof CreativeModeInventoryScreen',
    'GgoLaunchTicketClient.isOfficialLaunch()',
]:
    if required not in fence:
        raise SystemExit(f"Stage117 engine firewall missing: {required}")

for required in [
    'System.getenv("GGO_READY_FILE")',
    'GgoLaunchTicketClient.isOfficialLaunch()',
    'Files.writeString(',
    'stableTicks < 8',
]:
    if required not in bridge:
        raise SystemExit(f"Stage117 unified-surface bridge missing: {required}")

print("Applied GGO Stage117 engine invisibility contract")
print(" - vanilla/Forge navigation families are unreachable in official GGO launches")
print(" - engine readiness remains launcher-coordinated")
print(" - OP Creative Inventory remains available only as the admin-build exception")
