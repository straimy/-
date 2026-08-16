package arena.forge;

import arena.GunnerArenaMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Lobby/spawn cleanup. Menu is virtual (M), so no physical compass is injected anymore. */
@Mod.EventBusSubscriber(modid="gunnerarena",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class SpawnLoadoutGuard {
    private static final String COMPASS_TAG="gunnerarena_menu_compass";
    private SpawnLoadoutGuard(){}

    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e){
        if(e.phase!=TickEvent.Phase.END||e.player.level().isClientSide||!(e.player instanceof ServerPlayer p)||(p.tickCount%10)!=0)return;
        ArenaRuntime r=GunnerArenaMod.RUNTIME;if(r==null||!r.auth().isAuthenticated(p)||!r.safeRegions().isSafe(p))return;
        boolean changed=false;
        for(int i=0;i<p.getInventory().getContainerSize();i++){
            ItemStack s=p.getInventory().getItem(i);
            if(isCombatItem(s)||isMenuCompass(s)){p.getInventory().setItem(i,ItemStack.EMPTY);changed=true;}
        }
        if(isCombatItem(p.getOffhandItem())||isMenuCompass(p.getOffhandItem())){p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,ItemStack.EMPTY);changed=true;}
        if(changed)p.getInventory().setChanged();
    }

    private static boolean isCombatItem(ItemStack s){
        if(s==null||s.isEmpty())return false;
        if(s.hasTag()&&(s.getTag().getBoolean("GunnerArenaBound")||s.getTag().getBoolean("GunnerArenaKnife")))return true;
        ResourceLocation id=ForgeRegistries.ITEMS.getKey(s.getItem());return id!=null&&"jeg".equals(id.getNamespace());
    }
    private static boolean isMenuCompass(ItemStack s){return s!=null&&!s.isEmpty()&&s.is(Items.COMPASS)&&s.hasTag()&&s.getTag().getBoolean(COMPASS_TAG);}
}
