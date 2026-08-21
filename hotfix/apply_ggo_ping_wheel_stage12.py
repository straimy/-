from pathlib import Path
import re

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
JAVA = ROOT / "client-ui/src/main/java/arena/client/shell"
JAVA.mkdir(parents=True, exist_ok=True)

ping_type = r'''package arena.client.shell;

public enum GgoPingType {
    ENEMY("ENEMY", 0xFFE34B55),
    MOVE("MOVE", 0xFF76B7FF),
    DANGER("DANGER", 0xFFFF8B4A),
    LOOT("LOOT", 0xFFF1CC63),
    DEFEND("DEFEND", 0xFF79D6A0),
    REGROUP("REGROUP", 0xFFC49AFF);

    private final String label;
    private final int color;

    GgoPingType(String label, int color) {
        this.label = label;
        this.color = color;
    }

    public String label() { return label; }
    public int color() { return color; }
}
'''
(JAVA / "GgoPingType.java").write_text(ping_type)

bridge = r'''package arena.client.shell;

public final class GgoSquadPingBridge {
    public record Ping(int x, int y, int z, GgoPingType type, String label, String sender, long createdAtMillis) {}

    public interface Transport {
        void send(Ping ping);
        default void clear() {}
    }

    private static volatile Transport transport;

    private GgoSquadPingBridge() {}

    public static void installTransport(Transport next) {
        transport = next;
    }

    public static void sendLocalPing(int x, int y, int z, GgoPingType type, String sender) {
        GgoPingType safeType = type == null ? GgoPingType.MOVE : type;
        sendLocalPing(x, y, z, safeType, safeType.label(), sender);
    }

    public static void sendLocalPing(int x, int y, int z, GgoPingType type, String label, String sender) {
        Ping ping = sanitize(new Ping(x, y, z, type, label, sender, System.currentTimeMillis()));
        GgoNavigationState.setWaypoint(ping.x(), ping.y(), ping.z(), ping.label(), ping.type());
        Transport t = transport;
        if (t != null) {
            try { t.send(ping); } catch (RuntimeException ignored) {}
        }
    }

    // Compatibility for earlier stages and external adapters.
    public static void sendLocalPing(int x, int y, int z, String label, String sender) {
        sendLocalPing(x, y, z, GgoPingType.MOVE, label, sender);
    }

    public static void receiveSquadPing(Ping incoming) {
        Ping ping = sanitize(incoming);
        GgoNavigationState.setWaypoint(ping.x(), ping.y(), ping.z(), ping.label(), ping.type());
    }

    public static void clearLocal() {
        GgoNavigationState.clearWaypoint();
        Transport t = transport;
        if (t != null) {
            try { t.clear(); } catch (RuntimeException ignored) {}
        }
    }

    private static Ping sanitize(Ping ping) {
        if (ping == null) return new Ping(0, 0, 0, GgoPingType.MOVE, "MOVE", "", System.currentTimeMillis());
        GgoPingType type = ping.type() == null ? GgoPingType.MOVE : ping.type();
        String label = ping.label() == null || ping.label().isBlank() ? type.label() : ping.label().trim();
        if (label.length() > 32) label = label.substring(0, 32);
        String sender = ping.sender() == null ? "" : ping.sender().replaceAll("[\\r\\n\\t]", "").trim();
        if (sender.length() > 32) sender = sender.substring(0, 32);
        return new Ping(ping.x(), ping.y(), ping.z(), type, label, sender, ping.createdAtMillis());
    }
}
'''
(JAVA / "GgoSquadPingBridge.java").write_text(bridge)

nav = JAVA / "GgoNavigationState.java"
if nav.exists():
    s = nav.read_text()
    s = s.replace(
        'public record Waypoint(int x, int y, int z, String label, long createdAtMillis) {}',
        'public record Waypoint(int x, int y, int z, String label, GgoPingType type, long createdAtMillis) {}'
    )
    old = '''    public static void setWaypoint(int x, int y, int z, String label) {\n        String safe = label == null || label.isBlank() ? "PING" : label.trim();'''
    if old in s:
        new = '''    public static void setWaypoint(int x, int y, int z, String label) {\n        setWaypoint(x, y, z, label, GgoPingType.MOVE);\n    }\n\n    public static void setWaypoint(int x, int y, int z, String label, GgoPingType type) {\n        String safe = label == null || label.isBlank() ? "PING" : label.trim();'''
        s = s.replace(old, new, 1)
    s = s.replace(
        'waypoint = new Waypoint(x, y, z, safe, now);',
        'waypoint = new Waypoint(x, y, z, safe, type == null ? GgoPingType.MOVE : type, now);'
    )
    nav.write_text(s)

wheel = r'''package arena.client.shell;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;

public final class GgoPingWheelScreen extends Screen {
    private final BlockPos target;
    private GgoPingType selected = GgoPingType.MOVE;

    public GgoPingWheelScreen(BlockPos target) {
        super(Component.literal("GGO Tactical Ping"));
        this.target = target;
    }

    public GgoPingType selected() { return selected; }
    public BlockPos target() { return target; }

    public void confirmSelection() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || target == null) return;
        String sender = GgoAccountContext.onlineReady()
                ? GgoAccountContext.displayName()
                : mc.player.getGameProfile().getName();
        GgoSquadPingBridge.sendLocalPing(target.getX(), target.getY(), target.getZ(), selected, sender);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int cx = this.width / 2;
        int cy = this.height / 2;
        double dx = mouseX - cx;
        double dy = mouseY - cy;
        double angle = Math.atan2(dy, dx);
        if (angle < 0) angle += Math.PI * 2.0;
        int index = (int)Math.floor((angle + Math.PI / 6.0) / (Math.PI / 3.0)) % 6;
        GgoPingType[] order = {
                GgoPingType.MOVE,
                GgoPingType.DANGER,
                GgoPingType.ENEMY,
                GgoPingType.REGROUP,
                GgoPingType.DEFEND,
                GgoPingType.LOOT
        };
        selected = order[index];

        g.fill(0, 0, this.width, this.height, 0x55000000);
        g.fill(cx - 58, cy - 58, cx + 59, cy + 59, 0xDF0A0E14);
        g.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFFF1F3F6);

        drawChoice(g, GgoPingType.MOVE, cx + 78, cy, selected == GgoPingType.MOVE);
        drawChoice(g, GgoPingType.DANGER, cx + 40, cy + 68, selected == GgoPingType.DANGER);
        drawChoice(g, GgoPingType.ENEMY, cx - 74, cy + 68, selected == GgoPingType.ENEMY);
        drawChoice(g, GgoPingType.REGROUP, cx - 112, cy, selected == GgoPingType.REGROUP);
        drawChoice(g, GgoPingType.DEFEND, cx - 74, cy - 68, selected == GgoPingType.DEFEND);
        drawChoice(g, GgoPingType.LOOT, cx + 40, cy - 68, selected == GgoPingType.LOOT);

        String title = selected.label();
        g.drawString(this.font, title, cx - this.font.width(title) / 2, cy - 20, selected.color(), false);
        g.drawString(this.font, "RELEASE MMB", cx - 38, cy + 17, 0xFF7E8998, false);
    }

    private void drawChoice(GuiGraphics g, GgoPingType type, int x, int y, boolean active) {
        int color = active ? type.color() : 0xFF778292;
        String text = type.label();
        g.drawString(this.font, text, x - this.font.width(text) / 2, y - 4, color, false);
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean shouldCloseOnEsc() { return true; }
}
'''
(JAVA / "GgoPingWheelScreen.java").write_text(wheel)

controller = r'''package arena.client.shell;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoPingWheelController {
    private static final long HOLD_MILLIS = 350L;
    private static boolean holding;
    private static long pressedAt;
    private static BlockPos target;

    private GgoPingWheelController() {}

    @SubscribeEvent
    public static void onMouse(InputEvent.MouseButton.Pre event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_MIDDLE) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        if (event.getAction() == GLFW.GLFW_PRESS) {
            if (mc.screen != null) return;
            if (mc.player.isShiftKeyDown()) {
                GgoSquadPingBridge.clearLocal();
                event.setCanceled(true);
                reset();
                return;
            }
            if (mc.hitResult instanceof BlockHitResult hit) {
                target = hit.getBlockPos().relative(hit.getDirection());
                pressedAt = System.currentTimeMillis();
                holding = true;
                event.setCanceled(true);
            }
            return;
        }

        if (event.getAction() == GLFW.GLFW_RELEASE) {
            if (mc.screen instanceof GgoPingWheelScreen wheel) {
                wheel.confirmSelection();
                mc.setScreen(null);
                event.setCanceled(true);
                reset();
                return;
            }
            if (holding && target != null) {
                String sender = GgoAccountContext.onlineReady()
                        ? GgoAccountContext.displayName()
                        : mc.player.getGameProfile().getName();
                GgoSquadPingBridge.sendLocalPing(target.getX(), target.getY(), target.getZ(), GgoPingType.MOVE, sender);
                event.setCanceled(true);
                reset();
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !holding || target == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) return;
        if (System.currentTimeMillis() - pressedAt >= HOLD_MILLIS) {
            mc.setScreen(new GgoPingWheelScreen(target));
        }
    }

    private static void reset() {
        holding = false;
        pressedAt = 0L;
        target = null;
    }
}
'''
(JAVA / "GgoPingWheelController.java").write_text(controller)

# Stage 9 inserted an immediate MMB handler into GgoShellHooks. Remove it so the
# hold-vs-tap controller is the single owner of MMB input.
hooks = JAVA / "GgoShellHooks.java"
if hooks.exists():
    s = hooks.read_text()
    pattern = re.compile(
        r'    @SubscribeEvent\n    public static void onMouseButton\(InputEvent\.MouseButton\.Pre event\) \{.*?\n    \}\n\n(?=    @SubscribeEvent\n    public static void onPlayerListOverlay)',
        re.S,
    )
    s = pattern.sub('', s, count=1)
    hooks.write_text(s)

# Use ping type colors instead of a single fixed waypoint color wherever Stage
# 9 rendered the shared waypoint.
hud = JAVA / "GgoCombatHud.java"
if hud.exists():
    s = hud.read_text()
    s = s.replace(
        'g.fill(wx - 2, wy - 2, wx + 3, wy + 3, 0xFFF0C75E);',
        'g.fill(wx - 2, wy - 2, wx + 3, wy + 3, waypoint.type().color());'
    )
    s = s.replace(
        'g.drawString(mc.font, text, wx + 5, wy - 4, 0xFFF0D27A, false);',
        'g.drawString(mc.font, text, wx + 5, wy - 4, waypoint.type().color(), false);'
    )
    hud.write_text(s)

screen = JAVA / "GgoShellScreen.java"
if screen.exists():
    s = screen.read_text()
    s = s.replace(
        'g.fill(wx - 4, wy - 4, wx + 5, wy + 5, 0xFFF0C75E);',
        'g.fill(wx - 4, wy - 4, wx + 5, wy + 5, mapWaypoint.type().color());'
    )
    s = s.replace(
        'g.drawString(this.font, mapWaypoint.label() + "  " + distance + "m", wx + 9, wy - 4, 0xFFF3D482, false);',
        'g.drawString(this.font, mapWaypoint.label() + "  " + distance + "m", wx + 9, wy - 4, mapWaypoint.type().color(), false);'
    )
    s = s.replace('"MMB  PLACE PING    SHIFT+MMB  CLEAR"', '"MMB TAP  MOVE    HOLD MMB  PING WHEEL    SHIFT+MMB  CLEAR"')
    screen.write_text(s)

print("GGO Tactical Ping Wheel Stage 12 applied")
print(" - tap MMB -> MOVE ping")
print(" - hold MMB 350ms -> radial tactical ping wheel")
print(" - release MMB -> confirm selected ping")
print(" - types: ENEMY / MOVE / DANGER / LOOT / DEFEND / REGROUP")
print(" - Shift+MMB clears current ping")
print(" - type color shared by minimap and full Navigation map")
