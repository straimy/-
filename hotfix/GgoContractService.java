package arena.forge;

import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server-owned contract catalog/tracking for Runtime v1. */
public final class GgoContractService {
    public record Contract(String id,String title,String description,String activity,int current,int target,int rewardCredits,boolean completed) {}

    private static final Map<UUID, LinkedHashMap<String, Contract>> BY_PLAYER=new ConcurrentHashMap<>();
    private static final Map<UUID,String> TRACKED=new ConcurrentHashMap<>();
    private GgoContractService(){}

    public static List<Contract> list(ServerPlayer p){
        if(p==null)return List.of();
        ensureDefaults(p);
        return new ArrayList<>(BY_PLAYER.getOrDefault(p.getUUID(),new LinkedHashMap<>()).values()).stream().limit(8).toList();
    }
    public static String trackedId(ServerPlayer p){return p==null?"":TRACKED.getOrDefault(p.getUUID(),"");}
    public static Contract tracked(ServerPlayer p){
        if(p==null)return null; ensureDefaults(p);
        String id=TRACKED.get(p.getUUID());
        return id==null?null:BY_PLAYER.get(p.getUUID()).get(id);
    }
    public static boolean track(ServerPlayer p,String id){
        if(p==null||id==null)return false; ensureDefaults(p);
        Contract c=BY_PLAYER.get(p.getUUID()).get(id);
        if(c==null)return false;
        TRACKED.put(p.getUUID(),id);
        publishObjective(p,c);
        return true;
    }
    public static void addProgress(ServerPlayer p,String id,int delta){
        if(p==null||id==null||delta==0)return; ensureDefaults(p);
        BY_PLAYER.get(p.getUUID()).computeIfPresent(id,(k,c)->{
            int cur=Math.max(0,Math.min(c.target(),c.current()+delta));
            return new Contract(c.id(),c.title(),c.description(),c.activity(),cur,c.target(),c.rewardCredits(),cur>=c.target());
        });
        Contract c=BY_PLAYER.get(p.getUUID()).get(id);
        if(c!=null&&id.equals(TRACKED.get(p.getUUID())))publishObjective(p,c);
    }
    public static void clear(UUID id){if(id!=null){BY_PLAYER.remove(id);TRACKED.remove(id);}}

    private static void publishObjective(ServerPlayer p,Contract c){
        GgoObjectiveService.set(p,"contract:"+c.id(),c.activity(),c.title(),c.description(),c.current(),c.target());
        if(c.completed())GgoObjectiveService.complete(p);
    }
    private static void ensureDefaults(ServerPlayer p){
        BY_PLAYER.computeIfAbsent(p.getUUID(),id->{
            LinkedHashMap<String,Contract> m=new LinkedHashMap<>();
            m.put("field_test",new Contract("field_test","FIELD TEST","Eliminate hostiles with any firearm.","CONTRACT",0,10,1200,false));
            m.put("supply_run",new Contract("supply_run","SUPPLY RUN","Recover marked supplies and return safely.","CONTRACT",0,5,900,false));
            m.put("distance_drill",new Contract("distance_drill","DISTANCE DRILL","Land precision hits at range.","TRAINING",0,8,700,false));
            return m;
        });
    }
}
