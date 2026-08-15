package arena.client.ui;

import arena.client.net.ClientSnapshotStore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Permanent lobby cosmetics. Combat weapons are still bought only in-match with G. */
final class LobbyShopScreen extends AbstractArenaScreen {
    LobbyShopScreen(){super(Component.literal("GunGloryOnline • Магазин"),UiRoute.SHOP);}

    @Override protected void init(){
        installNavigation(); arena.client.net.ArenaClientNetwork.requestSnapshot();
        UiLayout.Rect p=UiLayout.of(width,height).contentPanel(); int x=p.x()+16, y=p.y()+76, w=p.width()-32;
        addSkinButtons(x,y,w,"neon_pulse","NEON PULSE",8); y+=50;
        addSkinButtons(x,y,w,"crimson_grid","CRIMSON GRID",12); y+=50;
        addSkinButtons(x,y,w,"void_ice","VOID ICE",18);
    }

    private void addSkinButtons(int x,int y,int w,String id,String name,int price){
        int buyW=Math.min(116,Math.max(88,w/4)); int useW=Math.min(92,Math.max(70,w/5));
        addRenderableWidget(new ArenaButton(new UiLayout.Rect(x+w-buyW-useW-8,y+19,buyW,20),Component.literal("КУПИТЬ "+price+"◆"),b->send("skinbuy "+id)));
        addRenderableWidget(new ArenaButton(new UiLayout.Rect(x+w-useW,y+19,useW,20),Component.literal("ВЫБРАТЬ"),b->send("skinuse "+id)));
    }
    private void send(String cmd){ if(minecraft!=null&&minecraft.player!=null)minecraft.player.connection.sendCommand(cmd); arena.client.net.ArenaClientNetwork.requestSnapshot(); }

    @Override public void render(GuiGraphics g,int mx,int my,float pt){
        drawBackdrop(g); UiLayout.Rect p=UiLayout.of(width,height).contentPanel(); drawPanel(g,p); int x=p.x()+16,y=p.y()+13;
        g.drawString(font,Component.literal("✦ ПОСТОЯННЫЕ СКИНЫ"),x,y,UiTheme.PINK); y+=18;
        var s=ClientSnapshotStore.get(); g.drawString(font,Component.literal("◆ "+s.crystals()+"   ◇ "+s.coins()+"   •   оружие: только в матче [G]"),x,y,UiTheme.BLUE); y+=28;
        card(g,x,y,p.width()-32,"NEON PULSE","голубой + фиолетовый","8◆"); y+=50;
        card(g,x,y,p.width()-32,"CRIMSON GRID","красный + тёмный","12◆"); y+=50;
        card(g,x,y,p.width()-32,"VOID ICE","ледяной + фиолетовый","18◆"); y+=54;
        g.drawString(font,Component.literal("Покупка остаётся навсегда • /skins — список"),x,y,UiTheme.MUTED);
        super.render(g,mx,my,pt);
    }
    private void card(GuiGraphics g,int x,int y,int w,String n,String sub,String price){
        g.fill(x,y,x+w,y+43,0xA0182238); g.renderOutline(x,y,w,43,UiTheme.BLUE);
        g.drawString(font,Component.literal("◆ "+n),x+10,y+7,UiTheme.TEXT); g.drawString(font,Component.literal(sub),x+10,y+23,UiTheme.MUTED);
        g.drawString(font,Component.literal(price),x+Math.max(120,w-205),y+7,UiTheme.ACCENT_2);
    }
    @Override public boolean isPauseScreen(){return false;}
}
