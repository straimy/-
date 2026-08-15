package arena.forge;

import arena.GunnerArenaMod;
import arena.forge.net.ArenaNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="gunnerarena",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class CompassMenuFix {
    private static final String TAG="gunnerarena_menu_compass";
    private CompassMenuFix(){}

    @SubscribeEvent(priority=EventPriority.HIGHEST,receiveCanceled=true)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e){
        open(e.getEntity() instanceof ServerPlayer p?p:null,e.getItemStack(),e);
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST,receiveCanceled=true)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem e){
        open(e.getEntity() instanceof ServerPlayer p?p:null,e.getItemStack(),e);
    }

    private static void open(ServerPlayer p,ItemStack s,PlayerInteractEvent e){
        if(p==null||s==null||!s.is(Items.COMPASS)||!s.hasTag()||!s.getTag().getBoolean(TAG))return;
        e.setCanceled(true);
        ArenaRuntime r=GunnerArenaMod.RUNTIME;
        if(r==null)return;
        if(r.auth().isAuthenticated(p)) ArenaNetwork.openUi(p,ArenaNetwork.UiTarget.MAIN);
        else r.auth().deny(p);
    }
}
