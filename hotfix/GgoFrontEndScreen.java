package arena.client.shell;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

/**
 * GunGloryOnline player entry surface.
 *
 * The launcher starts the game into this screen. Mode selection belongs here, not on the
 * desktop launcher. Vanilla title/server-selection surfaces are intentionally not exposed.
 */
public final class GgoFrontEndScreen extends Screen {
    private static final String OFFICIAL_SERVER = "play.kvicloud.ru:24842";
    private static final int ACCENT = 0xFFC83245;
    private static final int ACCENT_DARK = 0xFF6D1D2A;
    private static final int TEXT = 0xFFF2F4F7;
    private static final int MUTED = 0xFF8792A3;
    private static final int SOFT = 0xFFBAC2CD;
    private static final int READY = 0xFF72C391;
    private static final int WARN = 0xFFE26A73;

    public GgoFrontEndScreen() {
        super(Component.literal("GunGloryOnline"));
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        boolean connected = mc.getConnection() != null && mc.player != null;
        boolean officialLaunch = GgoLaunchTicketClient.isOfficialLaunch();
        boolean canStartOnline = GgoLaunchTicketClient.canStartOnline();

        int cardW = Math.min(356, Math.max(280, width / 4));
        int cardX = width - cardW - Math.max(44, width / 24);
        int buttonX = cardX + 22;
        int buttonW = cardW - 44;
        int firstY = Math.max(210, height / 2 - 72);
        int gap = 42;

        if (connected) {
            addRenderableWidget(Button.builder(Component.literal("CONTINUE"), button -> mc.setScreen(null))
                .bounds(buttonX, firstY, buttonW, 32).build());
        } else if (officialLaunch) {
            Button online = Button.builder(
                    Component.literal(canStartOnline ? "PLAY ONLINE" : "ONLINE SESSION EXPIRED"),
                    button -> connectOfficial())
                .bounds(buttonX, firstY, buttonW, 32).build();
            online.active = canStartOnline;
            addRenderableWidget(online);
        } else {
            addRenderableWidget(Button.builder(Component.literal("RETURN TO GGO LAUNCHER"), button -> mc.stop())
                .bounds(buttonX, firstY, buttonW, 32).build());
        }

        int row = 1;
        if (!connected) {
            addRenderableWidget(Button.builder(Component.literal("TRAINING"), button -> openTraining())
                .bounds(buttonX, firstY + gap * row++, buttonW, 30).build());
        }
        addRenderableWidget(Button.builder(Component.literal("SETTINGS"), button -> mc.setScreen(new GgoSettingsScreen(this)))
            .bounds(buttonX, firstY + gap * row++, buttonW, 30).build());
        addRenderableWidget(Button.builder(Component.literal("EXIT"), button -> mc.stop())
            .bounds(buttonX, firstY + gap * row, buttonW, 30).build());
    }

    private void connectOfficial() {
        Minecraft mc = Minecraft.getInstance();
        if (!GgoLaunchTicketClient.canStartOnline()) {
            mc.setScreen(new GgoFrontEndScreen());
            return;
        }
        ServerData server = new ServerData("GunGloryOnline", OFFICIAL_SERVER, false);
        ConnectScreen.startConnecting(this, mc, ServerAddress.parseString(OFFICIAL_SERVER), server, false);
    }

    private void openTraining() {
        Minecraft.getInstance().setScreen(new GgoTrainingScreen(this));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        boolean connected = mc.getConnection() != null && mc.player != null;
        boolean officialLaunch = GgoLaunchTicketClient.isOfficialLaunch();
        boolean canStartOnline = GgoLaunchTicketClient.canStartOnline();
        String account = mc.getUser() == null ? "GGO PLAYER" : mc.getUser().getName();

        // Dark neutral base with a restrained GGO red glow. No faux Minecraft panels or
        // placeholder operator silhouette: the entry screen should read as a game frontend.
        g.fill(0, 0, width, height, 0xFF05070B);
        g.fill(0, 0, width, 3, ACCENT);

        int glowW = Math.max(260, width / 3);
        g.fill(0, 3, glowW, height, 0x351D0710);
        g.fill(glowW, 3, glowW + Math.max(120, width / 8), height, 0x160D070B);

        int marginX = Math.max(46, width / 18);
        int top = Math.max(54, height / 12);
        int cardW = Math.min(356, Math.max(280, width / 4));
        int cardX = width - cardW - Math.max(44, width / 24);
        int cardTop = Math.max(116, height / 6);
        int cardBottom = Math.min(height - 60, cardTop + 420);

        // Brand block.
        g.drawString(font, "GUN GLORY ONLINE", marginX, top, TEXT, false);
        g.drawString(font, "CLOSED BETA  /  RUNTIME STAGE96", marginX, top + 22, ACCENT, false);

        int heroY = Math.max(top + 100, height / 2 - 110);
        g.drawString(font, "WELCOME BACK,", marginX, heroY, MUTED, false);
        g.drawString(font, account.toUpperCase(), marginX, heroY + 28, TEXT, false);
        g.fill(marginX, heroY + 58, marginX + 72, heroY + 61, ACCENT);

        String headline;
        String detail;
        int statusColor;
        if (connected) {
            headline = "SESSION ACTIVE";
            detail = "You are connected to GunGloryOnline.";
            statusColor = READY;
        } else if (officialLaunch && canStartOnline) {
            headline = "READY TO DEPLOY";
            detail = "Secure launcher session is ready for the official shard.";
            statusColor = READY;
        } else if (officialLaunch) {
            headline = "SESSION EXPIRED";
            detail = "Return to the launcher and press PLAY again.";
            statusColor = WARN;
        } else {
            headline = "LAUNCHER SESSION REQUIRED";
            detail = "Start GunGloryOnline from the official GGO Launcher.";
            statusColor = WARN;
        }

        g.drawString(font, headline, marginX, heroY + 94, statusColor, false);
        g.drawString(font, detail, marginX, heroY + 116, SOFT, false);
        g.drawString(font, "Official network  •  " + OFFICIAL_SERVER, marginX, heroY + 146, MUTED, false);
        g.drawString(font, "Training is offline and grants no online rewards.", marginX, heroY + 168, MUTED, false);
        if (officialLaunch && canStartOnline && !connected) {
            g.drawString(font,
                "Secure entry window  •  " + GgoLaunchTicketClient.menuSecondsRemaining() + "s",
                marginX, heroY + 198, 0xFFB47A83, false);
        }

        // Action card.
        g.fill(cardX, cardTop, cardX + cardW, cardBottom, 0xD90B0E14);
        g.fill(cardX, cardTop, cardX + 3, cardBottom, ACCENT);
        g.fill(cardX + 18, cardTop + 58, cardX + cardW - 18, cardTop + 59, 0xFF242A34);
        g.drawString(font, "PLAY", cardX + 22, cardTop + 24, TEXT, false);
        g.drawString(font, connected ? "CURRENT SESSION" : "CHOOSE MODE", cardX + 22, cardTop + 41, MUTED, false);

        int footerY = height - Math.max(30, height / 24);
        g.drawString(font, "GGO CLIENT  •  STAGE96", marginX, footerY, 0xFF596474, false);
        g.drawString(font, "ESC LOCKED ON FRONTEND", width - Math.max(210, width / 7), footerY, 0xFF4E5866, false);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
