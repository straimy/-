package arena.client.shell;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Final production-facing HUD fence for Minecraft-specific surfaces that are not part of GGO.
 *
 * Chat, subtitles and GGO-owned title/action feedback deliberately remain available. Admin/dev
 * tooling is not removed from the engine; this class only prevents normal player-facing rendering.
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoProductionSurfaceFence {
    private GgoProductionSurfaceFence() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void hideVanillaOverlays(RenderGuiOverlayEvent.Pre event) {
        var id = event.getOverlay().id();
        if (id.equals(VanillaGuiOverlay.DEBUG_TEXT.id())
                || id.equals(VanillaGuiOverlay.FPS_GRAPH.id())
                || id.equals(VanillaGuiOverlay.ITEM_NAME.id())
                || id.equals(VanillaGuiOverlay.POTION_ICONS.id())
                || id.equals(VanillaGuiOverlay.SCOREBOARD.id())
                || id.equals(VanillaGuiOverlay.BOSS_EVENT_PROGRESS.id())
                || id.equals(VanillaGuiOverlay.MOUNT_HEALTH.id())
                || id.equals(VanillaGuiOverlay.JUMP_BAR.id())
                || id.equals(VanillaGuiOverlay.RECORD_OVERLAY.id())) {
            event.setCanceled(true);
        }
    }

    /**
     * Prevents the vanilla dirt/world-loading frame from ever reaching the user while preserving
     * the original screen instance and its tick/lifecycle callbacks. Only rendering is cancelled.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void coverVanillaTransition(ScreenEvent.Render.Pre event) {
        String name = event.getScreen().getClass().getName();
        if (!(name.endsWith("GenericDirtMessageScreen") || name.endsWith("LevelLoadingScreen"))) return;

        Minecraft mc = Minecraft.getInstance();
        GuiGraphics g = event.getGuiGraphics();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        long phase = (System.currentTimeMillis() / 220L) % 16L;
        int barW = Math.max(180, Math.min(420, w - 96));
        int x = (w - barW) / 2;
        int y = h / 2 + 34;
        int fill = (int) ((barW - 2) * (phase + 1) / 16L);

        g.pose().pushPose();
        g.pose().last().pose().identity();
        g.fill(0, 0, w, h, 0xFF07090D);
        g.fill(0, 0, w, 3, 0xFFC83240);
        g.drawCenteredString(mc.font, "GUN GLORY ONLINE", w / 2, h / 2 - 34, 0xFFF1F3F6);
        g.drawCenteredString(mc.font, "SYNCHRONIZING SESSION", w / 2, h / 2 - 10, 0xFFD34B57);
        g.drawCenteredString(mc.font, "Preparing GGO runtime...", w / 2, h / 2 + 10, 0xFF7C8796);
        g.fill(x, y, x + barW, y + 5, 0xFF202733);
        g.fill(x + 1, y + 1, x + 1 + fill, y + 4, 0xFFC73A47);
        g.pose().popPose();
        event.setCanceled(true);
    }
}
