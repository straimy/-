package arena.client.shell;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-side input policy for the three-slot GGO combat belt.
 *
 * Vanilla Q/offhand/pick-block and hotbar 4..9 shortcuts are consumed before normal gameplay can
 * use them. Mouse wheel is redefined to cycle only Primary/Secondary/Sidearm.
 */
@Mod.EventBusSubscriber(value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoCombatInputFence {
    private static int lastValidSlot;
    private GgoCombatInputFence(){}

    @SubscribeEvent public static void onClientTick(TickEvent.ClientTickEvent event){
        Minecraft mc=Minecraft.getInstance();
        if(event.phase==TickEvent.Phase.START){
            if(mc.player==null)return;
            drain(mc.options.keyDrop);
            drain(mc.options.keySwapOffhand);
            drain(mc.options.keyPickItem);
            for(int i=3;i<mc.options.keyHotbarSlots.length;i++)drain(mc.options.keyHotbarSlots[i]);
            return;
        }
        if(mc.player==null){lastValidSlot=0;return;}
        int selected=mc.player.getInventory().selected;
        if(selected>=0&&selected<3)lastValidSlot=selected;
        else mc.player.getInventory().selected=Math.max(0,Math.min(2,lastValidSlot));
    }

    @SubscribeEvent public static void onScroll(InputEvent.MouseScrollingEvent event){
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||mc.screen!=null||event.getScrollDelta()==0)return;
        int current=mc.player.getInventory().selected;
        if(current<0||current>2)current=Math.max(0,Math.min(2,lastValidSlot));
        int direction=event.getScrollDelta()>0?-1:1;
        int next=Math.floorMod(current+direction,3);
        mc.player.getInventory().selected=next;lastValidSlot=next;
        event.setCanceled(true);
    }

    private static void drain(net.minecraft.client.KeyMapping key){while(key.consumeClick()){} }
}
