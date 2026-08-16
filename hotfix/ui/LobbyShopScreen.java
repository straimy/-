package arena.client.ui;

import arena.client.net.ClientSnapshotStore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

final class LobbyShopScreen extends AbstractArenaScreen {
    LobbyShopScreen(){super(Component.literal("Магазин"),UiRoute.SHOP);}
    @Override protected void init(){installNavigation();arena.client.net.ArenaClientNetwork.requestSnapshot();UiLayout.Rect p=UiLayout.of(width,height).contentPanel();int x=p.x()+14,y=p.y()+54,w=p.width()-28,step=p.height()<300?40:43;addSkinButtons(x,y,w,"neon_pulse",8);y+=step;addSkinButtons(x,y,w,"crimson_grid",12);y+=step;addSkinButtons(x,y,w,"void_ice",18);y+=step;addRenderableWidget(new ArenaButton(new UiLayout.Rect(x+w-126,y+14,126,18),Component.literal("✦ 6◆  НА РАУНД"),b->send("companion swittie")));int bottom=p.y()+p.height()-24;addRenderableWidget(new ArenaButton(new UiLayout.Rect(p.x()+14,bottom,84,18),Component.literal("×  СНЯТЬ"),b->send("skinuse none")));}
    private void addSkinButtons(int x,int y,int w,String id,int price){int buyW=Math.min(98,Math.max(80,w/4)),useW=Math.min(78,Math.max(64,w/5)),by=y+15;addRenderableWidget(new ArenaButton(new UiLayout.Rect(x+w-buyW-useW-5,by,buyW,18),Component.literal("◆"+price+"  КУПИТЬ"),b->send("skinbuy "+id)));addRenderableWidget(new ArenaButton(new UiLayout.Rect(x+w-useW,by,useW,18),Component.literal("✓ НАДЕТЬ"),b->send("skinuse "+id)));}
    private void send(String cmd){if(minecraft!=null&&minecraft.player!=null)minecraft.player.connection.sendCommand(cmd);arena.client.net.ArenaClientNetwork.requestSnapshot();}
    @Override public void render(GuiGraphics g,int mx,int my,float pt){drawBackdrop(g);UiLayout.Rect p=UiLayout.of(width,height).contentPanel();drawPanel(g,p);int x=p.x()+14,y=p.y()+10;var s=ClientSnapshotStore.get();g.fill(x,y-3,p.x()+p.width()-14,y+14,0x4A182338);g.drawString(font,Component.literal("✦ МАГАЗИН"),x+5,y,UiTheme.PINK);String crystals="◆ "+s.crystals();g.drawString(font,Component.literal(crystals),p.x()+p.width()-20-font.width(crystals),y,UiTheme.ACCENT_2);y+=39;int step=p.height()<300?40:43;card(g,x,y,p.width()-28,"NEON PULSE","◆ 8",0xFF55DFFF);y+=step;card(g,x,y,p.width()-28,"CRIMSON GRID","◆ 12",0xFFD86B7E);y+=step;card(g,x,y,p.width()-28,"VOID ICE","◆ 18",0xFF93B7EF);y+=step;companion(g,x,y,p.width()-28);super.render(g,mx,my,pt);}
    private void card(GuiGraphics g,int x,int y,int w,String n,String price,int tint){g.fill(x,y,x+w,y+36,0xB0141D2D);g.renderOutline(x,y,w,36,UiTheme.HAIRLINE);g.fill(x,y,x+2,y+36,tint);g.drawString(font,Component.literal("◇ "+n),x+10,y+7,UiTheme.TEXT);g.drawString(font,Component.literal(price),x+10,y+20,UiTheme.MUTED);}
    private void companion(GuiGraphics g,int x,int y,int w){g.fill(x,y,x+w,y+36,0xB0181728);g.renderOutline(x,y,w,36,UiTheme.ACCENT_2);g.fill(x,y,x+2,y+36,UiTheme.PINK);g.drawString(font,Component.literal("✦ СВИТТИ ФОКС"),x+10,y+7,UiTheme.PINK);g.drawString(font,Component.literal("спутник • следующий раунд"),x+10,y+20,UiTheme.DIM);}
    @Override public boolean isPauseScreen(){return false;}
}
