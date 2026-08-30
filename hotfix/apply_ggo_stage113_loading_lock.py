#!/usr/bin/env python3
from pathlib import Path

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
SHELL = ROOT / "client-ui/src/main/java/arena/client/shell"
UI = ROOT / "client-ui/src/main/java/arena/client/ui"

surface = (SHELL / "GgoProductionSurfaceFence.java").read_text(encoding="utf-8")
fence = (SHELL / "GgoVanillaRuntimeFence.java").read_text(encoding="utf-8")
hooks = (SHELL / "GgoShellHooks.java").read_text(encoding="utf-8")
entry = (SHELL / "GgoFrontEndScreen.java").read_text(encoding="utf-8")
bridge = (SHELL / "GgoUnifiedSurfaceBridge.java").read_text(encoding="utf-8")
routes = (UI / "ClientUiOpener.java").read_text(encoding="utf-8")

for required in [
    "ScreenEvent.Render.Pre",
    "event.setCanceled(true)",
    "GenericDirtMessageScreen",
    "LevelLoadingScreen",
    "SYNCHRONIZING SESSION",
    "pose().last().pose().identity()",
]:
    if required not in surface:
        raise SystemExit(f"Stage113 transition lock missing: {required}")
if "ScreenEvent.Render.Post" in surface:
    raise SystemExit("Stage113 must suppress vanilla loading before it renders")

for required in [
    "screen instanceof TitleScreen",
    "screen instanceof OptionsScreen",
    "screen instanceof SelectWorldScreen",
    "screen instanceof JoinMultiplayerScreen",
    "screen instanceof PauseScreen",
    "GgoLaunchTicketClient.isOfficialLaunch()",
]:
    if required not in fence:
        raise SystemExit(f"Stage113 vanilla navigation fence missing: {required}")

if "MAIN snapshot only" not in routes:
    raise SystemExit("Stage113 requires passive MAIN route")
if "default -> mc.setScreen(new GgoShellScreen(GgoShellScreen.Page.HOME))" in routes:
    raise SystemExit("Stage113 must not auto-open the GGO Hub after join")
if "GLFW.GLFW_KEY_M" not in hooks or "new GgoShellScreen(GgoShellScreen.Page.HOME)" not in hooks:
    raise SystemExit("Stage113 requires explicit local M -> GGO Hub")
if 'Component.literal("PLAY ONLINE")' not in entry:
    raise SystemExit("Stage113 requires explicit PLAY ONLINE frontend")
for required in [
    "GGO_READY_FILE",
    "Files.writeString",
    '"ready\\n"',
    "GgoLaunchTicketClient.isOfficialLaunch()",
]:
    if required not in bridge:
        raise SystemExit(f"Stage113 unified-surface handshake missing: {required}")

print("Applied GGO Stage113 unified shell/loading lock")
print(" - MAIN route remains passive after server join")
print(" - M explicitly opens the local GGO Hub")
print(" - vanilla title/options/world/server/pause navigation stays fenced")
print(" - dirt/world-loading frames are cancelled before vanilla rendering")
print(" - launcher readiness handshake remains installed")
