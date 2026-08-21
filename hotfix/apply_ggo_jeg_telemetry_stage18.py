from pathlib import Path

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
JAVA = ROOT / "client-ui/src/main/java/arena/client/shell"
JAVA.mkdir(parents=True, exist_ok=True)

provider = r'''package arena.client.shell;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/** Runtime v1 JEG telemetry adapter using the same authoritative item state
 * already enforced by JegArenaAmmoGuard. No guessed ammo ids or capacities. */
public final class GgoJegWeaponTelemetryProvider {
    private static volatile boolean installed;

    private GgoJegWeaponTelemetryProvider() {}

    public static void install() {
        if (installed) return;
        GgoWeaponTelemetry.installProvider(GgoJegWeaponTelemetryProvider::snapshot);
        installed = true;
    }

    private static GgoWeaponTelemetry.Snapshot snapshot(Minecraft mc, ItemStack held) {
        if (mc == null || mc.player == null || held == null || held.isEmpty())
            return GgoWeaponTelemetry.Snapshot.unavailable(held);

        ResourceLocation itemKey = ForgeRegistries.ITEMS.getKey(held.getItem());
        if (itemKey == null) return GgoWeaponTelemetry.Snapshot.unavailable(held);

        Object definition = definition(itemKey.toString());
        if (definition == null) return GgoWeaponTelemetry.Snapshot.unavailable(held);

        var tag = held.getTag();
        if (tag == null || !tag.contains("AmmoCount")) return GgoWeaponTelemetry.Snapshot.unavailable(held);

        int magazine = Math.max(0, tag.getInt("AmmoCount"));
        String ammoId = stringAccessor(definition, "ammoItem");
        int reserve = countAmmo(mc, ammoId);
        boolean reloading = tag.getBoolean("Reloading") || tag.contains("ReloadEnd") || tag.contains("ReloadTimer");
        String fireMode = exactFireMode(definition);
        String weaponName = held.getHoverName().getString();
        return new GgoWeaponTelemetry.Snapshot(weaponName, magazine, reserve, fireMode, reloading, true);
    }

    private static Object definition(String itemId) {
        try {
            Class<?> mod = Class.forName("arena.GunnerArenaMod");
            Field runtimeField = mod.getField("RUNTIME");
            Object runtime = runtimeField.get(null);
            if (runtime == null) return null;
            Object weapons = runtime.getClass().getMethod("weapons").invoke(runtime);
            if (weapons == null) return null;
            return weapons.getClass().getMethod("get", String.class).invoke(weapons, itemId);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static String exactFireMode(Object definition) {
        try {
            Object raw = definition.getClass().getMethod("fireModes").invoke(definition);
            if (raw instanceof List<?> modes && modes.size() == 1 && modes.get(0) != null)
                return String.valueOf(modes.get(0));
        } catch (ReflectiveOperationException ignored) {}
        return "--";
    }

    private static String stringAccessor(Object target, String name) {
        try {
            Object value = target.getClass().getMethod(name).invoke(target);
            return value == null ? "" : String.valueOf(value);
        } catch (ReflectiveOperationException ignored) {
            return "";
        }
    }

    private static int countAmmo(Minecraft mc, String ammoId) {
        if (ammoId == null || ammoId.isBlank()) return 0;
        ResourceLocation key = ResourceLocation.tryParse(ammoId);
        if (key == null) return 0;
        Item ammo = ForgeRegistries.ITEMS.getValue(key);
        if (ammo == null) return 0;
        int count = 0;
        for (ItemStack stack : mc.player.getInventory().items) if (stack.is(ammo)) count += stack.getCount();
        for (ItemStack stack : mc.player.getInventory().offhand) if (stack.is(ammo)) count += stack.getCount();
        return count;
    }
}
'''

(JAVA / "GgoJegWeaponTelemetryProvider.java").write_text(provider)

controller = JAVA / "GgoPingWheelController.java"
if controller.exists():
    s = controller.read_text()
    anchor = "        GgoRuntimeV1NetworkAdapter.install();"
    if anchor in s and "GgoJegWeaponTelemetryProvider.install();" not in s:
        s = s.replace(anchor, anchor + "\n        GgoJegWeaponTelemetryProvider.install();", 1)
    controller.write_text(s)

print("GGO JEG Weapon Telemetry Stage 18 applied")
print(" - magazine: exact AmmoCount NBT")
print(" - reserve: exact WeaponDefinition ammoItem inventory count")
print(" - reload: same Reloading/ReloadEnd/ReloadTimer state as server guard")
print(" - fire mode: exact only when catalog has one allowed mode; no guessing for multi-mode guns")
