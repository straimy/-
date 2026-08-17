package arena.forge;

import arena.GunnerArenaMod;
import arena.forge.player.ArenaPlayerState;
import arena.round.RoundState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Repairs a stale LOBBY/dead state only when the player has the authoritative GGO knife and is already on the live arena. */
@Mod.EventBusSubscriber(modid="gunnerarena",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class ArenaSessionConsistencyGuard {
    private ArenaSessionConsistencyGuard(){}

    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e){
        if(e.phase!=TickEvent.Phase.END||e.player.level().isClientSide||!(e.player instanceof ServerPlayer p)||(p.tickCount%10)!=0)return;
        ArenaRuntime r=GunnerArenaMod.RUNTIME;if(r==null||!r.auth().isAuthenticated(p))return;
        if(r.rounds().state()!= RoundState.PLAYING||r.safeRegions().isSafe(p))return;
        if(r.players().session(p).state()== ArenaPlayerState.ALIVE)return;
        if(!hasArenaKnife(p))return;

        // A GGO knife is only issued by the official match flow/respawn guard. Therefore this is a
        // safe signal that a player who is physically in the live arena should be attackable/alive,
        // even if an older session transition left their state stale.
        r.players().session(p).state(ArenaPlayerState.ALIVE);
        p.setInvisible(false);
    }

    private static boolean hasArenaKnife(ServerPlayer p){
        for(int i=0;i<Math.min(3,p.getInventory().getContainerSize());i++){
            ItemStack s=p.getInventory().getItem(i);
            if(s!=null&&!s.isEmpty()&&s.hasTag()&&s.getTag().getBoolean("GunnerArenaKnife"))return true;
        }
        return false;
    }
}
