package arena.client.shell;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Softens the first audible GGO music after launcher handoff.
 *
 * This never persists the temporary values to options.txt: the player's configured MUSIC volume
 * remains the source of truth. The fade is official-launch-only and finishes after roughly eight
 * seconds, so the OST rises from silence instead of appearing at full volume on the first frame.
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoMusicFadeClient {
    private static final int FADE_TICKS = 20 * 8;
    private static boolean initialized;
    private static boolean complete;
    private static double targetVolume;
    private static int ticks;

    private GgoMusicFadeClient() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || complete) return;
        if (!GgoLaunchTicketClient.isOfficialLaunch()) {
            complete = true;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return;

        var music = mc.options.getSoundSourceOptionInstance(SoundSource.MUSIC);
        if (!initialized) {
            targetVolume = clamp(music.get());
            if (targetVolume <= 0.0001D) {
                complete = true;
                return;
            }
            music.set(0.0D);
            initialized = true;
            ticks = 0;
            return;
        }

        // Smoothstep makes both the start and the finish gentle instead of linear/abrupt.
        ticks++;
        double t = Math.min(1.0D, ticks / (double) FADE_TICKS);
        double eased = t * t * (3.0D - 2.0D * t);
        music.set(targetVolume * eased);
        if (t >= 1.0D) {
            music.set(targetVolume);
            complete = true;
        }
    }

    private static double clamp(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
