package arena.client.ui;

import arena.client.net.ClientSnapshotStore;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.net.URI;

public final class MainArenaScreen extends AbstractArenaScreen {
    private static final URI TELEGRAM_URI = URI.create("https://t.me/GunGloryOnline");
    private ArenaButton playButton;
    public MainArenaScreen() { super(Component.literal("GunGloryOnline"), UiRoute.MAIN); }

    @Override
    protected void init() {
        arena.client.net.ArenaClientNetwork.requestSnapshot();
        installNavigation();
        boolean fresh = ClientSnapshotStore.fresh(System.currentTimeMillis());
        var snapshot = ClientSnapshotStore.get();
        boolean playAllowed = UiAccessPolicy.canPlay(snapshot, fresh);
        UiLayout layout = UiLayout.of(width, height);
        playButton = new ArenaButton(layout.primaryButton(0), Component.literal("✦  ИГРАТЬ"), b -> {
            if (minecraft != null && minecraft.player != null && UiAccessPolicy.canPlay(ClientSnapshotStore.get(), ClientSnapshotStore.fresh(System.currentTimeMillis()))) {
                minecraft.player.connection.sendCommand("play");
                onClose();
            }
        });
        playButton.active = playAllowed;
        addRenderableWidget(playButton);
        addRenderableWidget(new ArenaButton(layout.primaryButton(1), Component.literal("◈  ЛОББИ-МАГАЗИН"), b -> Minecraft.getInstance().setScreen(new LobbyShopScreen())));
        addRenderableWidget(new ArenaButton(layout.primaryButton(2), Component.literal("◇  ПРОФИЛЬ"), b -> Minecraft.getInstance().setScreen(new ProfileScreen())));

        int tgW = Math.min(170, Math.max(132, layout.panelWidth() / 3));
        int tgH = 19;
        int tgY = Math.max(layout.primaryButton(2).y() + layout.primaryButton(2).height() + 10, height - 50);
        tgY = Math.min(tgY, height - 34);
        addRenderableWidget(new ArenaButton(
            new UiLayout.Rect(layout.centerX() - tgW / 2, tgY, tgW, tgH),
            Component.literal("♡ TELEGRAM • ПОДПИСАТЬСЯ"),
            b -> Util.getPlatform().openUri(TELEGRAM_URI)
        ));
    }

    @Override public void tick() {
        super.tick();
        if (playButton != null) playButton.active = UiAccessPolicy.canPlay(ClientSnapshotStore.get(), ClientSnapshotStore.fresh(System.currentTimeMillis()));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        drawBackdrop(g);
        UiLayout layout = UiLayout.of(width, height);
        g.drawCenteredString(font, Component.literal("✦ GunGloryOnline ✦"), layout.centerX(), layout.titleY(), UiTheme.PINK);
        g.drawCenteredString(font, Component.literal("GUN GLORY ONLINE"), layout.centerX(), layout.titleY() + 18, UiTheme.ACCENT);
        g.drawCenteredString(font, Component.literal("PvP • оружие • прогресс"), layout.centerX(), layout.titleY() + 34, UiTheme.MUTED);
        var snapshot = ClientSnapshotStore.get();
        boolean fresh = ClientSnapshotStore.fresh(System.currentTimeMillis());
        String status = fresh && snapshot.authenticated() && snapshot.initialized()
            ? "$" + snapshot.roundCredits() + "   ◇ " + snapshot.coins() + "   ◆ " + snapshot.crystals()
            : UiAccessPolicy.status(snapshot, fresh);
        g.drawString(font, Component.literal(status), 12, height - 14, fresh && snapshot.initialized() ? UiTheme.BLUE : UiTheme.ACCENT_2);
        if (fresh && snapshot.initialized()) {
            String round = "R" + snapshot.roundNumber() + " • " + snapshot.roundState() + " • " + UiFormat.roundTimer(snapshot.remainingSeconds());
            int w = font.width(round);
            g.drawString(font, Component.literal(round), Math.max(12, width - 12 - w), height - 14, UiTheme.MUTED);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override public boolean isPauseScreen() { return false; }
}
