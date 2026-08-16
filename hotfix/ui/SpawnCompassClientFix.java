package arena.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client fallback for the menu compass. Covers empty air, block and item use paths. */
@Mod.EventBusSubscriber(modid=GunnerArenaUiMod.MODID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class SpawnCompassClientFix {
    private static final String TAG="gunnerarena_menu_compass";
    private static long lastOpen;
    private SpawnCompassClientFix(){}

    @SubscribeEvent public static void empty(PlayerInteractEvent.RightClickEmpty e){open(e.getItemStack());}
    @SubscribeEvent public static void item(PlayerInteractEvent.RightClickItem e){open(e.getItemStack());}
    @SubscribeEvent public static void block(PlayerInteractEvent.RightClickBlock e){open(e.getItemStack());}

    private static void open(ItemStack stack){
        if(stack==null||!stack.is(Items.COMPASS)||!stack.hasTag()||!stack.getTag().getBoolean(TAG)) return;
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||mc.player.connection==null||mc.screen!=null) return;
        long now=System.currentTimeMillis();
        if(now-lastOpen<350) return;
        lastOpen=now;
        mc.player.connection.sendCommand("menu");
    }
}
