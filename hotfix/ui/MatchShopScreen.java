package arena.client.ui;

import arena.client.net.ArenaClientNetwork;
import arena.client.net.ArenaClientShopItem;
import arena.client.net.ClientShopStore;
import arena.client.net.ClientSnapshotStore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Dedicated in-match weapon shop opened by G. No links to profile/skills/main/lobby shop by design. */
final class MatchShopScreen extends Screen {
    private String category="ALL"; private int selectedIndex,page; private long seen=-1; private final List<ArenaButton> buttons=new ArrayList<>();
    MatchShopScreen(){super(Component.literal("GunGloryOnline • Оружие"));}
    @Override protected void init(){ArenaClientNetwork.requestSnapshot();ArenaClientNetwork.requestCatalog();rebuild();}
    private void rebuild(){for(ArenaButton b:buttons)removeWidget(b);buttons.clear();UiLayout.Rect p=UiLayout.of(width,height).contentPanel();int x=p.x()+12,y=p.y()+38;
        String[] cats={"ALL","PISTOL","SMG","RIFLE","SHOTGUN","SNIPER","HEAVY"};int cw=Math.max(42,(p.width()-24-(cats.length-1)*3)/cats.length),cx=x;
        for(String c:cats){final String v=c;ArenaButton b=new ArenaButton(new UiLayout.Rect(cx,y,cw,18),Component.literal(label(c)),q->{category=v;page=0;selectedIndex=0;rebuild();});buttons.add(addRenderableWidget(b));cx+=cw+3;}
        List<ArenaClientShopItem> f=filtered();int rows=Math.max(3,Math.min(6,(p.height()-112)/23));int start=Math.min(page*rows,Math.max(0,f.size()-1));if(selectedIndex>=f.size())selectedIndex=Math.max(0,f.size()-1);int listW=Math.max(150,p.width()/2-18),listY=y+25;
        for(int i=0;i<rows&&start+i<f.size();i++){int idx=start+i;ArenaClientShopItem it=f.get(idx);String ru=WeaponNames.russianFor(it.id(),"");String state=it.available()?"":it.quarantined()?" [ОТКЛ]":" [СКОРО]";ArenaButton b=new ArenaButton(new UiLayout.Rect(x,listY+i*23,listW,19),Component.literal(it.displayName()+" ("+ru+")  $"+it.price()+state),q->{selectedIndex=idx;rebuild();});buttons.add(addRenderableWidget(b));}
        int bottom=p.y()+p.height()-25;if(start>0)buttons.add(addRenderableWidget(new ArenaButton(new UiLayout.Rect(x,bottom,44,18),Component.literal("‹"),b->{page--;rebuild();})));if(start+rows<f.size())buttons.add(addRenderableWidget(new ArenaButton(new UiLayout.Rect(x+49,bottom,44,18),Component.literal("›"),b->{page++;rebuild();})));
        if(!f.isEmpty()){ArenaClientShopItem it=f.get(Math.min(selectedIndex,f.size()-1));int bx=p.x()+p.width()/2+5,bw=p.x()+p.width()-12-bx;ArenaButton buy=new ArenaButton(new UiLayout.Rect(bx,bottom,bw,18),Component.literal(it.available()?"✦ КУПИТЬ  $"+it.price():"НЕДОСТУПНО"),b->ArenaClientNetwork.buy(it.id()));buy.active=it.available()&&UiAccessPolicy.canShop(ClientSnapshotStore.get(),ClientSnapshotStore.fresh(System.currentTimeMillis()));buttons.add(addRenderableWidget(buy));}}
    private List<ArenaClientShopItem> filtered(){return ClientShopStore.items().stream().filter(e->"ALL".equals(category)||category.equals(e.category())).toList();}
    private static String label(String c){return switch(c){case"ALL"->"ВСЕ";case"PISTOL"->"ПИСТ.";case"SMG"->"ПП";case"RIFLE"->"ВИНТ.";case"SHOTGUN"->"ДРОБ.";case"SNIPER"->"СНАЙП.";case"HEAVY"->"ТЯЖ.";default->c;};}
    @Override public void tick(){long s=ClientShopStore.resultSerial();if(s!=seen){seen=s;rebuild();}}
    @Override public void render(GuiGraphics g,int mx,int my,float pt){UiLayout.Rect p=UiLayout.of(width,height).contentPanel();g.fill(0,0,width,height,0xA60A0F1A);g.fill(p.x(),p.y(),p.x()+p.width(),p.y()+p.height(),0xF0121828);g.renderOutline(p.x(),p.y(),p.width(),p.height(),UiTheme.BLUE);var snap=ClientSnapshotStore.get();g.drawString(font,Component.literal("✦ ОРУЖИЕ • G"),p.x()+12,p.y()+12,UiTheme.PINK);String money="$"+snap.roundCredits();g.drawString(font,Component.literal(money),p.x()+p.width()-12-font.width(money),p.y()+12,UiTheme.ACCENT_2);List<ArenaClientShopItem>f=filtered();if(!f.isEmpty()){ArenaClientShopItem it=f.get(Math.min(selectedIndex,f.size()-1));int x=p.x()+p.width()/2+5,y=p.y()+72;String ru=WeaponNames.russianFor(it.id(),"");g.drawString(font,Component.literal(it.displayName()),x,y,UiTheme.TEXT);g.drawString(font,Component.literal("("+ru+")"),x,y+15,UiTheme.PINK);g.drawString(font,Component.literal("▣ "+it.magazineSize()+"  +"+it.startingReserve()+"   •   "+it.fireModes()),x,y+32,UiTheme.MUTED);g.drawString(font,Component.literal("Прицел: "+it.scope()),x,y+47,UiTheme.MUTED);}g.drawCenteredString(font,Component.literal("ESC — закрыть • это отдельный боевой магазин"),p.x()+p.width()/2,p.y()+p.height()-41,UiTheme.MUTED);String r=ClientShopStore.resultMessage();if(!r.isBlank())g.drawString(font,Component.literal(r),p.x()+12,p.y()+p.height()-12,ClientShopStore.resultOk()?UiTheme.ACCENT:0xFFFF7373);super.render(g,mx,my,pt);}
    @Override public void onClose(){if(minecraft!=null)minecraft.setScreen(null);}
    @Override public boolean isPauseScreen(){return false;}
}
