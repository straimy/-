package arena.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Compact GGO belt: three real slots + a minimal M menu hint, rendered in the native hotbar area. */
@Mod.EventBusSubscriber(modid=GunnerArenaUiMod.MODID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class ArenaBeltOverlay {
    private static final int SLOTS=3;
    private ArenaBeltOverlay(){}

    @SubscribeEvent public static void render(RenderGuiOverlayEvent.Post e){
        Minecraft mc=Minecraft.getInstance();
        boolean chat=mc.screen instanceof ChatScreen;
        if(mc.player==null||mc.options.hideGui||(mc.screen!=null&&!chat))return;
        var g=e.getGuiGraphics();int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();

        // Raise the custom belt while chat is open so the input line never hides it.
        int lift=chat?18:0;
        int nativeX=w/2-92,nativeY=h-24-lift;
        g.fill(nativeX-2,nativeY-2,nativeX+184,nativeY+24,0xE00A0D14);

        int total=SLOTS*22;int x=w/2-total/2;int y=h-23-lift;
        for(int i=0;i<SLOTS;i++){
            int sx=x+i*22;boolean selected=mc.player.getInventory().selected==i;
            g.fill(sx,y,sx+20,y+20,selected?0xE0314668:0xD0141B2A);
            g.renderOutline(sx,y,20,20,selected?UiTheme.BLUE:0xFF344158);
            var stack=mc.player.getInventory().getItem(i);if(!stack.isEmpty()){
                g.renderItem(stack,sx+2,y+2);g.renderItemDecorations(mc.font,stack,sx+2,y+2);
            }
            g.drawString(mc.font,Component.literal(Integer.toString(i+1)),sx+2,y-8,selected?UiTheme.BLUE:UiTheme.MUTED,false);
        }

        String hint="M  ✦ МЕНЮ";int hw=mc.font.width(hint)+12;int hx=x+total+7,hy=y+2;
        if(hx+hw>w-4)hx=x-hw-7;
        g.fill(hx,hy,hx+hw,hy+16,0xD0141B2A);g.renderOutline(hx,hy,hw,16,UiTheme.PINK);
        g.drawString(mc.font,Component.literal(hint),hx+6,hy+4,UiTheme.TEXT,false);

        // A very small divider keeps the hearts/food row visually separate from the belt without covering vanilla HUD icons.
        if(!chat){
            int lineY=y-11;
            g.fill(x-3,lineY,x+total+3,lineY+1,0x704A6B8F);
        }
    }
}
