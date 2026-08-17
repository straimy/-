package arena.client.ui;

import arena.forge.ClanNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Compact neon clan hub: my clan / search / create, with owner and deputy management controls. */
public final class ClanScreen extends AbstractArenaScreen {
    private enum Mode{HOME,MY,SEARCH,CREATE,SETTINGS}
    private Mode mode=Mode.HOME;
    private ClanNetwork.Snapshot snapshot=new ClanNetwork.Snapshot(false,"NONE","","","",0,0,500,100,List.of(),List.of());
    private EditBox a,b,c;
    private String sort="WEALTH",selectedClan="",selectedMember="";
    private long glowStart=System.currentTimeMillis();
    public ClanScreen(){super(Component.literal("Кланы"),UiRoute.MAIN);}

    @Override protected void init(){ClanNetwork.setClientConsumer(s->{snapshot=s;});ClanNetwork.request("",sort);build();}
    private void build(){clearWidgets();installNavigation();UiLayout.Rect p=UiLayout.of(width,height).contentPanel();int x=p.x()+14,w=p.width()-28,y=p.y()+36;
        if(mode==Mode.HOME){
            addRenderableWidget(new ArenaButton(new UiLayout.Rect(x,y,w,22),Component.literal("♜  МОЙ КЛАН"),q->{mode=Mode.MY;build();}));
            addRenderableWidget(new ArenaButton(new UiLayout.Rect(x,y+28,w,22),Component.literal("⌕  НАЙТИ КЛАН"),q->{mode=Mode.SEARCH;build();}));
            addRenderableWidget(new ArenaButton(new UiLayout.Rect(x,y+56,w,22),Component.literal("＋  СОЗДАТЬ КЛАН  ◆ "+snapshot.createCost()),q->{mode=Mode.CREATE;build();}));
        }else if(mode==Mode.CREATE){
            a=box(x,y,w,18,"Название клана");b=box(x,y+24,w,18,"Описание");c=box(x,y+48,90,18,"Цена входа");
            addRenderableWidget(new ArenaButton(new UiLayout.Rect(x+98,y+48,w-98,18),Component.literal("◆ СОЗДАТЬ • "+snapshot.createCost()),q->{int price=parse(c.getValue());ClanNetwork.create(a.getValue(),b.getValue(),price);mode=Mode.MY;build();}));
            back(x,y+76,w);
        }else if(mode==Mode.SEARCH){
            a=box(x,y,w-94,18,"Название или CLAN-ID");addRenderableWidget(new ArenaButton(new UiLayout.Rect(x+w-88,y,88,18),Component.literal("⌕ ИСКАТЬ"),q->ClanNetwork.request(a.getValue(),sort)));
            addRenderableWidget(new ArenaButton(new UiLayout.Rect(x,y+24,132,18),Component.literal("ФИЛЬТР: "+sortLabel()),q->{sort=nextSort(sort);ClanNetwork.request(a.getValue(),sort);}));
            if(!selectedClan.isBlank())addRenderableWidget(new ArenaButton(new UiLayout.Rect(x+140,y+24,w-140,18),Component.literal("ВСТУПИТЬ • "+selectedClan),q->ClanNetwork.join(selectedClan)));
            back(x,p.y()+p.height()-25,w);
        }else if(mode==Mode.MY){
            if(!snapshot.inClan()){
                addRenderableWidget(new ArenaButton(new UiLayout.Rect(x,y,w,22),Component.literal("У ВАС НЕТ КЛАНА"),q->{}));
                addRenderableWidget(new ArenaButton(new UiLayout.Rect(x,y+30,w/2-3,20),Component.literal("⌕ НАЙТИ"),q->{mode=Mode.SEARCH;build();}));
                addRenderableWidget(new ArenaButton(new UiLayout.Rect(x+w/2+3,y+30,w/2-3,20),Component.literal("＋ СОЗДАТЬ"),q->{mode=Mode.CREATE;build();}));back(x,y+58,w);
            }else{
                boolean manage=snapshot.selfRole().equals("OWNER")||snapshot.selfRole().equals("DEPUTY");
                if(manage)addRenderableWidget(new ArenaButton(new UiLayout.Rect(x,p.y()+p.height()-47,w/2-3,18),Component.literal("⚙ НАСТРОЙКИ"),q->{mode=Mode.SETTINGS;build();}));
                addRenderableWidget(new ArenaButton(new UiLayout.Rect(x+(manage?w/2+3:0),p.y()+p.height()-47,manage?w/2-3:w,18),Component.literal("← ПОКИНУТЬ"),q->ClanNetwork.leave()));
                back(x,p.y()+p.height()-25,w);
            }
        }else if(mode==Mode.SETTINGS){
            a=box(x,y,w,18,"Новое название (◆ "+snapshot.renameCost()+")");b=box(x,y+24,w,18,"Описание");c=box(x,y+48,90,18,"Цена входа");
            addRenderableWidget(new ArenaButton(new UiLayout.Rect(x+98,y+48,w-98,18),Component.literal("СОХРАНИТЬ ЦЕНУ"),q->ClanNetwork.settings("ENTRY_PRICE",c.getValue())));
            addRenderableWidget(new ArenaButton(new UiLayout.Rect(x,y+72,w/2-3,18),Component.literal("ИЗМЕНИТЬ НАЗВАНИЕ"),q->ClanNetwork.settings("NAME",a.getValue())));
            addRenderableWidget(new ArenaButton(new UiLayout.Rect(x+w/2+3,y+72,w/2-3,18),Component.literal("ОПИСАНИЕ"),q->ClanNetwork.settings("DESCRIPTION",b.getValue())));
            if(!selectedMember.isBlank()){
                int yy=y+98;addRenderableWidget(new ArenaButton(new UiLayout.Rect(x,yy,74,18),Component.literal("ВЕТЕРАН"),q->ClanNetwork.member(selectedMember,"VETERAN")));
                addRenderableWidget(new ArenaButton(new UiLayout.Rect(x+78,yy,74,18),Component.literal("УЧАСТНИК"),q->ClanNetwork.member(selectedMember,"MEMBER")));
                if(snapshot.selfRole().equals("OWNER"))addRenderableWidget(new ArenaButton(new UiLayout.Rect(x+156,yy,76,18),Component.literal("♕ ЗАМ"),q->ClanNetwork.member(selectedMember,"DEPUTY")));
                addRenderableWidget(new ArenaButton(new UiLayout.Rect(x+w-72,yy,72,18),Component.literal("× КИК"),q->ClanNetwork.member(selectedMember,"KICK")));
            }
            back(x,p.y()+p.height()-25,w);
        }
    }
    private EditBox box(int x,int y,int w,int h,String hint){EditBox e=new EditBox(font,x,y,w,h,Component.literal(hint));e.setHint(Component.literal(hint));addRenderableWidget(e);return e;}
    private void back(int x,int y,int w){addRenderableWidget(new ArenaButton(new UiLayout.Rect(x,y,w,18),Component.literal("← НАЗАД"),q->{mode=Mode.HOME;build();}));}

    @Override public void render(GuiGraphics g,int mx,int my,float pt){
        drawBackdrop(g);UiLayout.Rect p=UiLayout.of(width,height).contentPanel();drawPanel(g,p);long t=(System.currentTimeMillis()-glowStart)%2400;int pulse=(int)(18+18*Math.abs(Math.sin(t/2400.0*Math.PI*2)));
        g.fill(p.x()+2,p.y()+1,p.x()+p.width()-2,p.y()+3,(pulse<<24)|(UiTheme.ACCENT&0xFFFFFF));g.drawString(font,Component.literal("♜ КЛАНЫ // GGO NETWORK"),p.x()+14,p.y()+12,UiTheme.PINK);
        if(snapshot.inClan())g.drawString(font,Component.literal(snapshot.clanName()+"  •  "+snapshot.clanId()+"  •  "+roleLabel(snapshot.selfRole())),p.x()+14,p.y()+24,UiTheme.ACCENT);
        else g.drawString(font,Component.literal("Кланы живут на GGO-ID, не на Minecraft UUID"),p.x()+14,p.y()+24,UiTheme.DIM);
        super.render(g,mx,my,pt);
        if(mode==Mode.SEARCH)renderSearch(g,p,mx,my);else if(mode==Mode.MY&&snapshot.inClan())renderClan(g,p,mx,my);else if(mode==Mode.SETTINGS&&snapshot.inClan())renderMembers(g,p,mx,my,true);
    }
    private void renderSearch(GuiGraphics g,UiLayout.Rect p,int mx,int my){int x=p.x()+14,y=p.y()+86,w=p.width()-28,i=0;for(ClanNetwork.ClanCard c:snapshot.results()){
        if(i++>=7)break;boolean sel=c.id().equals(selectedClan);g.fill(x,y,x+w,y+29,sel?0xB0253852:0x88121B2A);g.renderOutline(x,y,w,29,sel?UiTheme.ACCENT:UiTheme.HAIRLINE);g.drawString(font,Component.literal(c.name()+"  "+c.id()),x+7,y+5,UiTheme.TEXT);g.drawString(font,Component.literal("👥 "+c.members()+"   ◆ вход "+c.entryPrice()+"   ◇ казна "+c.treasury()),x+7,y+17,UiTheme.MUTED);y+=33;}}
    private void renderClan(GuiGraphics g,UiLayout.Rect p,int mx,int my){int x=p.x()+14,y=p.y()+46,w=p.width()-28;g.fill(x,y,x+w,y+44,0x8A151F31);g.renderOutline(x,y,w,44,UiTheme.HAIRLINE);g.drawString(font,Component.literal(snapshot.description().isBlank()?"Без описания":snapshot.description()),x+8,y+7,UiTheme.TEXT);g.drawString(font,Component.literal("◆ Вход: "+snapshot.entryPrice()+"    ◇ Казна: "+snapshot.treasury()+"    👥 "+snapshot.members().size()+"/50"),x+8,y+24,UiTheme.GOLD);renderMembers(g,new UiLayout.Rect(p.x(),y+49,p.width(),p.height()-95),mx,my,false);}
    private void renderMembers(GuiGraphics g,UiLayout.Rect p,int mx,int my,boolean settings){int x=p.x()+14,y=settings?p.y()+142:p.y(),w=p.width()-28,i=0;for(ClanNetwork.Member m:snapshot.members()){
        if(i++>=6)break;boolean sel=m.publicId().equals(selectedMember);g.fill(x,y,x+w,y+20,sel?0xAF29324A:0x75131C2C);g.renderOutline(x,y,w,20,sel?UiTheme.PINK:0x50355068);String tag=roleIcon(m.role())+" "+m.name();g.drawString(font,Component.literal(tag),x+7,y+6,roleColor(m.role()));String st=status(m.status());g.drawString(font,Component.literal(st),x+w-7-font.width(st),y+6,statusColor(m.status()));y+=23;}}

    @Override public boolean mouseClicked(double mx,double my,int button){if(button==0){UiLayout.Rect p=UiLayout.of(width,height).contentPanel();if(mode==Mode.SEARCH){int x=p.x()+14,y=p.y()+86,w=p.width()-28,i=0;for(ClanNetwork.ClanCard c:snapshot.results()){if(i++>=7)break;if(new UiLayout.Rect(x,y,w,29).contains(mx,my)){selectedClan=c.id();build();return true;}y+=33;}}else if((mode==Mode.MY||mode==Mode.SETTINGS)&&snapshot.inClan()){int x=p.x()+14,y=mode==Mode.SETTINGS?p.y()+142:p.y()+139,w=p.width()-28,i=0;for(ClanNetwork.Member m:snapshot.members()){if(i++>=6)break;if(new UiLayout.Rect(x,y,w,20).contains(mx,my)){selectedMember=m.publicId();if(mode==Mode.SETTINGS)build();return true;}y+=23;}}}return super.mouseClicked(mx,my,button);}
    private static String nextSort(String s){return switch(s){case "WEALTH"->"MEMBERS";case "MEMBERS"->"CHEAP";case "CHEAP"->"NAME";default->"WEALTH";};}
    private String sortLabel(){return switch(sort){case "MEMBERS"->"УЧАСТНИКИ";case "CHEAP"->"ДЕШЕВЛЕ";case "NAME"->"НАЗВАНИЕ";default->"БОГАТЫЕ";};}
    private static int parse(String s){try{return Integer.parseInt(s.trim());}catch(Exception e){return 0;}}
    private static String roleLabel(String r){return switch(r){case "OWNER"->"♛ ВЛАДЕЛЕЦ";case "DEPUTY"->"♕ ЗАМЕСТИТЕЛЬ";case "VETERAN"->"✦ ВЕТЕРАН";default->"УЧАСТНИК";};}
    private static String roleIcon(String r){return switch(r){case "OWNER"->"♛";case "DEPUTY"->"♕";case "VETERAN"->"✦";default->"•";};}
    private static int roleColor(String r){return switch(r){case "OWNER"->0xFFFF6666;case "DEPUTY"->UiTheme.GOLD;case "VETERAN"->UiTheme.PINK;default->UiTheme.TEXT;};}
    private static String status(String s){return "ONLINE".equals(s)?"● В СЕТИ":"BATTLE".equals(s)?"◆ В БОЮ":"○ ОФЛАЙН";}
    private static int statusColor(String s){return "ONLINE".equals(s)?UiTheme.GREEN:"BATTLE".equals(s)?0xFFFF6666:UiTheme.MUTED;}
    @Override public void onClose(){ClanNetwork.setClientConsumer(null);super.onClose();}
    @Override public boolean isPauseScreen(){return false;}
}
