package arena.client.ui;

import arena.forge.FriendNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class FriendsScreen extends AbstractArenaScreen {
    private FriendNetwork.Snapshot snapshot=new FriendNetwork.Snapshot("--------",true,List.of(),List.of());
    private FriendNetwork.ChatSnapshot chat=new FriendNetwork.ChatSnapshot("",List.of());
    private final List<ArenaButton> dynamic=new ArrayList<>();
    private EditBox addInput,messageInput;
    private String selectedId="",selectedName="";
    private long copiedUntil;

    public FriendsScreen(){super(Component.literal("Друзья"),UiRoute.MAIN);}

    @Override protected void init(){
        installNavigation();UiLayout.Rect p=UiLayout.of(width,height).contentPanel();
        addInput=new EditBox(font,p.x()+14,p.y()+51,Math.max(120,p.width()/2-100),18,Component.literal("Ник или GGO-ID"));addInput.setHint(Component.literal("ник / GGO-ID"));addRenderableWidget(addInput);
        messageInput=new EditBox(font,p.x()+p.width()/2+12,p.y()+p.height()-32,Math.max(80,p.width()/2-108),18,Component.literal("Сообщение"));messageInput.setHint(Component.literal("написать сообщение…"));addRenderableWidget(messageInput);
        FriendNetwork.setClientConsumer(s->{snapshot=s;if(!selectedId.isBlank()&&snapshot.rows().stream().noneMatch(r->r.publicId().equals(selectedId))){selectedId="";selectedName="";chat=new FriendNetwork.ChatSnapshot("",List.of());}rebuildDynamic();});
        FriendNetwork.setChatConsumer(s->{chat=s;});
        FriendNetwork.request();rebuildDynamic();
    }

    private void rebuildDynamic(){
        if(addInput==null)return;for(ArenaButton b:dynamic)removeWidget(b);dynamic.clear();UiLayout.Rect p=UiLayout.of(width,height).contentPanel();
        dynamic.add(addRenderableWidget(new ArenaButton(new UiLayout.Rect(p.x()+p.width()/2-78,p.y()+51,72,18),Component.literal("＋ ЗАЯВКА"),b->{String t=addInput.getValue().trim();if(!t.isEmpty()){FriendNetwork.add(t);addInput.setValue("");}})));
        dynamic.add(addRenderableWidget(new ArenaButton(new UiLayout.Rect(p.x()+p.width()-122,p.y()+12,108,18),Component.literal("⧉ КОПИРОВАТЬ ID"),b->{Minecraft.getInstance().keyboardHandler.setClipboard(snapshot.selfId());copiedUntil=System.currentTimeMillis()+1800;})));
        dynamic.add(addRenderableWidget(new ArenaButton(new UiLayout.Rect(p.x()+p.width()-122,p.y()+33,108,16),Component.literal(snapshot.allowRequests()?"ЗАЯВКИ  ● ВКЛ":"ЗАЯВКИ  ○ ВЫКЛ"),b->FriendNetwork.toggleRequests(!snapshot.allowRequests()))));

        int pendingY=p.y()+94;int pn=0;
        for(FriendNetwork.Row r:snapshot.pending()){
            if(pn++>=3)break;int y=pendingY+(pn-1)*23;
            dynamic.add(addRenderableWidget(new ArenaButton(new UiLayout.Rect(p.x()+p.width()/2-70,y,31,18),Component.literal("✓"),b->FriendNetwork.accept(r.publicId()))));
            dynamic.add(addRenderableWidget(new ArenaButton(new UiLayout.Rect(p.x()+p.width()/2-35,y,31,18),Component.literal("×"),b->FriendNetwork.decline(r.publicId()))));
        }

        int listY=p.y()+94+Math.min(3,snapshot.pending().size())*23+(snapshot.pending().isEmpty()?0:13);int max=Math.max(2,(p.y()+p.height()-listY-8)/25);int n=0;
        for(FriendNetwork.Row r:snapshot.rows()){
            if(n++>=max)break;int y=listY+(n-1)*25;boolean selected=r.publicId().equals(selectedId);
            dynamic.add(addRenderableWidget(new ArenaButton(new UiLayout.Rect(p.x()+14,y,p.width()/2-26,21),Component.empty(),b->{selectedId=r.publicId();selectedName=r.name();FriendNetwork.requestChat(r.publicId());rebuildDynamic();})));
            if(selected){
                dynamic.add(addRenderableWidget(new ArenaButton(new UiLayout.Rect(p.x()+p.width()-91,p.y()+58,77,18),Component.literal("✕ УДАЛИТЬ"),b->{FriendNetwork.remove(r.publicId());selectedId="";selectedName="";chat=new FriendNetwork.ChatSnapshot("",List.of());rebuildDynamic();})));
                dynamic.add(addRenderableWidget(new ArenaButton(new UiLayout.Rect(p.x()+p.width()-93,p.y()+p.height()-32,79,18),Component.literal("ОТПРАВИТЬ"),b->sendMessage())));
            }
        }
        messageInput.visible=!selectedId.isBlank();messageInput.active=!selectedId.isBlank();
    }

    private void sendMessage(){if(selectedId.isBlank()||messageInput==null)return;String text=messageInput.getValue().strip();if(text.isEmpty())return;FriendNetwork.sendMessage(selectedId,text);messageInput.setValue("");}

    @Override public void render(GuiGraphics g,int mx,int my,float pt){
        drawBackdrop(g);UiLayout.Rect p=UiLayout.of(width,height).contentPanel();drawPanel(g,p);
        long now=System.currentTimeMillis();
        UiEffects.verticalGradient(g,p.x()+1,p.y()+1,p.x()+p.width()-1,p.y()+Math.min(72,p.height()-1),0x441C3658,0x07111725);
        UiEffects.animatedSheen(g,p.x()+1,p.y()+1,p.width()-2,3,now,UiTheme.ACCENT);
        UiEffects.pulseBorder(g,p.x(),p.y(),p.width(),p.height(),now,UiTheme.ACCENT);
        double wave=(Math.sin(now/420.0)+1.0)*.5;int glowAlpha=36+(int)(42*wave);int glow=(glowAlpha<<24)|0x006BE7E3;
        g.fill(p.x()-2,p.y()-2,p.x()+p.width()+2,p.y(),glow);g.fill(p.x()-2,p.y()+p.height(),p.x()+p.width()+2,p.y()+p.height()+2,glow);
        g.drawString(font,Component.literal("✦ СОЦИАЛ"),p.x()+14,p.y()+12,UiTheme.PINK);
        g.drawString(font,Component.literal("GGO-ID  "+snapshot.selfId()),p.x()+14,p.y()+27,UiTheme.ACCENT);
        if(now<copiedUntil)g.drawString(font,Component.literal("✓ ID скопирован"),p.x()+14,p.y()+39,UiTheme.GREEN);
        super.render(g,mx,my,pt);

        int half=p.x()+p.width()/2;g.fill(half,p.y()+78,half+1,p.y()+p.height()-10,0x506BE7E3);
        int y=p.y()+79;
        if(!snapshot.pending().isEmpty()){
            g.drawString(font,Component.literal("✦ ВХОДЯЩИЕ  "+snapshot.pending().size()),p.x()+14,y,UiTheme.GOLD);y+=15;int n=0;
            for(FriendNetwork.Row r:snapshot.pending()){if(n++>=3)break;drawAvatar(g,p.x()+15,y+1,r.name(),false);g.drawString(font,Component.literal(r.name()),p.x()+38,y+6,UiTheme.TEXT);g.drawString(font,Component.literal(r.publicId()),p.x()+135,y+6,UiTheme.DIM);y+=23;}y+=13;
        }
        g.drawString(font,Component.literal("ДРУЗЬЯ  "+snapshot.rows().size()),p.x()+14,y,UiTheme.MUTED);y+=15;
        int max=Math.max(2,(p.y()+p.height()-y-8)/25);int n=0;
        for(FriendNetwork.Row r:snapshot.rows()){
            if(n++>=max)break;boolean sel=r.publicId().equals(selectedId);int bg=sel?0xC3263650:0x78131C2C;g.fill(p.x()+14,y,p.x()+p.width()/2-12,y+21,bg);g.renderOutline(p.x()+14,y,p.width()/2-26,21,sel?UiTheme.ACCENT:0x50355068);drawAvatar(g,p.x()+17,y+2,r.name(),"ONLINE".equals(r.status()));g.drawString(font,Component.literal(r.name()),p.x()+41,y+4,UiTheme.TEXT);String st=statusText(r.status());g.drawString(font,Component.literal(st),p.x()+p.width()/2-18-font.width(st),y+4,statusColor(r.status()));g.drawString(font,Component.literal(r.publicId()),p.x()+41,y+12,UiTheme.DIM);y+=25;
        }

        int dx=half+12,dy=p.y()+57,dw=p.x()+p.width()-14-dx;
        if(selectedId.isBlank()){
            g.drawCenteredString(font,Component.literal("Выбери друга, чтобы открыть чат"),dx+dw/2,p.y()+p.height()/2,UiTheme.DIM);
        }else{
            g.drawString(font,Component.literal("✦ "+selectedName),dx,dy,UiTheme.TEXT);g.drawString(font,Component.literal(selectedId),dx,dy+13,UiTheme.DIM);dy+=35;
            g.fill(dx,dy,dx+dw,dy+1,UiTheme.GLOW);dy+=8;
            List<FriendNetwork.ChatLine> lines=chat.friendId().equals(selectedId)?chat.lines():List.of();int available=Math.max(2,(p.y()+p.height()-50-dy)/20);int start=Math.max(0,lines.size()-available);
            for(int i=start;i<lines.size();i++){FriendNetwork.ChatLine l=lines.get(i);boolean me=l.senderId().equals(snapshot.selfId());String prefix=me?"ВЫ":""+l.senderName();int c=me?UiTheme.ACCENT:UiTheme.PINK;g.drawString(font,Component.literal(prefix),dx,dy,c);g.drawString(font,Component.literal(fit(l.text(),dw-8)),dx,dy+9,UiTheme.TEXT);dy+=20;}
        }
    }

    private void drawAvatar(GuiGraphics g,int x,int y,String name,boolean online){int border=online?UiTheme.GREEN:UiTheme.ACCENT_2;g.fill(x,y,x+18,y+18,0xD21A2639);g.renderOutline(x,y,18,18,border);String initial=(name==null||name.isBlank())?"?":name.substring(0,1).toUpperCase();g.drawCenteredString(font,Component.literal(initial),x+9,y+5,UiTheme.TEXT);}
    private String statusText(String s){return "ONLINE".equals(s)?"● В СЕТИ":"BATTLE".equals(s)?"◆ В БОЮ":"○ ОФЛАЙН";}
    private int statusColor(String s){return "ONLINE".equals(s)?UiTheme.GREEN:"BATTLE".equals(s)?0xFFFF6B87:UiTheme.MUTED;}
    private String fit(String t,int w){if(t==null)return"";if(font.width(t)<=w)return t;String e="…";return font.plainSubstrByWidth(t,Math.max(0,w-font.width(e)))+e;}
    @Override public boolean keyPressed(int key,int scan,int mods){if(key==257&&messageInput!=null&&messageInput.isFocused()&&!selectedId.isBlank()){sendMessage();return true;}return super.keyPressed(key,scan,mods);}
    @Override public void onClose(){FriendNetwork.setClientConsumer(null);FriendNetwork.setChatConsumer(null);super.onClose();}
    @Override public boolean isPauseScreen(){return false;}
}
