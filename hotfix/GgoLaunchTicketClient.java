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
    // Backend tickets live for 180 seconds. Stop offering a new connection before that hard edge so
    // Forge startup / DNS / handshake latency cannot turn an obviously stale menu session into a confusing kick.
    private static final long MENU_TICKET_WINDOW_NANOS = TimeUnit.SECONDS.toNanos(150);
    private static final long PROCESS_TICKET_START_NANOS = System.nanoTime();
    private static String ticket = readTicket();
    private static final boolean OFFICIAL_LAUNCH = ticket != null;
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
     * Once sent, or once the conservative menu window expires, a fresh launcher session is required.
     */
    public static boolean canStartOnline() {
        return OFFICIAL_LAUNCH
            && !sent
            && ticket != null
            && System.nanoTime() - PROCESS_TICKET_START_NANOS < MENU_TICKET_WINDOW_NANOS;
    }

    public static long menuSecondsRemaining() {
        if (!canStartOnline()) return 0L;
        long remaining = MENU_TICKET_WINDOW_NANOS - (System.nanoTime() - PROCESS_TICKET_START_NANOS);
        return Math.max(0L, TimeUnit.NANOSECONDS.toSeconds(remaining));
    }

    private static String readTicket() {
        String value = System.getenv("GGO_GAME_TICKET");
        if (value == null) return null;
        value = value.trim();
        return value.isEmpty() || value.length() > 256 ? null : value;
    }
}
