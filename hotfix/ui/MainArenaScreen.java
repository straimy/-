package arena.client.ui;

import arena.client.net.ClientSnapshotStore;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.net.URI;

public final class MainArenaScreen extends AbstractArenaScreen {
    private static final URI TELEGRAM_URI=URI.create("https://t.me/GunGloryOnline");
    private ArenaButton playButton;
    public MainArenaScreen(){super(Component.literal("GunGloryOnline"),UiRoute.MAIN);}
    @Override protected void init(){
        arena.client.net.ArenaClientNetwork.requestSnapshot();installNavigation();boolean fresh=ClientSnapshotStore.fresh(System.currentTimeMillis());var s=ClientSnapshotStore.get();UiLayout l=UiLayout.of(width,height);
        playButton=new ArenaButton(l.primaryButton(0),Component.literal("ИГРАТЬ"),b->{if(minecraft!=null&&minecraft.player!=null&&UiAccessPolicy.canPlay(ClientSnapshotStore.get(),ClientSnapshotStore.fresh(System.currentTimeMillis()))){minecraft.player.connection.sendCommand("play");onClose();}});playButton.active=UiAccessPolicy.canPlay(s,fresh);addRenderableWidget(playButton);
        addRenderableWidget(new ArenaButton(l.primaryButton(1),Component.literal("МАГАЗИН"),b->Minecraft.getInstance().setScreen(new LobbyShopScreen())));
        addRenderableWidget(new ArenaButton(l.primaryButton(2),Component.literal("ПРОФИЛЬ"),b->Minecraft.getInstance().setScreen(new ProfileScreen())));
        int w=Math.min(138,Math.max(108,l.panelWidth()/4)),y=Math.min(Math.max(l.primaryButton(2).y()+l.primaryButton(2).height()+8,height-43),height-31);
        addRenderableWidget(new ArenaButton(new UiLayout.Rect(l.centerX()-w/2,y,w,18),Component.literal("TELEGRAM"),b->Util.getPlatform().openUri(TELEGRAM_URI)));
    }
    @Override public void tick(){super.tick();if(playButton!=null)playButton.active=UiAccessPolicy.canPlay(ClientSnapshotStore.get(),ClientSnapshotStore.fresh(System.currentTimeMillis()));}
    @Override public void render(GuiGraphics g,int mx,int my,float pt){drawBackdrop(g);UiLayout l=UiLayout.of(width,height);g.drawCenteredString(font,Component.literal("GUN GLORY ONLINE"),l.centerX(),l.titleY()+10,UiTheme.PINK);var s=ClientSnapshotStore.get();boolean fresh=ClientSnapshotStore.fresh(System.currentTimeMillis());String status=fresh&&s.authenticated()&&s.initialized()?"$"+s.roundCredits()+"  ◇"+s.coins()+"  ◆"+s.crystals():UiAccessPolicy.status(s,fresh);g.drawString(font,Component.literal(status),10,height-12,fresh&&s.initialized()?UiTheme.BLUE:UiTheme.ACCENT_2);super.render(g,mx,my,pt);}
    @Override public boolean isPauseScreen(){return false;}
}
