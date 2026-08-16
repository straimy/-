package arena.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Compact GunGlory belt overlay: five combat slots + a separate menu chip instead of a fake sixth weapon slot. */
@Mod.EventBusSubscriber(modid="gunnerarena_ui",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class ArenaBeltOverlay {
    private static final String MENU_TAG="gunnerarena_menu_compass";
    private ArenaBeltOverlay(){}

    @SubscribeEvent public static void render(RenderGuiOverlayEvent.Post e){
        Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.options.hideGui||mc.screen!=null)return;
        GuiGraphics g=e.getGuiGraphics();int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();
        int cell=20,gap=2,beltW=5*cell+4*gap,x=w/2-beltW/2,y=h-47;
        for(int i=0;i<5;i++){
            int sx=x+i*(cell+gap);boolean sel=mc.player.getInventory().selected==i;
            g.fill(sx,y,sx+cell,y+cell,sel?0xD9283C58:0xB8121B2A);g.renderOutline(sx,y,cell,cell,sel?UiTheme.ACCENT:UiTheme.HAIRLINE);
            ItemStack s=mc.player.getInventory().getItem(i);if(!s.isEmpty())g.renderItem(s,sx+2,y+2);
            g.drawString(mc.font,Component.literal(Integer.toString(i+1)),sx+2,y+2,sel?UiTheme.TEXT:UiTheme.DIM,false);
        }
        boolean menu=false;for(int i=0;i<9;i++){ItemStack s=mc.player.getInventory().getItem(i);if(s.is(Items.COMPASS)&&s.hasTag()&&s.getTag().getBoolean(MENU_TAG)){menu=true;break;}}
        if(menu){int mw=53,mx=x+beltW+7;g.fill(mx,y,mx+mw,y+cell,0xC8142032);g.renderOutline(mx,y,mw,cell,UiTheme.ACCENT_2);g.drawCenteredString(mc.font,Component.literal("M  ✦ MENU"),mx+mw/2,y+6,UiTheme.TEXT);}
    }
}
