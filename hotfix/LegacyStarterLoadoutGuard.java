package arena.forge;

import arena.GunnerArenaMod;
import arena.forge.player.ArenaPlayerState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Neutralizes the legacy map starter kit that can still be injected by command blocks
 * after death/regeneration. We intentionally do not disable command blocks globally,
 * because the map may still use them for unrelated structure/regeneration logic.
 *
 * In lobby/non-ALIVE state every old combat item is removed. During a live round only
 * the known-broken Double Barrel is hard-blocked; normal shop purchases stay untouched.
 */
@Mod.EventBusSubscriber(modid="gunnerarena", bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class LegacyStarterLoadoutGuard {
    private static long nextSweep;
    private LegacyStarterLoadoutGuard(){}

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;
        ArenaRuntime r=GunnerArenaMod.RUNTIME;
        var server=ServerLifecycleHooks.getCurrentServer();
        if(r==null||server==null)return;
        long now=r.serverTick();
        if(now<nextSweep)return;
        nextSweep=now+5;
        for(ServerPlayer p:server.getPlayerList().getPlayers()){
            if(!r.auth().isAuthenticated(p))continue;
            boolean alive=r.players().session(p).state()==ArenaPlayerState.ALIVE;
            for(int i=0;i<p.getInventory().getContainerSize();i++){
                ItemStack s=p.getInventory().getItem(i);
                if(s.isEmpty())continue;
                ResourceLocation id=ForgeRegistries.ITEMS.getKey(s.getItem());
                String sid=id==null?"":id.toString();
                if(isDoubleBarrel(sid)||(!alive&&isLegacyStarterCombatItem(s,sid))){
                    p.getInventory().setItem(i,ItemStack.EMPTY);
                }
            }
        }
    }

    private static boolean isDoubleBarrel(String id){
        return "jeg:double_barrel_shotgun".equals(id)||id.endsWith(":double_barrel_shotgun");
    }

    private static boolean isLegacyStarterCombatItem(ItemStack s,String id){
        if(s.getTag()!=null&&s.getTag().getBoolean("GunnerArenaKnife"))return false;
        if(s.getTag()!=null&&s.getTag().getBoolean("gunnerarena_menu_compass"))return false;
        if(s.is(Items.COMPASS))return false;
        if(s.is(Items.BOW)||s.is(Items.CROSSBOW)||s.is(Items.ARROW)||s.is(Items.SPECTRAL_ARROW)||s.is(Items.TIPPED_ARROW))return true;
        if(id.startsWith("jeg:"))return true;
        return s.getTag()!=null&&(s.getTag().getBoolean("GunnerArenaBound")||s.getTag().getBoolean("GunGloryBotWeapon"));
    }
}
