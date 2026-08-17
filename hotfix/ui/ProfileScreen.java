package arena.client.ui;

import arena.client.net.ArenaClientNetwork;
import arena.client.net.ClientSnapshotStore;
import arena.forge.ProgressionNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** GGO profile with PvP rank, kill XP and next-rank progress. */
final class ProfileScreen extends AbstractArenaScreen {
    private ProgressionNetwork.Snapshot data=new ProgressionNetwork.Snapshot(0,0,0,0,0,0,"НОВОБРАНЕЦ","БОЕЦ",300,5,100);
    ProfileScreen(){super(Component.literal("GunGloryOnline • Профиль"),UiRoute.PROFILE);}

    @Override protected void init(){installNavigation();ProgressionNetwork.setClientConsumer(s->data=s);ProgressionNetwork.request();ArenaClientNetwork.requestSnapshot();UiLayout.Rect p=UiLayout.of(width,height).contentPanel();int bw=Math.min(190,p.width()-36);addRenderableWidget(new ArenaButton(new UiLayout.Rect(p.x()+p.width()/2-bw/2,p.y()+p.height()-28,bw,19),Component.literal("← В ГЛАВНОЕ МЕНЮ"),b->ArenaNavigation.navigate(UiRoute.MAIN)));}

    @Override public void render(GuiGraphics g,int mx,int my,float pt){
        drawBackdrop(g);UiLayout.Rect p=UiLayout.of(width,height).contentPanel();drawPanel(g,p);long now=System.currentTimeMillis();UiEffects.pulseBorder(g,p.x(),p.y(),p.width(),p.height(),now,UiTheme.ACCENT);UiEffects.verticalGradient(g,p.x()+1,p.y()+1,p.x()+p.width()-1,p.y()+74,0x551B3550,0x06101725);
        var snap=ClientSnapshotStore.get();int x=p.x()+20,y=p.y()+17;
        g.drawString(font,Component.literal("✦ ПРОФИЛЬ GGO"),x,y,UiTheme.PINK);g.drawString(font,Component.literal("ЗВАНИЕ"),x,y+33,UiTheme.MUTED);g.drawString(font,Component.literal("★ "+data.rank()),x,y+47,UiTheme.GOLD);
        int bx=x,by=y+78,bw=p.width()-40,bh=16;int currentFloor=rankFloor(data.rank());int target=Math.max(currentFloor+1,data.nextThreshold());double progress=data.nextRank().equals("MAX")?1.0:Math.max(0.0,Math.min(1.0,(data.xp()-currentFloor)/(double)Math.max(1,target-currentFloor)));g.fill(bx,by,bx+bw,by+bh,0xC10A101B);g.renderOutline(bx,by,bw,bh,UiTheme.HAIRLINE);int fill=(int)((bw-2)*progress);if(fill>0)UiEffects.verticalGradient(g,bx+1,by+1,bx+1+fill,by+bh-1,0xFF39D9E6,0xFFFF5FA2);
        String xpText=data.nextRank().equals("MAX")?data.xp()+" XP • МАКСИМАЛЬНОЕ ЗВАНИЕ":data.xp()+" / "+data.nextThreshold()+" XP  →  "+data.nextRank();g.drawCenteredString(font,Component.literal(xpText),bx+bw/2,by+4,UiTheme.TEXT);
        g.drawString(font,Component.literal("До следующего звания: "+(data.nextRank().equals("MAX")?"—":Math.max(0,data.nextThreshold()-data.xp())+" XP")),x,by+25,UiTheme.ACCENT);g.drawString(font,Component.literal("Убийства игроков: "+data.kills()+"   •   +"+data.xpPerKill()+" XP за убийство"),x,by+43,UiTheme.TEXT);
        int cy=by+76;g.drawString(font,Component.literal("БОЕВЫЕ УЛУЧШЕНИЯ"),x,cy,UiTheme.MUTED);g.drawString(font,Component.literal("Скорость "+data.speed()+"/5   •   Живучесть "+data.health()+"/5"),x,cy+18,UiTheme.TEXT);g.drawString(font,Component.literal("Урон "+data.damage()+"/5   •   Защита "+data.armor()+"/5"),x,cy+34,UiTheme.TEXT);
        g.drawString(font,Component.literal("Баланс:  ◆ "+snap.crystals()+"   ◇ "+snap.coins()),x,cy+64,UiTheme.ACCENT);
        super.render(g,mx,my,pt);
    }
    private static int rankFloor(String rank){return switch(rank){case "БОЕЦ"->300;case "ШТУРМОВИК"->800;case "ВЕТЕРАН"->1500;case "ЭЛИТА"->2500;case "АС"->4000;case "ЛЕГЕНДА"->6500;default->0;};}
    @Override public void onClose(){ProgressionNetwork.setClientConsumer(null);super.onClose();}
    @Override public boolean isPauseScreen(){return false;}
}