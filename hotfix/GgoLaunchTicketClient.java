package arena.client.shell;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * Reads the short-lived launcher ticket from this game's child-process environment and forwards it once.
 * Reflection keeps the UI module compilable independently while Core owns the authenticated network channel.
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoLaunchTicketClient {
    private static final long HANDSHAKE_SAFETY_MILLIS = TimeUnit.SECONDS.toMillis(15);
    private static String ticket = readTicket();
    private static final boolean OFFICIAL_LAUNCH = ticket != null;
    private static final long TICKET_EXPIRES_AT_MILLIS = readExpiryMillis();
    private static boolean sent;
    private static int retryTicks;

    private GgoLaunchTicketClient() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || sent || ticket == null) return;
        if (retryTicks > 0) {
            retryTicks--;
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getConnection() == null) return;
        try {
            Class<?> network = Class.forName("arena.forge.GgoLaunchTicketNetwork");
            Method send = network.getMethod("sendTicket", String.class);
            send.invoke(null, ticket);
            sent = true;
            // Reduce the lifetime of the sensitive value inside the Java heap after the packet is encoded.
            ticket = null;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            retryTicks = 20;
        }
    }

    /** True only for launcher-originated online sessions that still await the server bind acknowledgement. */
    public static boolean verificationPending() {
        if (!OFFICIAL_LAUNCH) return false;
        try {
            Class<?> network = Class.forName("arena.forge.GgoLaunchTicketNetwork");
            Method expected = network.getMethod("isClientVerificationExpected");
            Method complete = network.getMethod("isClientVerificationComplete");
            boolean coreExpected = Boolean.TRUE.equals(expected.invoke(null));
            boolean coreComplete = Boolean.TRUE.equals(complete.invoke(null));
            return !coreComplete && (sent || coreExpected);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Fail visually closed after the packet was sent; the server quarantine remains authoritative.
            return sent;
        }
    }

    public static boolean isOfficialLaunch() {
        return OFFICIAL_LAUNCH;
    }

    /**
     * Whether this process still has a one-shot credential fresh enough to begin a new official connection.
     * A safety margin is reserved for DNS + Forge handshake + server-side consume.
     */
    public static boolean canStartOnline() {
        return OFFICIAL_LAUNCH
            && !sent
            && ticket != null
            && System.currentTimeMillis() + HANDSHAKE_SAFETY_MILLIS < TICKET_EXPIRES_AT_MILLIS;
    }

    public static long menuSecondsRemaining() {
        if (!OFFICIAL_LAUNCH || sent || ticket == null) return 0L;
        long remaining = TICKET_EXPIRES_AT_MILLIS - System.currentTimeMillis();
        return Math.max(0L, TimeUnit.MILLISECONDS.toSeconds(remaining));
    }

    private static String readTicket() {
        String value = System.getenv("GGO_GAME_TICKET");
        if (value == null) return null;
        value = value.trim();
        return value.isEmpty() || value.length() > 256 ? null : value;
    }

    private static long readExpiryMillis() {
        if (!OFFICIAL_LAUNCH) return 0L;
        String raw = System.getenv("GGO_GAME_TICKET_EXPIRES_AT");
        if (raw != null) {
            try {
                long seconds = Long.parseLong(raw.trim());
                if (seconds > 0L && seconds <= Long.MAX_VALUE / 1000L) return seconds * 1000L;
            } catch (NumberFormatException ignored) {
                // Fall through to the conservative compatibility window.
            }
        }
        // Compatibility with pre-Stage76 launchers. New beta packages always provide absolute expiry.
        return System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(150);
    }
}
