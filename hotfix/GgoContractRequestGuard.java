package arena.forge;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-player server-side throttling for client-initiated contract requests. */
public final class GgoContractRequestGuard {
    private static final long SNAPSHOT_INTERVAL_TICKS=10L;
    private static final long TRACK_INTERVAL_TICKS=4L;
    private static final Map<UUID,Long> CONTRACT_SNAPSHOT=new ConcurrentHashMap<>();
    private static final Map<UUID,Long> MAP_SNAPSHOT=new ConcurrentHashMap<>();
    private static final Map<UUID,Long> TRACK=new ConcurrentHashMap<>();

    private GgoContractRequestGuard(){}

    public static boolean allowContractSnapshot(ServerPlayer player){
        return allow(player,CONTRACT_SNAPSHOT,SNAPSHOT_INTERVAL_TICKS);
    }
    public static boolean allowMapSnapshot(ServerPlayer player){
        return allow(player,MAP_SNAPSHOT,SNAPSHOT_INTERVAL_TICKS);
    }
    public static boolean allowTrack(ServerPlayer player){
        return allow(player,TRACK,TRACK_INTERVAL_TICKS);
    }
    public static void clear(UUID playerId){
        if(playerId==null)return;
        CONTRACT_SNAPSHOT.remove(playerId);
        MAP_SNAPSHOT.remove(playerId);
        TRACK.remove(playerId);
    }

    private static boolean allow(ServerPlayer player,Map<UUID,Long> state,long interval){
        if(player==null)return false;
        UUID id=player.getUUID();
        long now=player.serverLevel().getGameTime();
        long previous=state.getOrDefault(id,Long.MIN_VALUE/2L);
        if(now<previous){
            state.put(id,now);
            return true;
        }
        if(now-previous<interval)return false;
        state.put(id,now);
        return true;
    }
}
