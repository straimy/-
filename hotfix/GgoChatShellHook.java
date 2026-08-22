package arena.client.shell;

import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;

/** Routes T and slash chat entry into GGO chrome while preserving ChatScreen transport semantics. */
@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoChatShellHook {
    private GgoChatShellHook() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onOpening(ScreenEvent.Opening event) {
        var screen = event.getNewScreen();
        if (!(screen instanceof ChatScreen chat) || screen instanceof GgoChatScreen) return;
        event.setNewScreen(new GgoChatScreen(readInitial(chat)));
    }

    /**
     * ChatScreen has two instance String fields in 1.20.1: history buffer and original input.
     * Before init, the history buffer is empty, so selecting the non-empty value preserves the
     * slash-command key without relying on an obfuscated field name. Empty T-chat remains empty.
     */
    private static String readInitial(ChatScreen screen) {
        try {
            for (Field field : ChatScreen.class.getDeclaredFields()) {
                if (field.getType() != String.class || java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                Object value = field.get(screen);
                if (value instanceof String text && !text.isEmpty()) return text;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Fail visually safe: T-chat still opens and works with an empty initial value.
        }
        return "";
    }
}
