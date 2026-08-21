from pathlib import Path

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
JAVA = ROOT / "client-ui/src/main/java/arena/client/shell"
JAVA.mkdir(parents=True, exist_ok=True)

adapter = r'''package arena.client.shell;

import java.lang.reflect.Method;
import java.util.function.Consumer;

public final class GgoRuntimeV1HudAdapter {
    private static volatile boolean installed;
    private static volatile boolean attempted;
    private static volatile GgoObjectiveState.Snapshot latest = GgoObjectiveState.Snapshot.empty();
    private static Method request;
    private static long lastRequest;

    private GgoRuntimeV1HudAdapter() {}

    public static void install() {
        if (installed || attempted) return;
        attempted = true;
        try {
            Class<?> cls = Class.forName("arena.forge.GgoHudNetwork");
            request = cls.getMethod("request");
            Method consumer = cls.getMethod("setClientConsumer", Consumer.class);
            consumer.invoke(null, (Consumer<Object>) GgoRuntimeV1HudAdapter::receive);
            GgoObjectiveState.installProvider(() -> latest);
            installed = true;
        } catch (ReflectiveOperationException | LinkageError ignored) {}
    }

    public static void tick() {
        install();
        if (!installed || request == null) return;
        long now = System.currentTimeMillis();
        if (now - lastRequest < 1000L) return;
        lastRequest = now;
        try { request.invoke(null); } catch (ReflectiveOperationException ignored) {}
    }

    private static void receive(Object packet) {
        try {
            latest = new GgoObjectiveState.Snapshot(
                    str(packet, "title"), str(packet, "description"), str(packet, "progress"), str(packet, "activity"), bool(packet, "available")
            );
        } catch (ReflectiveOperationException ignored) {}
    }

    private static Object value(Object target, String accessor) throws ReflectiveOperationException { return target.getClass().getMethod(accessor).invoke(target); }
    private static String str(Object o, String n) throws ReflectiveOperationException { Object v=value(o,n); return v==null?"":String.valueOf(v); }
    private static boolean bool(Object o, String n) throws ReflectiveOperationException { return Boolean.TRUE.equals(value(o,n)); }
}
'''
(JAVA / "GgoRuntimeV1HudAdapter.java").write_text(adapter)

controller = JAVA / "GgoPingWheelController.java"
if controller.exists():
    s = controller.read_text()
    anchor = '        GgoRuntimeV1NetworkAdapter.install();\n'
    if anchor in s and 'GgoRuntimeV1HudAdapter.tick();' not in s:
        s = s.replace(anchor, anchor + '        GgoRuntimeV1HudAdapter.tick();\n', 1)
    controller.write_text(s)

print("GGO Server HUD Stage 20 applied")
print(" - server-driven activity/objective snapshot")
print(" - HUD polls at most once per second")
print(" - OPEN WORLD / BR queue / countdown / running / finished")
print(" - Training/offline keeps safe empty fallback")
