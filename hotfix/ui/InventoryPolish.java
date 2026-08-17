package arena.client.ui;

import arena.client.net.ClientSnapshotStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
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

        // Header / identity strip above the vanilla panel.
        int hx=left-2,hy=top-27,hw=180;
        UiEffects.verticalGradient(g,hx,hy,hx+hw,top-3,0xD7192B48,0xD4101725);
        UiEffects.animatedSheen(g,hx,hy,hw,24,now,UiTheme.ACCENT);
        UiEffects.pulseBorder(g,hx,hy,hw,24,now,UiTheme.ACCENT);
        g.drawString(mc.font,Component.literal("✦ LOADOUT // GUN GLORY ONLINE"),hx+8,hy+5,UiTheme.TEXT,false);
        var snap=ClientSnapshotStore.get();
        String state=snap!=null&&"RUNNING".equalsIgnoreCase(snap.roundState())?"◆ В БОЮ":"◇ ЛОББИ";
        int stateColor=state.startsWith("◆")?0xFFFF6B87:UiTheme.ACCENT;
        g.drawString(mc.font,Component.literal(state),hx+8,hy+14,stateColor,false);

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

        // Compact weapon card on the right. Do not invent ammo values if JEG metadata is unknown.
        int px=left+184,py=top+8,pw=Math.min(156,Math.max(118,(sw-left-188)));
        if(px+pw<sw-3){
            UiEffects.verticalGradient(g,px,py,px+pw,py+82,0xD0142135,0xC6090F1A);
            UiEffects.pulseBorder(g,px,py,pw,82,now,UiTheme.ACCENT_2);
            g.drawString(mc.font,Component.literal("БОЕВОЙ КОМПЛЕКТ"),px+8,py+8,UiTheme.PINK,false);
            ItemStack held=mc.player.getMainHandItem();
            if(!held.isEmpty()){
                g.renderItem(held,px+8,py+27);
                String name=held.getHoverName().getString();
                if(name.length()>18)name=name.substring(0,18)+"…";
                g.drawString(mc.font,Component.literal(name),px+31,py+27,UiTheme.TEXT,false);
                int count=held.getCount();
                g.drawString(mc.font,Component.literal("Слот "+(mc.player.getInventory().selected+1)+(count>1?"  ×"+count:"")),px+31,py+39,UiTheme.MUTED,false);
            }else g.drawString(mc.font,Component.literal("Оружие не выбрано"),px+8,py+31,UiTheme.DIM,false);
            g.fill(px+8,py+56,px+pw-8,py+57,UiTheme.HAIRLINE);
            g.drawString(mc.font,Component.literal("M — меню"),px+8,py+63,UiTheme.ACCENT,false);
            g.drawString(mc.font,Component.literal("G — магазин"),px+8,py+72,UiTheme.MUTED,false);
        }

        // Bottom footer keeps the screen visually tied to other GGO menus.
        int fy=top+169;
        if(fy+14<sh){
            UiEffects.verticalGradient(g,left-2,fy,left+178,fy+14,0xB9101725,0xB7192B48);
            g.drawCenteredString(mc.font,Component.literal("3 БОЕВЫХ СЛОТА  •  E — ЗАКРЫТЬ"),left+88,fy+3,UiTheme.MUTED);
        }
    }
}
