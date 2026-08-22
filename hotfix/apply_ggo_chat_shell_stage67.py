#!/usr/bin/env python3
from pathlib import Path
import shutil

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
JAVA = ROOT / "client-ui/src/main/java/arena/client/shell"
SOURCES = [Path("hotfix/GgoChatScreen.java"), Path("hotfix/GgoChatShellHook.java")]

if not JAVA.is_dir():
    raise SystemExit("client-ui source tree is missing")
for source in SOURCES:
    if not source.is_file():
        raise SystemExit(f"missing {source}")
    shutil.copy2(source, JAVA / source.name)

screen = (JAVA / "GgoChatScreen.java").read_text(encoding="utf-8")
hook = (JAVA / "GgoChatShellHook.java").read_text(encoding="utf-8")
for required in [
    "extends ChatScreen",
    "super.render(g, mouseX, mouseY, partialTick)",
    "this.input.render",
    "GGO COMMS",
    "ENTER SEND   ESC CLOSE",
]:
    if required not in screen:
        raise SystemExit(f"stage67 chat screen missing: {required}")
for required in [
    "ScreenEvent.Opening",
    "screen instanceof ChatScreen",
    "screen instanceof GgoChatScreen",
    "event.setNewScreen(new GgoChatScreen",
    "ChatScreen.class.getDeclaredFields()",
]:
    if required not in hook:
        raise SystemExit(f"stage67 chat hook missing: {required}")
for forbidden in ["sendChat(", "sendCommand("]:
    if forbidden in screen or forbidden in hook:
        raise SystemExit(f"stage67 must preserve ChatScreen transport instead of reimplementing it: {forbidden}")

print("Applied GGO Stage 67 chat shell")
print(" - T/slash chat opens first-party GGO chrome")
print(" - ChatScreen remains the transport/history/signing engine")
print(" - chat messages are not hidden or replaced")
