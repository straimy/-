#!/usr/bin/env python3
from pathlib import Path
import shutil

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
JAVA = ROOT / "client-ui/src/main/java/arena/client/shell"
SHELL = JAVA / "GgoShellScreen.java"
FENCE = JAVA / "GgoProductionSurfaceFence.java"
TOGGLE = JAVA / "GgoToggleHotkeys.java"
OPENER = ROOT / "client-ui/src/main/java/arena/client/ui/ClientUiOpener.java"

for required in [SHELL, FENCE, OPENER]:
    if not required.is_file():
        raise SystemExit(f"Stage115 missing generated source: {required}")

source = Path("hotfix/GgoToggleHotkeys.java")
if not source.is_file():
    raise SystemExit("Stage115 missing canonical GgoToggleHotkeys.java")
shutil.copy2(source, TOGGLE)

shell = SHELL.read_text(encoding="utf-8")
# Expose only the current first-party page to the toggle listener. Keep the field itself private.
if "public Page ggoPage()" not in shell:
    marker = "    private static String titleFor(Page page) {"
    if marker not in shell:
        raise SystemExit("Stage115 could not locate GgoShellScreen titleFor marker")
    accessor = '''    public Page ggoPage() {\n        return page;\n    }\n\n'''
    shell = shell.replace(marker, accessor + marker, 1)
    SHELL.write_text(shell, encoding="utf-8")

# Stage66 copies the canonical fence before this stage. Restore the latest canonical copy here so
# Stage115 cannot accidentally build an older transition list from a stale generated tree.
canonical_fence = Path("hotfix/GgoProductionSurfaceFence.java")
if not canonical_fence.is_file():
    raise SystemExit("Stage115 missing canonical GgoProductionSurfaceFence.java")
shutil.copy2(canonical_fence, FENCE)

shell = SHELL.read_text(encoding="utf-8")
toggle = TOGGLE.read_text(encoding="utf-8")
fence = FENCE.read_text(encoding="utf-8")
opener = OPENER.read_text(encoding="utf-8")

checks = {
    "page accessor": "public Page ggoPage()" in shell,
    "M toggle close": "key == GLFW.GLFW_KEY_M" in toggle,
    "E inventory toggle close": "GLFW.GLFW_KEY_E && page == GgoShellScreen.Page.INVENTORY" in toggle,
    "N navigation toggle close": "GLFW.GLFW_KEY_N && page == GgoShellScreen.Page.MAP" in toggle,
    "J activities toggle close": "GLFW.GLFW_KEY_J && page == GgoShellScreen.Page.ACTIVITIES" in toggle,
    "G store toggle close": "screen instanceof ShopScreen && key == GLFW.GLFW_KEY_G" in toggle,
    "receiving level pre cover": 'name.endsWith("ReceivingLevelScreen")' in fence,
    "transition pre cancellation": "ScreenEvent.Render.Pre" in fence and "event.setCanceled(true);" in fence,
    "passive MAIN route": "MAIN snapshot only; UI opens exclusively by explicit player action" in opener,
}
for label, ok in checks.items():
    if not ok:
        raise SystemExit(f"Stage115 contract failed: {label}")

print("Applied GGO Stage115 toggle/world-entry polish")
print(" - E/M/N/J close their own first-party surface on the same key")
print(" - G closes the real retained weapon store on G")
print(" - server MAIN remains passive; Hub never auto-opens after join")
print(" - ReceivingLevelScreen is covered in Render.Pre before any vanilla dirt frame")
