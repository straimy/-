package arena.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Replaces only the vanilla 9-slot hotbar with the real fixed three-slot GGO belt. */
@Mod.EventBusSubscriber(modid=GunnerArenaUiMod.MODID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class ArenaBeltOverlay {
    private static final int SLOTS=3;
    private ArenaBeltOverlay(){}

    @SubscribeEvent
    public static void hideVanillaHotbar(RenderGuiOverlayEvent.Pre e){
        Minecraft mc=Minecraft.getInstance();
        if(mc.player!=null&&mc.screen==null&&e.getOverlay()==VanillaGuiOverlay.HOTBAR.type())e.setCanceled(true);
    }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post e){
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||mc.options.hideGui||mc.screen!=null)return;
        var g=e.getGuiGraphics();int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();
        int slot=20,total=SLOTS*slot,x=w/2-total/2,y=h-22;long now=System.currentTimeMillis();
        for(int i=0;i<SLOTS;i++){
            int sx=x+i*slot;boolean selected=mc.player.getInventory().selected==i;
            UiEffects.verticalGradient(g,sx,y,sx+18,y+18,selected?0xD52A4160:0xC0142033,selected?0xC316293F:0xB0101828);
            g.renderOutline(sx,y,18,18,selected?UiTheme.PINK:UiTheme.ACCENT);
            if(selected)UiEffects.pulseBorder(g,sx-1,y-1,20,20,now,UiTheme.PINK);
            var stack=mc.player.getInventory().getItem(i);if(!stack.isEmpty()){g.renderItem(stack,sx+1,y+1);g.renderItemDecorations(mc.font,stack,sx+1,y+1);}
            g.drawString(mc.font,Integer.toString(i+1),sx+2,y-7,selected?UiTheme.PINK:UiTheme.MUTED,false);
        }
    }
}
