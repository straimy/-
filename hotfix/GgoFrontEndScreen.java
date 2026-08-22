package arena.client.shell;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

/** Player-facing GGO entry surface. Vanilla title/server selection is intentionally not exposed. */
public final class GgoFrontEndScreen extends Screen {
    private static final String OFFICIAL_SERVER = "play.kvicloud.ru:24842";

    public GgoFrontEndScreen() {
        super(Component.literal("GunGloryOnline"));
    }

    @Override
    protected void init() {
        int rightWidth = Math.min(320, Math.max(250, width / 4));
        int x = Math.max(width / 2 + 70, width - rightWidth - 52);
        int y = Math.max(170, height / 2 - 54);
        boolean connected = minecraft != null && minecraft.getConnection() != null && minecraft.player != null;
        boolean officialLaunch = GgoLaunchTicketClient.isOfficialLaunch();
        boolean canStartOnline = GgoLaunchTicketClient.canStartOnline();

        if (connected) {
            addRenderableWidget(Button.builder(Component.literal("CONTINUE GGO"), button -> minecraft.setScreen(null))
                .bounds(x, y, rightWidth, 30).build());
        } else if (officialLaunch) {
            Button online = Button.builder(
                    Component.literal(canStartOnline ? "PLAY ONLINE" : "ONLINE SESSION EXPIRED"),
                    button -> connectOfficial())
                .bounds(x, y, rightWidth, 30).build();
            online.active = canStartOnline;
            addRenderableWidget(online);
        } else {
            addRenderableWidget(Button.builder(Component.literal("RETURN TO GGO LAUNCHER"), button -> minecraft.stop())
                .bounds(x, y, rightWidth, 30).build());
        }

        if (!connected) {
            addRenderableWidget(Button.builder(Component.literal("TRAINING"), button -> openTraining())
                .bounds(x, y + 40, rightWidth, 26).build());
        }

        int settingsY = connected ? y + 40 : y + 76;
        addRenderableWidget(Button.builder(Component.literal("SETTINGS"), button -> minecraft.setScreen(new GgoSettingsScreen(this)))
            .bounds(x, settingsY, rightWidth, 26).build());
        if (officialLaunch && !connected && !canStartOnline) {
            addRenderableWidget(Button.builder(Component.literal("REFRESH SESSION IN GGO LAUNCHER"), button -> minecraft.stop())
                .bounds(x, settingsY + 36, rightWidth, 26).build());
            addRenderableWidget(Button.builder(Component.literal("EXIT"), button -> minecraft.stop())
                .bounds(x, settingsY + 72, rightWidth, 26).build());
        } else {
            addRenderableWidget(Button.builder(Component.literal("EXIT"), button -> minecraft.stop())
                .bounds(x, settingsY + 36, rightWidth, 26).build());
        }
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
        g.fill(0, 0, width, height, 0xFF050609);
        g.fill(0, 0, width, Math.max(3, height / 110), 0xFF9A2532);
        g.fill(0, height - 2, width, height, 0xFF311017);

        Minecraft mc = Minecraft.getInstance();
        boolean connected = mc.getConnection() != null && mc.player != null;
        boolean officialLaunch = GgoLaunchTicketClient.isOfficialLaunch();
        boolean canStartOnline = GgoLaunchTicketClient.canStartOnline();
        String account = mc.getUser() == null ? "GGO ACCOUNT" : mc.getUser().getName();

        int margin = Math.max(28, width / 35);
        int top = Math.max(28, height / 18);
        int leftW = Math.min(310, Math.max(230, width / 5));
        int rightW = Math.min(360, Math.max(280, width / 4));
        int rightX = width - rightW - margin;
        int centerX = margin + leftW + 22;
        int centerW = Math.max(220, rightX - centerX - 22);
        int panelBottom = height - Math.max(34, height / 20);

        // Account / progression column.
        g.fill(margin, top + 58, margin + leftW, panelBottom, 0xD90B0E13);
        g.fill(margin, top + 58, margin + 3, panelBottom, 0xFF9A2532);
        g.drawString(font, "GGO ACCOUNT", margin + 18, top + 78, 0xFF8C96A6, false);
        g.drawString(font, account, margin + 18, top + 100, 0xFFF1F3F6, false);
        g.drawString(font, "OPERATOR PROFILE", margin + 18, top + 134, 0xFFB44A56, false);
        g.drawString(font, "Rank  •  Recruit", margin + 18, top + 157, 0xFFCBD2DC, false);
        g.drawString(font, "Progress  •  Beta season", margin + 18, top + 178, 0xFF737F90, false);
        g.fill(margin + 18, top + 201, margin + leftW - 18, top + 205, 0xFF242B35);
        g.fill(margin + 18, top + 201, margin + 92, top + 205, 0xFF9A2532);
        g.drawString(font, "WARDROBE", margin + 18, top + 234, 0xFF8C96A6, false);
        g.drawString(font, "GGO appearance active", margin + 18, top + 255, 0xFFC2CAD5, false);
        g.drawString(font, "No Minecraft skin dependency", margin + 18, top + 276, 0xFF697587, false);

        // Center operator stage. Kept renderer-safe for beta; character renderer can replace this silhouette later.
        g.fill(centerX, top + 58, centerX + centerW, panelBottom, 0x8C090B0F);
        int bodyCx = centerX + centerW / 2;
        int bodyTop = top + 104;
        int bodyBottom = Math.min(panelBottom - 46, bodyTop + Math.max(240, height / 2));
        g.fill(bodyCx - 42, bodyTop + 58, bodyCx + 42, bodyBottom, 0xFF151A21);
        g.fill(bodyCx - 28, bodyTop + 8, bodyCx + 28, bodyTop + 66, 0xFF1B2028);
        g.fill(bodyCx - 68, bodyTop + 76, bodyCx - 42, bodyBottom - 28, 0xFF12171D);
        g.fill(bodyCx + 42, bodyTop + 76, bodyCx + 68, bodyBottom - 28, 0xFF12171D);
        g.fill(bodyCx - 40, bodyBottom, bodyCx - 8, panelBottom - 16, 0xFF11161C);
        g.fill(bodyCx + 8, bodyBottom, bodyCx + 40, panelBottom - 16, 0xFF11161C);
        g.drawCenteredString(font, Component.literal("OPERATOR"), bodyCx, panelBottom - 34, 0xFF6F7B8D);

        // Activity column.
        g.fill(rightX, top + 58, width - margin, panelBottom, 0xD90B0E13);
        g.fill(rightX, top + 58, rightX + 3, panelBottom, 0xFF9A2532);
        g.drawString(font, "ACTIVITIES", rightX + 20, top + 78, 0xFFF0F3F6, false);
        String state = connected ? "ONLINE SESSION READY" : officialLaunch && canStartOnline ? "SECURE ENTRY READY" : officialLaunch ? "ONLINE SESSION EXPIRED" : "LAUNCHER REQUIRED";
        int stateColor = connected || (officialLaunch && canStartOnline) ? 0xFF78B994 : 0xFFD05A64;
        g.drawString(font, state, rightX + 20, top + 101, stateColor, false);
        g.drawString(font, "Official shard", rightX + 20, top + 125, 0xFF7B8798, false);
        g.drawString(font, "Automatic route  •  GGO network", rightX + 20, top + 145, 0xFFB8C0CB, false);
        g.drawString(font, "Training", rightX + 20, top + 174, 0xFF7B8798, false);
        g.drawString(font, "Offline drills  •  no online rewards", rightX + 20, top + 194, 0xFFB8C0CB, false);
        if (officialLaunch && canStartOnline && !connected) {
            g.drawString(font, "Secure session  •  " + GgoLaunchTicketClient.menuSecondsRemaining() + "s", rightX + 20, top + 222, 0xFF9B6B72, false);
        }

        // Brand header.
        g.drawString(font, "GUN GLORY ONLINE", margin, top, 0xFFF3F5F7, false);
        g.drawString(font, "GGO CLIENT  •  BETA", margin, top + 22, 0xFFB74350, false);
        g.drawString(font, "ACCOUNT  /  OPERATOR  /  ACTIVITIES", width - margin - 215, top + 8, 0xFF697587, false);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public boolean shouldCloseOnEsc() { return false; }
}
