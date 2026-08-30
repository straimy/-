#!/usr/bin/env python3
from pathlib import Path
import runpy

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
SHELL = ROOT / "client-ui/src/main/java/arena/client/shell"

# Stage112 rewrites the canonical frontend after Stage105. Re-run the first-party widget migration
# as the final visual pass so no later menu contract can silently restore vanilla gray buttons.
runpy.run_path("hotfix/apply_ggo_widget_stage105.py", run_name="__main__")

screens = [
    "GgoShellScreen.java",
    "GgoSettingsScreen.java",
    "GgoFrontEndScreen.java",
    "GgoTrainingScreen.java",
    "GgoEntryDisconnectedScreen.java",
]
for name in screens:
    path = SHELL / name
    if not path.is_file():
        raise SystemExit(f"Stage114 missing GGO screen: {name}")
    text = path.read_text(encoding="utf-8")
    if "net.minecraft.client.gui.components.Button" in text:
        raise SystemExit(f"Stage114 vanilla button import leaked into {name}")
    if name != "GgoTrainingScreen.java" and "GgoButton" not in text:
        raise SystemExit(f"Stage114 first-party control missing from {name}")

frontend = (SHELL / "GgoFrontEndScreen.java").read_text(encoding="utf-8")
fence = (SHELL / "GgoVanillaRuntimeFence.java").read_text(encoding="utf-8")
shell = (SHELL / "GgoShellScreen.java").read_text(encoding="utf-8")
button = (SHELL / "GgoButton.java").read_text(encoding="utf-8")

checks = {
    "explicit online entry": 'Component.literal("PLAY ONLINE")' in frontend,
    "frontend uses GgoButton": "GgoButton.builder" in frontend,
    "hub uses GgoButton": "GgoButton.builder" in shell,
    "flat first-party button": "No vanilla button sprite is rendered" in button,
    "vanilla options fenced": "screen instanceof OptionsScreen" in fence,
    "vanilla title fenced": "screen instanceof TitleScreen" in fence,
    "vanilla worlds fenced": "screen instanceof SelectWorldScreen" in fence,
    "vanilla server browser fenced": "screen instanceof JoinMultiplayerScreen" in fence,
    "vanilla pause fenced": "screen instanceof PauseScreen" in fence,
    "engine exit returns to launcher": "Minecraft.getInstance().stop();" in shell,
}
for label, ok in checks.items():
    if not ok:
        raise SystemExit(f"Stage114 visual contract failed: {label}")

print("Applied GGO Stage114 visual contract")
print(" - first-party dark/red GGO controls are the final pass after all menu rewrites")
print(" - no vanilla gray Button rendering remains on primary GGO screens")
print(" - Title/Options/World/Server/Pause navigation remains fenced in official launcher sessions")
print(" - EXIT TO GGO terminates only the engine and returns control to the supervising launcher")
