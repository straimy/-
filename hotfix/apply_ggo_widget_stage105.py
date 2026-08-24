#!/usr/bin/env python3
from pathlib import Path
import re
import shutil

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
JAVA = ROOT / "client-ui/src/main/java/arena/client/shell"
SOURCE = Path("hotfix/GgoButton.java")
TARGET = JAVA / "GgoButton.java"

if not JAVA.is_dir():
    raise SystemExit("client-ui source tree missing")
if not SOURCE.is_file():
    raise SystemExit("missing hotfix/GgoButton.java")
shutil.copy2(SOURCE, TARGET)

screens = [
    "GgoShellScreen.java",
    "GgoSettingsScreen.java",
    "GgoFrontEndScreen.java",
    "GgoTrainingScreen.java",
    "GgoEntryDisconnectedScreen.java",
]
changed = 0
for name in screens:
    path = JAVA / name
    if not path.is_file():
        continue
    text = path.read_text(encoding="utf-8")
    before = text
    text = text.replace("import net.minecraft.client.gui.components.Button;\n", "")
    text = re.sub(r"\bButton\b", "GgoButton", text)
    if text != before:
        path.write_text(text, encoding="utf-8")
        changed += 1

widget = TARGET.read_text(encoding="utf-8")
for required in [
    "extends AbstractButton",
    "No vanilla button sprite is rendered",
    "0xFFD24452",
    "isHoveredOrFocused()",
    "createNarrationMessage()",
]:
    if required not in widget:
        raise SystemExit(f"Stage105 widget missing: {required}")

shell = JAVA / "GgoShellScreen.java"
if not shell.is_file() or "GgoButton.builder" not in shell.read_text(encoding="utf-8"):
    raise SystemExit("Stage105 GGO shell did not adopt first-party controls")
if "net.minecraft.client.gui.components.Button" in shell.read_text(encoding="utf-8"):
    raise SystemExit("Stage105 shell still imports vanilla Button")

print("Applied GGO Stage105 first-party controls")
print(f" - migrated {changed} GGO screens away from vanilla gray Button rendering")
print(" - dark flat controls + red focus/hover accent now match launcher visual language")
