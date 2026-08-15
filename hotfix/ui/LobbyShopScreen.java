package arena.client.ui;

import arena.client.net.ClientSnapshotStore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Permanent lobby cosmetics. Combat weapons are still bought only in-match with G. */
final class LobbyShopScreen extends AbstractArenaScreen {
    LobbyShopScreen(){super(Component.literal("GunGloryOnline • Магазин"),UiRoute.SHOP);}

    @Override protected void init(){
        installNavigation(); arena.client.net.ArenaClientNetwork.requestSnapshot();
        UiLayout.Rect p=UiLayout.of(width,height).contentPanel(); int x=p.x()+14, y=p.y()+67, w=p.width()-28;
        int step=p.height()<300?44:48;
        addSkinButtons(x,y,w,"neon_pulse",8); y+=step;
        addSkinButtons(x,y,w,"crimson_grid",12); y+=step;
        addSkinButtons(x,y,w,"void_ice",18);
        int bottom=p.y()+p.height()-27;
        addRenderableWidget(new ArenaButton(new UiLayout.Rect(p.x()+14,bottom,92,18),Component.literal("× СНЯТЬ СКИН"),b->send("skinuse none")));
    }

    private void addSkinButtons(int x,int y,int w,String id,int price){
        int buyW=Math.min(105,Math.max(82,w/4)); int useW=Math.min(82,Math.max(65,w/5));
        int by=y+17;
        addRenderableWidget(new ArenaButton(new UiLayout.Rect(x+w-buyW-useW-6,by,buyW,18),Component.literal(price+"◆  КУПИТЬ"),b->send("skinbuy "+id)));
        addRenderableWidget(new ArenaButton(new UiLayout.Rect(x+w-useW,by,useW,18),Component.literal("ВЫБРАТЬ"),b->send("skinuse "+id)));
    }
    private void send(String cmd){ if(minecraft!=null&&minecraft.player!=null)minecraft.player.connection.sendCommand(cmd); arena.client.net.ArenaClientNetwork.requestSnapshot(); }

    @Override public void render(GuiGraphics g,int mx,int my,float pt){
        drawBackdrop(g); UiLayout.Rect p=UiLayout.of(width,height).contentPanel(); drawPanel(g,p); int x=p.x()+14,y=p.y()+11;
        g.drawString(font,Component.literal("✦ СКИНЫ НАВСЕГДА"),x,y,UiTheme.PINK); y+=17;
        var s=ClientSnapshotStore.get();
        g.drawString(font,Component.literal("◆ "+s.crystals()+"   •   скин красит JEG-пушки   •   оружие [G] только в матче"),x,y,UiTheme.BLUE); y+=26;
        int step=p.height()<300?44:48;
        card(g,x,y,p.width()-28,"NEON PULSE","неоновый голубой","8◆",0xFF55DFFF); y+=step;
        card(g,x,y,p.width()-28,"CRIMSON GRID","малиново-красный","12◆",0xFFD83B5B); y+=step;
        card(g,x,y,p.width()-28,"VOID ICE","холодный сиреневый","18◆",0xFF9CB9FF);
        g.drawString(font,Component.literal("Купил один раз — остаётся в профиле навсегда"),x,p.y()+p.height()-43,UiTheme.MUTED);
        super.render(g,mx,my,pt);
    }
    private void card(GuiGraphics g,int x,int y,int w,String n,String sub,String price,int tint){
        g.fill(x,y,x+w,y+39,0xA0182238); g.renderOutline(x,y,w,39,UiTheme.BLUE);
        g.fill(x+8,y+8,x+25,y+31,tint); g.renderOutline(x+7,y+7,19,25,0xFFFFFFFF);
        g.drawString(font,Component.literal(n),x+33,y+7,UiTheme.TEXT);
        g.drawString(font,Component.literal(sub),x+33,y+22,UiTheme.MUTED);
        int px=Math.max(x+128,x+w-199); g.drawString(font,Component.literal(price),px,y+7,UiTheme.ACCENT_2);
    }
    @Override public boolean isPauseScreen(){return false;}
}
