package arena.client.shell;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Child-side half of the launcher <-> GGO unified-surface contract.
 *
 * The launcher intentionally stays visible while Forge performs early bootstrap. Only after a
 * first-party GGO screen is actually alive do we atomically publish the one-shot ready flag. This
 * prevents users from being left staring at the raw engine bootstrap and lets the launcher hide at
 * the exact hand-off point. No credential is written to disk.
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoUnifiedSurfaceReady {
    private static final String READY_ENV = "GGO_READY_FILE";
    private static final String FULLSCREEN_ENV = "GGO_FULLSCREEN_AFTER_READY";
    private static boolean completed;
    private static int stableTicks;

    private GgoUnifiedSurfaceReady() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || completed) return;
        if (!GgoLaunchTicketClient.isOfficialLaunch()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null || mc.getWindow().getWindow() == 0L) return;
        if (!isFirstPartySurface(mc)) {
            stableTicks = 0;
            return;
        }

        // Require a few consecutive ticks so the window has finished its first layout/render pass.
        if (++stableTicks < 3) return;

        String raw = System.getenv(READY_ENV);
        if (raw == null || raw.isBlank()) {
            // Compatibility: absence of the optional hand-off contract must never block the game.
            completed = true;
            applyDeferredFullscreen(mc);
            return;
        }

        try {
            Path path = Path.of(raw).toAbsolutePath().normalize();
            String name = path.getFileName() == null ? "" : path.getFileName().toString();
            if (!name.startsWith("ggo-ready-") || !name.endsWith(".flag")) {
                completed = true;
                return;
            }
            Files.writeString(
                path,
                "ready\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            );
            completed = true;
            applyDeferredFullscreen(mc);
        } catch (IOException | RuntimeException ignored) {
            // Retry on later ticks. A compositor/filesystem hiccup must not crash the GGO client.
            stableTicks = 2;
        }
    }

    private static boolean isFirstPartySurface(Minecraft mc) {
        return mc.screen instanceof GgoFrontEndScreen
            || mc.screen instanceof GgoShellScreen
            || mc.screen instanceof GgoSettingsScreen
            || mc.screen instanceof GgoTrainingScreen
            || mc.screen instanceof GgoEntryDisconnectedScreen;
    }

    private static void applyDeferredFullscreen(Minecraft mc) {
        if (!"1".equals(System.getenv(FULLSCREEN_ENV))) return;
        try {
            if (!mc.getWindow().isFullscreen()) mc.getWindow().toggleFullScreen();
        } catch (RuntimeException ignored) {
            // Window-mode preference is cosmetic and must remain fail-open.
        }
    }
}
