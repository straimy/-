package arena.forge;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Command-block-free pickups for the recovered Classic Arena.
 *
 * The old map used three scoreboard-driven gun slots and dynamically rewrote command blocks for
 * each selected weapon. V2 resolves the gun directly from hotbar slots 0/1/2 and applies the exact
 * recovered reserve caps, pickup amounts and respawn times in Java.
 */
@Mod.EventBusSubscriber(modid = "gunnerarena", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClassicArenaPickupService {
    public static final String VERSION = "GGO-CLASSIC-PICKUPS-V2";

    private static final double PICKUP_RADIUS = 1.3D;
    private static final long HEALTH_COOLDOWN_TICKS = 16L * 20L;
    private static final AABB ARENA = new AABB(47.0D, 68.0D, 47.0D, 113.0D, 105.0D, 113.0D);
    private static final Map<UUID, Long> READY_AT = new HashMap<>();

    private record WeaponMeta(String ammoId, int reserveCapIncludingMagazine, int pickupAmount, int respawnSeconds, String soundId, float pitch) {}

    /** Exact values recovered from the legacy gun-display command graph. */
    private static final Map<String, WeaponMeta> CLASSIC_WEAPONS = Map.of(
        "jeg:semi_auto_pistol", new WeaponMeta("jeg:pistol_ammo", 60, 20, 11, "jeg:s1queence.custom.smg_bullets_pickup", 1.0F),
        "jeg:primitive_bow", new WeaponMeta("minecraft:arrow", 30, 10, 16, "jeg:s1queence.custom.arrow_pickup", 1.0F),
        "jeg:bolt_action_rifle", new WeaponMeta("jeg:rifle_ammo", 30, 10, 16, "jeg:s1queence.custom.smg_bullets_pickup", 0.6F),
        "jeg:double_barrel_shotgun", new WeaponMeta("jeg:shotgun_shell", 15, 5, 11, "jeg:s1queence.custom.shell_pickup", 1.0F),
        "jeg:pump_shotgun", new WeaponMeta("jeg:shotgun_shell", 30, 10, 13, "jeg:s1queence.custom.shell_pickup", 1.0F),
        "jeg:custom_smg", new WeaponMeta("jeg:pistol_ammo", 180, 60, 11, "jeg:s1queence.custom.smg_bullets_pickup", 0.6F)
    );

    private ClassicArenaPickupService() {}

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer() == null) return;
        long now = event.getServer().getTickCount();
        if ((now & 3L) != 0L) return;

        for (ServerLevel level : event.getServer().getAllLevels()) {
            tickHealth(level, now);
            tickAmmo(level, now);
        }

        if ((now % 200L) == 0L && !READY_AT.isEmpty()) {
            READY_AT.entrySet().removeIf(entry -> now - entry.getValue() > 20L * 60L * 5L);
        }
    }

    private static void tickHealth(ServerLevel level, long now) {
        List<Marker> markers = level.getEntities(EntityType.MARKER, ARENA, ClassicArenaPickupService::isHealthMarker);
        for (Marker marker : markers) {
            if (!ready(marker, now)) continue;
            ServerPlayer player = nearestEligible(level, marker, p -> p.getHealth() < p.getMaxHealth());
            if (player == null) continue;

            player.heal(Math.min(4.0F, player.getMaxHealth() - player.getHealth()));
            play(level, marker, "jeg:s1queence.custom.health_pickup", 0.7F, 1.0F);
            READY_AT.put(marker.getUUID(), now + HEALTH_COOLDOWN_TICKS);
        }
    }

    private static void tickAmmo(ServerLevel level, long now) {
        List<Marker> markers = level.getEntities(EntityType.MARKER, ARENA, ClassicArenaPickupService::isAmmoMarker);
        for (Marker marker : markers) {
            if (!ready(marker, now)) continue;
            int slot = slotFor(marker);
            if (slot < 0) continue;

            ServerPlayer player = nearestEligible(level, marker, p -> canTakeAmmo(p, slot));
            if (player == null) continue;

            ItemStack weapon = player.getInventory().getItem(slot);
            WeaponMeta meta = metaFor(weapon);
            if (meta == null) continue;
            Item ammo = registryItem(meta.ammoId());
            if (ammo == null) continue;

            int current = countItem(player, ammo) + magazineAmmo(weapon);
            int amount = Math.min(meta.pickupAmount(), Math.max(0, meta.reserveCapIncludingMagazine() - current));
            if (amount <= 0) continue;

            ItemStack added = new ItemStack(ammo, amount);
            player.getInventory().add(added);
            int accepted = amount - added.getCount();
            if (accepted <= 0) continue;

            play(level, marker, meta.soundId(), 0.6F, meta.pitch());
            READY_AT.put(marker.getUUID(), now + meta.respawnSeconds() * 20L);
        }
    }

    private static boolean canTakeAmmo(ServerPlayer player, int slot) {
        ItemStack weapon = player.getInventory().getItem(slot);
        WeaponMeta meta = metaFor(weapon);
        if (meta == null) return false;
        Item ammo = registryItem(meta.ammoId());
        if (ammo == null) return false;
        return countItem(player, ammo) + magazineAmmo(weapon) < meta.reserveCapIncludingMagazine();
    }

    private static ServerPlayer nearestEligible(ServerLevel level, Marker marker, java.util.function.Predicate<ServerPlayer> extra) {
        AABB pickup = marker.getBoundingBox().inflate(PICKUP_RADIUS);
        ServerPlayer best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, pickup, p ->
            p.isAlive() && ClassicArenaMatchService.isParticipant(p) && extra.test(p))) {
            double distance = player.distanceToSqr(marker);
            if (distance < bestDistance) {
                best = player;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static WeaponMeta metaFor(ItemStack weapon) {
        if (weapon.isEmpty()) return null;
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(weapon.getItem());
        return key == null ? null : CLASSIC_WEAPONS.get(key.toString());
    }

    private static int magazineAmmo(ItemStack weapon) {
        if (!weapon.hasTag() || weapon.getTag() == null) return 0;
        return Math.max(0, weapon.getTag().getInt("AmmoCount"));
    }

    private static int countItem(ServerPlayer player, Item item) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items) if (stack.is(item)) total += stack.getCount();
        for (ItemStack stack : player.getInventory().offhand) if (stack.is(item)) total += stack.getCount();
        return total;
    }

    private static Item registryItem(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) return null;
        return ForgeRegistries.ITEMS.getValue(key);
    }

    private static boolean ready(Marker marker, long now) {
        return READY_AT.getOrDefault(marker.getUUID(), 0L) <= now;
    }

    private static boolean isHealthMarker(Marker marker) {
        Set<String> tags = marker.getTags();
        return tags.contains("small_health_orb") || tags.contains("health_orb");
    }

    private static boolean isAmmoMarker(Marker marker) {
        Set<String> tags = marker.getTags();
        return tags.contains("gun_1_ammo") || tags.contains("gun_2_ammo") || tags.contains("gun_3_ammo");
    }

    private static int slotFor(Marker marker) {
        Set<String> tags = marker.getTags();
        if (tags.contains("gun_1_ammo")) return 0;
        if (tags.contains("gun_2_ammo")) return 1;
        if (tags.contains("gun_3_ammo")) return 2;
        return -1;
    }

    private static void play(ServerLevel level, Marker marker, String soundId, float volume, float pitch) {
        ResourceLocation id = ResourceLocation.tryParse(soundId);
        SoundEvent sound = id == null ? null : ForgeRegistries.SOUND_EVENTS.getValue(id);
        if (sound != null) level.playSound(null, marker.blockPosition(), sound, SoundSource.PLAYERS, volume, pitch);
    }
}
