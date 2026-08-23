package arena.forge;

import arena.GunnerArenaMod;
import arena.weapon.WeaponDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REPORT ONLY weapon-state validator for the beta anti-cheat.
 *
 * The server never trusts client-side firearm NBT as proof of a valid weapon state.
 * This detector only records high-confidence impossible states and deliberately does
 * not kick, ban, mutate inventory, or expose detector thresholds to the client.
 */
@Mod.EventBusSubscriber(modid = "gunnerarena", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoWeaponStateAntiCheat {
    private static final String AMMO = "AmmoCount";
    private static final String IGNORE_AMMO = "IgnoreAmmo";
    private static final int SAMPLE_EVERY_TICKS = 4;
    private static final int JOIN_GRACE_TICKS = 20 * 5;
    private static final Map<UUID, Integer> JOIN_TICK = new ConcurrentHashMap<>();

    private GgoWeaponStateAntiCheat() {}

    @SubscribeEvent
    public static void join(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            JOIN_TICK.put(player.getUUID(), player.tickCount);
        }
    }

    @SubscribeEvent
    public static void leave(PlayerEvent.PlayerLoggedOutEvent event) {
        JOIN_TICK.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || event.player.level().isClientSide
                || !(event.player instanceof ServerPlayer player)
                || player.tickCount % SAMPLE_EVERY_TICKS != 0) {
            return;
        }
        if (!GgoOfficialAuthState.required() || !GgoOfficialAuthState.isAuthenticated(player)) return;
        int joinedAt = JOIN_TICK.getOrDefault(player.getUUID(), player.tickCount);
        if (player.tickCount - joinedAt < JOIN_GRACE_TICKS) return;
        if (GunnerArenaMod.RUNTIME == null) return;

        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            inspect(player, slot, player.getInventory().getItem(slot));
        }
        inspect(player, -1, player.getOffhandItem());
    }

    private static void inspect(ServerPlayer player, int slot, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key == null || !"jeg".equals(key.getNamespace())) return;
        String path = key.getPath();
        if (path.contains("knife") || path.contains("melee")) return;
        if (!stack.hasTag()) return;

        var tag = stack.getTag();
        if (tag == null) return;

        if (tag.getBoolean(IGNORE_AMMO)) {
            report(player, "WEAPON_IGNORE_AMMO", 18.0D, key, slot,
                    "forbidden ammo bypass flag present");
        }

        WeaponDefinition def = GunnerArenaMod.RUNTIME.weapons().get(key.toString());
        if (def == null || !tag.contains(AMMO)) return;
        int ammo = tag.getInt(AMMO);
        int capacity = Math.max(1, def.magazineSize());

        if (ammo < 0) {
            report(player, "WEAPON_NEGATIVE_AMMO", 12.0D, key, slot,
                    "ammo below zero");
        } else if (ammo > capacity) {
            report(player, "WEAPON_MAGAZINE_OVERFLOW", 18.0D, key, slot,
                    "ammo exceeds server magazine capacity");
        }
    }

    private static void report(ServerPlayer player, String type, double weight,
                               ResourceLocation weapon, int slot, String reason) {
        GgoAntiCheatEvidence.record(player, type, weight,
                "weapon=" + weapon + ";slot=" + slot + ";reason=" + reason);
    }
}
