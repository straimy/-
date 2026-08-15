package arena.forge;

import arena.GunnerArenaMod;
import arena.profile.PlayerProfile;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;

/**
 * Applies owned GunGloryOnline cosmetics using JEG's own IColored contract.
 * JEG 0.13.2 renders the integer NBT key "Color" through DyeUtils, so this
 * produces a real visible gun tint instead of a UI-only marker.
 */
@Mod.EventBusSubscriber(modid = "gunnerarena", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CosmeticSkinRuntime {
    private static final String SKIN_TAG = "GunGlorySkin";
    private static final String APPLIED_TAG = "GunGlorySkinApplied";
    private static final String COLOR_TAG = "Color";
    private static final Map<String,Integer> COLORS = Map.of(
        "neon_pulse", 0x55DFFF,
        "crimson_grid", 0xD83B5B,
        "void_ice", 0x9CB9FF
    );

    private CosmeticSkinRuntime() {}

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide || !(event.player instanceof ServerPlayer player)) return;
        if ((player.tickCount % 20) != 0) return;
        var runtime = GunnerArenaMod.RUNTIME;
        if (runtime == null || !runtime.auth().isAuthenticated(player)) return;
        PlayerProfile profile = runtime.players().profile(player);
        if (profile == null) return;
        applyAll(player, profile.equippedSkin);
    }

    public static void applyAll(ServerPlayer player, String skinId) {
        if (player == null) return;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            apply(player.getInventory().getItem(i), skinId);
        }
        apply(player.getOffhandItem(), skinId);
    }

    public static void apply(ItemStack stack, String skinId) {
        if (stack == null || stack.isEmpty()) return;
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key == null || !"jeg".equals(key.getNamespace())) return;

        var tag = stack.getOrCreateTag();
        String id = skinId == null ? "NONE" : skinId.toLowerCase();
        Integer color = COLORS.get(id);
        if (color == null) {
            // Only remove color if it was applied by GunGloryOnline. Never erase a player's manual JEG dye.
            if (tag.getBoolean(APPLIED_TAG)) {
                tag.remove(COLOR_TAG);
                tag.remove(APPLIED_TAG);
                tag.remove(SKIN_TAG);
            }
            return;
        }

        // Native JEG IColored#setColor is exactly an integer "Color" tag in 0.13.2.
        tag.putInt(COLOR_TAG, color);
        tag.putString(SKIN_TAG, id);
        tag.putBoolean(APPLIED_TAG, true);
    }

    public static int colorFor(String skinId) {
        if (skinId == null) return -1;
        return COLORS.getOrDefault(skinId.toLowerCase(), -1);
    }
}
