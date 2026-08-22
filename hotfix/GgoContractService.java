package arena.forge;

import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server-owned contract catalog/tracking for Runtime v1. */
public final class GgoContractService {
    public record Contract(String id,String title,String description,String activity,int current,int target,int rewardCredits,boolean completed) {}

    private static final Map<UUID, LinkedHashMap<String, Contract>> BY_PLAYER=new ConcurrentHashMap<>();
    private static final Map<UUID,String> TRACKED=new ConcurrentHashMap<>();
    private static final Map<UUID, Set<String>> REWARDED=new ConcurrentHashMap<>();
    private GgoContractService(){}

    public static List<Contract> list(ServerPlayer p){
        if(p==null)return List.of();
        ensureDefaults(p);
        return new ArrayList<>(BY_PLAYER.getOrDefault(p.getUUID(),new LinkedHashMap<>()).values()).stream().limit(8).toList();
    }
    public static String trackedId(ServerPlayer p){
        if(p==null)return "";
        ensureDefaults(p);
        return TRACKED.getOrDefault(p.getUUID(),"");
    }
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
        persist(p);
        publishObjective(p,c);
        pushState(p,true);
        return true;
    }
    public static void addProgress(ServerPlayer p,String id,int delta){
        if(p==null||id==null||delta==0)return; ensureDefaults(p);
        final boolean[] changed={false};
        final boolean[] completedNow={false};
        BY_PLAYER.get(p.getUUID()).computeIfPresent(id,(k,c)->{
            int cur=Math.max(0,Math.min(c.target(),c.current()+delta));
            boolean done=cur>=c.target();
            changed[0]=cur!=c.current()||done!=c.completed();
            completedNow[0]=done&&!c.completed();
            return new Contract(c.id(),c.title(),c.description(),c.activity(),cur,c.target(),c.rewardCredits(),done);
        });
        if(!changed[0])return;
        persist(p);
        Contract c=BY_PLAYER.get(p.getUUID()).get(id);
        if(c!=null&&id.equals(TRACKED.get(p.getUUID())))publishObjective(p,c);
        boolean balanceChanged=c!=null&&completedNow[0]&&rewardOnce(p,c);
        pushState(p,balanceChanged);
    }

    /** Load durable state and push a complete snapshot for login/respawn/dimension lifecycle events. */
    public static void syncPlayer(ServerPlayer p){
        if(p==null)return;
        ensureDefaults(p);
        Contract current=tracked(p);
        if(current!=null)publishObjective(p,current);
        pushState(p,true);
    }

    /** Explicitly flush mutable state before session cache eviction. */
    public static void flush(ServerPlayer p){
        if(p==null)return;
        ensureDefaults(p);
        persist(p);
    }

    /** Unload session caches only. Durable state remains in the world save. */
    public static void clear(UUID id){if(id!=null){BY_PLAYER.remove(id);TRACKED.remove(id);REWARDED.remove(id);}}

    private static boolean rewardOnce(ServerPlayer p,Contract c){
        Set<String> rewarded=REWARDED.computeIfAbsent(p.getUUID(),id->ConcurrentHashMap.newKeySet());
        if(!rewarded.add(c.id()))return false;

        // Claim first, then grant. This gives at-most-once behavior across a crash/restart.
        persist(p);
        if(!GgoContractRewardBridge.award(p,c.rewardCredits())){
            rewarded.remove(c.id());
            persist(p);
            return false;
        }
        persist(p);
        return true;
    }
    private static void pushState(ServerPlayer p,boolean includeEconomy){
        GgoContractNetwork.sync(p);
        if(includeEconomy)GgoContractMapNetwork.sync(p);
    }
    private static void publishObjective(ServerPlayer p,Contract c){
        GgoObjectiveService.set(p,"contract:"+c.id(),c.activity(),c.title(),c.description(),c.current(),c.target());
        if(c.completed())GgoObjectiveService.complete(p);
    }
    private static void persist(ServerPlayer p){
        LinkedHashMap<String,Contract> contracts=BY_PLAYER.get(p.getUUID());
        if(contracts==null)return;
        GgoContractPersistence.save(
                p,
                TRACKED.getOrDefault(p.getUUID(),""),
                contracts.values(),
                REWARDED.getOrDefault(p.getUUID(),Set.of())
        );
    }
    private static void ensureDefaults(ServerPlayer p){
        UUID playerId=p.getUUID();
        BY_PLAYER.computeIfAbsent(playerId,id->{
            LinkedHashMap<String,Contract> m=defaults();
            GgoContractPersistence.PlayerState state=GgoContractPersistence.load(p,m.values());
            for(Map.Entry<String,Contract> entry:new ArrayList<>(m.entrySet())){
                Contract base=entry.getValue();
                int current=Math.max(0,Math.min(base.target(),state.progress().getOrDefault(base.id(),0)));
                boolean completed=state.completed().contains(base.id())||current>=base.target();
                m.put(entry.getKey(),new Contract(base.id(),base.title(),base.description(),base.activity(),current,base.target(),base.rewardCredits(),completed));
            }
            if(m.containsKey(state.trackedId()))TRACKED.put(playerId,state.trackedId());
            Set<String> rewarded=ConcurrentHashMap.newKeySet();
            for(String contractId:state.rewarded())if(m.containsKey(contractId))rewarded.add(contractId);
            REWARDED.put(playerId,rewarded);
            return m;
        });
        REWARDED.computeIfAbsent(playerId,id->ConcurrentHashMap.newKeySet());
    }
    private static LinkedHashMap<String,Contract> defaults(){
        LinkedHashMap<String,Contract> m=new LinkedHashMap<>();
        m.put("field_test",new Contract("field_test","FIELD TEST","Eliminate hostiles with any firearm.","CONTRACT",0,10,1200,false));
        m.put("supply_run",new Contract("supply_run","SUPPLY RUN","Recover marked supplies and return safely.","CONTRACT",0,5,900,false));
        m.put("distance_drill",new Contract("distance_drill","DISTANCE DRILL","Land precision hits at range.","TRAINING",0,8,700,false));
        return m;
    }
}
