package arena.client.ui;

import arena.client.net.ClientSnapshotStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** One clean GunGlory belt: the vanilla hotbar is hidden, so there is no duplicated UI underneath. */
@Mod.EventBusSubscriber(modid="gunnerarena_ui",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class ArenaBeltOverlay {
    private ArenaBeltOverlay(){}

    @SubscribeEvent public static void hideVanillaHotbar(RenderGuiOverlayEvent.Pre e){
        if(e.getOverlay()==VanillaGuiOverlay.HOTBAR.type())e.setCanceled(true);
    }

    @SubscribeEvent public static void render(RenderGuiOverlayEvent.Post e){
        Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.options.hideGui||mc.screen!=null)return;
        var snap=ClientSnapshotStore.get();boolean battle=snap.initialized()&&"ALIVE".equalsIgnoreCase(snap.state());
        GuiGraphics g=e.getGuiGraphics();int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();
        int cell=22,gap=2,beltW=5*cell+4*gap,menuW=62,total=beltW+(battle?0:7+menuW),x=w/2-total/2,y=h-25;
        for(int i=0;i<5;i++){
            int sx=x+i*(cell+gap);boolean sel=mc.player.getInventory().selected==i;
            g.fill(sx,y,sx+cell,y+cell,sel?0xE22A405F:0xD0101826);g.renderOutline(sx,y,cell,cell,sel?UiTheme.ACCENT:UiTheme.HAIRLINE);
            ItemStack s=mc.player.getInventory().getItem(i);if(!s.isEmpty())g.renderItem(s,sx+3,y+3);
            g.drawString(mc.font,Component.literal(Integer.toString(i+1)),sx+2,y+2,sel?UiTheme.TEXT:UiTheme.DIM,false);
        }
        // Menu is now a UI action, not a fake inventory item. This keeps the Minecraft inventory clean.
        if(!battle){int mx=x+beltW+7;g.fill(mx,y,mx+menuW,y+cell,0xD0142032);g.renderOutline(mx,y,menuW,cell,UiTheme.ACCENT_2);g.drawCenteredString(mc.font,Component.literal("M  ✦  МЕНЮ"),mx+menuW/2,y+7,UiTheme.TEXT);}
    }
}
