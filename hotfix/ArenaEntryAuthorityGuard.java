package arena.forge;

import arena.GunnerArenaMod;
import arena.forge.player.ArenaPlayerState;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Makes /play (and the Play button which calls it) the authority for entering combat.
 * Legacy command blocks may still teleport entities for map scripts, but a player who is not ALIVE
 * is immediately returned to their last known safe/lobby position.
 */
@Mod.EventBusSubscriber(modid="gunnerarena",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class ArenaEntryAuthorityGuard {
    private static final Map<UUID,SafePos> LAST_SAFE=new ConcurrentHashMap<>();
    private ArenaEntryAuthorityGuard(){}

    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e){
        if(e.phase!=TickEvent.Phase.END||e.player.level().isClientSide||!(e.player instanceof ServerPlayer p)||(p.tickCount%2)!=0)return;
        ArenaRuntime r=GunnerArenaMod.RUNTIME;if(r==null||!r.auth().isAuthenticated(p))return;
        if(r.safeRegions().isSafe(p)){
            LAST_SAFE.put(p.getUUID(),new SafePos(p.level().dimension(),p.getX(),p.getY(),p.getZ(),p.getYRot(),p.getXRot()));
            return;
        }
        if(r.players().session(p).state()== ArenaPlayerState.ALIVE)return;
        SafePos pos=LAST_SAFE.get(p.getUUID());if(pos==null)return;
        ServerLevel target=p.getServer()==null?null:p.getServer().getLevel(pos.dimension);if(target==null)return;
        p.teleportTo(target,pos.x,pos.y,pos.z,pos.yaw,pos.pitch);
        p.setDeltaMovement(0,0,0);p.fallDistance=0;
    }

    private record SafePos(ResourceKey<Level> dimension,double x,double y,double z,float yaw,float pitch){}
}
