package arena.forge;

import arena.GunnerArenaMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Three real player slots in normal GGO play. Explicit /gm 1 or /gm 3 admin modes are exempt. */
@Mod.EventBusSubscriber(modid="gunnerarena",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class ArenaBeltGuard {
    private static final int COMBAT_SLOTS=3;
    private static final String COMPASS_TAG="gunnerarena_menu_compass";
    private ArenaBeltGuard(){}

    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e){
        if(e.phase!=TickEvent.Phase.END||e.player.level().isClientSide||!(e.player instanceof ServerPlayer p)||(p.tickCount%2)!=0)return;
        ArenaRuntime r=GunnerArenaMod.RUNTIME;if(r==null||!r.auth().isAuthenticated(p))return;
        removeMenuTokens(p);

        // Do NOT globally exempt OPs. Only an admin who deliberately entered /gm 1 or /gm 3 gets
        // unrestricted inventory/build access. /gm 0 returns them to the normal Adventure + 3-slot rules.
        if(p.hasPermissions(2)&&(p.gameMode.getGameModeForPlayer()==GameType.CREATIVE||p.gameMode.getGameModeForPlayer()==GameType.SPECTATOR))return;

        packEverythingIntoThreeSlots(p);
        if(p.getInventory().selected>=COMBAT_SLOTS)p.getInventory().selected=0;
    }

    private static void removeMenuTokens(ServerPlayer p){
        for(int i=0;i<p.getInventory().getContainerSize();i++)if(isMenuCompass(p.getInventory().getItem(i)))p.getInventory().setItem(i,ItemStack.EMPTY);
        if(isMenuCompass(p.getOffhandItem()))p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,ItemStack.EMPTY);
    }

    /**
     * GGO inventory is always three physical hotbar slots for normal play, including spawn/lobby.
     * Old map/command-block items are first compacted there; overflow is deleted by the legacy scrub.
     */
    private static void packEverythingIntoThreeSlots(ServerPlayer p){
        for(int i=COMBAT_SLOTS;i<p.getInventory().getContainerSize();i++){
            ItemStack s=p.getInventory().getItem(i);if(s==null||s.isEmpty())continue;
            int free=firstFree(p);if(free>=0){p.getInventory().setItem(free,s);p.getInventory().setItem(i,ItemStack.EMPTY);}
            else if(isGgoOrLegacyCombat(s)){p.getInventory().setItem(i,ItemStack.EMPTY);}
        }
        // Hotbar 4-9 must never visually become usable in normal GGO play.
        for(int i=COMBAT_SLOTS;i<9;i++)if(!p.getInventory().getItem(i).isEmpty())p.getInventory().setItem(i,ItemStack.EMPTY);
        p.getInventory().setChanged();
    }
    private static int firstFree(ServerPlayer p){for(int i=0;i<COMBAT_SLOTS;i++)if(p.getInventory().getItem(i).isEmpty())return i;return -1;}
    private static boolean isMenuCompass(ItemStack s){return s!=null&&!s.isEmpty()&&s.is(Items.COMPASS)&&s.hasTag()&&s.getTag().getBoolean(COMPASS_TAG);}
    private static boolean isGgoOrLegacyCombat(ItemStack s){
        if(s==null||s.isEmpty())return false;if(s.hasTag()&&(s.getTag().getBoolean("GunnerArenaBound")||s.getTag().getBoolean("GunnerArenaKnife")||s.getTag().getBoolean("GunGloryBotWeapon")))return true;
        ResourceLocation id=ForgeRegistries.ITEMS.getKey(s.getItem());return id!=null&&("jeg".equals(id.getNamespace())||"jeg_cfg".equals(id.getNamespace()));
    }
}
