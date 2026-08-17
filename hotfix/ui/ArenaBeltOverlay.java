package arena.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** GGO accent layer for the real three combat slots. It never moves the vanilla HUD. */
@Mod.EventBusSubscriber(modid=GunnerArenaUiMod.MODID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class ArenaBeltOverlay {
    private static final int SLOTS=3;
    private ArenaBeltOverlay(){}

    @SubscribeEvent public static void render(RenderGuiOverlayEvent.Post e){
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||mc.options.hideGui||mc.screen!=null)return;
        var g=e.getGuiGraphics();
        int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();

        // Keep the belt absolutely fixed. Opening chat or another screen must never push it around.
        int total=SLOTS*20;
        int x=w/2-total/2;
        int y=h-22;
        long now=System.currentTimeMillis();

        // No extra black bar and no separate M-menu panel: just polish the three real GGO slots.
        for(int i=0;i<SLOTS;i++){
            int sx=x+i*20;
            boolean selected=mc.player.getInventory().selected==i;
            int top=selected?0xB52A4160:0x8E142033;
            int bottom=selected?0xA316293F:0x76101828;
            UiEffects.verticalGradient(g,sx,y,sx+18,y+18,top,bottom);
            int border=selected?UiTheme.PINK:UiTheme.ACCENT;
            g.renderOutline(sx,y,18,18,border);
            if(selected)UiEffects.pulseBorder(g,sx-1,y-1,20,20,now,UiTheme.PINK);
            var stack=mc.player.getInventory().getItem(i);
            if(!stack.isEmpty()){
                g.renderItem(stack,sx+1,y+1);
                g.renderItemDecorations(mc.font,stack,sx+1,y+1);
            }
            g.drawString(mc.font,Integer.toString(i+1),sx+2,y-7,selected?UiTheme.PINK:UiTheme.MUTED,false);
        }
    }
}
