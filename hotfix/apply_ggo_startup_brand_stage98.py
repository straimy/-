from pathlib import Path
import shutil

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
MODULE = ROOT / "client-ui"
MIXIN_JAVA = MODULE / "src/main/java/arena/mixin"
SHELL_JAVA = MODULE / "src/main/java/arena/client/shell"
RES = MODULE / "src/main/resources"
BUILD = MODULE / "build.gradle"
HOTFIX = Path("hotfix")
ICON = HOTFIX / "assets/ggo-window-icon.png"

for required in (
    HOTFIX / "GgoForgeLoadingOverlayMixin.java",
    HOTFIX / "GgoWindowIconClient.java",
    HOTFIX / "ggo-startup.mixins.json",
    ICON,
    BUILD,
):
    if not required.exists():
        raise SystemExit(f"Stage98 startup brand: missing {required}")

MIXIN_JAVA.mkdir(parents=True, exist_ok=True)
SHELL_JAVA.mkdir(parents=True, exist_ok=True)
(RES / "assets/ggo").mkdir(parents=True, exist_ok=True)
shutil.copy2(HOTFIX / "GgoForgeLoadingOverlayMixin.java", MIXIN_JAVA / "GgoForgeLoadingOverlayMixin.java")
# A previous Window#setTitle mixin crashed in Forge runtime because the target method
# name is remapped/obfuscated. Keep title branding in the safe GLFW client hook only.
stale_title_mixin = MIXIN_JAVA / "GgoWindowTitleMixin.java"
if stale_title_mixin.exists():
    stale_title_mixin.unlink()
shutil.copy2(HOTFIX / "GgoWindowIconClient.java", SHELL_JAVA / "GgoWindowIconClient.java")
shutil.copy2(HOTFIX / "ggo-startup.mixins.json", RES / "ggo-startup.mixins.json")
shutil.copy2(ICON, RES / "assets/ggo/icon.png")

text = BUILD.read_text(encoding="utf-8")
marker = "// GGO_STAGE98_STARTUP_MIXIN_MANIFEST"
if marker not in text:
    text += '''

// GGO_STAGE98_STARTUP_MIXIN_MANIFEST
// Forge discovers the client-only startup mixin from the built UI JAR manifest.
tasks.named('jar') {
    manifest {
        attributes('MixinConfigs': 'ggo-startup.mixins.json')
    }
}
'''
    BUILD.write_text(text, encoding="utf-8")

mixin = (MIXIN_JAVA / "GgoForgeLoadingOverlayMixin.java").read_text(encoding="utf-8")
window_brand = (SHELL_JAVA / "GgoWindowIconClient.java").read_text(encoding="utf-8")
config = (RES / "ggo-startup.mixins.json").read_text(encoding="utf-8")
build = BUILD.read_text(encoding="utf-8")
packaged_icon = RES / "assets/ggo/icon.png"

checks = {
    "ForgeLoadingOverlay target": "ForgeLoadingOverlay.class" in mixin,
    "non-cancelling TAIL injection": '@At("TAIL")' in mixin and "CallbackInfo ci" in mixin,
    "runtime method": 'method = "m_88315_"' in mixin,
    "GGO brand": '"GUNGLORYONLINE"' in mixin,
    "safe GLFW title installer": "glfwSetWindowTitle" in window_brand and '"GunGloryOnline"' in window_brand,
    "native GLFW icon installer": "glfwSetWindowIcon" in window_brand and '"/assets/ggo/icon.png"' in window_brand,
    "native icon packaged": packaged_icon.is_file() and packaged_icon.stat().st_size > 0,
    "client loading mixin config": '"GgoForgeLoadingOverlayMixin"' in config,
    "unsafe title mixin absent": '"GgoWindowTitleMixin"' not in config and not stale_title_mixin.exists(),
    "jar manifest wiring": "attributes('MixinConfigs': 'ggo-startup.mixins.json')" in build,
}
for label, ok in checks.items():
    if not ok:
        raise SystemExit(f"Stage98 startup brand: failed check: {label}")

print("GGO Stage98 startup branding applied")
for label in checks:
    print(f" - {label}: ok")
