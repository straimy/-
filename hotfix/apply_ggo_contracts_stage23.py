from pathlib import Path

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
JAVA = ROOT / "client-ui/src/main/java/arena/client/shell"
JAVA.mkdir(parents=True, exist_ok=True)

state = r'''package arena.client.shell;

import java.util.List;

public final class GgoContractState {
    public record Entry(String id,String title,String description,String activity,int current,int target,int rewardCredits,boolean completed) {}
    private static volatile String trackedId="";
    private static volatile List<Entry> entries=List.of();
    private GgoContractState(){}
    public static void update(String tracked,List<Entry> next){trackedId=tracked==null?"":tracked;entries=next==null?List.of():List.copyOf(next).stream().limit(8).toList();}
    public static List<Entry> entries(){return entries;}
    public static String trackedId(){return trackedId;}
    public static Entry tracked(){return entries.stream().filter(e->e.id().equals(trackedId)).findFirst().orElse(null);}
}
'''
(JAVA / "GgoContractState.java").write_text(state)

adapter = r'''package arena.client.shell;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class GgoRuntimeV1ContractAdapter {
    private static boolean attempted,installed;
    private static Method request,track;
    private static long lastRequest;
    private GgoRuntimeV1ContractAdapter(){}
    public static void install(){
        if(installed||attempted)return;attempted=true;
        try{
            Class<?> c=Class.forName("arena.forge.GgoContractNetwork");
            request=c.getMethod("request");track=c.getMethod("track",String.class);
            c.getMethod("setClientConsumer",Consumer.class).invoke(null,(Consumer<Object>)GgoRuntimeV1ContractAdapter::receive);
            installed=true;
        }catch(ReflectiveOperationException|LinkageError ignored){}
    }
    public static void tick(){install();if(!installed||request==null)return;long now=System.currentTimeMillis();if(now-lastRequest<1500L)return;lastRequest=now;invoke(request);}
    public static void track(String id){install();if(installed&&track!=null)invoke(track,id);}
    private static void receive(Object packet){
        try{
            String tracked=String.valueOf(value(packet,"trackedId"));
            Object raw=value(packet,"entries");List<GgoContractState.Entry> out=new ArrayList<>();
            if(raw instanceof List<?> list)for(Object e:list)out.add(new GgoContractState.Entry(str(e,"id"),str(e,"title"),str(e,"description"),str(e,"activity"),integer(e,"current"),integer(e,"target"),integer(e,"rewardCredits"),bool(e,"completed")));
            GgoContractState.update(tracked,out);
        }catch(ReflectiveOperationException ignored){}
    }
    private static void invoke(Method m,Object...a){try{m.invoke(null,a);}catch(ReflectiveOperationException ignored){}}
    private static Object value(Object o,String n)throws ReflectiveOperationException{return o.getClass().getMethod(n).invoke(o);}
    private static String str(Object o,String n)throws ReflectiveOperationException{return String.valueOf(value(o,n));}
    private static int integer(Object o,String n)throws ReflectiveOperationException{return ((Number)value(o,n)).intValue();}
    private static boolean bool(Object o,String n)throws ReflectiveOperationException{return Boolean.TRUE.equals(value(o,n));}
}
'''
(JAVA / "GgoRuntimeV1ContractAdapter.java").write_text(adapter)

screen=JAVA / "GgoShellScreen.java"
if screen.exists():
    s=screen.read_text()
    # Poll while any shell page is visible.
    anchor='        Minecraft mc = Minecraft.getInstance();\n'
    if anchor in s and 'GgoRuntimeV1ContractAdapter.tick();' not in s:
        s=s.replace(anchor,anchor+'        GgoRuntimeV1ContractAdapter.tick();\n',1)

    # Add contract rendering under activity cards.
    marker='        card(g, 576, 160, 260, 128, "EVENTS", "Seasonal operations", "COMING SOON");\n'
    if marker in s and 'AVAILABLE CONTRACTS' not in s:
        extra=r'''        g.drawString(this.font, "AVAILABLE CONTRACTS", 24, 310, 0xFFB9C3D1, false);
        int cy=332; int ci=0;
        for(var c:GgoContractState.entries()){
            boolean tracked=c.id().equals(GgoContractState.trackedId());
            g.fill(24,cy,Math.min(this.width-24,700),cy+34,tracked?0xFF202832:0xFF0D1218);
            g.fill(24,cy,27,cy+34,tracked?0xFFD34B57:0xFF384454);
            String progress=c.target()>0?c.current()+"/"+c.target():"";
            g.drawString(this.font,(tracked?"TRACKED  ":"")+c.title(),36,cy+7,tracked?0xFFF3F5F7:0xFFD5DAE1,false);
            g.drawString(this.font,progress+"   +"+c.rewardCredits()+" CR",this.width>760?560:430,cy+7,0xFF8E9AAC,false);
            g.drawString(this.font,c.description(),36,cy+20,0xFF748195,false);
            cy+=40; ci++; if(ci>=6)break;
        }
        if(GgoContractState.entries().isEmpty())g.drawString(this.font,"SYNCING CONTRACTS...",36,338,0xFF657183,false);
        g.drawString(this.font,"CLICK CONTRACT TO TRACK",24,Math.min(this.height-42,cy+6),0xFF657183,false);
'''
        s=s.replace(marker,marker+extra,1)

    # Add tracked contract to Navigation.
    nav='        g.drawString(this.font, "MMB  PLACE PING    Minimap: disabled by default", mapX + 14, mapY + mapH - 22, 0xFF697688, false);\n'
    if nav in s and 'TRACKED CONTRACT' not in s:
        extra=r'''        var trackedContract=GgoContractState.tracked();
        if(trackedContract!=null){
            String cp=trackedContract.target()>0?trackedContract.current()+"/"+trackedContract.target():"";
            g.drawString(this.font,"TRACKED CONTRACT  "+trackedContract.title()+"  "+cp,mapX+14,mapY+34,0xFFD34B57,false);
        }
'''
        s=s.replace(nav,nav+extra,1)

    # Click rows to track.
    close='    @Override\n    public boolean isPauseScreen() {\n'
    if close in s and 'TRACK CONTRACT ROW' not in s:
        method=r'''    // TRACK CONTRACT ROW
    @Override
    public boolean mouseClicked(double mouseX,double mouseY,int button){
        if(page==Page.ACTIVITIES && button==0 && mouseX>=24 && mouseX<=Math.min(this.width-24,700) && mouseY>=332){
            int index=(int)((mouseY-332)/40.0);
            var list=GgoContractState.entries();
            if(index>=0&&index<Math.min(6,list.size())){GgoRuntimeV1ContractAdapter.track(list.get(index).id());return true;}
        }
        return super.mouseClicked(mouseX,mouseY,button);
    }

'''
        s=s.replace(close,method+close,1)
    screen.write_text(s)

print("GGO Contracts Stage 23 applied")
print(" - server contract snapshots in Activities")
print(" - click a row to TRACK")
print(" - tracked contract appears on Navigation")
print(" - tracked server contract feeds existing Objective HUD")
