package arena.client.shell;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

/** Canonical GunGloryOnline entry surface. Official launcher sessions connect automatically. */
public final class GgoFrontEndScreen extends Screen {
    private static final String OFFICIAL_SERVER = "play.kvicloud.ru:24842";
    private static final int BG = 0xFF05070B;
    private static final int PANEL = 0xE80B0E14;
    private static final int ACCENT = 0xFFC83245;
    private static final int TEXT = 0xFFF2F4F7;
    private static final int MUTED = 0xFF8792A3;
    private static final int READY = 0xFF72C391;
    private static final int WARN = 0xFFE26A73;
    private boolean autoConnectScheduled;

    public GgoFrontEndScreen() {
        super(Component.literal("GunGloryOnline"));
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        boolean connected = mc.getConnection() != null && mc.player != null;
        boolean officialLaunch = GgoLaunchTicketClient.isOfficialLaunch();
        boolean canStartOnline = GgoLaunchTicketClient.canStartOnline();

        // Launcher PLAY is the only online-play action. Do not make the player click PLAY twice.
        if (!connected && officialLaunch && canStartOnline && !autoConnectScheduled) {
            autoConnectScheduled = true;
            mc.execute(this::connectOfficial);
            return;
        }

        int w = Math.min(370, Math.max(290, width / 3));
        int x = (width - w) / 2;
        int y = Math.max(220, height / 2 - 34);

        if (connected) {
            addRenderableWidget(Button.builder(Component.literal("CONTINUE"), b -> mc.setScreen(null))
                .bounds(x, y, w, 34).build());
        } else {
            Button retry = Button.builder(Component.literal("RETURN TO LAUNCHER"), b -> mc.stop())
                .bounds(x, y, w, 34).build();
            addRenderableWidget(retry);
        }

        Button practice = Button.builder(Component.literal("PRACTICE · COMING SOON"), b -> {})
            .bounds(x, y + 46, w, 28).build();
        practice.active = false;
        addRenderableWidget(practice);

        addRenderableWidget(Button.builder(Component.literal("SETTINGS"), b -> mc.setScreen(new GgoSettingsScreen(this)))
            .bounds(x, y + 86, w, 28).build());
        addRenderableWidget(Button.builder(Component.literal("EXIT"), b -> mc.stop())
            .bounds(x, y + 126, w, 28).build());
    }

    private void connectOfficial() {
        Minecraft mc = Minecraft.getInstance();
        if (!GgoLaunchTicketClient.isOfficialLaunch() || !GgoLaunchTicketClient.canStartOnline()) {
            mc.setScreen(new GgoFrontEndScreen());
            return;
        }
        ServerData server = new ServerData("GunGloryOnline", OFFICIAL_SERVER, false);
        ConnectScreen.startConnecting(this, mc, ServerAddress.parseString(OFFICIAL_SERVER), server, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        boolean connected = mc.getConnection() != null && mc.player != null;
        boolean officialLaunch = GgoLaunchTicketClient.isOfficialLaunch();
        boolean canStartOnline = GgoLaunchTicketClient.canStartOnline();
        String account = mc.getUser() == null ? "GGO PLAYER" : mc.getUser().getName();

        g.fill(0, 0, width, height, BG);
        g.fill(0, 0, width, 3, ACCENT);

        int center = width / 2;
        int top = Math.max(66, height / 8);
        int cardW = Math.min(520, width - 70);
        int cardX = center - cardW / 2;
        int cardTop = top + 70;
        int cardBottom = Math.min(height - 46, cardTop + 390);
        g.fill(cardX, cardTop, cardX + cardW, cardBottom, PANEL);
        g.fill(cardX, cardTop, cardX + 3, cardBottom, ACCENT);

        g.drawCenteredString(font, Component.literal("GUN GLORY ONLINE"), center, top, TEXT);
        g.drawCenteredString(font, Component.literal("CLOSED BETA"), center, top + 22, ACCENT);
        g.drawCenteredString(font, Component.literal(account.toUpperCase()), center, cardTop + 34, TEXT);

        String status;
        int statusColor;
        if (connected) {
            status = "ONLINE SESSION ACTIVE";
            statusColor = READY;
        } else if (officialLaunch && canStartOnline) {
            status = "CONNECTING TO OFFICIAL GGO";
            statusColor = READY;
        } else {
            status = "LAUNCHER SESSION REQUIRED";
            statusColor = WARN;
        }
        g.drawCenteredString(font, Component.literal(status), center, cardTop + 58, statusColor);

        if (!connected && (!officialLaunch || !canStartOnline)) {
            g.drawCenteredString(font, Component.literal("Return to the GGO Launcher and press PLAY."), center, cardTop + 80, MUTED);
        }

        g.drawCenteredString(font, Component.literal("Official server · " + OFFICIAL_SERVER), center, cardBottom - 50, MUTED);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public boolean shouldCloseOnEsc() { return false; }
}
