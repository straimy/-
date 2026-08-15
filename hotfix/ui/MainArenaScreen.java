package arena.client.ui;

import arena.client.net.ClientSnapshotStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class MainArenaScreen extends AbstractArenaScreen {
    private ArenaButton playButton;
    public MainArenaScreen() { super(Component.literal("KVICloud • Gunner Arena"), UiRoute.MAIN); }

    @Override
    protected void init() {
        arena.client.net.ArenaClientNetwork.requestSnapshot();
        installNavigation();
        boolean fresh = ClientSnapshotStore.fresh(System.currentTimeMillis());
        var snapshot = ClientSnapshotStore.get();
        boolean playAllowed = UiAccessPolicy.canPlay(snapshot, fresh);
        playButton = new ArenaButton(UiLayout.of(width,height).primaryButton(0), Component.literal("✦  ИГРАТЬ"), b -> {
            if (minecraft != null && minecraft.player != null && UiAccessPolicy.canPlay(ClientSnapshotStore.get(), ClientSnapshotStore.fresh(System.currentTimeMillis()))) {
                minecraft.player.connection.sendCommand("play");
                onClose();
            }
        });
        playButton.active = playAllowed;
        addRenderableWidget(playButton);
        UiLayout layout = UiLayout.of(width, height);
        addRenderableWidget(new ArenaButton(layout.primaryButton(1), Component.literal("◈  МАГАЗИН"), b -> Minecraft.getInstance().setScreen(new ShopScreen())));
        addRenderableWidget(new ArenaButton(layout.primaryButton(2), Component.literal("◇  ПРОФИЛЬ"), b -> Minecraft.getInstance().setScreen(new ProfileScreen())));
    }

    @Override public void tick() {
        super.tick();
        if (playButton != null) playButton.active = UiAccessPolicy.canPlay(ClientSnapshotStore.get(), ClientSnapshotStore.fresh(System.currentTimeMillis()));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        drawBackdrop(g);
        UiLayout layout = UiLayout.of(width, height);
        g.drawCenteredString(font, Component.literal("✦ KVICloud ✦"), layout.centerX(), layout.titleY(), UiTheme.PINK);
        g.drawCenteredString(font, Component.literal("GUNNER ARENA"), layout.centerX(), layout.titleY() + 18, UiTheme.ACCENT);
        g.drawCenteredString(font, Component.literal("PvP • оружие • прогресс"), layout.centerX(), layout.titleY() + 34, UiTheme.MUTED);
        var snapshot = ClientSnapshotStore.get();
        boolean fresh = ClientSnapshotStore.fresh(System.currentTimeMillis());
        String status = fresh && snapshot.authenticated() && snapshot.initialized()
            ? "$" + snapshot.roundCredits() + "   ◇ " + snapshot.coins() + "   ◆ " + snapshot.crystals()
            : UiAccessPolicy.status(snapshot, fresh);
        g.drawString(font, Component.literal(status), 18, height - 26, fresh && snapshot.initialized() ? UiTheme.BLUE : UiTheme.ACCENT_2);
        if (fresh && snapshot.initialized()) {
            String round = "R" + snapshot.roundNumber() + " • " + snapshot.roundState() + " • " + UiFormat.roundTimer(snapshot.remainingSeconds());
            int w = font.width(round);
            g.drawString(font, Component.literal(round), Math.max(18, width - 18 - w), height - 26, UiTheme.MUTED);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override public boolean isPauseScreen() { return false; }
}
