package arena.client.ui;

import arena.client.net.ArenaClientNetwork;
import arena.client.net.ClientSnapshotStore;
import arena.client.net.ClientShopStore;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod(GunnerArenaUiMod.MODID)
public final class GunnerArenaUiMod {
    public static final String MODID = "gunnerarena_ui";
    private static final KeyMapping RELOAD_WEAPON = new KeyMapping("key.gunnerarena_ui.reload_weapon", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_U, "key.categories.gunnerarena_ui");
    private static final KeyMapping FIREMODE = new KeyMapping("key.gunnerarena_ui.firemode", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.gunnerarena_ui");
    private static final KeyMapping GUN_SHOP = new KeyMapping("key.gunnerarena_ui.gun_shop", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.gunnerarena_ui");
    private static final KeyMapping OPEN_MENU = new KeyMapping("key.gunnerarena_ui.open_menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, "key.categories.gunnerarena_ui");

    public GunnerArenaUiMod() { ArenaClientNetwork.register(); }

    @Mod.EventBusSubscriber(modid = MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(RELOAD_WEAPON); event.register(FIREMODE); event.register(GUN_SHOP); event.register(OPEN_MENU);
        }
    }

    @Mod.EventBusSubscriber(modid = MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ForgeEvents {
        private static int snapshotPollTicks;
        private static boolean hadPlayer;

        @SubscribeEvent public static void clientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.screen == null) {
                while (OPEN_MENU.consumeClick()) mc.player.connection.sendCommand("menu");
                while (GUN_SHOP.consumeClick()) mc.player.connection.sendCommand("gunshop");
                while (RELOAD_WEAPON.consumeClick()) {
                    var stack = mc.player.getMainHandItem();
                    var id = stack.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(stack.getItem());
                    if (id != null && "gunnerarena".equals(id.getNamespace())) ArenaClientNetwork.requestReload();
                }
                while (FIREMODE.consumeClick()) ArenaClientNetwork.requestCycleFireMode();
            }
            if (mc.player == null) {
                if (hadPlayer) { ClientSnapshotStore.clear(); ClientShopStore.clear(); }
                hadPlayer=false; snapshotPollTicks=0; return;
            }
            hadPlayer=true;
            if (!(mc.screen instanceof AbstractArenaScreen)) { snapshotPollTicks=0; return; }
            if (++snapshotPollTicks>=20) { snapshotPollTicks=0; ArenaClientNetwork.requestSnapshot(); if(mc.screen instanceof ShopScreen) ArenaClientNetwork.requestCatalog(); }
        }

        @SubscribeEvent public static void weaponHud(RenderGuiOverlayEvent.Post event) {
            Minecraft mc=Minecraft.getInstance();
            if(mc.player==null||mc.options.hideGui||mc.screen instanceof AbstractArenaScreen)return;
            var stack=mc.player.getMainHandItem(); if(stack.isEmpty())return;
            var id=BuiltInRegistries.ITEM.getKey(stack.getItem());
            if(id==null||!("jeg".equals(id.getNamespace())||"gunnerarena".equals(id.getNamespace())))return;
            var g=event.getGuiGraphics(); int w=mc.getWindow().getGuiScaledWidth(), h=mc.getWindow().getGuiScaledHeight();
            int boxW=Math.min(176,Math.max(132,w/4)); int x=Math.max(5,w-boxW-6), y=Math.max(5,h-49);
            g.fill(x,y,x+boxW,y+41,0xB8121728); g.renderOutline(x,y,boxW,41,UiTheme.BLUE); g.renderItem(stack,x+6,y+12);
            String ru=WeaponNames.russianFor(id.toString(),""); String title=stack.getHoverName().getString(); if(title.length()>18)title=title.substring(0,18)+"…";
            g.drawString(mc.font,Component.literal("✦ "+title),x+28,y+7,UiTheme.TEXT);
            g.drawString(mc.font,Component.literal("("+ru+")"),x+28,y+19,UiTheme.PINK);
            String hint="jeg".equals(id.getNamespace())?"R reload • G shop":"U reload • G shop"; g.drawString(mc.font,Component.literal(hint),x+28,y+31,UiTheme.MUTED);
        }
    }
}
