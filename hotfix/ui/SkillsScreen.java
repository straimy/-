package arena.client.ui;

import arena.client.net.ArenaClientNetwork;
import arena.client.net.ClientSnapshotStore;
import arena.forge.ProgressionNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Four simple, real combat upgrades. No dependency tree or pagination. */
final class SkillsScreen extends AbstractArenaScreen {
    private ProgressionNetwork.Snapshot data=new ProgressionNetwork.Snapshot(0,0,0,0,0,0,"НОВОБРАНЕЦ","БОЕЦ",300,5,100);
    SkillsScreen(){super(Component.literal("GunGloryOnline • Навыки"),UiRoute.SKILLS);}

    @Override protected void init(){
        installNavigation();
        ProgressionNetwork.setClientConsumer(s->{data=s;rebuild();});
        ProgressionNetwork.request();ArenaClientNetwork.requestSnapshot();rebuild();
    }

    private void rebuild(){
        clearWidgets();installNavigation();UiLayout.Rect p=UiLayout.of(width,height).contentPanel();int x=p.x()+18,y=p.y()+78,w=p.width()-36;int gap=8,rowH=46;
        skill(x,y,w,rowH,"speed","СКОРОСТЬ",data.speed(),"+3% скорости движения за уровень");
        skill(x,y+(rowH+gap),w,rowH,"health","ЖИВУЧЕСТЬ",data.health(),"+1 сердце максимального здоровья за уровень");
        skill(x,y+(rowH+gap)*2,w,rowH,"damage","УРОН",data.damage(),"+2.5% любого наносимого урона за уровень");
        skill(x,y+(rowH+gap)*3,w,rowH,"armor","ЗАЩИТА",data.armor(),"−2.5% получаемого урона за уровень");
        int bw=Math.min(190,w);addRenderableWidget(new ArenaButton(new UiLayout.Rect(p.x()+p.width()/2-bw/2,p.y()+p.height()-28,bw,19),Component.literal("← В ГЛАВНОЕ МЕНЮ"),b->ArenaNavigation.navigate(UiRoute.MAIN)));
    }

    private void skill(int x,int y,int w,int h,String id,String title,int level,String desc){
        int buttonW=Math.min(150,Math.max(118,w/3));int cost=20+level*15;String lv="LV "+level+" / "+data.maxLevel();
        addRenderableWidget(new ArenaButton(new UiLayout.Rect(x+w-buttonW,y+8,buttonW,28),Component.literal(level>=data.maxLevel()?"✓ МАКСИМУМ":"✦ ПРОКАЧАТЬ  ◆ "+cost),b->{ProgressionNetwork.upgrade(id);ProgressionNetwork.request();ArenaClientNetwork.requestSnapshot();}));
    }

    @Override public void tick(){super.tick();}
    @Override public void render(GuiGraphics g,int mx,int my,float pt){
        drawBackdrop(g);UiLayout.Rect p=UiLayout.of(width,height).contentPanel();drawPanel(g,p);long now=System.currentTimeMillis();
        UiEffects.verticalGradient(g,p.x()+1,p.y()+1,p.x()+p.width()-1,p.y()+66,0x501B3550,0x06101725);UiEffects.animatedSheen(g,p.x()+1,p.y()+1,p.width()-2,3,now,UiTheme.ACCENT);UiEffects.pulseBorder(g,p.x(),p.y(),p.width(),p.height(),now,UiTheme.ACCENT);
        int x=p.x()+18,y=p.y()+14;g.drawString(font,Component.literal("✦ БОЕВЫЕ НАВЫКИ"),x,y,UiTheme.PINK);g.drawString(font,Component.literal("4 понятных улучшения • работают в каждом бою"),x,y+16,UiTheme.MUTED);
        var snap=ClientSnapshotStore.get();g.drawString(font,Component.literal("◆ "+snap.crystals()+" кристаллов"),x,y+33,UiTheme.ACCENT);
        int yy=p.y()+78,w=p.width()-36,rowH=46,gap=8;drawSkill(g,x,yy,w,rowH,"СКОРОСТЬ",data.speed(),"+3% скорости движения за уровень");drawSkill(g,x,yy+(rowH+gap),w,rowH,"ЖИВУЧЕСТЬ",data.health(),"+1 сердце максимального здоровья за уровень");drawSkill(g,x,yy+(rowH+gap)*2,w,rowH,"УРОН",data.damage(),"+2.5% наносимого урона за уровень");drawSkill(g,x,yy+(rowH+gap)*3,w,rowH,"ЗАЩИТА",data.armor(),"−2.5% получаемого урона за уровень");
        super.render(g,mx,my,pt);
    }
    private void drawSkill(GuiGraphics g,int x,int y,int w,int h,String name,int lv,String desc){int buttonW=Math.min(150,Math.max(118,w/3));int cardW=w-buttonW-8;UiEffects.verticalGradient(g,x,y,x+cardW,y+h,0xC0192940,0xA80E1726);g.renderOutline(x,y,cardW,h,lv>=data.maxLevel()?UiTheme.GOLD:UiTheme.HAIRLINE);g.drawString(font,Component.literal(name),x+10,y+8,UiTheme.TEXT);g.drawString(font,Component.literal("LV "+lv+" / "+data.maxLevel()),x+10,y+21,lv>=data.maxLevel()?UiTheme.GOLD:UiTheme.ACCENT);g.drawString(font,Component.literal(desc),x+82,y+21,UiTheme.MUTED);}
    @Override public void onClose(){ProgressionNetwork.setClientConsumer(null);super.onClose();}
    @Override public boolean isPauseScreen(){return false;}
}