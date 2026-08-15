package arena.client.ui;

import arena.client.net.ArenaClientNetwork;
import arena.client.net.ArenaClientShopItem;
import arena.client.net.ClientShopStore;
import arena.client.net.ClientSnapshotStore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class ShopScreen extends AbstractArenaScreen {
    private String category = "ALL";
    private int selectedIndex;
    private int page;
    private long seenResultSerial = -1;
    private final List<ArenaButton> dynamicButtons = new ArrayList<>();

    ShopScreen() { super(Component.literal("Магазин"), UiRoute.SHOP); }

    @Override protected void init() {
        clearWidgets(); dynamicButtons.clear(); installNavigation(); ArenaClientNetwork.requestSnapshot(); ArenaClientNetwork.requestCatalog(); rebuildDynamicButtons();
    }

    private void rebuildDynamicButtons() {
        for (ArenaButton b : dynamicButtons) removeWidget(b);
        dynamicButtons.clear();
        UiLayout.Rect panel = UiLayout.of(width,height).contentPanel();
        int x = panel.x()+18, y=panel.y()+40;
        Set<String> cats = new LinkedHashSet<>(); cats.add("ALL");
        for (ArenaClientShopItem e : ClientShopStore.items()) cats.add(e.category());
        int catWidth = Math.max(58, Math.min(88, (panel.width()-36) / Math.max(1,cats.size())));
        int cx=x;
        for(String c:cats){
            if(cx+catWidth>panel.x()+panel.width()-18) break;
            final String value=c;
            ArenaButton b=new ArenaButton(new UiLayout.Rect(cx,y,catWidth-4,19),Component.literal(labelCategory(c)),btn->{ category=value;page=0;selectedIndex=0;rebuildDynamicButtons(); });
            dynamicButtons.add(addRenderableWidget(b)); cx+=catWidth;
        }

        List<ArenaClientShopItem> filtered=filtered();
        int rows=Math.max(3,Math.min(6,(panel.height()-122)/24));
        int start=Math.min(page*rows,Math.max(0,filtered.size()-1));
        if(selectedIndex>=filtered.size()) selectedIndex=Math.max(0,filtered.size()-1);
        int listX=x, listY=y+27, listW=Math.max(180,panel.width()/2-22);
        for(int i=0;i<rows && start+i<filtered.size();i++){
            int idx=start+i; ArenaClientShopItem item=filtered.get(idx);
            String state=item.quarantined()?" ×":(!item.available()?" …":"");
            String label=WeaponNames.label(item)+"  §b$"+item.price()+state;
            ArenaButton b=new ArenaButton(new UiLayout.Rect(listX,listY+i*24,listW,20),Component.literal(label),btn->{selectedIndex=idx;rebuildDynamicButtons();});
            b.active=true; dynamicButtons.add(addRenderableWidget(b));
        }
        int detailX=panel.x()+panel.width()/2+12;
        int buyY=panel.y()+panel.height()-44;
        if(!filtered.isEmpty()){
            ArenaClientShopItem selected=filtered.get(Math.min(selectedIndex,filtered.size()-1));
            String buyLabel=selected.available()?"✦ КУПИТЬ  $"+selected.price():(selected.quarantined()?"× ОТКЛЮЧЕНО":"… НЕДОСТУПНО");
            ArenaButton buy=new ArenaButton(new UiLayout.Rect(detailX,buyY,Math.max(120,panel.width()/2-32),23),Component.literal(buyLabel),btn->ArenaClientNetwork.buy(selected.id()));
            buy.active=selected.available() && UiAccessPolicy.canShop(ClientSnapshotStore.get(), ClientSnapshotStore.fresh(System.currentTimeMillis()));
            dynamicButtons.add(addRenderableWidget(buy));
        }
        if(start>0){ ArenaButton prev=new ArenaButton(new UiLayout.Rect(listX,buyY,54,23),Component.literal("←"),b->{page=Math.max(0,page-1);rebuildDynamicButtons();});dynamicButtons.add(addRenderableWidget(prev)); }
        if(start+rows<filtered.size()){ ArenaButton next=new ArenaButton(new UiLayout.Rect(listX+60,buyY,54,23),Component.literal("→"),b->{page++;rebuildDynamicButtons();});dynamicButtons.add(addRenderableWidget(next)); }
    }

    private List<ArenaClientShopItem> filtered(){ return ClientShopStore.items().stream().filter(e->"ALL".equals(category)||category.equals(e.category())).toList(); }
    private static String labelCategory(String c){ return switch(c){case "ALL"->"✦ ВСЕ";case "PISTOL"->"ПИСТ.";case "SMG"->"ПП";case "RIFLE"->"ВИНТ.";case "SHOTGUN"->"ДРОБ.";case "DMR"->"DMR";case "SNIPER"->"СНАЙП.";case "HEAVY"->"ТЯЖ.";default->c;}; }

    @Override public void tick(){ super.tick(); long serial=ClientShopStore.resultSerial(); if(serial!=seenResultSerial){ seenResultSerial=serial; rebuildDynamicButtons(); } }

    private void drawWeaponPreview(GuiGraphics g, ArenaClientShopItem e, int x, int y) {
        ResourceLocation id = ResourceLocation.tryParse(e.id());
        ItemStack stack = ItemStack.EMPTY;
        if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
            var item = BuiltInRegistries.ITEM.get(id);
            if (item != Items.AIR) stack = new ItemStack(item);
        }
        g.fill(x-6,y-6,x+62,y+62,0x99101626);
        g.renderOutline(x-6,y-6,68,68,UiTheme.PINK);
        if (!stack.isEmpty()) {
            g.pose().pushPose(); g.pose().translate(x+4,y+4,120.0F); g.pose().scale(3.0F,3.0F,3.0F); g.renderItem(stack,0,0); g.pose().popPose();
        } else g.drawCenteredString(font,Component.literal("✦ WEAPON ✦"),x+28,y+22,UiTheme.BLUE);
    }

    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick){
        drawBackdrop(g); UiLayout.Rect panel=UiLayout.of(width,height).contentPanel(); drawPanel(g,panel);
        var snap=ClientSnapshotStore.get();
        g.drawString(font,Component.literal("◈ МАГАЗИН"),panel.x()+18,panel.y()+15,UiTheme.PINK);
        String wallet="$"+snap.roundCredits()+"  ◇"+snap.coins()+"  ◆"+snap.crystals();
        g.drawString(font,Component.literal(wallet),panel.x()+panel.width()-font.width(wallet)-18,panel.y()+15,UiTheme.BLUE);
        List<ArenaClientShopItem> filtered=filtered();
        boolean snapshotFresh=ClientSnapshotStore.fresh(System.currentTimeMillis());
        boolean catalogFresh=ClientShopStore.catalogFresh(System.currentTimeMillis());
        if(!snapshotFresh) g.drawString(font,Component.literal("↻ Core…"),panel.x()+18,panel.y()+68,UiTheme.ACCENT_2);
        else if(!snap.authenticated()||!snap.initialized()) g.drawString(font,Component.literal("Нужна авторизация"),panel.x()+18,panel.y()+68,UiTheme.MUTED);
        else if(!catalogFresh) g.drawString(font,Component.literal("↻ Каталог…"),panel.x()+18,panel.y()+68,UiTheme.MUTED);
        else if(ClientShopStore.items().isEmpty()) g.drawString(font,Component.literal("Каталог пуст"),panel.x()+18,panel.y()+68,UiTheme.MUTED);
        else if(!filtered.isEmpty()){
            ArenaClientShopItem e=filtered.get(Math.min(selectedIndex,filtered.size()-1));
            int x=panel.x()+panel.width()/2+12,y=panel.y()+68;
            drawWeaponPreview(g,e,x,y);
            int tx=x+76;
            g.drawString(font,Component.literal(e.displayName()),tx,y,UiTheme.TEXT);
            g.drawString(font,Component.literal("("+WeaponNames.russianFor(e.id(),e.category())+")"),tx,y+13,UiTheme.PINK);
            g.drawString(font,Component.literal("$"+e.price()+"  •  "+labelCategory(e.category())),tx,y+31,UiTheme.GOLD);
            g.drawString(font,Component.literal("▣ "+e.magazineSize()+" / "+e.startingReserve()),tx,y+46,UiTheme.BLUE);
            g.drawString(font,Component.literal("⚙ "+e.fireModes()+"  ◉ "+e.scope()),tx,y+61,UiTheme.MUTED);
            int status=e.quarantined()?0xFFFF7373:(e.available()?UiTheme.GREEN:UiTheme.MUTED);
            String txt=e.quarantined()?"× ОТКЛЮЧЕНО":(e.available()?"● ДОСТУПНО":"… СКОРО");
            g.drawString(font,Component.literal(txt),tx,y+78,status);
        }
        String result=ClientShopStore.resultMessage();
        if(!result.isBlank()) g.drawString(font,Component.literal("✦ "+result),panel.x()+18,panel.y()+panel.height()-17,ClientShopStore.resultOk()?UiTheme.GREEN:0xFFFF7373);
        super.render(g,mouseX,mouseY,partialTick);
    }
    @Override public boolean isPauseScreen(){return false;}
}
