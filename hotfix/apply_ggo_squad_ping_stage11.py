from pathlib import Path

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
JAVA = ROOT / "client-ui/src/main/java/arena/client/shell"
JAVA.mkdir(parents=True, exist_ok=True)

bridge = r'''package arena.client.shell;

public final class GgoSquadPingBridge {
    public record Ping(int x, int y, int z, String label, String sender, long createdAtMillis) {}

    public interface Transport {
        void send(Ping ping);
        default void clear() {}
    }

    private static volatile Transport transport;

    private GgoSquadPingBridge() {}

    public static void installTransport(Transport next) {
        transport = next;
    }

    public static void sendLocalPing(int x, int y, int z, String label, String sender) {
        Ping ping = sanitize(new Ping(x, y, z, label, sender, System.currentTimeMillis()));
        GgoNavigationState.setWaypoint(ping.x(), ping.y(), ping.z(), ping.label());
        Transport t = transport;
        if (t != null) {
            try { t.send(ping); } catch (RuntimeException ignored) {}
        }
    }

    public static void receiveSquadPing(Ping incoming) {
        Ping ping = sanitize(incoming);
        GgoNavigationState.setWaypoint(ping.x(), ping.y(), ping.z(), ping.label());
    }

    public static void clearLocal() {
        GgoNavigationState.clearWaypoint();
        Transport t = transport;
        if (t != null) {
            try { t.clear(); } catch (RuntimeException ignored) {}
        }
    }

    private static Ping sanitize(Ping ping) {
        if (ping == null) return new Ping(0, 0, 0, "PING", "", System.currentTimeMillis());
        String label = ping.label() == null || ping.label().isBlank() ? "PING" : ping.label().trim();
        if (label.length() > 32) label = label.substring(0, 32);
        String sender = ping.sender() == null ? "" : ping.sender().replaceAll("[\\r\\n\\t]", "").trim();
        if (sender.length() > 32) sender = sender.substring(0, 32);
        return new Ping(ping.x(), ping.y(), ping.z(), label, sender, ping.createdAtMillis());
    }
}
'''

(JAVA / "GgoSquadPingBridge.java").write_text(bridge)

nav = JAVA / "GgoNavigationState.java"
if nav.exists():
    s = nav.read_text()
    s = s.replace('private static volatile Waypoint waypoint;', 'private static volatile Waypoint waypoint;\n    private static volatile long expiresAtMillis;')
    s = s.replace('    public static Waypoint waypoint() {\n        return waypoint;\n    }', '''    public static Waypoint waypoint() {\n        expireIfNeeded();\n        return waypoint;\n    }''')
    s = s.replace('    public static boolean hasWaypoint() {\n        return waypoint != null;\n    }', '''    public static boolean hasWaypoint() {\n        expireIfNeeded();\n        return waypoint != null;\n    }''')
    s = s.replace('        waypoint = new Waypoint(x, y, z, safe, System.currentTimeMillis());', '        long now = System.currentTimeMillis();\n        waypoint = new Waypoint(x, y, z, safe, now);\n        expiresAtMillis = now + 45_000L;')
    s = s.replace('    public static void clearWaypoint() {\n        waypoint = null;\n    }', '''    public static void clearWaypoint() {\n        waypoint = null;\n        expiresAtMillis = 0L;\n    }\n\n    public static void pinWaypoint() {\n        if (waypoint != null) expiresAtMillis = Long.MAX_VALUE;\n    }\n\n    private static void expireIfNeeded() {\n        if (waypoint != null && expiresAtMillis != Long.MAX_VALUE && System.currentTimeMillis() >= expiresAtMillis) {\n            clearWaypoint();\n        }\n    }''')
    s = s.replace('        Waypoint w = waypoint;', '        Waypoint w = waypoint();')
    nav.write_text(s)

# Route Stage 9 local ping operations through the bridge when the generated hook exists.
for candidate in [JAVA / "GgoPingHooks.java", JAVA / "GgoShellHooks.java"]:
    if not candidate.exists():
        continue
    s = candidate.read_text()
    s = s.replace('GgoNavigationState.clearWaypoint();', 'GgoSquadPingBridge.clearLocal();')
    # Common Stage 9 call shape; harmless if absent.
    s = s.replace('GgoNavigationState.setWaypoint(hit.getBlockPos().getX(), hit.getBlockPos().getY(), hit.getBlockPos().getZ(), "PING");',
                  'GgoSquadPingBridge.sendLocalPing(hit.getBlockPos().getX(), hit.getBlockPos().getY(), hit.getBlockPos().getZ(), "PING", mc.player.getGameProfile().getName());')
    candidate.write_text(s)

print("GGO Squad Ping Stage 11 applied")
print(" - runtime-neutral squad ping transport bridge")
print(" - local ping works without network transport")
print(" - remote squad ping receive hook")
print(" - temporary ping TTL: 45 seconds")
print(" - waypoint can be pinned explicitly")
