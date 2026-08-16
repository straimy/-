package arena.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** RightClickEmpty is client-only; this covers the spawn case where no block/entity is targeted. */
@Mod.EventBusSubscriber(modid=GunnerArenaUiMod.MODID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class SpawnCompassClientFix {
    private static final String TAG="gunnerarena_menu_compass";
    private static long lastOpen;
    private SpawnCompassClientFix(){}

    @SubscribeEvent
    public static void empty(PlayerInteractEvent.RightClickEmpty e){
        var stack=e.getItemStack();
        if(stack==null||!stack.is(Items.COMPASS)||!stack.hasTag()||!stack.getTag().getBoolean(TAG)) return;
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||mc.player.connection==null) return;
        long now=System.currentTimeMillis(); if(now-lastOpen<350) return; lastOpen=now;
        mc.player.connection.sendCommand("menu");
    }
}
