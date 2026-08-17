package arena.client.ui;

import arena.client.net.ClientSnapshotStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Decorative GGO layer for the vanilla E inventory. Server rules still own slot restrictions. */
@Mod.EventBusSubscriber(modid=GunnerArenaUiMod.MODID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class InventoryPolish {
    private InventoryPolish(){}

    @SubscribeEvent
    public static void render(ScreenEvent.Render.Post event){
        if(!(event.getScreen() instanceof InventoryScreen))return;
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null)return;
        GuiGraphics g=event.getGuiGraphics();
        int sw=event.getScreen().width,sh=event.getScreen().height;
        int left=(sw-176)/2,top=(sh-166)/2;
        long now=System.currentTimeMillis();

        // Header stays exactly over the inventory width so potion/effect cards on the right remain unobstructed.
        int hx=left-2,hy=top-27,hw=180;
        UiEffects.verticalGradient(g,hx,hy,hx+hw,top-3,0xD7192B48,0xD4101725);
        UiEffects.animatedSheen(g,hx,hy,hw,24,now,UiTheme.ACCENT);
        UiEffects.pulseBorder(g,hx,hy,hw,24,now,UiTheme.ACCENT);
        g.drawString(mc.font,Component.literal("✦ LOADOUT // GUN GLORY ONLINE"),hx+8,hy+5,UiTheme.TEXT,false);
        var snap=ClientSnapshotStore.get();
        String state=snap!=null&&"RUNNING".equalsIgnoreCase(snap.roundState())?"◆ В БОЮ":"◇ ЛОББИ";
        int stateColor=state.startsWith("◆")?0xFFFF6B87:UiTheme.ACCENT;
        g.drawString(mc.font,Component.literal(state),hx+8,hy+14,stateColor,false);

        // Tiny held-item marker in the header instead of a large right-side card that covered status effects.
        var held=mc.player.getMainHandItem();
        if(!held.isEmpty()){
            g.renderItem(held,hx+158,hy+4);
            g.renderOutline(hx+156,hy+2,20,20,UiTheme.ACCENT_2);
        }

        // Dim every normal inventory cell: gameplay owns only the three combat belt slots.
        g.fill(left+7,top+83,left+169,top+140,0x8A07101C);
        g.fill(left+62,top+141,left+169,top+162,0xA807101C);
        g.drawCenteredString(mc.font,Component.literal("ИНВЕНТАРЬ ЗАБЛОКИРОВАН В GGO"),left+88,top+108,UiTheme.DIM);

        // Emphasize the actual 3 combat slots in the vanilla hotbar row.
        for(int i=0;i<3;i++){
            int x=left+7+i*18,y=top+141;
            int c=i==mc.player.getInventory().selected?UiTheme.PINK:UiTheme.ACCENT;
            g.renderOutline(x,y,18,18,c);
            if(i==mc.player.getInventory().selected)g.fill(x+1,y+1,x+17,y+17,0x3018DDEA);
            g.drawString(mc.font,Component.literal(Integer.toString(i+1)),x+2,y-8,c,false);
        }

        // Footer is narrow and anchored to the inventory panel only.
        int fy=top+169;
        if(fy+14<sh){
            UiEffects.verticalGradient(g,left-2,fy,left+178,fy+14,0xB9101725,0xB7192B48);
            g.drawCenteredString(mc.font,Component.literal("3 БОЕВЫХ СЛОТА  •  M МЕНЮ  •  E ЗАКРЫТЬ"),left+88,fy+3,UiTheme.MUTED);
        }
    }
}
