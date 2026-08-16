package arena.forge;

import arena.GunnerArenaMod;
import arena.weapon.WeaponDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Keeps GunGloryOnline-owned JEG guns on JEG's native AmmoCount model.
 * New arena guns start with one full magazine, then JEG owns consumption/reload.
 * Legacy IgnoreAmmo flags are stripped so a shotgun can never become infinite.
 */
@Mod.EventBusSubscriber(modid="gunnerarena", bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class JegArenaAmmoGuard {
    private static final String BOUND="GunnerArenaBound";
    private static final String INIT="GGOJEGAmmoInitialized";
    private static final String AMMO="AmmoCount";
    private static final String IGNORE="IgnoreAmmo";
    private JegArenaAmmoGuard(){}

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e){
        if(e.phase!=TickEvent.Phase.END || e.player.level().isClientSide || !(e.player instanceof ServerPlayer p)) return;
        if((p.tickCount & 3)!=0) return;
        for(int i=0;i<p.getInventory().getContainerSize();i++) sanitize(p.getInventory().getItem(i));
        sanitize(p.getOffhandItem());
    }

    private static void sanitize(ItemStack stack){
        if(stack==null||stack.isEmpty()||!stack.hasTag()) return;
        var tag=stack.getOrCreateTag();
        if(!tag.getBoolean(BOUND)) return;
        ResourceLocation key=ForgeRegistries.ITEMS.getKey(stack.getItem());
        if(key==null||!"jeg".equals(key.getNamespace())) return;
        var runtime=GunnerArenaMod.RUNTIME;
        if(runtime==null) return;
        WeaponDefinition def=runtime.weapons().get(key.toString());
        if(def==null) return;

        // JEG treats IgnoreAmmo as an infinite-ammo escape hatch. Arena guns never need it.
        if(tag.contains(IGNORE)) tag.remove(IGNORE);
        int cap=Math.max(1,def.magazineSize());
        if(!tag.getBoolean(INIT)){
            tag.putInt(AMMO,cap);
            tag.putBoolean(INIT,true);
            return;
        }
        int ammo=tag.getInt(AMMO);
        if(ammo<0) tag.putInt(AMMO,0);
        else if(ammo>cap) tag.putInt(AMMO,cap);
    }
}
