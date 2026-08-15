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
    private static final boolean DEV_KEY_ENABLED = UiNavigationPolicy.devKeyEnabled(System.getProperty("gunnerarena.ui.devKey"));
    private static final KeyMapping RELOAD_WEAPON = new KeyMapping("key.gunnerarena_ui.reload_weapon", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.gunnerarena_ui");
    private static final KeyMapping FIREMODE = new KeyMapping("key.gunnerarena_ui.firemode", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.gunnerarena_ui");
    private static final KeyMapping OPEN_MENU = new KeyMapping("key.gunnerarena_ui.open_menu_dev", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8, "key.categories.gunnerarena_ui");

    public GunnerArenaUiMod() { ArenaClientNetwork.register(); }

    @Mod.EventBusSubscriber(modid = MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(RELOAD_WEAPON); event.register(FIREMODE); if (DEV_KEY_ENABLED) event.register(OPEN_MENU);
        }
    }

    @Mod.EventBusSubscriber(modid = MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ForgeEvents {
        private static int snapshotPollTicks;
        private static boolean hadPlayer;

        @SubscribeEvent
        public static void clientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            while (DEV_KEY_ENABLED && OPEN_MENU.consumeClick()) if (mc.player != null) ClientUiOpener.open(0);
            if (mc.player != null && mc.screen == null) {
                while (RELOAD_WEAPON.consumeClick()) ArenaClientNetwork.requestReload();
                while (FIREMODE.consumeClick()) ArenaClientNetwork.requestCycleFireMode();
            }
            if (mc.player == null) {
                if (hadPlayer) { ClientSnapshotStore.clear(); ClientShopStore.clear(); }
                hadPlayer = false; snapshotPollTicks = 0; return;
            }
            hadPlayer = true;
            if (!(mc.screen instanceof AbstractArenaScreen)) { snapshotPollTicks = 0; return; }
            if (++snapshotPollTicks >= 20) {
                snapshotPollTicks = 0; ArenaClientNetwork.requestSnapshot(); if (mc.screen instanceof ShopScreen) ArenaClientNetwork.requestCatalog();
            }
        }

        @SubscribeEvent
        public static void weaponHud(RenderGuiOverlayEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.options.hideGui || mc.screen instanceof AbstractArenaScreen) return;
            var stack = mc.player.getMainHandItem();
            if (stack.isEmpty()) return;
            var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id == null || !("jeg".equals(id.getNamespace()) || "gunnerarena".equals(id.getNamespace()))) return;
            var g = event.getGuiGraphics();
            int w = mc.getWindow().getGuiScaledWidth();
            int h = mc.getWindow().getGuiScaledHeight();
            int x = Math.max(6, w - 190), y = Math.max(6, h - 54);
            g.fill(x,y,x+184,y+45,0xB8121728);
            g.renderOutline(x,y,184,45,UiTheme.BLUE);
            g.renderItem(stack,x+8,y+14);
            String ru = WeaponNames.russianFor(id.toString(), "");
            String title = stack.getHoverName().getString();
            if (title.length() > 22) title = title.substring(0,22)+"…";
            g.drawString(mc.font, Component.literal("✦ "+title), x+31,y+9,UiTheme.TEXT);
            g.drawString(mc.font, Component.literal("("+ru+")"), x+31,y+22,UiTheme.PINK);
            g.drawString(mc.font, Component.literal("R перезарядка  •  V режим"), x+31,y+34,UiTheme.MUTED);
        }
    }
}
