from pathlib import Path

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
CLIENT = ROOT / "client-ui"
JAVA = CLIENT / "src/main/java/arena/client/shell"
JAVA.mkdir(parents=True, exist_ok=True)

screen = r'''package arena.client.shell;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class GgoShellScreen extends Screen {
    public enum Page { INVENTORY, ACTIVITIES, MAP, PAUSE }
    private final Page page;

    public GgoShellScreen(Page page) {
        super(Component.literal(titleFor(page)));
        this.page = page;
    }

    private static String titleFor(Page page) {
        return switch (page) {
            case INVENTORY -> "INVENTORY";
            case ACTIVITIES -> "ACTIVITIES";
            case MAP -> "NAVIGATION";
            case PAUSE -> "GUNGLORYONLINE";
        };
    }

    @Override
    protected void init() {
        if (page == Page.PAUSE) initPause();
        if (page == Page.ACTIVITIES) initActivities();
    }

    private void initPause() {
        int x = this.width / 2 - 118;
        int y = this.height / 2 - 92;
        addRenderableWidget(Button.builder(Component.literal("RESUME"), b -> onClose()).bounds(x, y, 236, 24).build());
        addRenderableWidget(Button.builder(Component.literal("INVENTORY"), b -> open(Page.INVENTORY)).bounds(x, y + 30, 236, 24).build());
        addRenderableWidget(Button.builder(Component.literal("ACTIVITIES"), b -> open(Page.ACTIVITIES)).bounds(x, y + 60, 236, 24).build());
        addRenderableWidget(Button.builder(Component.literal("NAVIGATION"), b -> open(Page.MAP)).bounds(x, y + 90, 236, 24).build());
        Button social = Button.builder(Component.literal("SOCIAL — COMING NEXT"), b -> {}).bounds(x, y + 120, 236, 24).build();
        social.active = false;
        addRenderableWidget(social);
        addRenderableWidget(Button.builder(Component.literal("BACK TO GAME"), b -> onClose()).bounds(x, y + 150, 236, 24).build());
    }

    private void initActivities() {
        int x = 34;
        int y = 92;
        addRenderableWidget(Button.builder(Component.literal("TRAINING"), b -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() != null) {
                mc.setScreen(null);
                mc.getConnection().sendCommand("play");
            }
        }).bounds(x, y, 220, 26).build());

        Button br = Button.builder(Component.literal("BATTLE ROYALE — MATCHMAKING NEXT"), b -> {})
                .bounds(x, y + 36, 220, 26).build();
        br.active = false;
        addRenderableWidget(br);

        Button events = Button.builder(Component.literal("EVENTS — COMING NEXT"), b -> {})
                .bounds(x, y + 72, 220, 26).build();
        events.active = false;
        addRenderableWidget(events);
    }

    private void open(Page next) {
        Minecraft.getInstance().setScreen(new GgoShellScreen(next));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderShellBackground(g);
        switch (page) {
            case INVENTORY -> renderInventory(g);
            case ACTIVITIES -> renderActivities(g);
            case MAP -> renderNavigation(g);
            case PAUSE -> renderPause(g);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderShellBackground(GuiGraphics g) {
        g.fill(0, 0, this.width, this.height, 0xEF080A0F);
        g.fill(0, 0, this.width, 3, 0xFFD13B48);
        g.fill(0, 3, 190, this.height, 0xC90D1017);
        g.drawString(this.font, "GUNGLORYONLINE", 22, 18, 0xFFF3F5F8, false);
        g.drawString(this.font, this.title, 22, 45, 0xFFD44855, false);
        g.drawString(this.font, "GGO CLIENT", 22, this.height - 30, 0xFF596579, false);
    }

    private void renderInventory(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        int left = 214;
        int top = 32;
        int panelW = Math.max(300, this.width - left - 28);
        g.fill(left, top, left + panelW, this.height - 28, 0xA60F131B);
        g.drawString(this.font, "EQUIPMENT", left + 18, top + 16, 0xFFC7CFDB, false);

        if (mc.player == null) {
            g.drawString(this.font, "Player data unavailable", left + 18, top + 40, 0xFF8B96A8, false);
            return;
        }

        g.drawString(this.font, mc.player.getGameProfile().getName(), left + 18, top + 36, 0xFFF2F4F8, false);
        g.drawString(this.font, "HP  " + Math.round(mc.player.getHealth()) + " / " + Math.round(mc.player.getMaxHealth()), left + 18, top + 52, 0xFF72D49A, false);

        int gridX = left + Math.max(150, panelW / 2 - 80);
        int gridY = top + 34;
        g.drawString(this.font, "BACKPACK", gridX, top + 16, 0xFFC7CFDB, false);

        for (int i = 9; i < 36; i++) {
            int slot = i - 9;
            int col = slot % 9;
            int row = slot / 9;
            drawSlot(g, mc.player.getInventory().items.get(i), gridX + col * 20, gridY + row * 20);
        }

        g.drawString(this.font, "QUICK SLOTS", gridX, gridY + 72, 0xFFC7CFDB, false);
        for (int i = 0; i < 9; i++) {
            drawSlot(g, mc.player.getInventory().items.get(i), gridX + i * 20, gridY + 88);
        }

        g.drawString(this.font, "Primary / Secondary / Sidearm / Armor / Backpack", left + 18, top + 82, 0xFF7E899B, false);
        g.drawString(this.font, "Dedicated GGO equipment slots are the next inventory stage.", left + 18, top + 100, 0xFF5F6B7D, false);
        g.drawString(this.font, "E or ESC  Close", left + 18, this.height - 52, 0xFF677386, false);
    }

    private void drawSlot(GuiGraphics g, ItemStack stack, int x, int y) {
        g.fill(x, y, x + 18, y + 18, 0xD7191E28);
        g.fill(x + 1, y + 1, x + 17, y + 17, 0xA60B0E14);
        if (!stack.isEmpty()) {
            g.renderItem(stack, x + 1, y + 1);
            g.renderItemDecorations(this.font, stack, x + 1, y + 1);
        }
    }

    private void renderActivities(GuiGraphics g) {
        int x = 286;
        int y = 78;
        g.drawString(this.font, "GGO ACTIVITIES", x, y, 0xFFF1F3F7, false);
        g.drawString(this.font, "Training is available now. Battle Royale will use the new matchmaking service.", x, y + 20, 0xFF7F8B9E, false);
        g.drawString(this.font, "M or ESC  Close", x, this.height - 52, 0xFF677386, false);
    }

    private void renderNavigation(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        int x = 220;
        int y = 36;
        int right = this.width - 28;
        int bottom = this.height - 30;
        g.fill(x, y, right, bottom, 0xA50D1118);
        g.drawString(this.font, "TACTICAL NAVIGATION", x + 18, y + 16, 0xFFF1F3F7, false);

        if (mc.player != null) {
            int px = mc.player.getBlockX();
            int py = mc.player.getBlockY();
            int pz = mc.player.getBlockZ();
            int sx = Math.floorDiv(px, 256);
            int sz = Math.floorDiv(pz, 256);
            String sector = sectorName(sx, sz);
            String heading = heading(mc.player.getYRot());

            g.drawString(this.font, "POSITION   " + px + " / " + py + " / " + pz, x + 18, y + 42, 0xFFC2CBD8, false);
            g.drawString(this.font, "SECTOR     " + sector, x + 18, y + 60, 0xFFD44855, false);
            g.drawString(this.font, "HEADING    " + heading, x + 18, y + 78, 0xFFC2CBD8, false);

            int mapL = x + 18;
            int mapT = y + 110;
            int mapR = right - 18;
            int mapB = bottom - 42;
            g.fill(mapL, mapT, mapR, mapB, 0xD3070A0F);
            for (int gx = mapL + 32; gx < mapR; gx += 32) g.fill(gx, mapT, gx + 1, mapB, 0x332B3543);
            for (int gy = mapT + 32; gy < mapB; gy += 32) g.fill(mapL, gy, mapR, gy + 1, 0x332B3543);
            int cx = (mapL + mapR) / 2;
            int cy = (mapT + mapB) / 2;
            g.fill(cx - 3, cy - 3, cx + 4, cy + 4, 0xFFE04A56);
            g.drawString(this.font, "YOU", cx + 8, cy - 4, 0xFFF0F3F7, false);
        }
        g.drawString(this.font, "Full terrain tiles / objectives / squad markers are Map Stage 2.", x + 18, bottom - 24, 0xFF667386, false);
    }

    private String sectorName(int sx, int sz) {
        char letter = (char) ('A' + Math.floorMod(sx, 26));
        return letter + "-" + (Math.abs(sz) + 1);
    }

    private String heading(float yaw) {
        float y = (yaw % 360 + 360) % 360;
        if (y < 45 || y >= 315) return "S";
        if (y < 135) return "W";
        if (y < 225) return "N";
        return "E";
    }

    private void renderPause(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            g.drawString(this.font, mc.player.getGameProfile().getName(), 22, 72, 0xFFF1F3F7, false);
            g.drawString(this.font, "GGO SESSION", 22, 89, 0xFF68758A, false);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 69 && page == Page.INVENTORY) { onClose(); return true; } // E
        if (keyCode == 77 && page == Page.ACTIVITIES) { onClose(); return true; } // M
        if (keyCode == 78 && page == Page.MAP) { onClose(); return true; } // N
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return page == Page.PAUSE;
    }
}
'''

hooks = r'''package arena.client.shell;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoShellHooks {
    private GgoShellHooks() {}

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getNewScreen() instanceof GgoShellScreen) return;
        if (event.getNewScreen() instanceof InventoryScreen) {
            event.setNewScreen(new GgoShellScreen(GgoShellScreen.Page.INVENTORY));
        } else if (event.getNewScreen() instanceof PauseScreen) {
            event.setNewScreen(new GgoShellScreen(GgoShellScreen.Page.PAUSE));
        }
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (event.getAction() != GLFW.GLFW_PRESS || mc.screen != null) return;

        if (event.getKey() == GLFW.GLFW_KEY_M) {
            mc.setScreen(new GgoShellScreen(GgoShellScreen.Page.ACTIVITIES));
        } else if (event.getKey() == GLFW.GLFW_KEY_N) {
            mc.setScreen(new GgoShellScreen(GgoShellScreen.Page.MAP));
        }
    }

    @SubscribeEvent
    public static void onPlayerListOverlay(RenderGuiOverlayEvent.Pre event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.PLAYER_LIST.id())) return;
        event.setCanceled(true);

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.options.keyPlayerList.isDown()) return;

        GuiGraphics g = event.getGuiGraphics();
        int width = mc.getWindow().getGuiScaledWidth();
        int boxW = Math.min(420, width - 32);
        int x = (width - boxW) / 2;
        int y = 14;
        int ping = -1;
        PlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(mc.player.getUUID());
        if (info != null) ping = info.getLatency();
        int sx = Math.floorDiv(mc.player.getBlockX(), 256);
        int sz = Math.floorDiv(mc.player.getBlockZ(), 256);
        char letter = (char) ('A' + Math.floorMod(sx, 26));
        String sector = letter + "-" + (Math.abs(sz) + 1);

        g.fill(x, y, x + boxW, y + 68, 0xE0090C12);
        g.fill(x, y, x + 3, y + 68, 0xFFD44855);
        g.drawString(mc.font, "GGO // SQUAD", x + 12, y + 10, 0xFFF5F6F8, false);
        g.drawString(mc.font, mc.player.getGameProfile().getName(), x + 12, y + 28, 0xFFD7DEE8, false);
        g.drawString(mc.font, "HP " + Math.round(mc.player.getHealth()) + "/" + Math.round(mc.player.getMaxHealth()), x + 150, y + 28, 0xFF74D79D, false);
        g.drawString(mc.font, "PING " + (ping < 0 ? "--" : ping + " ms"), x + 250, y + 28, 0xFF8A96A9, false);
        g.drawString(mc.font, "SECTOR " + sector, x + 12, y + 47, 0xFFD44855, false);
        g.drawString(mc.font, "Match data / party members / voice status next", x + 150, y + 47, 0xFF667286, false);
    }
}
'''

(JAVA / "GgoShellScreen.java").write_text(screen)
(JAVA / "GgoShellHooks.java").write_text(hooks)

print("GGO UI Shell Stage 2 applied:")
print(" - real player inventory item rendering")
print(" - functional Training action via existing /play")
print(" - tactical navigation position / sector / heading")
print(" - TAB HP / ping / sector overlay")
print(" - GGO pause navigation")
