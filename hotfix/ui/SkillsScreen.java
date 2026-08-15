package arena.client.ui;

import arena.client.net.ArenaClientNetwork;
import arena.client.net.ArenaClientSkillEntry;
import arena.client.net.ClientSkillTreeStore;
import arena.client.net.ClientSnapshotStore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.List;

final class SkillsScreen extends AbstractArenaScreen {
    private int page; private String selectedId;
    SkillsScreen(){super(Component.literal("GunGloryOnline • Навыки"),UiRoute.SKILLS);}
    @Override protected void init(){ installNavigation(); ArenaClientNetwork.requestSkillTree(); ArenaClientNetwork.requestSnapshot(); rebuild(); }

    private void rebuild(){
        clearWidgets(); installNavigation(); List<ArenaClientSkillEntry> all=ClientSkillTreeStore.entries(); UiLayout.Rect p=UiLayout.of(width,height).contentPanel();
        int rows=Math.max(3,Math.min(5,(p.height()-118)/25)); int cols=p.width()>=410?2:1; int per=rows*cols;
        int from=Math.min(page*per,all.size()),to=Math.min(from+per,all.size()); int gap=6, colW=(p.width()-32-gap*(cols-1))/cols; int baseY=p.y()+66;
        if(selectedId==null&&from<to)selectedId=all.get(from).id();
        for(int i=from;i<to;i++){
            ArenaClientSkillEntry e=all.get(i); int local=i-from,col=local/rows,row=local%rows; int x=p.x()+16+col*(colW+gap),y=baseY+row*25;
            String mark=e.id().equals(selectedId)?"◆ ":e.unlocked()?"✓ ":"· ";
            addRenderableWidget(new ArenaButton(new UiLayout.Rect(x,y,colW,20),Component.literal(mark+shortName(e.name())),b->{selectedId=e.id();rebuild();}));
        }
        ArenaClientSkillEntry sel=selected(all); int bottom=p.y()+p.height()-29;
        if(page>0)addRenderableWidget(new ArenaButton(new UiLayout.Rect(p.x()+16,bottom,70,19),Component.literal("‹"),b->{page--;selectedId=null;rebuild();}));
        if(to<all.size())addRenderableWidget(new ArenaButton(new UiLayout.Rect(p.x()+92,bottom,70,19),Component.literal("›"),b->{page++;selectedId=null;rebuild();}));
        if(sel!=null&&!sel.unlocked()){
            int bw=Math.min(160,p.width()/2); int bx=p.x()+p.width()-bw-16; int crystals=Math.max(1,(sel.cost()+9)/10);
            ArenaButton up=new ArenaButton(new UiLayout.Rect(bx,bottom,bw,19),Component.literal("✦ ПРОКАЧАТЬ • "+sel.cost()+"TP / "+crystals+"◆"),b->{ArenaClientNetwork.unlockSkill(sel.id());ArenaClientNetwork.requestSkillTree();ArenaClientNetwork.requestSnapshot();});
            up.active=sel.available(); addRenderableWidget(up);
        }
    }
    private ArenaClientSkillEntry selected(List<ArenaClientSkillEntry> a){for(var e:a)if(e.id().equals(selectedId))return e;return null;}
    private static String shortName(String s){return s.length()>20?s.substring(0,19)+"…":s;}

    @Override public void tick(){super.tick();if(minecraft!=null&&minecraft.level!=null&&minecraft.level.getGameTime()%20L==0L){ArenaClientNetwork.requestSkillTree();ArenaClientNetwork.requestSnapshot();}}
    @Override public void render(GuiGraphics g,int mx,int my,float pt){
        drawBackdrop(g);UiLayout.Rect p=UiLayout.of(width,height).contentPanel();drawPanel(g,p);int x=p.x()+16,y=p.y()+12;
        g.drawString(font,Component.literal("✦ НАВЫКИ"),x,y,UiTheme.ACCENT_2);var snap=ClientSnapshotStore.get();
        g.drawString(font,Component.literal("Lv."+ClientSkillTreeStore.level()+"  •  TP "+ClientSkillTreeStore.points()+"  •  ◆ "+snap.crystals()),x,y+18,UiTheme.TEXT);
        g.drawString(font,Component.literal("+1 TP / 20 мин  •  +1◆ / 30 мин  •  выбери навык → ПРОКАЧАТЬ"),x,y+34,UiTheme.MUTED);
        ArenaClientSkillEntry sel=selected(ClientSkillTreeStore.entries()); if(sel!=null){int cy=p.y()+p.height()-50;String state=sel.unlocked()?"✓ ОТКРЫТ":sel.available()?"доступен":"нужны предыдущие навыки";g.drawString(font,Component.literal(sel.name()+" • "+state),x,cy,sel.unlocked()?UiTheme.ACCENT_2:UiTheme.PINK);}
        super.render(g,mx,my,pt);
    }
    @Override public boolean isPauseScreen(){return false;}
}
