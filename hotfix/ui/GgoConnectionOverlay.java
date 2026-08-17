package arena.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Visual shell for connection/loading screens while vanilla networking keeps running underneath. */
@Mod.EventBusSubscriber(modid=GunnerArenaUiMod.MODID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoConnectionOverlay {
    private GgoConnectionOverlay(){}

    @SubscribeEvent
    public static void render(ScreenEvent.Render.Post event){
        var screen=event.getScreen();
        boolean connecting=screen instanceof ConnectScreen;
        boolean receiving=screen instanceof ReceivingLevelScreen;
        if(!connecting&&!receiving)return;

        Minecraft mc=Minecraft.getInstance();
        GuiGraphics g=event.getGuiGraphics();
        int w=screen.width,h=screen.height;
        long now=System.currentTimeMillis();

        g.fill(0,0,w,h,0xFF050810);
        UiEffects.verticalGradient(g,0,0,w,h,0xFF07111C,0xFF17070D);

        int cardW=Math.min(460,w-36),cardH=190;
        int x=(w-cardW)/2,y=(h-cardH)/2;
        UiEffects.verticalGradient(g,x,y,x+cardW,y+cardH,0xE9162233,0xE90A0F18);
        UiEffects.animatedSheen(g,x,y,cardW,cardH,now,UiTheme.ACCENT);
        UiEffects.pulseBorder(g,x,y,cardW,cardH,now,UiTheme.ACCENT);

        String title=receiving?"ENTERING GUNGLORY ONLINE":"CONNECTING TO GUNGLORY ONLINE";
        String status=receiving?"Synchronizing game world…":"Establishing secure game session…";
        int dots=(int)((now/450L)%4L);
        status=status+".".repeat(dots);

        g.drawCenteredString(mc.font,Component.literal("GUN GLORY ONLINE"),w/2,y+34,UiTheme.TEXT);
        g.drawCenteredString(mc.font,Component.literal(title),w/2,y+58,UiTheme.ACCENT_2);
        g.drawCenteredString(mc.font,Component.literal(status),w/2,y+88,UiTheme.MUTED);

        int barX=x+42,barY=y+122,barW=cardW-84;
        g.fill(barX,barY,barX+barW,barY+8,0xAA101827);
        int sweep=Math.max(42,barW/4);
        int travel=Math.max(1,barW+sweep);
        int pos=(int)((now/8L)%travel)-sweep;
        int sx=Math.max(barX,barX+pos),ex=Math.min(barX+barW,barX+pos+sweep);
        if(ex>sx)g.fill(sx,barY,ex,barY+8,UiTheme.ACCENT);

        g.drawCenteredString(mc.font,Component.literal("Minecraft runtime is hidden • networking remains Forge-native"),w/2,y+151,UiTheme.DIM);
    }
}
