from pathlib import Path

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
JAVA = ROOT / "client-ui/src/main/java/arena/client/shell"
JAVA.mkdir(parents=True, exist_ok=True)

objective = r'''package arena.client.shell;

public final class GgoObjectiveState {
    public record Snapshot(String title, String description, String progress, String activity, boolean available) {
        public static Snapshot empty() {
            return new Snapshot("", "", "", "OPEN WORLD", false);
        }
    }

    @FunctionalInterface
    public interface Provider {
        Snapshot current();
    }

    private static volatile Provider provider = Snapshot::empty;

    private GgoObjectiveState() {}

    public static void installProvider(Provider next) {
        provider = next == null ? Snapshot::empty : next;
    }

    public static Snapshot current() {
        try {
            Snapshot s = provider.current();
            return s == null ? Snapshot.empty() : sanitize(s);
        } catch (RuntimeException ignored) {
            return Snapshot.empty();
        }
    }

    private static Snapshot sanitize(Snapshot s) {
        return new Snapshot(trim(s.title(), 64), trim(s.description(), 120), trim(s.progress(), 32), trim(s.activity(), 32), s.available());
    }

    private static String trim(String value, int max) {
        if (value == null) return "";
        String clean = value.replaceAll("[\\r\\n\\t]", " ").trim();
        return clean.length() > max ? clean.substring(0, max) : clean;
    }
}
'''

sync = r'''package arena.client.shell;

import java.util.List;

public final class GgoSquadSyncBridge {
    public record MemberSnapshot(String name, float health, float maxHealth, boolean downed, int ping, boolean voiceActive, boolean leader, String sector, String activity) {}

    public interface Transport {
        default void requestSnapshot() {}
    }

    private static volatile Transport transport;

    private GgoSquadSyncBridge() {}

    public static void installTransport(Transport next) {
        transport = next;
    }

    public static void requestRefresh() {
        Transport t = transport;
        if (t != null) {
            try { t.requestSnapshot(); } catch (RuntimeException ignored) {}
        }
    }

    public static void receive(List<MemberSnapshot> members) {
        GgoSquadOverlayState.acceptRemote(members);
    }
}
'''

(JAVA / "GgoObjectiveState.java").write_text(objective)
(JAVA / "GgoSquadSyncBridge.java").write_text(sync)

# Patch squad overlay state if present with remote member support.
state = JAVA / "GgoSquadOverlayState.java"
if state.exists():
    s = state.read_text()
    if "acceptRemote" not in s:
        insert = '''\n    public static void acceptRemote(java.util.List<GgoSquadSyncBridge.MemberSnapshot> remote) {\n        if (remote == null || remote.isEmpty()) {\n            provider = null;\n            return;\n        }\n        java.util.List<GgoSquadSyncBridge.MemberSnapshot> copy = java.util.List.copyOf(remote);\n        provider = mc -> copy.stream().map(m -> new Member(\n                m.name(), m.health(), m.maxHealth(), m.downed(), m.ping(), m.voiceActive(), m.leader(), m.sector(), m.activity()\n        )).toList();\n    }\n'''
        marker = "\n    private GgoSquadOverlayState() {}"
        if marker in s:
            s = s.replace(marker, insert + marker, 1)
    state.write_text(s)

# Add objective/activity block to HUD.
hud = JAVA / "GgoCombatHud.java"
if hud.exists():
    s = hud.read_text()
    anchor = "        renderWorldStatus(g, mc, width);"
    if anchor in s and "renderObjective(g, mc, width);" not in s:
        s = s.replace(anchor, anchor + "\n        renderObjective(g, mc, width);", 1)
    method_anchor = "    private static void renderWorldStatus(GuiGraphics g, Minecraft mc, int width) {"
    method = '''    private static void renderObjective(GuiGraphics g, Minecraft mc, int width) {\n        GgoObjectiveState.Snapshot objective = GgoObjectiveState.current();\n        int x = 20;\n        int y = 18;\n        int w = 270;\n        int h = objective.available() ? 58 : 34;\n        g.fill(x, y, x + w, y + h, 0xC90A0E14);\n        g.fill(x, y, x + 3, y + h, 0xFFC73542);\n        String activity = objective.activity().isBlank() ? "OPEN WORLD" : objective.activity();\n        g.drawString(mc.font, activity, x + 12, y + 9, 0xFF8F9BAD, false);\n        if (objective.available()) {\n            g.drawString(mc.font, objective.title(), x + 12, y + 24, 0xFFF0F2F5, false);\n            String line = objective.progress().isBlank() ? objective.description() : objective.progress() + "  •  " + objective.description();\n            if (line.length() > 48) line = line.substring(0, 45) + "...";\n            g.drawString(mc.font, line, x + 12, y + 40, 0xFF7F8A9A, false);\n        }\n    }\n\n'''
    if method_anchor in s and "private static void renderObjective" not in s:
        s = s.replace(method_anchor, method + method_anchor, 1)
    hud.write_text(s)

# Make TAB request remote squad snapshot when used, if hooks exist.
hooks = JAVA / "GgoShellHooks.java"
if hooks.exists():
    s = hooks.read_text()
    anchor = "if (mc.player == null || !mc.options.keyPlayerList.isDown()) return;"
    if anchor in s and "GgoSquadSyncBridge.requestRefresh();" not in s:
        s = s.replace(anchor, anchor + "\n        GgoSquadSyncBridge.requestRefresh();", 1)
    hooks.write_text(s)

print("GGO Objective / Squad Sync Stage 15 applied")
print(" - runtime-neutral objective provider")
print(" - HUD activity/objective card")
print(" - squad remote snapshot bridge")
print(" - TAB refresh request hook")
