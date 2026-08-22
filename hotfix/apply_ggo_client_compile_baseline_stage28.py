from pathlib import Path

# Canonical accumulated client baseline repair used by the Stage 27 compile gate.
ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
SHELL = ROOT / "client-ui/src/main/java/arena/client/shell"
UI = ROOT / "client-ui/src/main/java/arena/client/ui"


def require(path: Path) -> str:
    if not path.exists():
        raise SystemExit(f"Stage 28 client baseline: missing {path}")
    return path.read_text(encoding="utf-8")


# Stage 19 used the pre-Stage-10 field name in its full-map squad projection.
screen = SHELL / "GgoShellScreen.java"
text = require(screen)
text = text.replace("0.35 * navigationZoom", "0.35 * mapZoom")
screen.write_text(text, encoding="utf-8")

# Transport has a default method, so it is intentionally not a functional interface.
adapter = SHELL / "GgoRuntimeV1NetworkAdapter.java"
text = require(adapter)
old_transport = "            GgoSquadSyncBridge.installTransport(() -> invoke(requestSnapshot));"
new_transport = '''            GgoSquadSyncBridge.installTransport(new GgoSquadSyncBridge.Transport() {
                @Override public void requestSnapshot() { invoke(requestSnapshot); }
            });'''
if old_transport in text:
    text = text.replace(old_transport, new_transport, 1)
adapter.write_text(text, encoding="utf-8")

# Stage 15 must map the remote snapshot into the exact Stage 13 Member order/types.
squad = SHELL / "GgoSquadOverlayState.java"
text = require(squad)
old_member = '''        provider = mc -> copy.stream().map(m -> new Member(
                m.name(), m.health(), m.maxHealth(), m.downed(), m.ping(), m.voiceActive(), m.leader(), m.sector(), m.activity()
        )).toList();'''
new_member = '''        provider = mc -> copy.stream().map(m -> new Member(
                m.name(), Math.round(m.health()), Math.round(m.maxHealth()), m.ping(), m.leader(), m.downed(), m.voiceActive(), m.activity(), m.sector()
        )).toList();'''
if old_member in text:
    text = text.replace(old_member, new_member, 1)
squad.write_text(text, encoding="utf-8")

# Minecraft 1.20.1 AbstractWidget requires narration implementation.
button = UI / "ArenaButton.java"
text = require(button)
if "NarrationElementOutput" not in text:
    text = text.replace(
        "import net.minecraft.client.gui.components.AbstractButton;\n",
        "import net.minecraft.client.gui.components.AbstractButton;\nimport net.minecraft.client.gui.narration.NarrationElementOutput;\n",
        1,
    )
if "updateWidgetNarration" not in text:
    pos = text.rfind("}\n")
    if pos < 0:
        raise SystemExit("Stage 28 client baseline: ArenaButton class end missing")
    narration = '''    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

'''
    text = text[:pos] + narration + text[pos:]
button.write_text(text, encoding="utf-8")

# Keep navigation behavior intact through a narrow package-private Screen bridge.
abstract_screen = UI / "AbstractArenaScreen.java"
text = require(abstract_screen)
if "addArenaButton(ArenaButton button)" not in text:
    pos = text.rfind("}\n")
    if pos < 0:
        raise SystemExit("Stage 28 client baseline: AbstractArenaScreen class end missing")
    bridge = '''    final void addArenaButton(ArenaButton button) {
        addRenderableWidget(button);
    }

'''
    text = text[:pos] + bridge + text[pos:]
abstract_screen.write_text(text, encoding="utf-8")

navigation = UI / "ArenaNavigation.java"
text = require(navigation)
text = text.replace("screen.addRenderableWidget(button);", "screen.addArenaButton(button);")
navigation.write_text(text, encoding="utf-8")

# Fail early if any known compile defect survived the patch.
checks = {
    screen: "navigationZoom",
    adapter: "installTransport(() -> invoke(requestSnapshot))",
    squad: "m.maxHealth(), m.downed(), m.ping()",
    navigation: "screen.addRenderableWidget(button)",
}
for path, forbidden in checks.items():
    if forbidden in path.read_text(encoding="utf-8"):
        raise SystemExit(f"Stage 28 client baseline: unresolved anchor {forbidden} in {path}")
if "updateWidgetNarration" not in button.read_text(encoding="utf-8"):
    raise SystemExit("Stage 28 client baseline: narration method missing")

print("GGO Stage 28 client compile baseline applied")
print(" - Stage 19 full-map zoom field aligned with Stage 10")
print(" - squad transport uses an explicit implementation")
print(" - remote squad fields map to exact member order/types")
print(" - legacy button narration implemented")
print(" - legacy navigation uses AbstractArenaScreen widget bridge")
