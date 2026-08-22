package arena.client.shell;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Installs the core death-recap consumer before a death packet can arrive. */
@Mod.EventBusSubscriber(value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoDeathRecapBootstrap {
    private GgoDeathRecapBootstrap(){}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase==TickEvent.Phase.START)GgoDeathRecapAdapter.install();
    }
}
