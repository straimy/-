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
 * Permanent GunGloryOnline cosmetics backed by JEG's native paint-job system.
 *
 * JEG 0.13.2 uses the NBT key "Paint_Job" for its animated gun paint-job
 * renderer and also honours the regular integer "Color" value used by
 * IColored.  We set both: Paint_Job gives a real alternate gun texture where
 * JEG ships one, while Color is a safe visual fallback on guns without that
 * particular paint-job texture.
 */
@Mod.EventBusSubscriber(modid = "gunnerarena", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CosmeticSkinRuntime {
    private static final String SKIN_TAG = "GunGlorySkin";
    private static final String APPLIED_TAG = "GunGlorySkinApplied";
    private static final String COLOR_TAG = "Color";
    private static final String PAINT_TAG = "Paint_Job";
    private static final String PREV_COLOR = "GunGloryPrevColor";
    private static final String PREV_PAINT = "GunGloryPrevPaintJob";
    private static final String HAD_COLOR = "GunGloryHadColor";
    private static final String HAD_PAINT = "GunGloryHadPaintJob";

    private record Visual(int color, String nativePaint) {}

    /*
     * The names are GunGloryOnline cosmetics. Their first implementation uses
     * JEG's own proven paint textures so the skins are genuinely visible now.
     * They can later be swapped to bespoke GGO textures without changing
     * ownership/profile data.
     */
    private static final Map<String, Visual> VISUALS = Map.of(
        "neon_pulse", new Visual(0x55DFFF, "toy"),
        "crimson_grid", new Visual(0xD83B5B, "bedrock"),
        "void_ice", new Visual(0x9CB9FF, "whiteout")
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

    /** Re-applies the selected permanent skin to newly bought/respawned guns. */
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
        Visual visual = VISUALS.get(id);

        if (visual == null) {
            restoreOriginal(tag);
            return;
        }

        // Save the player's original JEG dye/paint once, before GGO takes over.
        if (!tag.getBoolean(APPLIED_TAG)) {
            boolean hadColor = tag.contains(COLOR_TAG, 99);
            boolean hadPaint = tag.contains(PAINT_TAG, 8);
            tag.putBoolean(HAD_COLOR, hadColor);
            tag.putBoolean(HAD_PAINT, hadPaint);
            if (hadColor) tag.putInt(PREV_COLOR, tag.getInt(COLOR_TAG));
            if (hadPaint) tag.putString(PREV_PAINT, tag.getString(PAINT_TAG));
        }

        tag.putInt(COLOR_TAG, visual.color());
        tag.putString(PAINT_TAG, visual.nativePaint());
        tag.putString(SKIN_TAG, id);
        tag.putBoolean(APPLIED_TAG, true);
    }

    private static void restoreOriginal(net.minecraft.nbt.CompoundTag tag) {
        if (!tag.getBoolean(APPLIED_TAG)) return;

        if (tag.getBoolean(HAD_COLOR)) tag.putInt(COLOR_TAG, tag.getInt(PREV_COLOR));
        else tag.remove(COLOR_TAG);

        if (tag.getBoolean(HAD_PAINT)) tag.putString(PAINT_TAG, tag.getString(PREV_PAINT));
        else tag.remove(PAINT_TAG);

        tag.remove(SKIN_TAG);
        tag.remove(APPLIED_TAG);
        tag.remove(PREV_COLOR);
        tag.remove(PREV_PAINT);
        tag.remove(HAD_COLOR);
        tag.remove(HAD_PAINT);
    }

    public static int colorFor(String skinId) {
        if (skinId == null) return -1;
        Visual visual = VISUALS.get(skinId.toLowerCase());
        return visual == null ? -1 : visual.color();
    }

    public static String nativePaintFor(String skinId) {
        if (skinId == null) return "";
        Visual visual = VISUALS.get(skinId.toLowerCase());
        return visual == null ? "" : visual.nativePaint();
    }
}
