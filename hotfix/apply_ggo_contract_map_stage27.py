from pathlib import Path

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
JAVA = ROOT / "client-ui/src/main/java/arena/client/shell"
JAVA.mkdir(parents=True, exist_ok=True)

map_state = r'''package arena.client.shell;

import net.minecraft.client.Minecraft;
import java.util.List;

public final class GgoSupplyMapState {
    public record Marker(String id,double x,double y,double z,boolean available){}
    public record Snapshot(String dimension,boolean extractionAvailable,double extractionX,double extractionY,double extractionZ,double extractionRadius,long creditBalance,List<Marker> markers){}
    private static volatile Snapshot snapshot=new Snapshot("",false,0,0,0,0,0,List.of());
    private GgoSupplyMapState(){}
    public static void update(String dimension,boolean extractionAvailable,double extractionX,double extractionY,double extractionZ,double extractionRadius,long creditBalance,List<Marker> markers){
        snapshot=new Snapshot(dimension==null?"":dimension,extractionAvailable,extractionX,extractionY,extractionZ,Math.max(0,extractionRadius),Math.max(0,creditBalance),markers==null?List.of():List.copyOf(markers).stream().limit(64).toList());
    }
    public static Snapshot snapshot(){return snapshot;}
    public static boolean currentDimension(Minecraft mc){return mc!=null&&mc.player!=null&&snapshot.dimension().equals(mc.player.level().dimension().location().toString());}
}
'''
(JAVA / "GgoSupplyMapState.java").write_text(map_state, encoding="utf-8")

adapter = r'''package arena.client.shell;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class GgoRuntimeV1ContractMapAdapter {
    private static boolean attempted,installed;
    private static Method request;
    private static long lastRequest;
    private GgoRuntimeV1ContractMapAdapter(){}
    public static void install(){
        if(installed||attempted)return;attempted=true;
        try{
            Class<?> type=Class.forName("arena.forge.GgoContractMapNetwork");
            request=type.getMethod("request");
            type.getMethod("setClientConsumer",Consumer.class).invoke(null,(Consumer<Object>)GgoRuntimeV1ContractMapAdapter::receive);
            installed=true;
        }catch(ReflectiveOperationException|LinkageError ignored){}
    }
    public static void tick(){
        install();if(!installed||request==null)return;
        long now=System.currentTimeMillis();if(now-lastRequest<1200L)return;lastRequest=now;
        try{request.invoke(null);}catch(ReflectiveOperationException ignored){}
    }
    private static void receive(Object packet){
        try{
            String dimension=String.valueOf(value(packet,"dimension"));
            boolean extractionAvailable=bool(packet,"extractionAvailable");
            double extractionX=dbl(packet,"extractionX"), extractionY=dbl(packet,"extractionY"), extractionZ=dbl(packet,"extractionZ"), extractionRadius=dbl(packet,"extractionRadius");
            long creditBalance=lng(packet,"creditBalance");
            List<GgoSupplyMapState.Marker> markers=new ArrayList<>();
            Object raw=value(packet,"markers");
            if(raw instanceof List<?> list)for(Object marker:list){
                markers.add(new GgoSupplyMapState.Marker(str(marker,"id"),dbl(marker,"x"),dbl(marker,"y"),dbl(marker,"z"),bool(marker,"available")));
            }
            GgoSupplyMapState.update(dimension,extractionAvailable,extractionX,extractionY,extractionZ,extractionRadius,creditBalance,markers);
        }catch(ReflectiveOperationException ignored){}
    }
    private static Object value(Object o,String n)throws ReflectiveOperationException{return o.getClass().getMethod(n).invoke(o);}
    private static String str(Object o,String n)throws ReflectiveOperationException{return String.valueOf(value(o,n));}
    private static boolean bool(Object o,String n)throws ReflectiveOperationException{return Boolean.TRUE.equals(value(o,n));}
    private static double dbl(Object o,String n)throws ReflectiveOperationException{return ((Number)value(o,n)).doubleValue();}
    private static long lng(Object o,String n)throws ReflectiveOperationException{return ((Number)value(o,n)).longValue();}
}
'''
(JAVA / "GgoRuntimeV1ContractMapAdapter.java").write_text(adapter, encoding="utf-8")

completion = r'''package arena.client.shell;

import java.util.HashMap;
import java.util.Map;

public final class GgoContractCompletionState {
    public record Popup(String id,String title,int rewardCredits,long expiresAt){}
    private static final Map<String,Boolean> previous=new HashMap<>();
    private static boolean initialized;
    private static Popup popup;
    private GgoContractCompletionState(){}
    public static void poll(){
        var entries=GgoContractState.entries();
        if(entries.isEmpty())return;
        long now=System.currentTimeMillis();
        for(var entry:entries){
            boolean before=previous.getOrDefault(entry.id(),entry.completed());
            if(initialized&&!before&&entry.completed())popup=new Popup(entry.id(),entry.title(),entry.rewardCredits(),now+5500L);
            previous.put(entry.id(),entry.completed());
        }
        initialized=true;
    }
    public static Popup active(){
        Popup current=popup;
        if(current!=null&&System.currentTimeMillis()>current.expiresAt()){popup=null;return null;}
        return current;
    }
}
'''
(JAVA / "GgoContractCompletionState.java").write_text(completion, encoding="utf-8")

screen = JAVA / "GgoShellScreen.java"
if not screen.exists():
    raise SystemExit("Stage 27: GgoShellScreen.java missing")
s = screen.read_text(encoding="utf-8")

# Poll the authoritative marker/balance channel while Activities or Navigation is open.
poll_anchor = "        GgoRuntimeV1ContractAdapter.tick();\n"
if poll_anchor in s and "GgoRuntimeV1ContractMapAdapter.tick();" not in s:
    s = s.replace(poll_anchor, poll_anchor + "        GgoRuntimeV1ContractMapAdapter.tick();\n", 1)
elif "GgoRuntimeV1ContractMapAdapter.tick();" not in s:
    raise SystemExit("Stage 27: shell polling anchor missing")

# Expose the live balance next to the contract catalog.
contracts_anchor = '        g.drawString(this.font, "AVAILABLE CONTRACTS", 24, 310, 0xFFB9C3D1, false);\n'
contracts_extra = '''        String liveBalance="BALANCE  "+GgoSupplyMapState.snapshot().creditBalance()+" CR";
        g.drawString(this.font,liveBalance,Math.max(300,this.width-this.font.width(liveBalance)-30),310,0xFFD7A857,false);
'''
if contracts_anchor in s and "liveBalance" not in s:
    s = s.replace(contracts_anchor, contracts_anchor + contracts_extra, 1)
elif "liveBalance" not in s:
    raise SystemExit("Stage 27: Activities balance anchor missing")

progress_old = '            String progress=c.target()>0?c.current()+"/"+c.target():"";\n'
progress_new = '            String progress=c.completed()?"COMPLETE":(c.target()>0?c.current()+"/"+c.target():"");\n'
if progress_old in s:
    s = s.replace(progress_old, progress_new, 1)

# Full N-map markers: amber SUPPLY points and cyan extraction marker.
player_anchor = "        g.fill(px - 3, py - 3, px + 4, py + 4, 0xFFD84855);\n"
map_extra = r'''        var contractMap=GgoSupplyMapState.snapshot();
        if(GgoSupplyMapState.currentDimension(mc)){
            int availableSupplies=0;
            for(var marker:contractMap.markers()){
                if(!marker.available())continue;
                availableSupplies++;
                int sx=(int)Math.round(px+(marker.x()-mc.player.getX())*mapZoom);
                int sy=(int)Math.round(py+(marker.z()-mc.player.getZ())*mapZoom);
                if(sx>=mapX&&sx<mapX+mapW&&sy>=mapY&&sy<mapY+mapH){
                    g.fill(sx-3,sy-3,sx+4,sy+4,0xFFE0A64A);
                    g.fill(sx-1,sy-1,sx+2,sy+2,0xFF17120A);
                }
            }
            if(contractMap.extractionAvailable()){
                int ex=(int)Math.round(px+(contractMap.extractionX()-mc.player.getX())*mapZoom);
                int ey=(int)Math.round(py+(contractMap.extractionZ()-mc.player.getZ())*mapZoom);
                if(ex>=mapX&&ex<mapX+mapW&&ey>=mapY&&ey<mapY+mapH){
                    g.fill(ex-5,ey-1,ex+6,ey+2,0xFF54D7D1);
                    g.fill(ex-1,ey-5,ex+2,ey+6,0xFF54D7D1);
                    g.drawString(this.font,"EXTRACT",ex+8,ey-4,0xFF7FE8E3,false);
                }
            }
            g.drawString(this.font,"SUPPLY  "+availableSupplies,mapX+14,mapY+66,0xFFE0A64A,false);
        }
'''
if player_anchor in s and "availableSupplies" not in s:
    s = s.replace(player_anchor, map_extra + player_anchor, 1)
elif "availableSupplies" not in s:
    raise SystemExit("Stage 27: Full Map player anchor missing")

screen.write_text(s, encoding="utf-8")

hud = JAVA / "GgoCombatHud.java"
if not hud.exists():
    raise SystemExit("Stage 27: GgoCombatHud.java missing")
h = hud.read_text(encoding="utf-8")

# Poll contracts/map state and render completion popup during normal gameplay.
mc_anchor = "        if (mc.player == null || mc.level == null || mc.screen != null) return;\n"
if mc_anchor in h and "GgoRuntimeV1ContractMapAdapter.tick();" not in h:
    h = h.replace(mc_anchor, mc_anchor + "        GgoRuntimeV1ContractAdapter.tick();\n        GgoRuntimeV1ContractMapAdapter.tick();\n        GgoContractCompletionState.poll();\n", 1)
elif "GgoRuntimeV1ContractMapAdapter.tick();" not in h:
    raise SystemExit("Stage 27: HUD tick anchor missing")

world_calls = [
    "        renderWorldStatus(g, mc, width);\n",
    "                renderWorldStatus(g, mc, width);\n",
]
if "renderContractCompletion(g, mc, width);" not in h:
    for world_call in world_calls:
        if world_call in h:
            indent = world_call[:len(world_call) - len(world_call.lstrip())]
            h = h.replace(world_call, world_call + indent + "renderContractCompletion(g, mc, width);\n", 1)
            break
    else:
        raise SystemExit("Stage 27: HUD world-status call missing")

# Minimap markers use the same server snapshot; no client-side guessed positions.
mini_anchor = "        int cy = y + size / 2;\n"
mini_extra = r'''        var contractMap=GgoSupplyMapState.snapshot();
        if(GgoSupplyMapState.currentDimension(mc)){
            double scale=0.18D;
            for(var marker:contractMap.markers()){
                if(!marker.available())continue;
                int sx=(int)Math.round(cx+(marker.x()-mc.player.getX())*scale);
                int sy=(int)Math.round(cy+(marker.z()-mc.player.getZ())*scale);
                if(sx>x+2&&sx<x+size-2&&sy>y+2&&sy<y+size-2)g.fill(sx-2,sy-2,sx+3,sy+3,0xFFE0A64A);
            }
            if(contractMap.extractionAvailable()){
                int ex=(int)Math.round(cx+(contractMap.extractionX()-mc.player.getX())*scale);
                int ey=(int)Math.round(cy+(contractMap.extractionZ()-mc.player.getZ())*scale);
                if(ex>x+2&&ex<x+size-2&&ey>y+2&&ey<y+size-2){
                    g.fill(ex-3,ey,ex+4,ey+1,0xFF54D7D1);
                    g.fill(ex,ey-3,ex+1,ey+4,0xFF54D7D1);
                }
            }
        }
'''
if mini_anchor in h and "double scale=0.18D" not in h:
    h = h.replace(mini_anchor, mini_anchor + mini_extra, 1)
elif "double scale=0.18D" not in h:
    raise SystemExit("Stage 27: minimap center anchor missing")

method_anchor = "    private static void renderVitals(GuiGraphics g, Minecraft mc, int width, int height) {\n"
popup_method = r'''    private static void renderContractCompletion(GuiGraphics g,Minecraft mc,int width){
        var popup=GgoContractCompletionState.active();
        if(popup==null)return;
        int w=292,h=54,x=(width-w)/2,y=24;
        g.fill(x,y,x+w,y+h,0xEE090E14);
        g.fill(x,y,x+4,y+h,0xFF55C982);
        g.fill(x+4,y,x+w,y+1,0xFF32414A);
        g.drawString(mc.font,"CONTRACT COMPLETE",x+16,y+10,0xFF72E19A,false);
        g.drawString(mc.font,popup.title(),x+16,y+25,0xFFF0F3F6,false);
        String reward="+"+popup.rewardCredits()+" CREDITS  •  BALANCE "+GgoSupplyMapState.snapshot().creditBalance();
        g.drawString(mc.font,reward,x+16,y+39,0xFFD7A857,false);
    }

'''
if method_anchor in h and "private static void renderContractCompletion" not in h:
    h = h.replace(method_anchor, popup_method + method_anchor, 1)
elif "private static void renderContractCompletion" not in h:
    raise SystemExit("Stage 27: HUD method anchor missing")

hud.write_text(h, encoding="utf-8")

print("GGO Contracts Stage 27 client applied")
print(" - real supply loot markers on N-map and optional minimap")
print(" - authoritative extraction marker")
print(" - completion popup with reward")
print(" - live Runtime v1 credit balance in Activities and popup")
