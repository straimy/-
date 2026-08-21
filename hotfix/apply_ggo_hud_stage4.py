from pathlib import Path

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
CLIENT = ROOT / "client-ui"
JAVA = CLIENT / "src/main/java/arena/client/shell"
JAVA.mkdir(parents=True, exist_ok=True)

hud = r'''package arena.client.shell;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoCombatHud {
    private GgoCombatHud() {}

    @SubscribeEvent
    public static void hideVanillaHud(RenderGuiOverlayEvent.Pre event) {
        var id = event.getOverlay().id();
        if (id.equals(VanillaGuiOverlay.HOTBAR.id())
                || id.equals(VanillaGuiOverlay.PLAYER_HEALTH.id())
                || id.equals(VanillaGuiOverlay.ARMOR_LEVEL.id())
                || id.equals(VanillaGuiOverlay.FOOD_LEVEL.id())
                || id.equals(VanillaGuiOverlay.EXPERIENCE_BAR.id())
                || id.equals(VanillaGuiOverlay.AIR_LEVEL.id())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void renderGgoHud(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id())) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) return;

        GuiGraphics g = event.getGuiGraphics();
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();

        renderVitals(g, mc, width, height);
        renderWeapon(g, mc, width, height);
        renderQuickSlots(g, mc, width, height);
        renderWorldStatus(g, mc, width);
    }

    private static void renderVitals(GuiGraphics g, Minecraft mc, int width, int height) {
        int x = 22;
        int y = height - 74;
        int w = 210;
        int h = 48;
        panel(g, x, y, w, h);

        float health = mc.player.getHealth();
        float max = Math.max(1.0f, mc.player.getMaxHealth());
        float pct = Math.max(0.0f, Math.min(1.0f, health / max));
        int barW = 128;
        g.drawString(mc.font, "HP", x + 12, y + 10, 0xFF8995A6, false);
        g.fill(x + 34, y + 11, x + 34 + barW, y + 17, 0xFF202833);
        g.fill(x + 34, y + 11, x + 34 + Math.round(barW * pct), y + 17, 0xFFD03B48);
        g.drawString(mc.font, Math.round(health) + "/" + Math.round(max), x + 168, y + 9, 0xFFF2F4F7, false);

        int armor = mc.player.getArmorValue();
        g.drawString(mc.font, "ARMOR  " + armor, x + 12, y + 29, 0xFFAAB4C2, false);
    }

    private static void renderWeapon(GuiGraphics g, Minecraft mc, int width, int height) {
        ItemStack held = mc.player.getMainHandItem();
        int w = 250;
        int h = 54;
        int x = width - w - 22;
        int y = height - h - 20;
        panel(g, x, y, w, h);

        String name = held == null || held.isEmpty() ? "UNARMED" : held.getHoverName().getString();
        if (name.length() > 30) name = name.substring(0, 27) + "...";
        g.drawString(mc.font, name.toUpperCase(), x + 14, y + 11, 0xFFF2F4F7, false);
        g.drawString(mc.font, "AMMO", x + 14, y + 31, 0xFF7F8B9B, false);
        g.drawString(mc.font, "-- / --", x + 54, y + 31, 0xFFD9DEE5, false);
        g.drawString(mc.font, "GGO WEAPON LINK", x + 151, y + 31, 0xFF606C7C, false);
        if (held != null && !held.isEmpty()) g.renderItem(held, x + w - 34, y + 9);
    }

    private static void renderQuickSlots(GuiGraphics g, Minecraft mc, int width, int height) {
        int slots = 5;
        int size = 26;
        int gap = 4;
        int total = slots * size + (slots - 1) * gap;
        int startX = (width - total) / 2;
        int y = height - 38;
        int selected = mc.player.getInventory().selected;

        for (int i = 0; i < slots; i++) {
            int x = startX + i * (size + gap);
            int border = (selected == i) ? 0xFFD03B48 : 0xFF29323E;
            g.fill(x, y, x + size, y + size, border);
            g.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0xD90B1016);
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack != null && !stack.isEmpty()) {
                g.renderItem(stack, x + 5, y + 5);
                g.renderItemDecorations(mc.font, stack, x + 5, y + 5);
            }
            g.drawString(mc.font, String.valueOf(i + 1), x + 2, y + 2, 0xFF687486, false);
        }
    }

    private static void renderWorldStatus(GuiGraphics g, Minecraft mc, int width) {
        int ping = 0;
        if (mc.getConnection() != null && mc.getConnection().getPlayerInfo(mc.player.getUUID()) != null) {
            ping = mc.getConnection().getPlayerInfo(mc.player.getUUID()).getLatency();
        }
        String sector = sectorFor(mc.player.getBlockX(), mc.player.getBlockZ());
        String status = "GGO  //  " + sector + "  //  " + ping + " ms";
        int x = width - mc.font.width(status) - 18;
        g.drawString(mc.font, status, x, 14, 0xFF818D9D, false);
    }

    private static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xD90A0E14);
        g.fill(x, y, x + 3, y + h, 0xFFC73542);
        g.fill(x + 3, y, x + w, y + 1, 0xFF29323E);
    }

    private static String sectorFor(int x, int z) {
        int sx = Math.floorDiv(x, 256);
        int sz = Math.floorDiv(z, 256);
        char col = (char) ('A' + Math.floorMod(sx, 26));
        return col + "-" + Math.abs(sz);
    }
}
'''

(JAVA / "GgoCombatHud.java").write_text(hud)
print("GGO HUD Stage 4 applied")
print(" - hides vanilla hotbar/hearts/armor/hunger/xp/air overlays")
print(" - renders GGO HP/armor panel")
print(" - renders active weapon shell with weapon-runtime ammo adapter placeholder")
print(" - renders five GGO quick slots")
print(" - renders sector and ping status")
