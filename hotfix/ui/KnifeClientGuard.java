package arena.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Hides foreign/JEG dry-fire action-bar noise while the GGO tactical knife is selected. */
@Mod.EventBusSubscriber(modid=GunnerArenaUiMod.MODID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class KnifeClientGuard {
    private KnifeClientGuard(){}

    @SubscribeEvent
    public static void system(ClientChatReceivedEvent.System e){
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||!isKnife(mc.player.getMainHandItem()))return;
        String s=e.getMessage().getString().toLowerCase(java.util.Locale.ROOT);
        if(s.contains("патрон")||s.contains("ammo")||s.contains("reload"))e.setCanceled(true);
    }

    static boolean isKnife(ItemStack s){
        if(s==null||s.isEmpty())return false;
        if(s.hasTag()&&s.getTag().getBoolean("GunnerArenaKnife"))return true;
        String n=s.getHoverName().getString().toLowerCase(java.util.Locale.ROOT);
        return n.contains("тактический нож")||n.equals("нож")||n.contains("knife");
    }
}
