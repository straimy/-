package arena.forge;

import arena.GunnerArenaMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Three combat slots plus nine dedicated ammo slots in normal GGO play. */
@Mod.EventBusSubscriber(modid="gunnerarena",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class ArenaBeltGuard {
    private static final int COMBAT_SLOTS=3;
    public static final int AMMO_FIRST=9,AMMO_LAST=17;
    private ArenaBeltGuard(){}

    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e){
        if(e.phase!=TickEvent.Phase.END||e.player.level().isClientSide||!(e.player instanceof ServerPlayer p)||(p.tickCount%2)!=0)return;
        ArenaRuntime r=GunnerArenaMod.RUNTIME;if(r==null||!r.auth().isAuthenticated(p))return;
        removePhysicalMenuCompasses(p);
        if(p.hasPermissions(2)&&(p.gameMode.getGameModeForPlayer()==GameType.CREATIVE||p.gameMode.getGameModeForPlayer()==GameType.SPECTATOR))return;
        normalizeAmmoSlots(p);
        packCombatIntoThreeSlots(p);
        if(p.getInventory().selected>=COMBAT_SLOTS)p.getInventory().selected=0;
    }

    private static void removePhysicalMenuCompasses(ServerPlayer p){
        for(int i=0;i<p.getInventory().getContainerSize();i++){ItemStack s=p.getInventory().getItem(i);if(s!=null&&!s.isEmpty()&&s.is(Items.COMPASS))p.getInventory().setItem(i,ItemStack.EMPTY);}
        if(p.getOffhandItem()!=null&&!p.getOffhandItem().isEmpty()&&p.getOffhandItem().is(Items.COMPASS))p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,ItemStack.EMPTY);
    }

    /** Pull every ammo stack into the dedicated E-only ammo row, merging first. */
    public static void normalizeAmmoSlots(ServerPlayer p){
        for(int i=0;i<p.getInventory().getContainerSize();i++){
            if(i>=AMMO_FIRST&&i<=AMMO_LAST)continue;
            ItemStack s=p.getInventory().getItem(i);if(s==null||s.isEmpty()||!isAmmo(s))continue;
            moveAmmoIntoReserved(p,i,s);
        }
        // Reserved cells accept ammo only. Anything else is moved to a hidden normal inventory cell, never deleted.
        for(int i=AMMO_FIRST;i<=AMMO_LAST;i++){
            ItemStack s=p.getInventory().getItem(i);if(s.isEmpty()||isAmmo(s))continue;
            int dst=firstHiddenFree(p);if(dst>=0){p.getInventory().setItem(dst,s);p.getInventory().setItem(i,ItemStack.EMPTY);}
        }
        p.getInventory().setChanged();
    }

    private static void moveAmmoIntoReserved(ServerPlayer p,int source,ItemStack stack){
        for(int i=AMMO_FIRST;i<=AMMO_LAST&&!stack.isEmpty();i++){
            ItemStack d=p.getInventory().getItem(i);if(d.isEmpty()||!ItemStack.isSameItemSameTags(d,stack))continue;
            int n=Math.min(stack.getCount(),d.getMaxStackSize()-d.getCount());if(n>0){d.grow(n);stack.shrink(n);}
        }
        for(int i=AMMO_FIRST;i<=AMMO_LAST&&!stack.isEmpty();i++)if(p.getInventory().getItem(i).isEmpty()){p.getInventory().setItem(i,stack.copy());stack.setCount(0);}
        if(stack.isEmpty())p.getInventory().setItem(source,ItemStack.EMPTY);
    }

    private static void packCombatIntoThreeSlots(ServerPlayer p){
        for(int i=COMBAT_SLOTS;i<p.getInventory().getContainerSize();i++){
            if(i>=AMMO_FIRST&&i<=AMMO_LAST)continue;
            ItemStack s=p.getInventory().getItem(i);if(s==null||s.isEmpty()||isAmmo(s))continue;
            if(!isGgoOrLegacyCombat(s))continue;
            int free=firstFreeCombat(p);if(free>=0){p.getInventory().setItem(free,s);p.getInventory().setItem(i,ItemStack.EMPTY);}else p.getInventory().setItem(i,ItemStack.EMPTY);
        }
        for(int i=COMBAT_SLOTS;i<9;i++)if(!p.getInventory().getItem(i).isEmpty())p.getInventory().setItem(i,ItemStack.EMPTY);
        p.getInventory().setChanged();
    }
    private static int firstFreeCombat(ServerPlayer p){for(int i=0;i<COMBAT_SLOTS;i++)if(p.getInventory().getItem(i).isEmpty())return i;return -1;}
    private static int firstHiddenFree(ServerPlayer p){for(int i=18;i<36;i++)if(p.getInventory().getItem(i).isEmpty())return i;return -1;}

    public static boolean isAmmo(ItemStack s){
        if(s==null||s.isEmpty())return false;ResourceLocation id=ForgeRegistries.ITEMS.getKey(s.getItem());if(id==null)return false;
        String ns=id.getNamespace(),p=id.getPath().toLowerCase(java.util.Locale.ROOT);
        if(!("jeg".equals(ns)||"jeg_cfg".equals(ns)||"gunnerarena".equals(ns)))return false;
        return p.contains("ammo")||p.contains("bullet")||p.contains("shell")||p.contains("cartridge")||p.contains("round");
    }
    private static boolean isGgoOrLegacyCombat(ItemStack s){
        if(s==null||s.isEmpty())return false;if(s.hasTag()&&(s.getTag().getBoolean("GunnerArenaBound")||s.getTag().getBoolean("GunnerArenaKnife")||s.getTag().getBoolean("GunGloryBotWeapon")))return true;
        ResourceLocation id=ForgeRegistries.ITEMS.getKey(s.getItem());return id!=null&&("jeg".equals(id.getNamespace())||"jeg_cfg".equals(id.getNamespace()));
    }
}
