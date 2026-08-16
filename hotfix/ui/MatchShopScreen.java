package arena.client.ui;

import arena.client.net.ArenaClientNetwork;
import arena.client.net.ArenaClientShopItem;
import arena.client.net.ClientShopStore;
import arena.client.net.ClientSnapshotStore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Dedicated in-match weapon shop opened by G. Gameplay/network behavior stays server-authoritative. */
final class MatchShopScreen extends Screen {
    private static final String[] CATEGORIES={"ALL","PISTOL","SMG","RIFLE","SHOTGUN","SNIPER","HEAVY"};
    private final List<ArenaButton> buttons=new ArrayList<>();
    private final List<RowHit> rowHits=new ArrayList<>();
    private String category="ALL";
    private int selectedIndex;
    private int page;
    private long seen=-1;

    MatchShopScreen(){super(Component.literal("GunGloryOnline • Оружие"));}

    @Override protected void init(){
        ArenaClientNetwork.requestSnapshot();
        ArenaClientNetwork.requestCatalog();
        rebuild();
    }

    private void rebuild(){
        for(ArenaButton b:buttons) removeWidget(b);
        buttons.clear(); rowHits.clear();
        UiLayout.Rect p=panel();
        int pad=12;
        int categoryY=p.y()+38;
        int available=p.width()-pad*2-(CATEGORIES.length-1)*3;
        int cw=Math.max(36,available/CATEGORIES.length);
        int cx=p.x()+pad;
        for(String c:CATEGORIES){
            final String value=c;
            ArenaButton b=new ArenaButton(new UiLayout.Rect(cx,categoryY,cw,18),Component.literal(label(c)),q->{category=value;page=0;selectedIndex=0;rebuild();});
            buttons.add(addRenderableWidget(b));
            cx+=cw+3;
        }

        Layout l=layout(p);
        List<ArenaClientShopItem> filtered=filtered();
        if(selectedIndex>=filtered.size()) selectedIndex=Math.max(0,filtered.size()-1);
        int maxPage=Math.max(0,(filtered.size()-1)/Math.max(1,l.rows));
        page=Math.max(0,Math.min(page,maxPage));
        int start=page*l.rows;
        for(int i=0;i<l.rows&&start+i<filtered.size();i++){
            int idx=start+i;
            UiLayout.Rect rr=new UiLayout.Rect(l.listX,l.listY+i*l.rowH,l.listW,l.rowH-3);
            ArenaButton hit=new ArenaButton(rr,Component.empty(),q->{selectedIndex=idx;rebuild();});
            buttons.add(addRenderableWidget(hit));
            rowHits.add(new RowHit(rr,idx));
        }

        if(!filtered.isEmpty()){
            ArenaClientShopItem it=filtered.get(Math.min(selectedIndex,filtered.size()-1));
            var snap=ClientSnapshotStore.get();
            boolean canAfford=snap.roundCredits()>=it.price();
            String text=!it.available()?"НЕДОСТУПНО":(!canAfford?"НЕДОСТАТОЧНО СРЕДСТВ":"✦ КУПИТЬ ЗА $"+it.price());
            ArenaButton buy=new ArenaButton(new UiLayout.Rect(l.detailX,l.buyY,l.detailW,22),Component.literal(text),q->ArenaClientNetwork.buy(it.id()));
            buy.active=it.available()&&canAfford&&UiAccessPolicy.canShop(snap,ClientSnapshotStore.fresh(System.currentTimeMillis()));
            buttons.add(addRenderableWidget(buy));
        }
    }

    private UiLayout.Rect panel(){
        UiLayout.Rect base=UiLayout.of(width,height).contentPanel();
        int desired=Math.min(base.height(),Math.max(190,height-64));
        return new UiLayout.Rect(base.x(),base.y(),base.width(),desired);
    }

    private Layout layout(UiLayout.Rect p){
        int pad=12;
        int bodyY=p.y()+64;
        int footer=31;
        int bodyH=Math.max(92,p.height()-64-footer);
        int gap=9;
        int bodyW=p.width()-pad*2;
        int listW=Math.max(146,(bodyW-gap)*55/100);
        int detailW=Math.max(118,bodyW-gap-listW);
        if(listW+gap+detailW>bodyW) listW=Math.max(130,bodyW-gap-detailW);
        int rowH=39;
        int rows=Math.max(2,Math.min(7,bodyH/rowH));
        return new Layout(p.x()+pad,bodyY,listW,p.x()+pad+listW+gap,detailW,rowH,rows,p.y()+p.height()-27);
    }

    private List<ArenaClientShopItem> filtered(){return ClientShopStore.items().stream().filter(e->"ALL".equals(category)||category.equals(e.category())).toList();}
    private static String label(String c){return switch(c){case"ALL"->"ВСЕ";case"PISTOL"->"ПИСТ.";case"SMG"->"ПП";case"RIFLE"->"ВИНТ.";case"SHOTGUN"->"ДРОБ.";case"SNIPER"->"СНАЙП.";case"HEAVY"->"ТЯЖ.";default->c;};}

    @Override public void tick(){long s=ClientShopStore.resultSerial();if(s!=seen){seen=s;rebuild();}}

    @Override public void render(GuiGraphics g,int mx,int my,float pt){
        UiLayout.Rect p=panel();
        g.fill(0,0,width,height,0xA60A0F1A);
        g.fill(p.x(),p.y(),p.x()+p.width(),p.y()+p.height(),0xF0121828);
        g.renderOutline(p.x(),p.y(),p.width(),p.height(),UiTheme.BLUE);

        g.drawString(font,Component.literal("✦ ОРУЖИЕ • G"),p.x()+12,p.y()+12,UiTheme.PINK);
        var snap=ClientSnapshotStore.get();
        String money="$"+snap.roundCredits();
        int walletW=font.width(money)+14,wx=p.x()+p.width()-12-walletW;
        g.fill(wx,p.y()+7,wx+walletW,p.y()+25,0xC0182136);
        g.renderOutline(wx,p.y()+7,walletW,18,UiTheme.ACCENT_2);
        g.drawCenteredString(font,Component.literal(money),wx+walletW/2,p.y()+12,UiTheme.ACCENT_2);

        super.render(g,mx,my,pt);

        int pad=12,categoryY=p.y()+38;
        int available=p.width()-pad*2-(CATEGORIES.length-1)*3;
        int cw=Math.max(36,available/CATEGORIES.length),cx=p.x()+pad;
        for(String c:CATEGORIES){
            if(c.equals(category)){
                g.renderOutline(cx,categoryY,cw,18,UiTheme.BLUE);
                g.fill(cx+2,categoryY+16,cx+cw-2,categoryY+18,UiTheme.ACCENT);
            }
            cx+=cw+3;
        }

        Layout l=layout(p);
        g.fill(l.detailX-4,l.listY-4,l.detailX+l.detailW+4,l.buyY-6,0x6E0C1220);
        g.renderOutline(l.detailX-4,l.listY-4,l.detailW+8,Math.max(34,l.buyY-l.listY-2),0x804FD6FF);

        List<ArenaClientShopItem> filtered=filtered();
        int start=page*l.rows;
        for(RowHit hit:rowHits){
            if(hit.index<0||hit.index>=filtered.size()) continue;
            ArenaClientShopItem it=filtered.get(hit.index);
            UiLayout.Rect r=hit.rect;
            boolean selected=hit.index==selectedIndex;
            boolean hover=r.contains(mx,my);
            int bg=selected?0xE01A2A43:(hover?0xC8182438:0xAE101827);
            g.fill(r.x(),r.y(),r.x()+r.width(),r.y()+r.height(),bg);
            if(selected) g.fill(r.x(),r.y(),r.x()+3,r.y()+r.height(),UiTheme.ACCENT);
            int iconX=r.x()+7,iconY=r.y()+8;
            g.fill(iconX-3,iconY-3,iconX+19,iconY+19,0xA00A101C);
            ItemStack stack=itemStack(it);
            if(!stack.isEmpty()) g.renderItem(stack,iconX,iconY);
            int tx=r.x()+31;
            String en=fit(it.displayName(),Math.max(44,r.width()-76));
            String ru=fit(WeaponNames.russianFor(it.id(),it.category()),Math.max(44,r.width()-76));
            g.drawString(font,Component.literal(en),tx,r.y()+6,selected?0xFFFFFFFF:UiTheme.TEXT);
            g.drawString(font,Component.literal(ru),tx,r.y()+19,UiTheme.MUTED);
            String price="$"+it.price();
            g.drawString(font,Component.literal(price),r.x()+r.width()-6-font.width(price),r.y()+6,it.available()?UiTheme.ACCENT_2:UiTheme.MUTED);
        }

        if(!filtered.isEmpty()){
            ArenaClientShopItem it=filtered.get(Math.min(selectedIndex,filtered.size()-1));
            drawSelected(g,it,l);
        } else {
            g.drawCenteredString(font,Component.literal("Каталог пуст"),p.x()+p.width()/2,p.y()+p.height()/2,UiTheme.MUTED);
        }

        if(filtered.size()>l.rows){
            int maxPage=Math.max(0,(filtered.size()-1)/l.rows);
            String pg=(page+1)+" / "+(maxPage+1)+"  •  колесо мыши";
            g.drawString(font,Component.literal(pg),l.listX,l.buyY+6,UiTheme.MUTED);
        }

        String footer="ESC — закрыть   •   G — закрыть магазин";
        g.drawCenteredString(font,Component.literal(footer),p.x()+p.width()/2,p.y()+p.height()-13,UiTheme.MUTED);
        String result=ClientShopStore.resultMessage();
        if(!result.isBlank()) g.drawString(font,Component.literal(fit(result,p.width()-24)),p.x()+12,p.y()+p.height()-24,ClientShopStore.resultOk()?UiTheme.ACCENT:0xFFFF7373);
    }

    private void drawSelected(GuiGraphics g,ArenaClientShopItem it,Layout l){
        int cx=l.detailX+l.detailW/2;
        int previewY=l.listY+4;
        ItemStack stack=itemStack(it);
        g.fill(cx-27,previewY,cx+27,previewY+50,0xB00A101C);
        g.renderOutline(cx-27,previewY,54,50,UiTheme.PINK);
        if(!stack.isEmpty()){
            g.pose().pushPose();g.pose().translate(cx-16,previewY+9,120);g.pose().scale(2.0F,2.0F,2.0F);g.renderItem(stack,0,0);g.pose().popPose();
        }
        int y=previewY+57;
        g.drawCenteredString(font,Component.literal(fit(it.displayName(),l.detailW-8)),cx,y,UiTheme.TEXT);
        g.drawCenteredString(font,Component.literal(fit(WeaponNames.russianFor(it.id(),it.category()),l.detailW-8)),cx,y+12,UiTheme.PINK);
        y+=30;
        int gap=4,sw=(l.detailW-gap)/2,sh=27;
        stat(g,l.detailX,y,sw,sh,"МАГАЗИН",String.valueOf(it.magazineSize()));
        stat(g,l.detailX+sw+gap,y,sw,sh,"ЗАПАС",String.valueOf(it.startingReserve()));
        stat(g,l.detailX,y+sh+gap,sw,sh,"РЕЖИМ",fit(it.fireModes(),sw-8));
        stat(g,l.detailX+sw+gap,y+sh+gap,sw,sh,"ПРИЦЕЛ",fit(it.scope(),sw-8));
        int priceY=Math.min(l.buyY-25,y+(sh+gap)*2+7);
        String price="Цена  $"+it.price();
        g.drawString(font,Component.literal(price),l.detailX,priceY,UiTheme.ACCENT_2);
    }

    private void stat(GuiGraphics g,int x,int y,int w,int h,String k,String v){
        g.fill(x,y,x+w,y+h,0x9A151D2D);
        g.renderOutline(x,y,w,h,0x603A5675);
        g.drawString(font,Component.literal(k),x+5,y+4,UiTheme.MUTED);
        g.drawString(font,Component.literal(v),x+5,y+15,UiTheme.TEXT);
    }

    private ItemStack itemStack(ArenaClientShopItem it){
        ResourceLocation id=ResourceLocation.tryParse(it.id());
        if(id==null||!BuiltInRegistries.ITEM.containsKey(id)) return ItemStack.EMPTY;
        var item=BuiltInRegistries.ITEM.get(id);
        return item==Items.AIR?ItemStack.EMPTY:new ItemStack(item);
    }

    private String fit(String text,int widthPx){
        if(text==null||text.isBlank()) return "—";
        if(font.width(text)<=widthPx) return text;
        String ell="…";int limit=Math.max(0,widthPx-font.width(ell));
        return font.plainSubstrByWidth(text,limit)+ell;
    }

    @Override public boolean mouseScrolled(double mx,double my,double delta){
        Layout l=layout(panel());
        if(new UiLayout.Rect(l.listX,l.listY,l.listW,l.rows*l.rowH).contains(mx,my)){
            int maxPage=Math.max(0,(filtered().size()-1)/Math.max(1,l.rows));
            int old=page;
            if(delta<0) page=Math.min(maxPage,page+1); else if(delta>0) page=Math.max(0,page-1);
            if(page!=old){rebuild();return true;}
        }
        return super.mouseScrolled(mx,my,delta);
    }

    @Override public boolean keyPressed(int keyCode,int scanCode,int modifiers){
        if(keyCode==GLFW.GLFW_KEY_G||keyCode==GLFW.GLFW_KEY_ESCAPE){onClose();return true;}
        return super.keyPressed(keyCode,scanCode,modifiers);
    }

    @Override public void onClose(){if(minecraft!=null)minecraft.setScreen(null);}
    @Override public boolean isPauseScreen(){return false;}

    private record Layout(int listX,int listY,int listW,int detailX,int detailW,int rowH,int rows,int buyY){}
    private record RowHit(UiLayout.Rect rect,int index){}
}
