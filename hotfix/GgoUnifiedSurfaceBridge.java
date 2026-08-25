package arena.client.shell;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * One-way readiness bridge from the internal Java/Forge engine to the first-party GGO launcher.
 *
 * The launcher owns the visible startup surface. It keeps its Tauri window in front while the
 * engine initializes and passes a private local marker path through GGO_READY_FILE. Once a real
 * GGO surface exists, this class writes the literal word "ready" exactly once. No ticket, account
 * identifier or other credential is ever written to disk.
 *
 * Fullscreen is deliberately deferred until this point. Starting Forge directly in fullscreen
 * lets its unavoidable early window cover the launcher before GGO code exists. Stage107 starts
 * the engine windowed behind the launcher, then applies the user's fullscreen preference here.
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoUnifiedSurfaceBridge {
    private static final Path READY_FILE = readReadyFile();
    private static final boolean FULLSCREEN_AFTER_READY = "1".equals(System.getenv("GGO_FULLSCREEN_AFTER_READY"));
    private static boolean signaled;
    private static boolean fullscreenApplied;
    private static int stableTicks;

    private GgoUnifiedSurfaceBridge() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || signaled || READY_FILE == null) return;
        if (!GgoLaunchTicketClient.isOfficialLaunch()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null || mc.getWindow().getWindow() == 0L || mc.font == null) return;

        boolean frontendReady = mc.screen instanceof GgoFrontEndScreen;
        boolean worldReady = mc.player != null && mc.level != null && mc.getConnection() != null;
        if (!frontendReady && !worldReady) {
            stableTicks = 0;
            return;
        }

        // Apply fullscreen only after a first-party GGO surface exists. Give GLFW a few ticks to
        // settle before telling the launcher it is safe to reveal the engine window.
        if (FULLSCREEN_AFTER_READY && !fullscreenApplied) {
            fullscreenApplied = true;
            if (!mc.getWindow().isFullscreen()) {
                mc.getWindow().toggleFullScreen();
            }
            stableTicks = 0;
            return;
        }

        if (++stableTicks < 8) return;

        try {
            Path parent = READY_FILE.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(
                READY_FILE,
                "ready\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            );
            signaled = true;
        } catch (Exception ignored) {
            // Fail visually closed: launcher remains visible instead of exposing early engine UI.
            stableTicks = 0;
        }
    }

    private static Path readReadyFile() {
        String raw = System.getenv("GGO_READY_FILE");
        if (raw == null || raw.isBlank() || raw.length() > 4096) return null;
        try {
            return Path.of(raw).toAbsolutePath().normalize();
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
