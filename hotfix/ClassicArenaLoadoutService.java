package arena.forge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Server-owned Classic Arena three-gun loadout.
 *
 * Replaces #selected_gun_1_id/#selected_gun_2_id/#selected_gun_3_id, item-display copying and
 * command-block hotbar replacement. The recovered legacy catalog contains exactly six JEG weapons;
 * every round chooses three distinct entries and installs them into slots 0/1/2.
 */
public final class ClassicArenaLoadoutService {
    public static final String VERSION = "GGO-CLASSIC-LOADOUT-V1";

    private static final List<String> CATALOG = List.of(
        "jeg:semi_auto_pistol",
        "jeg:primitive_bow",
        "jeg:bolt_action_rifle",
        "jeg:double_barrel_shotgun",
        "jeg:pump_shotgun",
        "jeg:custom_smg"
    );

    private static volatile List<String> selected = List.of();

    private ClassicArenaLoadoutService() {}

    public static synchronized List<String> selectRound(ServerLevel level) {
        List<String> pool = new ArrayList<>(CATALOG);
        // Fisher-Yates with the world RNG keeps the selection server-authoritative and reproducible
        // inside the running world without relying on scoreboard increment loops.
        for (int i = pool.size() - 1; i > 0; i--) {
            int j = level.getRandom().nextInt(i + 1);
            Collections.swap(pool, i, j);
        }
        selected = List.copyOf(pool.subList(0, 3));
        return selected;
    }

    public static synchronized boolean prepareRound(ServerLevel level, List<ServerPlayer> players) {
        List<String> loadout = selectRound(level);
        for (ServerPlayer player : players) {
            if (!give(player, loadout)) return false;
        }
        return true;
    }

    private static boolean give(ServerPlayer player, List<String> loadout) {
        // Classic owns the combat hotbar during the round. Do not clear armor/profile/cosmetic state.
        for (int slot = 0; slot < 3; slot++) {
            Item item = item(loadout.get(slot));
            if (item == null) return false;
            player.getInventory().setItem(slot, new ItemStack(item));
        }
        player.getInventory().selected = 0;
        player.getInventory().setChanged();
        return true;
    }

    public static List<String> selectedWeapons() {
        return selected;
    }

    public static String selectedWeapon(int slot) {
        List<String> current = selected;
        return slot >= 0 && slot < current.size() ? current.get(slot) : "";
    }

    public static boolean isClassicWeapon(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key != null && CATALOG.contains(key.toString());
    }

    private static Item item(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key == null ? null : ForgeRegistries.ITEMS.getValue(key);
    }
}
