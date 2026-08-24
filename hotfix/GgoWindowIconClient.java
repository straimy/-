package arena.client.shell;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

/** Keeps first-party GGO native window branding after Minecraft/Forge title rewrites. */
@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoWindowIconClient {
    private static boolean iconAttempted;
    private static long lastTitleUpdate;

    private GgoWindowIconClient() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null || mc.getWindow().getWindow() == 0L) return;
        long handle = mc.getWindow().getWindow();
        long now = System.currentTimeMillis();

        // Minecraft and Forge rewrite the native title when entering a world/server.
        // Re-assert GGO periodically without touching mapped Window methods.
        if (now - lastTitleUpdate >= 1000L) {
            lastTitleUpdate = now;
            try {
                GLFW.glfwSetWindowTitle(handle, "GunGloryOnline");
            } catch (RuntimeException ignored) {
                // Branding must remain fail-open.
            }
        }

        if (!iconAttempted) {
            iconAttempted = true;
            installIcon(handle);
        }
    }

    private static void installIcon(long windowHandle) {
        try (InputStream stream = GgoWindowIconClient.class.getResourceAsStream("/assets/ggo/icon.png")) {
            if (stream == null) return;
            byte[] encoded = stream.readAllBytes();
            ByteBuffer input = BufferUtils.createByteBuffer(encoded.length);
            input.put(encoded).flip();

            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer width = stack.mallocInt(1);
                IntBuffer height = stack.mallocInt(1);
                IntBuffer channels = stack.mallocInt(1);
                ByteBuffer pixels = STBImage.stbi_load_from_memory(input, width, height, channels, 4);
                if (pixels == null) return;
                try (GLFWImage.Buffer icons = GLFWImage.malloc(1)) {
                    icons.position(0).width(width.get(0)).height(height.get(0)).pixels(pixels);
                    icons.position(0);
                    GLFW.glfwSetWindowIcon(windowHandle, icons);
                } finally {
                    STBImage.stbi_image_free(pixels);
                }
            }
        } catch (Exception ignored) {
            // Branding failure must never prevent the game from starting.
        }
    }
}
