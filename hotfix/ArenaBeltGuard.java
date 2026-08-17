package arena.forge;

import arena.GunnerArenaMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * GGO owns a compact three-slot inventory in every authenticated state, including spawn/lobby.
 * The menu is virtual (M), never a physical compass item.
 */
@Mod.EventBusSubscriber(modid="gunnerarena",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class ArenaBeltGuard {
    public static final int COMBAT_SLOTS=3;
    private static final String COMPASS_TAG="gunnerarena_menu_compass";
    private ArenaBeltGuard(){}

    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e){
        if(e.phase!=TickEvent.Phase.END||e.player.level().isClientSide||!(e.player instanceof ServerPlayer p)||(p.tickCount%2)!=0)return;
        ArenaRuntime r=GunnerArenaMod.RUNTIME;if(r==null||!r.auth().isAuthenticated(p))return;

        // Menu tokens from older builds must never occupy a real inventory slot.
        removeMenuTokens(p);

        // Pack anything that legacy scripts/shop code placed outside the compact belt into 1..3.
        // If all three slots are occupied, extra injected items are discarded instead of flashing in
        // the vanilla inventory and becoming usable for a tick.
        for(int i=COMBAT_SLOTS;i<p.getInventory().getContainerSize();i++){
            ItemStack s=p.getInventory().getItem(i);if(s.isEmpty())continue;
            int free=firstFree(p);
            if(free>=0)p.getInventory().setItem(free,s);
            p.getInventory().setItem(i,ItemStack.EMPTY);
        }
        if(!p.getOffhandItem().isEmpty()){
            int free=firstFree(p);if(free>=0)p.getInventory().setItem(free,p.getOffhandItem());
            p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,ItemStack.EMPTY);
        }
        if(p.getInventory().selected<0||p.getInventory().selected>=COMBAT_SLOTS)p.getInventory().selected=0;
        p.getInventory().setChanged();
    }

    private static void removeMenuTokens(ServerPlayer p){
        for(int i=0;i<p.getInventory().getContainerSize();i++)if(isMenuCompass(p.getInventory().getItem(i)))p.getInventory().setItem(i,ItemStack.EMPTY);
        if(isMenuCompass(p.getOffhandItem()))p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,ItemStack.EMPTY);
    }
    private static int firstFree(ServerPlayer p){for(int i=0;i<COMBAT_SLOTS;i++)if(p.getInventory().getItem(i).isEmpty())return i;return -1;}
    private static boolean isMenuCompass(ItemStack s){return s!=null&&!s.isEmpty()&&s.is(Items.COMPASS)&&s.hasTag()&&s.getTag().getBoolean(COMPASS_TAG);}
}
