#!/usr/bin/env python3
from pathlib import Path
import shutil

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
JAVA = ROOT / "client-ui/src/main/java/arena/client/shell"
SHELL = JAVA / "GgoShellScreen.java"
SETTINGS = JAVA / "GgoSettingsScreen.java"

for required in [SHELL, JAVA / "GgoLaunchTicketClient.java"]:
    if not required.is_file():
        raise SystemExit(f"Stage107 missing generated source: {required}")

# Always restore the canonical rich settings screen last in the chain. Older Stage6/Stage105
# transforms intentionally produced smaller intermediate settings surfaces and must not win.
for name in ["GgoSettingsScreen.java", "GgoMusicFadeClient.java"]:
    source = Path("hotfix") / name
    if not source.is_file():
        raise SystemExit(f"Stage107 source missing: {source}")
    shutil.copy2(source, JAVA / name)

shell = SHELL.read_text(encoding="utf-8")

# The Stage101 HOME renderer already contains the six informative cards. The old initHome()
# overlay added a second vertical stack of gray Minecraft buttons directly on top of them.
# Keep navigation in the top GGO bar and remove that duplicate widget layer.
shell = shell.replace("            case HOME -> initHome();", "            case HOME -> {}", 1)

# EXIT TO GGO means leave the internal Java engine and return to the Tauri GGO application.
# A one-shot launcher ticket cannot be reused after disconnect, so keeping the Java process alive
# produced the confusing SESSION REQUIRED screen and a stale launcher state. Shut down cleanly;
# the launcher owns process reaping and will restore its PLAY surface.
start = shell.find("    private void exitToGgo() {")
if start < 0:
    raise SystemExit("Stage107 could not locate exitToGgo")
end = shell.find("\n    }\n", start)
if end < 0:
    raise SystemExit("Stage107 could not locate exitToGgo end")
end += len("\n    }\n")
replacement = '''    private void exitToGgo() {
        Minecraft mc = Minecraft.getInstance();
        mc.stop();
    }
'''
shell = shell[:start] + replacement + shell[end:]
SHELL.write_text(shell, encoding="utf-8")

settings = SETTINGS.read_text(encoding="utf-8")
music = (JAVA / "GgoMusicFadeClient.java").read_text(encoding="utf-8")
shell = SHELL.read_text(encoding="utf-8")

checks = {
    "duplicate home buttons removed": "case HOME -> initHome();" not in shell,
    "clean engine exit": "mc.stop();" in shell,
    "old disconnect race absent": "requestReturnToFrontend" not in shell[shell.find("private void exitToGgo"):shell.find("@Override", shell.find("private void exitToGgo"))],
    "settings pages": "enum Page { AUDIO, VIDEO, CONTROLS }" in settings,
    "audio sliders": "MASTER VOLUME" in settings and "MUSIC" in settings,
    "video sliders": "FIELD OF VIEW" in settings and "RENDER DISTANCE" in settings,
    "controls sliders": "MOUSE SENSITIVITY" in settings and "RAW MOUSE INPUT" in settings,
    "gentle music": "FADE_TICKS = 20 * 8" in music and "eased = t * t" in music,
}
for label, ok in checks.items():
    if not ok:
        raise SystemExit(f"Stage107 check failed: {label}")

print("Applied GGO Stage107 polish")
for label in checks:
    print(f" - {label}: ok")
