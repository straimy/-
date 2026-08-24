#!/usr/bin/env python3
from pathlib import Path
import shutil

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
JAVA = ROOT / "client-ui/src/main/java/arena/client/shell"
SHELL = JAVA / "GgoShellScreen.java"

if not SHELL.is_file():
    raise SystemExit("Stage104 requires generated GgoShellScreen.java (apply Stage101 hub first)")

for name in ["GgoVanillaRuntimeFence.java", "GgoEntryExperience.java"]:
    source = Path("hotfix") / name
    if not source.is_file():
        raise SystemExit(f"missing {source}")
    shutil.copy2(source, JAVA / name)

shell = SHELL.read_text(encoding="utf-8")
old = '''    private void exitToGgo() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            mc.getConnection().getConnection().disconnect(Component.literal("Return to GunGloryOnline"));
        }
        mc.execute(() -> mc.setScreen(new GgoFrontEndScreen()));
    }
'''
new = '''    private void exitToGgo() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            mc.setScreen(new GgoFrontEndScreen());
            return;
        }
        // A requested return is not an error. Let the normal disconnect lifecycle finish once,
        // then GgoEntryExperience swaps its terminal screen to the GGO frontend. Never race two
        // competing setScreen() calls against the network teardown.
        GgoEntryExperience.requestReturnToFrontend();
        mc.getConnection().getConnection().disconnect(Component.literal("Return to GunGloryOnline"));
    }
'''
if old not in shell:
    if "GgoEntryExperience.requestReturnToFrontend();" not in shell:
        raise SystemExit("Stage104 could not locate legacy exitToGgo block")
else:
    shell = shell.replace(old, new, 1)
    SHELL.write_text(shell, encoding="utf-8")

fence = (JAVA / "GgoVanillaRuntimeFence.java").read_text(encoding="utf-8")
entry = (JAVA / "GgoEntryExperience.java").read_text(encoding="utf-8")
shell = SHELL.read_text(encoding="utf-8")

for required in [
    "screen instanceof TitleScreen",
    "screen instanceof OptionsScreen",
    "screen instanceof SelectWorldScreen",
    "screen instanceof JoinMultiplayerScreen",
    "screen instanceof PauseScreen",
    "GgoLaunchTicketClient.isOfficialLaunch()",
]:
    if required not in fence:
        raise SystemExit(f"Stage104 vanilla fence missing: {required}")

for required in [
    "RETURNING_TO_FRONTEND",
    "requestReturnToFrontend()",
    "RETURNING_TO_FRONTEND.getAndSet(false)",
]:
    if required not in entry:
        raise SystemExit(f"Stage104 return coordinator missing: {required}")

if "GgoEntryExperience.requestReturnToFrontend();" not in shell:
    raise SystemExit("Stage104 shell is not wired to the return coordinator")
if "mc.execute(() -> mc.setScreen(new GgoFrontEndScreen()))" in shell:
    raise SystemExit("Stage104 still contains the disconnect/setScreen race")

print("Applied GGO Stage104 shell lock")
print(" - official launcher sessions cannot open vanilla title/options/world/server navigation")
print(" - Esc/pause remains GGO-owned")
print(" - intentional EXIT TO GGO uses one race-free disconnect lifecycle")
