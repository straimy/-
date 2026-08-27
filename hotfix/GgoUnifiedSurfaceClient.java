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
 * Completes the launcher -> game unified-surface handshake.
 *
 * The launcher deliberately stays visible over Forge bootstrap. Once the Java client has reached a
 * first-party GGO surface this class writes a one-shot ready flag and the Tauri supervisor hides the
 * launcher. The signal is presentation-only: any filesystem failure is ignored and can never block
 * or crash the game.
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoUnifiedSurfaceClient {
    private static boolean signalled;
    private static int retryTicks;

    private GgoUnifiedSurfaceClient() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || signalled) return;
        if (retryTicks > 0) {
            retryTicks--;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        boolean firstPartySurface = mc.screen instanceof GgoFrontEndScreen
            || mc.screen instanceof GgoShellScreen
            || mc.screen instanceof GgoSettingsScreen
            || mc.screen instanceof GgoTrainingScreen
            || mc.player != null;
        if (!firstPartySurface) return;

        String raw = System.getenv("GGO_READY_FILE");
        if (raw == null || raw.isBlank() || raw.length() > 4096) {
            // Non-launcher/dev starts do not participate in the unified-surface protocol.
            signalled = true;
            return;
        }

        try {
            Path ready = Path.of(raw);
            Path parent = ready.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(
                ready,
                "ready\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            );
            signalled = true;
        } catch (Exception ignored) {
            // Best effort only. Keep launcher visible and retry later instead of affecting runtime.
            retryTicks = 20;
        }
    }
}
