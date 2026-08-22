package arena.client.shell;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Keeps the gameplay view hidden until the official server acknowledges the launcher session. */
@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoSessionVerificationOverlay {
    private static final int ACCENT = 0xFFD54855;
    private static final int TEXT = 0xFFF2F5F8;
    private static final int MUTED = 0xFF8B96A7;

    private GgoSessionVerificationOverlay() {}

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        if (!GgoLaunchTicketClient.verificationPending()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        GuiGraphics g = event.getGuiGraphics();
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        long now = System.currentTimeMillis();

        g.fill(0, 0, width, height, 0xFF05070C);
        g.fill(0, 0, width, Math.max(3, height / 120), ACCENT);

        int cardWidth = Math.min(520, width - 36);
        int cardHeight = 216;
        int x = (width - cardWidth) / 2;
        int y = (height - cardHeight) / 2;
        g.fill(x, y, x + cardWidth, y + cardHeight, 0xF20A0E16);
        g.fill(x, y, x + 3, y + cardHeight, ACCENT);

        int dots = (int) ((now / 450L) % 4L);
        g.drawCenteredString(mc.font, Component.literal("GUN GLORY ONLINE"), width / 2, y + 38, TEXT);
        g.drawCenteredString(mc.font, Component.literal("VERIFYING GGO ACCOUNT"), width / 2, y + 68, ACCENT);
        g.drawCenteredString(
            mc.font,
            Component.literal("Binding secure launcher session" + ".".repeat(dots)),
            width / 2,
            y + 98,
            MUTED
        );

        int barX = x + 46;
        int barY = y + 132;
        int barWidth = cardWidth - 92;
        g.fill(barX, barY, barX + barWidth, barY + 7, 0xFF151C28);
        int sweep = Math.max(52, barWidth / 5);
        int travel = Math.max(1, barWidth + sweep);
        int position = (int) ((now / 8L) % travel) - sweep;
        int start = Math.max(barX, barX + position);
        int end = Math.min(barX + barWidth, barX + position + sweep);
        if (end > start) g.fill(start, barY, end, barY + 7, ACCENT);

        g.drawCenteredString(
            mc.font,
            Component.literal("GAMEPLAY UNLOCKS AFTER VERIFIED ENTRY"),
            width / 2,
            y + 170,
            0xFF657287
        );
    }
}
