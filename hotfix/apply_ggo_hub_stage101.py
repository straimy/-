from pathlib import Path

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
JAVA = ROOT / "client-ui/src/main/java/arena/client/shell"
JAVA.mkdir(parents=True, exist_ok=True)

screen = r'''package arena.client.shell;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Stage101 canonical in-session GunGloryOnline hub. */
public final class GgoShellScreen extends Screen {
    public enum Page { HOME, ACTIVITIES, INVENTORY, CONTRACTS, SOCIAL, PROFILE, SHOP, SKILLS, SEASON, MAP, PAUSE }

    private final Page page;

    public GgoShellScreen(Page page) {
        super(Component.literal(titleFor(page)));
        this.page = page;
    }

    private static String titleFor(Page page) {
        return switch (page) {
            case HOME -> "GGO HUB";
            case ACTIVITIES -> "ACTIVITIES";
            case INVENTORY -> "INVENTORY / LOADOUT";
            case CONTRACTS -> "CONTRACTS";
            case SOCIAL -> "SOCIAL";
            case PROFILE -> "PROFILE";
            case SHOP -> "STORE";
            case SKILLS -> "SKILLS";
            case SEASON -> "SEASON / EVENTS";
            case MAP -> "NAVIGATION";
            case PAUSE -> "GAME MENU";
        };
    }

    @Override
    protected void init() {
        if (page == Page.PAUSE) {
            initPause();
            return;
        }
        initTopNavigation();
        switch (page) {
            case HOME -> initHome();
            case ACTIVITIES -> initActivities();
            case SOCIAL -> initSocial();
            case PROFILE -> initProfile();
            case SHOP -> initShop();
            case SKILLS -> initSkills();
            default -> {}
        }
    }

    private void initTopNavigation() {
        int gap = 6;
        int count = 7;
        int available = Math.max(700, width - 48);
        int bw = Math.max(88, Math.min(150, (available - gap * (count - 1)) / count));
        int total = bw * count + gap * (count - 1);
        int x = Math.max(24, (width - total) / 2);
        int y = 34;
        nav(x, y, bw, "HOME", Page.HOME); x += bw + gap;
        nav(x, y, bw, "PLAY", Page.ACTIVITIES); x += bw + gap;
        nav(x, y, bw, "LOADOUT", Page.INVENTORY); x += bw + gap;
        nav(x, y, bw, "CONTRACTS", Page.CONTRACTS); x += bw + gap;
        nav(x, y, bw, "SOCIAL", Page.SOCIAL); x += bw + gap;
        nav(x, y, bw, "PROFILE", Page.PROFILE); x += bw + gap;
        nav(x, y, bw, "MORE", Page.SEASON);
    }

    private void nav(int x, int y, int w, String label, Page target) {
        Button b = Button.builder(Component.literal(label), button -> open(target)).bounds(x, y, w, 22).build();
        b.active = page != target;
        addRenderableWidget(b);
    }

    private void initHome() {
        int w = Math.min(420, Math.max(300, width / 3));
        int x = Math.max(32, width / 2 - w / 2);
        int y = Math.max(150, height / 2 - 95);
        addRenderableWidget(Button.builder(Component.literal("ENTER GGO / ACTIVITIES"), b -> open(Page.ACTIVITIES)).bounds(x, y, w, 30).build());
        addRenderableWidget(Button.builder(Component.literal("INVENTORY / LOADOUT"), b -> open(Page.INVENTORY)).bounds(x, y + 38, w, 26).build());
        addRenderableWidget(Button.builder(Component.literal("STORE"), b -> open(Page.SHOP)).bounds(x, y + 72, w, 26).build());
        addRenderableWidget(Button.builder(Component.literal("PROFILE / SKILLS"), b -> open(Page.PROFILE)).bounds(x, y + 106, w, 26).build());
        addRenderableWidget(Button.builder(Component.literal("SOCIAL / CLANS"), b -> open(Page.SOCIAL)).bounds(x, y + 140, w, 26).build());
    }

    private void initPause() {
        int w = Math.min(300, Math.max(230, width / 3));
        int x = (width - w) / 2;
        int y = Math.max(80, height / 2 - 120);
        addRenderableWidget(Button.builder(Component.literal("RESUME"), b -> onClose()).bounds(x, y, w, 24).build());
        addRenderableWidget(Button.builder(Component.literal("GGO HUB"), b -> open(Page.HOME)).bounds(x, y + 31, w, 24).build());
        addRenderableWidget(Button.builder(Component.literal("INVENTORY"), b -> open(Page.INVENTORY)).bounds(x, y + 62, w, 24).build());
        addRenderableWidget(Button.builder(Component.literal("ACTIVITIES"), b -> open(Page.ACTIVITIES)).bounds(x, y + 93, w, 24).build());
        addRenderableWidget(Button.builder(Component.literal("SOCIAL"), b -> open(Page.SOCIAL)).bounds(x, y + 124, w, 24).build());
        addRenderableWidget(Button.builder(Component.literal("SETTINGS"), b -> Minecraft.getInstance().setScreen(new GgoSettingsScreen(this))).bounds(x, y + 155, w, 24).build());
        addRenderableWidget(Button.builder(Component.literal("EXIT TO GGO"), b -> exitToGgo()).bounds(x, y + 186, w, 24).build());
    }

    private void initActivities() {
        int w = Math.min(260, Math.max(190, (width - 96) / 3));
        int total = w * 3 + 16 * 2;
        int x = Math.max(24, (width - total) / 2);
        int y = 92;
        addRenderableWidget(Button.builder(Component.literal("TRAINING"), b -> runClientCommand("play")).bounds(x, y, w, 28).build());
        Button br = Button.builder(Component.literal("BATTLE ROYALE — SOON"), b -> {}).bounds(x + w + 16, y, w, 28).build(); br.active = false; addRenderableWidget(br);
        Button events = Button.builder(Component.literal("EVENTS — SOON"), b -> open(Page.SEASON)).bounds(x + (w + 16) * 2, y, w, 28).build(); events.active = false; addRenderableWidget(events);
    }

    private void initSocial() {
        int w = Math.min(250, Math.max(180, width / 5));
        int x = width / 2 - w - 8;
        int y = 104;
        addRenderableWidget(Button.builder(Component.literal("FRIENDS"), b -> runClientCommand("friends")).bounds(x, y, w, 26).build());
        addRenderableWidget(Button.builder(Component.literal("CLANS"), b -> runClientCommand("clan")).bounds(x + w + 16, y, w, 26).build());
    }

    private void initProfile() {
        int w = Math.min(250, Math.max(180, width / 5));
        int x = width / 2 - w - 8;
        int y = 104;
        addRenderableWidget(Button.builder(Component.literal("PROFILE DETAILS"), b -> runClientCommand("profile")).bounds(x, y, w, 26).build());
        addRenderableWidget(Button.builder(Component.literal("SKILLS"), b -> open(Page.SKILLS)).bounds(x + w + 16, y, w, 26).build());
    }

    private void initShop() {
        int w = Math.min(360, Math.max(240, width / 3));
        int x = (width - w) / 2;
        int y = 112;
        addRenderableWidget(Button.builder(Component.literal("OPEN MAIN STORE"), b -> runClientCommand("shop")).bounds(x, y, w, 28).build());
    }

    private void initSkills() {
        int w = Math.min(360, Math.max(240, width / 3));
        int x = (width - w) / 2;
        int y = 112;
        addRenderableWidget(Button.builder(Component.literal("OPEN SKILLS / PROGRESSION"), b -> runClientCommand("skills")).bounds(x, y, w, 28).build());
    }

    private void open(Page next) {
        Minecraft.getInstance().setScreen(new GgoShellScreen(next));
    }

    private void runClientCommand(String command) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.connection != null) {
            mc.setScreen(null);
            mc.player.connection.sendCommand(command);
        }
    }

    private void exitToGgo() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            mc.getConnection().getConnection().disconnect(Component.literal("Return to GunGloryOnline"));
        }
        mc.execute(() -> mc.setScreen(new GgoFrontEndScreen()));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        GgoRuntimeV1ContractAdapter.tick();
        renderBackdrop(g);
        renderHeader(g);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            renderPlayerStatus(g, mc);
            switch (page) {
                case HOME -> renderHome(g, mc);
                case ACTIVITIES -> renderActivities(g);
                case INVENTORY -> renderInventory(g, mc);
                case CONTRACTS -> renderContracts(g);
                case SOCIAL -> renderSocial(g, mc);
                case PROFILE -> renderProfile(g, mc);
                case SHOP -> renderShop(g);
                case SKILLS -> renderSkills(g, mc);
                case SEASON -> renderSeason(g);
                case MAP -> renderNavigation(g, mc);
                case PAUSE -> renderPauseInfo(g, mc);
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderBackdrop(GuiGraphics g) {
        g.fill(0, 0, width, height, 0xF207090D);
        g.fill(0, 0, width, 3, 0xFFC72F3C);
        g.fill(0, 66, width, 67, 0xFF1A202A);
    }

    private void renderHeader(GuiGraphics g) {
        g.drawString(font, "GUNGLORYONLINE", 24, 14, 0xFFF4F6F8, false);
        g.drawString(font, title, 24, 72, 0xFFD34B57, false);
        if (page != Page.PAUSE) {
            g.drawString(font, "M  GGO HUB     N  NAVIGATION     J  ACTIVITIES     ESC  BACK", 24, height - 24, 0xFF6E7887, false);
        }
    }

    private void renderPlayerStatus(GuiGraphics g, Minecraft mc) {
        String name = mc.player.getGameProfile().getName();
        int hp = Math.round(mc.player.getHealth());
        int maxHp = Math.round(mc.player.getMaxHealth());
        int ping = 0;
        if (mc.getConnection() != null && mc.getConnection().getPlayerInfo(mc.player.getUUID()) != null) ping = mc.getConnection().getPlayerInfo(mc.player.getUUID()).getLatency();
        String status = name + "   HP " + hp + "/" + maxHp + "   " + ping + " ms   GGO ONLINE";
        g.drawString(font, status, width - font.width(status) - 24, 14, 0xFF93A0B2, false);
    }

    private void renderHome(GuiGraphics g, Minecraft mc) {
        int x = 32;
        int y = 100;
        g.drawString(font, "WELCOME BACK, " + mc.player.getGameProfile().getName().toUpperCase(), x, y, 0xFFF0F2F5, false);
        g.drawString(font, "Official GunGloryOnline session", x, y + 18, 0xFF8794A6, false);
        card(g, 32, y + 48, 250, 92, "ACTIVITIES", "Training / Battle Royale / Events", "ENTER GGO");
        card(g, 298, y + 48, 250, 92, "LOADOUT", "Weapons / armor / backpack / quick slots", "READY");
        card(g, 564, y + 48, 250, 92, "CONTRACTS", "Tracked objectives and rewards", GgoContractState.entries().isEmpty() ? "SYNCING" : GgoContractState.entries().size() + " AVAILABLE");
        card(g, 32, y + 154, 250, 92, "SOCIAL", "Friends / squad / clans", "ONLINE");
        card(g, 298, y + 154, 250, 92, "PROFILE", "Level / stats / progression", "PLAYER");
        card(g, 564, y + 154, 250, 92, "SEASON", "Events / seasonal progression", "COMING NEXT");
    }

    private void renderActivities(GuiGraphics g) {
        g.drawString(font, "Choose what you want to do in GunGloryOnline.", 24, 142, 0xFF8996A9, false);
        card(g, 24, 176, 260, 118, "TRAINING", "Combat training", "AVAILABLE");
        card(g, 300, 176, 260, 118, "BATTLE ROYALE", "Dropzone / Mini PUBG", "MATCHMAKING NEXT");
        card(g, 576, 176, 260, 118, "EVENTS", "Seasonal operations", "COMING SOON");
    }

    private void renderInventory(GuiGraphics g, Minecraft mc) {
        int left = 24, top = 102;
        int panelH = Math.max(220, height - 150);
        int profileW = Math.max(190, width / 5);
        int statsW = Math.max(170, width / 6);
        int bagX = left + profileW + statsW + 24;
        int bagW = width - bagX - 24;
        panel(g, left, top, profileW, panelH, "EQUIPMENT");
        panel(g, left + profileW + 12, top, statsW, panelH, "COMBAT STATUS");
        panel(g, bagX, top, bagW, panelH, "BACKPACK");
        g.drawString(font, "Primary", left + 14, top + 44, 0xFF8895A7, false); renderStack(g, mc.player.getInventory().getItem(0), left + 86, top + 36);
        g.drawString(font, "Secondary", left + 14, top + 78, 0xFF8895A7, false); renderStack(g, mc.player.getInventory().getItem(1), left + 86, top + 70);
        g.drawString(font, "Sidearm", left + 14, top + 112, 0xFF8895A7, false); renderStack(g, mc.player.getInventory().getItem(2), left + 86, top + 104);
        int sx = left + profileW + 26;
        g.drawString(font, "HEALTH  " + Math.round(mc.player.getHealth()) + "/" + Math.round(mc.player.getMaxHealth()), sx, top + 44, 0xFFE8EBEF, false);
        g.drawString(font, "ARMOR   " + mc.player.getArmorValue(), sx, top + 68, 0xFFE8EBEF, false);
        g.drawString(font, "LEVEL   " + mc.player.experienceLevel, sx, top + 92, 0xFFE8EBEF, false);
        int cols = Math.max(6, Math.min(10, Math.max(1, (bagW - 28) / 24)));
        for (int i = 0; i < 36; i++) {
            int x = bagX + 14 + (i % cols) * 24;
            int y = top + 38 + (i / cols) * 24;
            slot(g, x, y); renderStack(g, mc.player.getInventory().getItem(i), x + 3, y + 3);
        }
    }

    private void renderContracts(GuiGraphics g) {
        int y = 112;
        g.drawString(font, "AVAILABLE CONTRACTS", 24, y, 0xFFB9C3D1, false);
        y += 24;
        int i = 0;
        for (var c : GgoContractState.entries()) {
            boolean tracked = c.id().equals(GgoContractState.trackedId());
            g.fill(24, y, Math.min(width - 24, 820), y + 38, tracked ? 0xFF202832 : 0xFF0D1218);
            g.fill(24, y, 27, y + 38, tracked ? 0xFFD34B57 : 0xFF384454);
            g.drawString(font, (tracked ? "TRACKED  " : "") + c.title(), 38, y + 7, tracked ? 0xFFF3F5F7 : 0xFFD5DAE1, false);
            g.drawString(font, c.current() + "/" + c.target() + "   +" + c.rewardCredits() + " CR", Math.min(width - 210, 650), y + 7, 0xFF8E9AAC, false);
            g.drawString(font, c.description(), 38, y + 22, 0xFF748195, false);
            y += 46; if (++i >= 8) break;
        }
        if (i == 0) g.drawString(font, "SYNCING CONTRACTS...", 38, y, 0xFF657183, false);
        g.drawString(font, "CLICK A CONTRACT TO TRACK", 24, Math.min(height - 50, y + 8), 0xFF657183, false);
    }

    private void renderSocial(GuiGraphics g, Minecraft mc) {
        panel(g, 24, 150, 300, Math.max(180, height - 205), "SQUAD / PARTY");
        panel(g, 340, 150, 320, Math.max(180, height - 205), "FRIENDS");
        panel(g, 676, 150, Math.max(220, width - 700), Math.max(180, height - 205), "CLANS / ACTIVITY");
        g.drawString(font, mc.player.getGameProfile().getName() + "  •  ONLINE", 38, 184, 0xFFF0F2F5, false);
        g.drawString(font, "GGO Account identity", 38, 203, 0xFF8794A6, false);
    }

    private void renderProfile(GuiGraphics g, Minecraft mc) {
        int x = 24, y = 150;
        panel(g, x, y, Math.min(620, width - 48), Math.max(230, height - 205), "GGO PROFILE");
        g.drawString(font, mc.player.getGameProfile().getName(), x + 18, y + 38, 0xFFF0F2F5, false);
        g.drawString(font, "LEVEL  " + mc.player.experienceLevel, x + 18, y + 66, 0xFFB9C3D1, false);
        g.drawString(font, "HEALTH  " + Math.round(mc.player.getHealth()) + "/" + Math.round(mc.player.getMaxHealth()), x + 18, y + 88, 0xFFB9C3D1, false);
        g.drawString(font, "Rank / statistics / seasonal profile continue to use server snapshots.", x + 18, y + 124, 0xFF748195, false);
    }

    private void renderShop(GuiGraphics g) {
        card(g, width / 2 - 210, 160, 420, 120, "GGO STORE", "Main progression store. Combat buy menu remains on G.", "SERVER CATALOG");
    }

    private void renderSkills(GuiGraphics g, Minecraft mc) {
        card(g, 24, 160, 260, 116, "COMBAT", "Weapon handling and combat progression", "LEVEL " + mc.player.experienceLevel);
        card(g, 300, 160, 260, 116, "SURVIVAL", "Medical / armor / utility progression", "PROGRESSION");
        card(g, 576, 160, 260, 116, "TACTICAL", "Squad and objective progression", "PROGRESSION");
    }

    private void renderSeason(GuiGraphics g) {
        card(g, 24, 150, 300, 130, "SEASON", "Seasonal progression and rewards", "COMING NEXT");
        card(g, 340, 150, 300, 130, "EVENTS", "Limited-time GGO operations", "COMING SOON");
    }

    private void renderNavigation(GuiGraphics g, Minecraft mc) {
        int mapX = 24, mapY = 104, mapW = width - 48, mapH = height - 154;
        g.fill(mapX, mapY, mapX + mapW, mapY + mapH, 0xFF0B0F14);
        for (int gx = mapX + 32; gx < mapX + mapW; gx += 48) g.fill(gx, mapY, gx + 1, mapY + mapH, 0xFF17202B);
        for (int gy = mapY + 32; gy < mapY + mapH; gy += 48) g.fill(mapX, gy, mapX + mapW, gy + 1, 0xFF17202B);
        int px = mapX + mapW / 2, py = mapY + mapH / 2;
        g.fill(px - 3, py - 3, px + 4, py + 4, 0xFFD84855);
        g.drawString(font, "YOU", px + 10, py - 4, 0xFFF0F2F5, false);
        g.drawString(font, "POSITION  " + mc.player.getBlockX() + " / " + mc.player.getBlockY() + " / " + mc.player.getBlockZ(), mapX + 14, mapY + 14, 0xFF9AA6B7, false);
        var tracked = GgoContractState.tracked();
        if (tracked != null) g.drawString(font, "TRACKED  " + tracked.title() + "  " + tracked.current() + "/" + tracked.target(), mapX + 14, mapY + 34, 0xFFD34B57, false);
    }

    private void renderPauseInfo(GuiGraphics g, Minecraft mc) {
        g.drawString(font, "GGO CLIENT  •  OFFICIAL SESSION", 24, height - 26, 0xFF697688, false);
    }

    private void panel(GuiGraphics g, int x, int y, int w, int h, String title) {
        g.fill(x, y, x + w, y + h, 0xFF0D1218);
        g.fill(x, y, x + w, y + 1, 0xFF27313D);
        g.drawString(font, title, x + 12, y + 10, 0xFFB9C3D1, false);
    }

    private void card(GuiGraphics g, int x, int y, int w, int h, String title, String body, String status) {
        g.fill(x, y, x + w, y + h, 0xFF0D1218);
        g.fill(x, y, x + 3, y + h, 0xFFC93643);
        g.drawString(font, title, x + 16, y + 16, 0xFFF0F2F5, false);
        g.drawString(font, body, x + 16, y + 42, 0xFF8996A9, false);
        g.drawString(font, status, x + 16, y + h - 24, 0xFFD34B57, false);
    }

    private void slot(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 22, y + 22, 0xFF151C24);
        g.fill(x + 1, y + 1, x + 21, y + 21, 0xFF0B1016);
    }

    private void renderStack(GuiGraphics g, ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty()) return;
        g.renderItem(stack, x, y);
        g.renderItemDecorations(font, stack, x, y);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (page == Page.CONTRACTS && button == 0 && mouseX >= 24 && mouseX <= Math.min(width - 24, 820) && mouseY >= 136) {
            int index = (int)((mouseY - 136) / 46.0);
            var list = GgoContractState.entries();
            if (index >= 0 && index < Math.min(8, list.size())) {
                GgoRuntimeV1ContractAdapter.track(list.get(index).id());
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 77) { open(Page.HOME); return true; } // M
        if (keyCode == 74) { open(Page.ACTIVITIES); return true; } // J
        if (keyCode == 78) { open(Page.MAP); return true; } // N
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return page == Page.PAUSE; }
}
'''

hooks = r'''package arena.client.shell;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Stage101 GGO-owned navigation and key routing. */
@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoShellHooks {
    private static boolean openHubAfterLogin;
    private GgoShellHooks() {}

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        openHubAfterLogin = true;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !openHubAfterLogin) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) return;
        openHubAfterLogin = false;
        mc.setScreen(new GgoShellScreen(GgoShellScreen.Page.HOME));
    }

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
            mc.setScreen(new GgoShellScreen(GgoShellScreen.Page.HOME));
        } else if (event.getKey() == GLFW.GLFW_KEY_J) {
            mc.setScreen(new GgoShellScreen(GgoShellScreen.Page.ACTIVITIES));
        } else if (event.getKey() == GLFW.GLFW_KEY_N) {
            mc.setScreen(new GgoShellScreen(GgoShellScreen.Page.MAP));
        }
    }
}
'''

(JAVA / "GgoShellScreen.java").write_text(screen)
(JAVA / "GgoShellHooks.java").write_text(hooks)

print("GGO Hub Stage101 applied")
print(" - M opens canonical GGO Hub")
print(" - J opens Activities")
print(" - PLAY ONLINE opens Hub after successful server login")
print(" - pause menu has EXIT TO GGO")
print(" - Contracts retain server snapshot/tracking adapter")
print(" - legacy Store/Profile/Skills/Clan commands remain reachable during migration")
