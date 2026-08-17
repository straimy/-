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

/** Server-owned Classic Arena three-gun loadout and round cleanup. */
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
        for (int slot = 0; slot < 3; slot++) {
            Item item = item(loadout.get(slot));
            if (item == null) return false;
            player.getInventory().setItem(slot, new ItemStack(item));
        }
        player.getInventory().selected = 0;
        player.getInventory().setChanged();
        return true;
    }

    /**
     * Removes only Classic-owned guns from the three combat slots. It intentionally does not clear
     * the entire inventory so profile/cosmetic/server items cannot be destroyed by round cleanup.
     */
    public static void cleanupPlayer(ServerPlayer player) {
        for (int slot = 0; slot < 3; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (isClassicWeapon(stack)) player.getInventory().setItem(slot, ItemStack.EMPTY);
        }
        player.getInventory().setChanged();
    }

    public static synchronized void finishRound() {
        selected = List.of();
    }

    public static List<String> selectedWeapons() { return selected; }

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
