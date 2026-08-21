from pathlib import Path
import re

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
JAVA = ROOT / "client-ui/src/main/java/arena/client/shell"
JAVA.mkdir(parents=True, exist_ok=True)

state = r'''package arena.client.shell;

import net.minecraft.client.Minecraft;
import java.util.List;

public final class GgoSquadOverlayState {
    public record Member(
            String name,
            int health,
            int maxHealth,
            int ping,
            boolean leader,
            boolean downed,
            boolean voiceActive,
            String activity,
            String sector
    ) {}

    @FunctionalInterface
    public interface Provider {
        List<Member> members(Minecraft minecraft);
    }

    private static volatile Provider provider = GgoSquadOverlayState::fallback;

    private GgoSquadOverlayState() {}

    public static void installProvider(Provider next) {
        provider = next == null ? GgoSquadOverlayState::fallback : next;
    }

    public static List<Member> current(Minecraft minecraft) {
        try {
            List<Member> members = provider.members(minecraft);
            return members == null || members.isEmpty() ? fallback(minecraft) : members.stream().limit(6).toList();
        } catch (RuntimeException ignored) {
            return fallback(minecraft);
        }
    }

    private static List<Member> fallback(Minecraft mc) {
        if (mc == null || mc.player == null) return List.of();
        int ping = 0;
        if (mc.getConnection() != null && mc.getConnection().getPlayerInfo(mc.player.getUUID()) != null) {
            ping = mc.getConnection().getPlayerInfo(mc.player.getUUID()).getLatency();
        }
        int x = mc.player.getBlockX();
        int z = mc.player.getBlockZ();
        int sx = Math.floorDiv(x, 256);
        int sz = Math.floorDiv(z, 256);
        char col = (char)('A' + Math.floorMod(sx, 26));
        String sector = col + "-" + Math.abs(sz);
        String name = GgoAccountContext.onlineReady() ? GgoAccountContext.displayName() : mc.player.getGameProfile().getName();
        return List.of(new Member(
                name,
                Math.round(mc.player.getHealth()),
                Math.round(mc.player.getMaxHealth()),
                ping,
                true,
                false,
                false,
                "GGO",
                sector
        ));
    }
}
'''
(JAVA / "GgoSquadOverlayState.java").write_text(state)

hooks = JAVA / "GgoShellHooks.java"
if hooks.exists():
    s = hooks.read_text()
    pattern = re.compile(
        r'    @SubscribeEvent\n    public static void onPlayerListOverlay\(RenderGuiOverlayEvent\.Pre event\) \{.*?\n    \}\n',
        re.S,
    )
    replacement = r'''    @SubscribeEvent
    public static void onPlayerListOverlay(RenderGuiOverlayEvent.Pre event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.PLAYER_LIST.id())) return;
        event.setCanceled(true);

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.options.keyPlayerList.isDown()) return;

        var members = GgoSquadOverlayState.current(mc);
        GuiGraphics g = event.getGuiGraphics();
        int width = mc.getWindow().getGuiScaledWidth();
        int boxW = Math.min(500, width - 32);
        int rowH = 26;
        int headerH = 34;
        int footerH = 28;
        int boxH = headerH + Math.max(1, members.size()) * rowH + footerH;
        int x = (width - boxW) / 2;
        int y = 14;

        g.fill(x, y, x + boxW, y + boxH, 0xE60A0E14);
        g.fill(x, y, x + 3, y + boxH, 0xFFD34855);
        g.drawString(mc.font, "GGO // SQUAD", x + 12, y + 10, 0xFFF5F6F8, false);

        int rowY = y + headerH;
        if (members.isEmpty()) {
            g.drawString(mc.font, "NO SQUAD DATA", x + 14, rowY + 8, 0xFF758195, false);
        } else {
            for (var member : members) {
                int hp = Math.max(0, member.health());
                int maxHp = Math.max(1, member.maxHealth());
                float pct = Math.max(0.0f, Math.min(1.0f, hp / (float)maxHp));
                int barX = x + 180;
                int barY = rowY + 10;
                int barW = 86;

                String prefix = member.leader() ? "◆ " : "  ";
                String status = member.downed() ? "DOWN" : hp + "/" + maxHp;
                g.drawString(mc.font, prefix + member.name(), x + 14, rowY + 8, member.downed() ? 0xFFE28A55 : 0xFFE8ECF1, false);
                g.fill(barX, barY, barX + barW, barY + 5, 0xFF222A35);
                g.fill(barX, barY, barX + Math.round(barW * pct), barY + 5, member.downed() ? 0xFFE18A4B : 0xFFD34855);
                g.drawString(mc.font, status, x + 278, rowY + 8, 0xFF9DA8B8, false);
                g.drawString(mc.font, member.ping() + "ms", x + 336, rowY + 8, 0xFF748195, false);
                g.drawString(mc.font, member.voiceActive() ? "VOICE" : "", x + 382, rowY + 8, 0xFF7FC9A0, false);
                g.drawString(mc.font, member.sector(), x + boxW - 62, rowY + 8, 0xFF7F8B9B, false);
                rowY += rowH;
            }
        }

        GgoNavigationState.Waypoint waypoint = GgoNavigationState.waypoint();
        String footer = waypoint == null
                ? "NO ACTIVE PING"
                : waypoint.type().label() + "  •  " + waypoint.label() + "  •  "
                    + (int)Math.round(GgoNavigationState.distanceTo(mc.player.getX(), mc.player.getY(), mc.player.getZ())) + "m";
        int footerColor = waypoint == null ? 0xFF657183 : waypoint.type().color();
        g.drawString(mc.font, footer, x + 14, y + boxH - 18, footerColor, false);
        g.drawString(mc.font, "TAB  HOLD", x + boxW - 64, y + boxH - 18, 0xFF596575, false);
    }
'''
    if pattern.search(s):
        s = pattern.sub(replacement, s, count=1)
    hooks.write_text(s)

print("GGO TAB Squad Stage 13 applied")
print(" - vanilla all-player list remains suppressed")
print(" - runtime-neutral squad provider")
print(" - member HP/downed/ping/voice/leader/sector")
print(" - active tactical ping shown in TAB footer")
print(" - local-player fallback until squad backend provider is installed")
