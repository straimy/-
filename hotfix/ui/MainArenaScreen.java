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
        playButton=new ArenaButton(l.primaryButton(0),Component.literal("▶  ИГРАТЬ"),b->{if(minecraft!=null&&minecraft.player!=null&&UiAccessPolicy.canPlay(ClientSnapshotStore.get(),ClientSnapshotStore.fresh(System.currentTimeMillis()))){minecraft.player.connection.sendCommand("play");onClose();}});playButton.active=UiAccessPolicy.canPlay(s,fresh);addRenderableWidget(playButton);
        addRenderableWidget(new ArenaButton(l.primaryButton(1),Component.literal("◇  МАГАЗИН"),b->Minecraft.getInstance().setScreen(new LobbyShopScreen())));
        addRenderableWidget(new ArenaButton(l.primaryButton(2),Component.literal("⌁  ПРОФИЛЬ"),b->Minecraft.getInstance().setScreen(new ProfileScreen())));
        addRenderableWidget(new ArenaButton(l.primaryButton(3),Component.literal("✦  ДРУЗЬЯ"),b->Minecraft.getInstance().setScreen(new FriendsScreen())));
        addRenderableWidget(new ArenaButton(l.primaryButton(4),Component.literal("♜  КЛАНЫ"),b->Minecraft.getInstance().setScreen(new ClanScreen())));
        int w=Math.min(132,Math.max(104,l.panelWidth()/4)),y=Math.min(Math.max(l.primaryButton(4).y()+l.primaryButton(4).height()+7,height-35),height-27);
        addRenderableWidget(new ArenaButton(new UiLayout.Rect(l.centerX()-w/2,y,w,17),Component.literal("↗  TELEGRAM"),b->Util.getPlatform().openUri(TELEGRAM_URI)));
    }
    @Override public void tick(){super.tick();if(playButton!=null)playButton.active=UiAccessPolicy.canPlay(ClientSnapshotStore.get(),ClientSnapshotStore.fresh(System.currentTimeMillis()));}
    @Override public void render(GuiGraphics g,int mx,int my,float pt){
        drawBackdrop(g);UiLayout l=UiLayout.of(width,height);int cx=l.centerX(),ty=l.titleY()+7;
        g.fill(cx-104,ty-5,cx+104,ty+18,0x4A111A2A);g.fill(cx-58,ty+19,cx+58,ty+20,UiTheme.GLOW);
        g.drawCenteredString(font,Component.literal("✦  GUN GLORY ONLINE  ✦"),cx,ty,UiTheme.PINK);
        g.drawCenteredString(font,Component.literal("ARENA // COMBAT NETWORK"),cx,ty+12,UiTheme.DIM);
        var s=ClientSnapshotStore.get();boolean fresh=ClientSnapshotStore.fresh(System.currentTimeMillis());String status=fresh&&s.authenticated()&&s.initialized()?"$"+s.roundCredits()+"   ◇ "+s.coins()+"   ◆ "+s.crystals():UiAccessPolicy.status(s,fresh);
        int sw=font.width(status)+16;g.fill(7,height-18,7+sw,height-5,0xA4101725);g.renderOutline(7,height-18,sw,13,UiTheme.HAIRLINE);g.drawString(font,Component.literal(status),14,height-15,fresh&&s.initialized()?UiTheme.ACCENT:UiTheme.ACCENT_2);
        super.render(g,mx,my,pt);
    }
    @Override public boolean isPauseScreen(){return false;}
}
