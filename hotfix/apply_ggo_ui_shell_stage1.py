from pathlib import Path

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
CLIENT = ROOT / "client-ui"
JAVA = CLIENT / "src/main/java/arena/client/shell"
JAVA.mkdir(parents=True, exist_ok=True)

screen = r'''package arena.client.shell;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class GgoShellScreen extends Screen {
    public enum Page { INVENTORY, ACTIVITIES, MAP, PAUSE }

    private final Page page;

    public GgoShellScreen(Page page) {
        super(Component.literal(titleFor(page)));
        this.page = page;
    }

    private static String titleFor(Page page) {
        return switch (page) {
            case INVENTORY -> "GGO Inventory";
            case ACTIVITIES -> "GGO Activities";
            case MAP -> "GGO Navigation";
            case PAUSE -> "GunGloryOnline";
        };
    }

    @Override
    protected void init() {
        if (page == Page.PAUSE) {
            int x = this.width / 2 - 110;
            int y = this.height / 2 - 55;
            addRenderableWidget(Button.builder(Component.literal("RESUME"), b -> onClose())
                    .bounds(x, y, 220, 24).build());
            addRenderableWidget(Button.builder(Component.literal("INVENTORY"), b -> Minecraft.getInstance().setScreen(new GgoShellScreen(Page.INVENTORY)))
                    .bounds(x, y + 30, 220, 24).build());
            addRenderableWidget(Button.builder(Component.literal("ACTIVITIES"), b -> Minecraft.getInstance().setScreen(new GgoShellScreen(Page.ACTIVITIES)))
                    .bounds(x, y + 60, 220, 24).build());
            addRenderableWidget(Button.builder(Component.literal("SETTINGS — NEXT STAGE"), b -> {})
                    .bounds(x, y + 90, 220, 24).build());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xE60A0C11);
        graphics.fill(0, 0, this.width, 3, 0xFFB52C38);
        graphics.drawString(this.font, "GUNGLORYONLINE", 22, 18, 0xFFF5F6F8, false);
        graphics.drawString(this.font, this.title, 22, 42, 0xFFD34855, false);

        if (page != Page.PAUSE) {
            String hint = switch (page) {
                case INVENTORY -> "Equipment / backpack / ammo / quick slots — Stage 2";
                case ACTIVITIES -> "Training / Battle Royale / Events / Contracts — Stage 2";
                case MAP -> "Navigation / sectors / squad / objectives — Stage 2";
                default -> "";
            };
            graphics.drawString(this.font, hint, 22, 64, 0xFF8A95A8, false);
            graphics.drawString(this.font, "ESC  Back", 22, this.height - 28, 0xFF707B8E, false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return page == Page.PAUSE;
    }
}
'''

hooks = r'''package arena.client.shell;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoShellHooks {
    private GgoShellHooks() {}

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getNewScreen() instanceof GgoShellScreen) return;
        if (event.getNewScreen() instanceof InventoryScreen) {
            event.setNewScreen(new GgoShellScreen(GgoShellScreen.Page.INVENTORY));
        } else if (event.getNewScreen() instanceof PauseScreen) {
            event.setNewScreen(new GgoShellScreen(GgoShellScreen.Page.PAUSE));
        }
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        if (mc.screen != null) return;

        if (event.getKey() == GLFW.GLFW_KEY_M) {
            mc.setScreen(new GgoShellScreen(GgoShellScreen.Page.ACTIVITIES));
        } else if (event.getKey() == GLFW.GLFW_KEY_N) {
            mc.setScreen(new GgoShellScreen(GgoShellScreen.Page.MAP));
        }
    }

    @SubscribeEvent
    public static void onPlayerListOverlay(RenderGuiOverlayEvent.Pre event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.PLAYER_LIST.id())) return;
        event.setCanceled(true);

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.options.keyPlayerList.isDown()) return;

        GuiGraphics g = event.getGuiGraphics();
        int width = event.getWindow().getGuiScaledWidth();
        int boxW = Math.min(360, width - 32);
        int x = (width - boxW) / 2;
        int y = 14;
        g.fill(x, y, x + boxW, y + 58, 0xD90A0C11);
        g.fill(x, y, x + 3, y + 58, 0xFFD34855);
        g.drawString(mc.font, "GGO // SQUAD", x + 12, y + 10, 0xFFF5F6F8, false);
        g.drawString(mc.font, mc.player.getGameProfile().getName(), x + 12, y + 27, 0xFFB9C3D1, false);
        g.drawString(mc.font, "Match / squad / ping overlay — Stage 2", x + 12, y + 42, 0xFF667389, false);
    }
}
'''

(JAVA / "GgoShellScreen.java").write_text(screen)
(JAVA / "GgoShellHooks.java").write_text(hooks)

print("GGO UI Shell Stage 1 applied:")
print(" - E / vanilla inventory -> GGO Inventory shell")
print(" - M -> GGO Activities shell")
print(" - N -> GGO Navigation shell")
print(" - ESC / vanilla pause -> GGO Pause shell")
print(" - TAB vanilla player list suppressed -> GGO squad placeholder")
