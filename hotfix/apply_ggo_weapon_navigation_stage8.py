from pathlib import Path

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
CLIENT = ROOT / "client-ui"
JAVA = CLIENT / "src/main/java/arena/client/shell"
JAVA.mkdir(parents=True, exist_ok=True)

telemetry = r'''package arena.client.shell;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

public final class GgoWeaponTelemetry {
    public record Snapshot(String weaponName, int magazine, int reserve, String fireMode, boolean reloading, boolean available) {
        public static Snapshot unavailable(ItemStack stack) {
            String name = stack == null || stack.isEmpty() ? "UNARMED" : stack.getHoverName().getString();
            return new Snapshot(name, -1, -1, "--", false, false);
        }
    }

    @FunctionalInterface
    public interface Provider {
        Snapshot snapshot(Minecraft minecraft, ItemStack held);
    }

    private static volatile Provider provider = (minecraft, held) -> Snapshot.unavailable(held);

    private GgoWeaponTelemetry() {}

    public static void installProvider(Provider next) {
        provider = next == null ? (minecraft, held) -> Snapshot.unavailable(held) : next;
    }

    public static Snapshot current(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null) return Snapshot.unavailable(ItemStack.EMPTY);
        ItemStack held = minecraft.player.getMainHandItem();
        try {
            Snapshot snapshot = provider.snapshot(minecraft, held);
            return snapshot == null ? Snapshot.unavailable(held) : snapshot;
        } catch (RuntimeException ignored) {
            return Snapshot.unavailable(held);
        }
    }

    public static String ammoText(Snapshot snapshot) {
        if (snapshot == null || !snapshot.available() || snapshot.magazine() < 0 || snapshot.reserve() < 0) return "-- / --";
        return snapshot.magazine() + " / " + snapshot.reserve();
    }
}
'''

navigation = r'''package arena.client.shell;

public final class GgoNavigationState {
    public record Waypoint(int x, int y, int z, String label, long createdAtMillis) {}

    private static volatile Waypoint waypoint;

    private GgoNavigationState() {}

    public static Waypoint waypoint() {
        return waypoint;
    }

    public static boolean hasWaypoint() {
        return waypoint != null;
    }

    public static void setWaypoint(int x, int y, int z, String label) {
        String safe = label == null || label.isBlank() ? "PING" : label.trim();
        if (safe.length() > 32) safe = safe.substring(0, 32);
        waypoint = new Waypoint(x, y, z, safe, System.currentTimeMillis());
    }

    public static void clearWaypoint() {
        waypoint = null;
    }

    public static double distanceTo(double x, double y, double z) {
        Waypoint w = waypoint;
        if (w == null) return -1.0;
        double dx = w.x() - x;
        double dy = w.y() - y;
        double dz = w.z() - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
'''

(JAVA / "GgoWeaponTelemetry.java").write_text(telemetry)
(JAVA / "GgoNavigationState.java").write_text(navigation)

hud = JAVA / "GgoCombatHud.java"
if hud.exists():
    s = hud.read_text()
    old = '''        ItemStack held = mc.player.getMainHandItem();\n        int w = 250;'''
    new = '''        ItemStack held = mc.player.getMainHandItem();\n        GgoWeaponTelemetry.Snapshot telemetry = GgoWeaponTelemetry.current(mc);\n        int w = 250;'''
    if old in s:
        s = s.replace(old, new, 1)

    old = '''        String name = held == null || held.isEmpty() ? "UNARMED" : held.getHoverName().getString();'''
    new = '''        String name = telemetry.weaponName();'''
    if old in s:
        s = s.replace(old, new, 1)

    old = '''        g.drawString(mc.font, "-- / --", x + 54, y + 31, 0xFFD9DEE5, false);\n        g.drawString(mc.font, "GGO WEAPON LINK", x + 151, y + 31, 0xFF606C7C, false);'''
    new = '''        g.drawString(mc.font, GgoWeaponTelemetry.ammoText(telemetry), x + 54, y + 31, 0xFFD9DEE5, false);\n        String mode = telemetry.available() ? telemetry.fireMode() : "RUNTIME LINK";\n        if (telemetry.reloading()) mode = "RELOADING";\n        g.drawString(mc.font, mode, x + 151, y + 31, telemetry.available() ? 0xFF9AA6B7 : 0xFF606C7C, false);'''
    if old in s:
        s = s.replace(old, new, 1)

    world_anchor = '''        String status = "GGO  //  " + sector + "  //  " + ping + " ms";'''
    world_new = '''        String status = "GGO  //  " + sector + "  //  " + ping + " ms";\n        GgoNavigationState.Waypoint waypoint = GgoNavigationState.waypoint();\n        if (waypoint != null) {\n            int distance = (int)Math.round(GgoNavigationState.distanceTo(mc.player.getX(), mc.player.getY(), mc.player.getZ()));\n            status += "  //  " + waypoint.label() + " " + distance + "m";\n        }'''
    if world_anchor in s and 'GgoNavigationState.Waypoint waypoint' not in s:
        s = s.replace(world_anchor, world_new, 1)
    hud.write_text(s)

screen = JAVA / "GgoShellScreen.java"
if screen.exists():
    s = screen.read_text()
    anchor = '''        g.drawString(this.font, "MMB  PLACE PING    Minimap: disabled by default", mapX + 14, mapY + mapH - 22, 0xFF697688, false);'''
    replacement = '''        GgoNavigationState.Waypoint waypoint = GgoNavigationState.waypoint();\n        String waypointText = waypoint == null\n                ? "MMB  PLACE PING    No active waypoint"\n                : waypoint.label() + "  " + waypoint.x() + " / " + waypoint.y() + " / " + waypoint.z()\n                    + "  •  " + (int)Math.round(GgoNavigationState.distanceTo(mc.player.getX(), mc.player.getY(), mc.player.getZ())) + "m";\n        g.drawString(this.font, waypointText, mapX + 14, mapY + mapH - 22, 0xFF697688, false);'''
    if anchor in s:
        s = s.replace(anchor, replacement, 1)
    screen.write_text(s)

print("GGO Weapon / Navigation Stage 8 applied")
print(" - runtime-neutral weapon telemetry provider interface")
print(" - HUD magazine/reserve/fire-mode/reload adapter")
print(" - shared waypoint state for HUD/map/minimap")
print(" - no weapon-mod-specific guessing in presentation layer")
