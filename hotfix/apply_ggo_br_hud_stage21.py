from pathlib import Path

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
JAVA = ROOT / "client-ui/src/main/java/arena/client/shell"
JAVA.mkdir(parents=True, exist_ok=True)

match_state = r'''package arena.client.shell;

public final class GgoMatchState {
    public record Snapshot(
            String activity,
            int alive,
            int total,
            int placement,
            int zonePhase,
            int secondsRemaining,
            boolean playerAlive,
            boolean available
    ) {
        public static Snapshot empty() {
            return new Snapshot("OPEN WORLD", 0, 0, 0, 0, 0, true, false);
        }
        public boolean battleRoyale() {
            return "BATTLE ROYALE".equalsIgnoreCase(activity);
        }
    }

    private static volatile Snapshot current = Snapshot.empty();
    private GgoMatchState() {}

    public static Snapshot current() { return current; }
    public static void accept(Snapshot next) { current = next == null ? Snapshot.empty() : sanitize(next); }

    private static Snapshot sanitize(Snapshot s) {
        String activity = s.activity() == null ? "" : s.activity().replaceAll("[\\r\\n\\t]", " ").trim();
        if (activity.length() > 32) activity = activity.substring(0, 32);
        int alive = Math.max(0, s.alive());
        int total = Math.max(alive, s.total());
        int placement = Math.max(0, s.placement());
        int zone = Math.max(0, s.zonePhase());
        int seconds = Math.max(0, s.secondsRemaining());
        return new Snapshot(activity, alive, total, placement, zone, seconds, s.playerAlive(), s.available());
    }
}
'''
(JAVA / "GgoMatchState.java").write_text(match_state)

# Extend Stage 20 reflection adapter to consume the typed server counters.
adapter = JAVA / "GgoRuntimeV1HudAdapter.java"
if adapter.exists():
    s = adapter.read_text()
    old = '''            latest = new GgoObjectiveState.Snapshot(\n                    str(packet, "title"), str(packet, "description"), str(packet, "progress"), str(packet, "activity"), bool(packet, "available")\n            );'''
    new = '''            String activity = str(packet, "activity");\n            boolean available = bool(packet, "available");\n            latest = new GgoObjectiveState.Snapshot(\n                    str(packet, "title"), str(packet, "description"), str(packet, "progress"), activity, available\n            );\n            GgoMatchState.accept(new GgoMatchState.Snapshot(\n                    activity, integer(packet, "alive"), integer(packet, "total"), integer(packet, "placement"),\n                    integer(packet, "zonePhase"), integer(packet, "secondsRemaining"), bool(packet, "playerAlive"), available\n            ));'''
    if old in s:
        s = s.replace(old, new, 1)
    helper = '    private static boolean bool(Object o, String n) throws ReflectiveOperationException { return Boolean.TRUE.equals(value(o,n)); }'
    if helper in s and 'private static int integer' not in s:
        s = s.replace(helper, '    private static int integer(Object o, String n) throws ReflectiveOperationException { return ((Number)value(o,n)).intValue(); }\n' + helper, 1)
    adapter.write_text(s)

# Add compact BR metrics to TAB squad header without exposing the full server player list.
hooks = JAVA / "GgoShellHooks.java"
if hooks.exists():
    s = hooks.read_text()
    anchor = '        g.drawString(mc.font, "GGO // SQUAD", x + 12, y + 10, 0xFFF5F6F8, false);'
    addition = '''\n        GgoMatchState.Snapshot match = GgoMatchState.current();\n        if (match.battleRoyale() && match.available()) {\n            String brStatus;\n            if (!match.playerAlive() && match.placement() > 0) {\n                brStatus = "PLACEMENT #" + match.placement();\n            } else {\n                brStatus = "ALIVE " + match.alive() + "/" + match.total();\n                if (match.zonePhase() > 0) brStatus += "  •  ZONE " + match.zonePhase();\n                if (match.secondsRemaining() > 0) brStatus += "  •  " + match.secondsRemaining() + "s";\n            }\n            g.drawString(mc.font, brStatus, x + boxW - mc.font.width(brStatus) - 12, y + 10, 0xFFD9A85C, false);\n        }\n'''
    if anchor in s and 'GgoMatchState.Snapshot match' not in s:
        s = s.replace(anchor, anchor + addition, 1)
    hooks.write_text(s)

# Add a dedicated compact BR counter line in the combat HUD. The objective card
# remains server driven; this block keeps combat-critical counters readable.
hud = JAVA / "GgoCombatHud.java"
if hud.exists():
    s = hud.read_text()
    anchor = '        renderObjective(g, mc, width);'
    if anchor in s and 'renderMatchStatus(g, mc, width);' not in s:
        s = s.replace(anchor, anchor + '\n        renderMatchStatus(g, mc, width);', 1)
    method_anchor = '    private static void renderObjective(GuiGraphics g, Minecraft mc, int width) {'
    method = '''    private static void renderMatchStatus(GuiGraphics g, Minecraft mc, int width) {\n        GgoMatchState.Snapshot match = GgoMatchState.current();\n        if (!match.battleRoyale() || !match.available()) return;\n        int x = Math.max(12, width - 208);\n        int y = 18;\n        int w = 196;\n        int h = 34;\n        g.fill(x, y, x + w, y + h, 0xC90A0E14);\n        g.fill(x, y, x + 3, y + h, match.playerAlive() ? 0xFFD34855 : 0xFFE18A4B);\n        String left = match.playerAlive() ? "ALIVE " + match.alive() + "/" + match.total() : "ELIMINATED";\n        String right = match.placement() > 0 && !match.playerAlive() ? "#" + match.placement() : (match.secondsRemaining() > 0 ? match.secondsRemaining() + "s" : "");\n        g.drawString(mc.font, left, x + 12, y + 8, 0xFFF0F2F5, false);\n        if (!right.isBlank()) g.drawString(mc.font, right, x + w - mc.font.width(right) - 10, y + 8, 0xFFD9A85C, false);\n        String zone = match.zonePhase() > 0 ? "ZONE " + match.zonePhase() : "DEPLOYMENT";\n        g.drawString(mc.font, zone, x + 12, y + 21, 0xFF7F8A9A, false);\n    }\n\n'''
    if method_anchor in s and 'private static void renderMatchStatus' not in s:
        s = s.replace(method_anchor, method + method_anchor, 1)
    hud.write_text(s)

print("GGO BR HUD Stage 21 applied")
print(" - typed server-authoritative BR match state")
print(" - alive / total / zone / timer")
print(" - final placement after elimination")
print(" - TAB squad header BR counters")
print(" - compact combat HUD BR panel")
