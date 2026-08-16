package arena.client.ui;

import arena.forge.FriendNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import java.util.List;

public final class FriendsScreen extends AbstractArenaScreen {
    private FriendNetwork.Snapshot snapshot=new FriendNetwork.Snapshot("--------",List.of());
    private EditBox input;
    public FriendsScreen(){super(Component.literal("Друзья"),UiRoute.MAIN);}
    @Override protected void init(){
        installNavigation();UiLayout.Rect p=UiLayout.of(width,height).contentPanel();
        input=new EditBox(font,p.x()+14,p.y()+42,p.width()-118,18,Component.literal("Ник или GGO-ID"));input.setHint(Component.literal("ник / GGO-ID"));addRenderableWidget(input);
        addRenderableWidget(new ArenaButton(new UiLayout.Rect(p.x()+p.width()-96,p.y()+42,82,18),Component.literal("＋ ДОБАВИТЬ"),b->{String t=input.getValue().trim();if(!t.isEmpty()){FriendNetwork.add(t);input.setValue("");}}));
        FriendNetwork.setClientConsumer(s->{snapshot=s;});FriendNetwork.request();
    }
    @Override public void render(GuiGraphics g,int mx,int my,float pt){
        drawBackdrop(g);UiLayout.Rect p=UiLayout.of(width,height).contentPanel();drawPanel(g,p);
        g.drawString(font,Component.literal("✦ ДРУЗЬЯ"),p.x()+14,p.y()+12,UiTheme.PINK);
        g.drawString(font,Component.literal("ID  "+snapshot.selfId()),p.x()+14,p.y()+25,UiTheme.DIM);
        super.render(g,mx,my,pt);
        int y=p.y()+70;int max=Math.max(1,(p.y()+p.height()-y-10)/24);int n=0;
        for(FriendNetwork.Row r:snapshot.rows()){
            if(n++>=max)break;int statusColor;String status;
            if("ONLINE".equals(r.status())){statusColor=UiTheme.GREEN;status="● В СЕТИ";}
            else if("BATTLE".equals(r.status())){statusColor=0xFFFF6666;status="◆ В БОЮ";}
            else{statusColor=UiTheme.MUTED;status="○ ОФЛАЙН";}
            g.fill(p.x()+14,y,p.x()+p.width()-14,y+20,0x75131C2C);g.renderOutline(p.x()+14,y,p.width()-28,20,0x50355068);
            g.drawString(font,Component.literal(r.name()),p.x()+22,y+6,UiTheme.TEXT);String st=status;g.drawString(font,Component.literal(st),p.x()+p.width()-22-font.width(st),y+6,statusColor);y+=24;
        }
    }
    @Override public void onClose(){FriendNetwork.setClientConsumer(null);super.onClose();}
    @Override public boolean isPauseScreen(){return false;}
}
