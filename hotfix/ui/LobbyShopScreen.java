package arena.client.ui;

import arena.client.net.ClientSnapshotStore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

final class LobbyShopScreen extends AbstractArenaScreen {
    LobbyShopScreen(){super(Component.literal("Магазин"),UiRoute.SHOP);}
    @Override protected void init(){installNavigation();arena.client.net.ArenaClientNetwork.requestSnapshot();UiLayout.Rect p=UiLayout.of(width,height).contentPanel();int x=p.x()+14,y=p.y()+54,w=p.width()-28,step=p.height()<300?42:45;addSkinButtons(x,y,w,"neon_pulse",8);y+=step;addSkinButtons(x,y,w,"crimson_grid",12);y+=step;addSkinButtons(x,y,w,"void_ice",18);int bottom=p.y()+p.height()-25;addRenderableWidget(new ArenaButton(new UiLayout.Rect(p.x()+14,bottom,80,18),Component.literal("СНЯТЬ"),b->send("skinuse none")));}
    private void addSkinButtons(int x,int y,int w,String id,int price){int buyW=Math.min(96,Math.max(78,w/4)),useW=Math.min(74,Math.max(62,w/5)),by=y+15;addRenderableWidget(new ArenaButton(new UiLayout.Rect(x+w-buyW-useW-5,by,buyW,18),Component.literal(price+"◆ КУПИТЬ"),b->send("skinbuy "+id)));addRenderableWidget(new ArenaButton(new UiLayout.Rect(x+w-useW,by,useW,18),Component.literal("НАДЕТЬ"),b->send("skinuse "+id)));}
    private void send(String cmd){if(minecraft!=null&&minecraft.player!=null)minecraft.player.connection.sendCommand(cmd);arena.client.net.ArenaClientNetwork.requestSnapshot();}
    @Override public void render(GuiGraphics g,int mx,int my,float pt){drawBackdrop(g);UiLayout.Rect p=UiLayout.of(width,height).contentPanel();drawPanel(g,p);int x=p.x()+14,y=p.y()+11;var s=ClientSnapshotStore.get();g.drawString(font,Component.literal("СКИНЫ   ◆ "+s.crystals()),x,y,UiTheme.PINK);y+=24;int step=p.height()<300?42:45;card(g,x,y,p.width()-28,"NEON PULSE","8◆",0xFF55DFFF);y+=step;card(g,x,y,p.width()-28,"CRIMSON GRID","12◆",0xFFD83B5B);y+=step;card(g,x,y,p.width()-28,"VOID ICE","18◆",0xFF9CB9FF);super.render(g,mx,my,pt);}
    private void card(GuiGraphics g,int x,int y,int w,String n,String price,int tint){g.fill(x,y,x+w,y+36,0xA0182238);g.renderOutline(x,y,w,36,UiTheme.BLUE);g.fill(x+8,y+7,x+24,y+29,tint);g.renderOutline(x+7,y+6,18,24,0xFFFFFFFF);g.drawString(font,Component.literal(n),x+32,y+7,UiTheme.TEXT);g.drawString(font,Component.literal(price),x+32,y+20,UiTheme.ACCENT_2);}
    @Override public boolean isPauseScreen(){return false;}
}
