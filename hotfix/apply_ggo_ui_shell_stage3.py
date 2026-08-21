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
    public enum Page { INVENTORY, ACTIVITIES, MAP, SOCIAL, PAUSE }

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
            case SOCIAL -> "SOCIAL";
            case PAUSE -> "GUNGLORYONLINE";
        };
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        if (page == Page.PAUSE) {
            int x = cx - 115;
            int y = Math.max(70, this.height / 2 - 95);
            addRenderableWidget(Button.builder(Component.literal("RESUME"), b -> onClose()).bounds(x, y, 230, 24).build());
            addRenderableWidget(Button.builder(Component.literal("INVENTORY"), b -> open(Page.INVENTORY)).bounds(x, y + 31, 230, 24).build());
            addRenderableWidget(Button.builder(Component.literal("ACTIVITIES"), b -> open(Page.ACTIVITIES)).bounds(x, y + 62, 230, 24).build());
            addRenderableWidget(Button.builder(Component.literal("NAVIGATION"), b -> open(Page.MAP)).bounds(x, y + 93, 230, 24).build());
            addRenderableWidget(Button.builder(Component.literal("SOCIAL"), b -> open(Page.SOCIAL)).bounds(x, y + 124, 230, 24).build());
            addRenderableWidget(Button.builder(Component.literal("SETTINGS"), b -> Minecraft.getInstance().setScreen(new net.minecraft.client.gui.screens.OptionsScreen(this, Minecraft.getInstance().options))).bounds(x, y + 155, 230, 24).build());
        } else if (page == Page.ACTIVITIES) {
            int x = Math.max(24, cx - 300);
            int y = 110;
            addRenderableWidget(Button.builder(Component.literal("TRAINING"), b -> runClientCommand("play")).bounds(x, y, 180, 28).build());
            addRenderableWidget(Button.builder(Component.literal("BATTLE ROYALE — SOON"), b -> {}).bounds(x + 195, y, 180, 28).build());
            addRenderableWidget(Button.builder(Component.literal("EVENTS — SOON"), b -> {}).bounds(x + 390, y, 180, 28).build());
        }
    }

    private void open(Page next) {
        Minecraft.getInstance().setScreen(new GgoShellScreen(next));
    }

    private void runClientCommand(String command) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.connection != null) {
            mc.player.connection.sendCommand(command);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackdrop(g);
        renderHeader(g);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            renderPlayerStatus(g, mc);
            switch (page) {
                case INVENTORY -> renderInventory(g, mc);
                case ACTIVITIES -> renderActivities(g);
                case MAP -> renderNavigation(g, mc);
                case SOCIAL -> renderSocial(g, mc);
                case PAUSE -> renderPauseInfo(g, mc);
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderBackdrop(GuiGraphics g) {
        g.fill(0, 0, this.width, this.height, 0xF207090D);
        g.fill(0, 0, this.width, 3, 0xFFC72F3C);
        g.fill(0, 56, this.width, 57, 0xFF1A202A);
    }

    private void renderHeader(GuiGraphics g) {
        g.drawString(this.font, "GUNGLORYONLINE", 24, 18, 0xFFF4F6F8, false);
        g.drawString(this.font, this.title, 24, 36, 0xFFD34B57, false);
        if (page != Page.PAUSE) {
            g.drawString(this.font, "ESC  BACK", 24, this.height - 26, 0xFF6E7887, false);
        }
    }

    private void renderPlayerStatus(GuiGraphics g, Minecraft mc) {
        String name = mc.player.getGameProfile().getName();
        int hp = Math.round(mc.player.getHealth());
        int maxHp = Math.round(mc.player.getMaxHealth());
        int ping = 0;
        if (mc.getConnection() != null && mc.getConnection().getPlayerInfo(mc.player.getUUID()) != null) {
            ping = mc.getConnection().getPlayerInfo(mc.player.getUUID()).getLatency();
        }
        String sector = sectorFor(mc.player.getBlockX(), mc.player.getBlockZ());
        String status = name + "   HP " + hp + "/" + maxHp + "   " + ping + " ms   SECTOR " + sector;
        g.drawString(this.font, status, this.width - this.font.width(status) - 24, 20, 0xFF93A0B2, false);
    }

    private void renderInventory(GuiGraphics g, Minecraft mc) {
        int left = 24;
        int top = 82;
        int panelH = this.height - 130;
        int profileW = Math.max(190, this.width / 5);
        int statsW = Math.max(170, this.width / 6);
        int bagX = left + profileW + statsW + 24;
        int bagW = this.width - bagX - 24;

        panel(g, left, top, profileW, panelH, "PROFILE / EQUIPMENT");
        panel(g, left + profileW + 12, top, statsW, panelH, "COMBAT STATUS");
        panel(g, bagX, top, bagW, panelH, "BACKPACK");

        g.drawString(this.font, mc.player.getGameProfile().getName(), left + 14, top + 30, 0xFFF0F2F5, false);
        g.drawString(this.font, "GGO PLAYER", left + 14, top + 46, 0xFF78869A, false);
        g.drawString(this.font, "Primary", left + 14, top + 82, 0xFF8895A7, false);
        renderStack(g, mc.player.getInventory().getItem(0), left + 78, top + 74);
        g.drawString(this.font, "Secondary", left + 14, top + 116, 0xFF8895A7, false);
        renderStack(g, mc.player.getInventory().getItem(1), left + 78, top + 108);
        g.drawString(this.font, "Sidearm", left + 14, top + 150, 0xFF8895A7, false);
        renderStack(g, mc.player.getInventory().getItem(2), left + 78, top + 142);

        int sx = left + profileW + 26;
        g.drawString(this.font, "HEALTH", sx, top + 34, 0xFF7F8A9A, false);
        g.drawString(this.font, Math.round(mc.player.getHealth()) + " / " + Math.round(mc.player.getMaxHealth()), sx, top + 51, 0xFFE8EBEF, false);
        g.drawString(this.font, "ARMOR", sx, top + 82, 0xFF7F8A9A, false);
        g.drawString(this.font, String.valueOf(mc.player.getArmorValue()), sx, top + 99, 0xFFE8EBEF, false);
        g.drawString(this.font, "LEVEL", sx, top + 130, 0xFF7F8A9A, false);
        g.drawString(this.font, String.valueOf(mc.player.experienceLevel), sx, top + 147, 0xFFE8EBEF, false);

        int cols = Math.max(6, Math.min(10, (bagW - 28) / 24));
        int startX = bagX + 14;
        int startY = top + 34;
        for (int i = 0; i < 36; i++) {
            int col = i % cols;
            int row = i / cols;
            int x = startX + col * 24;
            int y = startY + row * 24;
            slot(g, x, y);
            renderStack(g, mc.player.getInventory().getItem(i), x + 3, y + 3);
        }

        g.drawString(this.font, "QUICK SLOTS", bagX + 14, top + panelH - 60, 0xFF7F8A9A, false);
        for (int i = 0; i < 5; i++) {
            int x = bagX + 14 + i * 34;
            int y = top + panelH - 38;
            slotLarge(g, x, y);
            renderStack(g, mc.player.getInventory().getItem(i), x + 7, y + 7);
        }
    }

    private void renderActivities(GuiGraphics g) {
        int y = 78;
        g.drawString(this.font, "Choose what you want to do in GGO.", 24, y, 0xFF8996A9, false);
        card(g, 24, 160, 260, 128, "TRAINING", "Local combat training", "AVAILABLE");
        card(g, 300, 160, 260, 128, "BATTLE ROYALE", "Dropzone / Mini PUBG", "MATCHMAKING NEXT");
        card(g, 576, 160, 260, 128, "EVENTS", "Seasonal operations", "COMING SOON");
    }

    private void renderNavigation(GuiGraphics g, Minecraft mc) {
        int x = mc.player.getBlockX();
        int y = mc.player.getBlockY();
        int z = mc.player.getBlockZ();
        String sector = sectorFor(x, z);
        String facing = mc.player.getDirection().getName().toUpperCase();
        int mapX = 24;
        int mapY = 82;
        int mapW = this.width - 48;
        int mapH = this.height - 132;
        g.fill(mapX, mapY, mapX + mapW, mapY + mapH, 0xFF10151C);
        g.fill(mapX + 1, mapY + 1, mapX + mapW - 1, mapY + mapH - 1, 0xFF0B0F14);
        for (int gx = mapX + 32; gx < mapX + mapW; gx += 48) g.fill(gx, mapY, gx + 1, mapY + mapH, 0xFF17202B);
        for (int gy = mapY + 32; gy < mapY + mapH; gy += 48) g.fill(mapX, gy, mapX + mapW, gy + 1, 0xFF17202B);
        int px = mapX + mapW / 2;
        int py = mapY + mapH / 2;
        g.fill(px - 3, py - 3, px + 4, py + 4, 0xFFD84855);
        g.drawString(this.font, "YOU", px + 10, py - 4, 0xFFF0F2F5, false);
        g.drawString(this.font, "SECTOR " + sector + "   " + x + " / " + y + " / " + z + "   FACING " + facing, mapX + 14, mapY + 14, 0xFF9AA6B7, false);
        g.drawString(this.font, "MMB  PLACE PING    Minimap: disabled by default", mapX + 14, mapY + mapH - 22, 0xFF697688, false);
    }

    private void renderSocial(GuiGraphics g, Minecraft mc) {
        int left = 24;
        int top = 82;
        panel(g, left, top, 300, this.height - 130, "SQUAD");
        panel(g, left + 316, top, 360, this.height - 130, "FRIENDS");
        panel(g, left + 692, top, Math.max(220, this.width - left - 692), this.height - 130, "ACTIVITY");
        g.drawString(this.font, mc.player.getGameProfile().getName() + "  •  ONLINE", left + 14, top + 34, 0xFFF0F2F5, false);
        g.drawString(this.font, "Party system will use GGO Account identities.", left + 330, top + 34, 0xFF8794A6, false);
        g.drawString(this.font, "Invites / recent players / voice status", left + 706, top + 34, 0xFF8794A6, false);
    }

    private void renderPauseInfo(GuiGraphics g, Minecraft mc) {
        String s = "GGO CLIENT  •  " + sectorFor(mc.player.getBlockX(), mc.player.getBlockZ());
        g.drawString(this.font, s, 24, this.height - 26, 0xFF697688, false);
    }

    private void panel(GuiGraphics g, int x, int y, int w, int h, String title) {
        g.fill(x, y, x + w, y + h, 0xFF0D1218);
        g.fill(x, y, x + w, y + 1, 0xFF27313D);
        g.drawString(this.font, title, x + 12, y + 10, 0xFFB9C3D1, false);
    }

    private void card(GuiGraphics g, int x, int y, int w, int h, String title, String body, String status) {
        g.fill(x, y, x + w, y + h, 0xFF0D1218);
        g.fill(x, y, x + 3, y + h, 0xFFC93643);
        g.drawString(this.font, title, x + 16, y + 18, 0xFFF0F2F5, false);
        g.drawString(this.font, body, x + 16, y + 44, 0xFF8996A9, false);
        g.drawString(this.font, status, x + 16, y + h - 26, 0xFFD34B57, false);
    }

    private void slot(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 22, y + 22, 0xFF151C24);
        g.fill(x + 1, y + 1, x + 21, y + 21, 0xFF0B1016);
    }

    private void slotLarge(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 30, y + 30, 0xFF202934);
        g.fill(x + 1, y + 1, x + 29, y + 29, 0xFF0B1016);
    }

    private void renderStack(GuiGraphics g, ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty()) return;
        g.renderItem(stack, x, y);
        g.renderItemDecorations(this.font, stack, x, y);
    }

    private static String sectorFor(int x, int z) {
        int sx = Math.floorDiv(x, 256);
        int sz = Math.floorDiv(z, 256);
        char col = (char) ('A' + Math.floorMod(sx, 26));
        return col + "-" + Math.abs(sz);
    }

    @Override
    public boolean isPauseScreen() {
        return page == Page.PAUSE;
    }
}
'''

hooks_path = JAVA / "GgoShellHooks.java"
if hooks_path.exists():
    hooks = hooks_path.read_text()
    hooks = hooks.replace('GgoShellScreen.Page.PAUSE));', 'GgoShellScreen.Page.PAUSE));')
    hooks_path.write_text(hooks)

(JAVA / "GgoShellScreen.java").write_text(screen)
print("GGO UI Shell Stage 3 applied")
print(" - visual inventory layout with real ItemStacks")
print(" - activities cards")
print(" - navigation grid + real position/sector")
print(" - social page")
print(" - expanded pause hub")
