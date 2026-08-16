package arena.forge;

import arena.GunnerArenaMod;
import arena.weapon.WeaponDefinition;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Server hardening for JEG firearms. Arena melee/knife items bypass all ammo logic. */
@Mod.EventBusSubscriber(modid="gunnerarena",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class JegArenaAmmoGuard {
    private static final String INIT="GGOJEGAmmoInitialized",AMMO="AmmoCount",IGNORE="IgnoreAmmo";
    private JegArenaAmmoGuard(){}

    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e){
        if(e.phase!=TickEvent.Phase.END||e.player.level().isClientSide||!(e.player instanceof ServerPlayer p)||(p.tickCount&3)!=0)return;
        var runtime=GunnerArenaMod.RUNTIME;if(runtime==null||!runtime.auth().isAuthenticated(p))return;
        for(int i=0;i<p.getInventory().getContainerSize();i++)sanitize(p,p.getInventory().getItem(i));sanitize(p,p.getOffhandItem());
    }

    private static void sanitize(ServerPlayer p,ItemStack stack){
        if(stack==null||stack.isEmpty()||isArenaKnife(stack))return;
        ResourceLocation key=ForgeRegistries.ITEMS.getKey(stack.getItem());
        if(key==null||!"jeg".equals(key.getNamespace())||key.getPath().contains("knife")||key.getPath().contains("melee"))return;
        var tag=stack.getOrCreateTag();if(tag.contains(IGNORE))tag.remove(IGNORE);
        var runtime=GunnerArenaMod.RUNTIME;if(runtime==null)return;WeaponDefinition def=runtime.weapons().get(key.toString());
        if(def==null){if(tag.contains(AMMO)&&tag.getInt(AMMO)<0)tag.putInt(AMMO,0);return;}
        int cap=Math.max(1,def.magazineSize());
        if(!tag.getBoolean(INIT)){int existing=tag.contains(AMMO)?tag.getInt(AMMO):cap;tag.putInt(AMMO,Math.max(0,Math.min(cap,existing)));tag.putBoolean(INIT,true);}
        int ammo=tag.getInt(AMMO);if(ammo<0){ammo=0;tag.putInt(AMMO,0);}else if(ammo>cap){ammo=cap;tag.putInt(AMMO,cap);}
        int reserve=countAmmo(p,def.ammoItem());if(reserve<=0&&ammo<cap&&isReloading(tag)){clearReload(tag);p.displayClientMessage(Component.literal("✦ НЕТ ПАТРОНОВ").withStyle(ChatFormatting.RED),true);}
    }
    private static boolean isArenaKnife(ItemStack s){return s.hasTag()&&s.getTag().getBoolean("GunnerArenaKnife");}
    private static boolean isReloading(net.minecraft.nbt.CompoundTag tag){return tag.getBoolean("Reloading")||tag.contains("ReloadEnd")||tag.contains("ReloadTimer");}
    private static void clearReload(net.minecraft.nbt.CompoundTag tag){tag.remove("Reloading");tag.remove("ReloadEnd");tag.remove("ReloadTimer");tag.remove("ReloadProgress");}
    private static int countAmmo(ServerPlayer p,String ammoId){if(ammoId==null||ammoId.isBlank())return 0;ResourceLocation id=ResourceLocation.tryParse(ammoId);if(id==null)return 0;Item item=ForgeRegistries.ITEMS.getValue(id);if(item==null)return 0;int n=0;for(ItemStack s:p.getInventory().items)if(s.is(item))n+=s.getCount();for(ItemStack s:p.getInventory().offhand)if(s.is(item))n+=s.getCount();return n;}
}
