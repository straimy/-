#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
JAVA = ROOT / "client-ui/src/main/java/arena/client/shell"
SETTINGS = JAVA / "GgoSettingsScreen.java"
SHELL = JAVA / "GgoShellScreen.java"

for path in (SETTINGS, SHELL):
    if not path.is_file():
        raise SystemExit(f"missing generated client source: {path}")

settings = SETTINGS.read_text(encoding="utf-8")
settings = settings.replace(
    "GGO settings only. Minecraft / Forge technical controls stay hidden in Advanced.",
    "GGO client settings. Advanced runtime options stay hidden by default.",
)
SETTINGS.write_text(settings, encoding="utf-8")

shell = SHELL.read_text(encoding="utf-8")
shell = shell.replace(
    "Minecraft crafting / armor grid / recipe book are not part of GGO.",
    "Only GGO equipment and field storage are available here.",
)
shell = shell.replace(
    "Crafting, recipe book and Minecraft armor inventory are not exposed.",
    "Only GGO equipment and field storage are available here.",
)
SHELL.write_text(shell, encoding="utf-8")

# Audit quoted values only: engine-side Java identifiers are allowed, player-facing brand names are not.
STRING_LITERAL = re.compile(r'"(?:\\.|[^"\\])*"')
UI_CALLS = ("Component.literal(", "drawString(", "drawCenteredString(", "Button.builder(")
ENGINE_BRANDS = ("minecraft", "forge", "mojang")
violations = []
for path in JAVA.glob("*.java"):
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not any(token in line for token in UI_CALLS):
            continue
        for literal in STRING_LITERAL.findall(line):
            value = literal[1:-1].lower()
            if any(word in value for word in ENGINE_BRANDS):
                violations.append(f"{path.name}:{number}:{literal}")

if violations:
    raise SystemExit("player-facing engine brand leak(s):\n" + "\n".join(violations))

for required in [
    "GGO client settings. Advanced runtime options stay hidden by default.",
    "Only GGO equipment and field storage are available here.",
]:
    if required not in SETTINGS.read_text(encoding="utf-8") + SHELL.read_text(encoding="utf-8"):
        raise SystemExit(f"stage68 cleaned copy missing: {required}")

print("Applied GGO Stage 68 player-facing brand leak cleanup")
print(" - removes remaining Minecraft/Forge/Mojang names from GGO UI copy")
print(" - technical package/import/reflection internals remain untouched")
