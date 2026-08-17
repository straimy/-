package arena.client.ui;

import arena.client.net.ClientSnapshotStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** GGO layer for E: three combat slots, a dedicated ammo row and safe utility actions. */
@Mod.EventBusSubscriber(modid=GunnerArenaUiMod.MODID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class InventoryPolish {
    private InventoryPolish(){}

    @SubscribeEvent public static void init(ScreenEvent.Init.Post event){
        if(!(event.getScreen() instanceof InventoryScreen screen))return;
        int left=(screen.width-176)/2,top=(screen.height-166)/2;
        int y=top+184;
        if(y+18>=screen.height)y=top+164;
        event.addListener(Button.builder(Component.literal("СОБРАТЬ"),b->send("ggoinv ammo")).bounds(left,y,55,18).build());
        event.addListener(Button.builder(Component.literal("МУСОР ↓"),b->send("ggoinv clear")).bounds(left+60,y,55,18).build());
        event.addListener(Button.builder(Component.literal("ПАТРОНЫ ↓"),b->send("ggoinv dropammo")).bounds(left+120,y,56,18).build());
    }

    private static void send(String command){Minecraft mc=Minecraft.getInstance();if(mc.player!=null&&mc.player.connection!=null)mc.player.connection.sendCommand(command);}

    @SubscribeEvent public static void render(ScreenEvent.Render.Post event){
        if(!(event.getScreen() instanceof InventoryScreen))return;
        Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;
        GuiGraphics g=event.getGuiGraphics();int sw=event.getScreen().width,sh=event.getScreen().height;int left=(sw-176)/2,top=(sh-166)/2;long now=System.currentTimeMillis();

        int hx=left-2,hy=top-27,hw=180;
        UiEffects.verticalGradient(g,hx,hy,hx+hw,top-3,0xD7192B48,0xD4101725);UiEffects.animatedSheen(g,hx,hy,hw,24,now,UiTheme.ACCENT);UiEffects.pulseBorder(g,hx,hy,hw,24,now,UiTheme.ACCENT);
        g.drawString(mc.font,Component.literal("✦ LOADOUT // GUN GLORY ONLINE"),hx+8,hy+5,UiTheme.TEXT,false);
        var snap=ClientSnapshotStore.get();String state=snap!=null&&"RUNNING".equalsIgnoreCase(snap.roundState())?"◆ В БОЮ":"◇ ЛОББИ";g.drawString(mc.font,Component.literal(state),hx+8,hy+14,state.startsWith("◆")?0xFFFF6B87:UiTheme.ACCENT,false);
        var held=mc.player.getMainHandItem();if(!held.isEmpty()){g.renderItem(held,hx+158,hy+4);g.renderOutline(hx+156,hy+2,20,20,UiTheme.ACCENT_2);}

        // First normal inventory row (indices 9..17) is the real ammo pouch. The remaining two rows stay visually locked.
        int ammoY=top+83;
        UiEffects.verticalGradient(g,left+7,ammoY,left+169,ammoY+18,0x70304A63,0x50203850);
        for(int i=0;i<9;i++){int x=left+7+i*18;g.renderOutline(x,ammoY,18,18,UiTheme.ACCENT);}
        g.drawString(mc.font,Component.literal("◈ ПАТРОНЫ • 9 СЛОТОВ"),left+8,ammoY-10,UiTheme.ACCENT_2,false);
        g.fill(left+7,top+102,left+169,top+140,0x9A07101C);
        g.drawCenteredString(mc.font,Component.literal("ОСТАЛЬНЫЕ СЛОТЫ ЗАБЛОКИРОВАНЫ"),left+88,top+116,UiTheme.DIM);

        // Three actual combat cells in the vanilla hotbar row.
        for(int i=0;i<3;i++){int x=left+7+i*18,y=top+141;int c=i==mc.player.getInventory().selected?UiTheme.PINK:UiTheme.ACCENT;g.renderOutline(x,y,18,18,c);if(i==mc.player.getInventory().selected)g.fill(x+1,y+1,x+17,y+17,0x3018DDEA);g.drawString(mc.font,Component.literal(Integer.toString(i+1)),x+2,y-8,c,false);}
        g.fill(left+62,top+141,left+169,top+162,0xA807101C);

        int fy=top+166;if(fy+14<sh){UiEffects.verticalGradient(g,left-2,fy,left+178,fy+14,0xB9101725,0xB7192B48);g.drawCenteredString(mc.font,Component.literal("3 БОЕВЫХ СЛОТА • 9 СЛОТОВ ПАТРОНОВ"),left+88,fy+3,UiTheme.MUTED);}
        superRenderHint(g,mc,left,top,sh);
    }
    private static void superRenderHint(GuiGraphics g,Minecraft mc,int left,int top,int sh){int y=top+205;if(y<sh-8)g.drawCenteredString(mc.font,Component.literal("МУСОР ↓ и ПАТРОНЫ ↓ выбрасывают предметы рядом с вами"),left+88,y,UiTheme.DIM);}
}
