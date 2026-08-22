package arena.client.shell;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.util.Locale;

/** Owns the visible launcher-to-world transition while vanilla networking stays an implementation detail. */
@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoEntryExperience {
    private static final int ACCENT = 0xFFD54855;
    private static final int ACCENT_2 = 0xFF9F6CFF;
    private static final int TEXT = 0xFFF2F5F8;
    private static final int MUTED = 0xFF8B96A7;

    private GgoEntryExperience() {}

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (!(event.getNewScreen() instanceof DisconnectedScreen disconnected)) return;
        event.setNewScreen(new GgoEntryDisconnectedScreen(readDisconnectReason(disconnected)));
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        boolean connecting = event.getScreen() instanceof ConnectScreen;
        boolean receiving = event.getScreen() instanceof ReceivingLevelScreen;
        if (!connecting && !receiving) return;

        Minecraft mc = Minecraft.getInstance();
        GuiGraphics g = event.getGuiGraphics();
        int width = event.getScreen().width;
        int height = event.getScreen().height;
        long now = System.currentTimeMillis();

        g.fill(0, 0, width, height, 0xFF05070C);
        g.fill(0, 0, width, Math.max(3, height / 120), ACCENT);

        int cardWidth = Math.min(520, width - 36);
        int cardHeight = 210;
        int x = (width - cardWidth) / 2;
        int y = (height - cardHeight) / 2;
        g.fill(x, y, x + cardWidth, y + cardHeight, 0xF20A0E16);
        g.fill(x, y, x + 3, y + cardHeight, ACCENT);
        g.fill(x + 3, y, x + cardWidth, y + 1, 0x665E6E86);

        String title = receiving ? "ENTERING GGO" : "SECURE SESSION";
        String status = receiving ? "Synchronizing operation state" : "Authenticating official GGO session";
        int dots = (int) ((now / 450L) % 4L);
        String animatedStatus = status + ".".repeat(dots);

        g.drawCenteredString(mc.font, Component.literal("GUN GLORY ONLINE"), width / 2, y + 34, TEXT);
        g.drawCenteredString(mc.font, Component.literal(title), width / 2, y + 60, receiving ? ACCENT_2 : ACCENT);
        g.drawCenteredString(mc.font, Component.literal(animatedStatus), width / 2, y + 90, MUTED);

        int barX = x + 44;
        int barY = y + 126;
        int barWidth = cardWidth - 88;
        g.fill(barX, barY, barX + barWidth, barY + 7, 0xFF151C28);
        int sweep = Math.max(48, barWidth / 5);
        int travel = Math.max(1, barWidth + sweep);
        int position = (int) ((now / 8L) % travel) - sweep;
        int start = Math.max(barX, barX + position);
        int end = Math.min(barX + barWidth, barX + position + sweep);
        if (end > start) g.fill(start, barY, end, barY + 7, receiving ? ACCENT_2 : ACCENT);

        g.drawCenteredString(
            mc.font,
            Component.literal("OFFICIAL GGO NETWORK  •  VERIFIED ENTRY"),
            width / 2,
            y + 162,
            0xFF657287
        );
    }

    static Component readDisconnectReason(DisconnectedScreen screen) {
        Component fallback = Component.literal("Connection to GunGloryOnline was closed");
        Component best = null;
        int bestLength = 0;

        for (Field field : DisconnectedScreen.class.getDeclaredFields()) {
            if (!Component.class.isAssignableFrom(field.getType())) continue;
            try {
                if (!field.trySetAccessible()) continue;
                Object value = field.get(screen);
                if (!(value instanceof Component component)) continue;
                String text = component.getString().trim();
                if (text.length() > bestLength) {
                    best = component;
                    bestLength = text.length();
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Fall back to a generic GGO message rather than exposing an internal screen.
            }
        }
        return best == null ? fallback : best;
    }

    static boolean looksLikeSessionFailure(Component reason) {
        if (reason == null) return false;
        String text = reason.getString().toLowerCase(Locale.ROOT);
        return text.contains("ticket")
            || text.contains("session")
            || text.contains("auth")
            || text.contains("login")
            || text.contains("launcher");
    }
}
