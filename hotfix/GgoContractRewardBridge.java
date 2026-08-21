package arena.forge;

import arena.GunnerArenaMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;

/** Runtime-v1 adapter for persistent GGO contract rewards. */
public final class GgoContractRewardBridge {
    private GgoContractRewardBridge() {}

    /**
     * Credits are a GGO domain value. Runtime v1 currently stores player economy
     * inside the legacy profile object, so this adapter prefers a credits field
     * when present and falls back to crystals until the account economy backend
     * replaces the legacy profile storage.
     */
    public static boolean award(ServerPlayer player, int credits) {
        if (player == null || credits <= 0) return false;
        try {
            var runtime = GunnerArenaMod.RUNTIME;
            if (runtime == null) return false;
            Object profile = runtime.players().profile(player);
            if (profile == null) return false;

            Field field = field(profile.getClass(), "credits");
            if (field == null) field = field(profile.getClass(), "crystals");
            if (field == null) return false;
            field.setAccessible(true);

            if (field.getType() == int.class || field.getType() == Integer.class) {
                int old = ((Number) field.get(profile)).intValue();
                field.set(profile, Math.max(0, old + credits));
            } else if (field.getType() == long.class || field.getType() == Long.class) {
                long old = ((Number) field.get(profile)).longValue();
                field.set(profile, Math.max(0L, old + credits));
            } else {
                return false;
            }

            runtime.profiles().markDirty(player.getUUID());
            player.sendSystemMessage(Component.literal("GGO • CONTRACT COMPLETE  +" + credits + " CREDITS")
                    .withStyle(ChatFormatting.GOLD));
            player.displayClientMessage(Component.literal("CONTRACT COMPLETE  •  +" + credits + " CREDITS")
                    .withStyle(ChatFormatting.GREEN), true);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private static Field field(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try { return current.getDeclaredField(name); }
            catch (NoSuchFieldException ignored) { current = current.getSuperclass(); }
        }
        return null;
    }
}
