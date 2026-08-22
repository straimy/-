package arena.forge;

import arena.GunnerArenaMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
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
        normalizeOffhand(p);
        normalizeAmmoSlots(p);
        packCombatIntoThreeSlots(p);
        if(p.getInventory().selected>=COMBAT_SLOTS)p.getInventory().selected=0;
    }

    private static void removePhysicalMenuCompasses(ServerPlayer p){
        for(int i=0;i<p.getInventory().getContainerSize();i++){ItemStack s=p.getInventory().getItem(i);if(s!=null&&!s.isEmpty()&&s.is(Items.COMPASS))p.getInventory().setItem(i,ItemStack.EMPTY);}
        if(p.getOffhandItem()!=null&&!p.getOffhandItem().isEmpty()&&p.getOffhandItem().is(Items.COMPASS))p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
    }

    private static void normalizeOffhand(ServerPlayer p){
        ItemStack off=p.getOffhandItem();if(off==null||off.isEmpty())return;
        if(isAmmo(off)){
            ItemStack work=off.copy();
            storeAmmo(p,work);
            if(work.isEmpty())p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
            else p.setItemInHand(InteractionHand.OFF_HAND,work);
            return;
        }
        if(isGgoOrLegacyCombat(off)){
            int combat=firstFreeCombat(p);
            if(combat>=0){p.getInventory().setItem(combat,off.copy());p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);p.getInventory().setChanged();return;}
        }
        int field=firstHiddenFree(p);
        if(field>=0){p.getInventory().setItem(field,off.copy());p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);p.getInventory().setChanged();}
    }

    /** Pull every ammo stack into the dedicated E-only ammo row, merging first. */
    public static void normalizeAmmoSlots(ServerPlayer p){
        for(int i=0;i<p.getInventory().getContainerSize();i++){
            if(i>=AMMO_FIRST&&i<=AMMO_LAST)continue;
            ItemStack s=p.getInventory().getItem(i);if(s==null||s.isEmpty()||!isAmmo(s))continue;
            moveAmmoIntoReserved(p,i,s);
        }
        for(int i=AMMO_FIRST;i<=AMMO_LAST;i++){
            ItemStack s=p.getInventory().getItem(i);if(s.isEmpty()||isAmmo(s))continue;
            int dst=firstHiddenFree(p);if(dst>=0){p.getInventory().setItem(dst,s);p.getInventory().setItem(i,ItemStack.EMPTY);}
        }
        p.getInventory().setChanged();
    }

    private static void moveAmmoIntoReserved(ServerPlayer p,int source,ItemStack stack){
        storeAmmo(p,stack);
        if(stack.isEmpty())p.getInventory().setItem(source,ItemStack.EMPTY);
    }

    private static void storeAmmo(ServerPlayer p,ItemStack stack){
        for(int i=AMMO_FIRST;i<=AMMO_LAST&&!stack.isEmpty();i++){
            ItemStack d=p.getInventory().getItem(i);if(d.isEmpty()||!ItemStack.isSameItemSameTags(d,stack))continue;
            int n=Math.min(stack.getCount(),d.getMaxStackSize()-d.getCount());if(n>0){d.grow(n);stack.shrink(n);}
        }
        for(int i=AMMO_FIRST;i<=AMMO_LAST&&!stack.isEmpty();i++)if(p.getInventory().getItem(i).isEmpty()){
            int n=Math.min(stack.getCount(),stack.getMaxStackSize());ItemStack part=stack.copy();part.setCount(n);p.getInventory().setItem(i,part);stack.shrink(n);
        }
        p.getInventory().setChanged();
    }

    private static void packCombatIntoThreeSlots(ServerPlayer p){
        for(int i=COMBAT_SLOTS;i<p.getInventory().getContainerSize();i++){
            if(i>=AMMO_FIRST&&i<=AMMO_LAST)continue;
            ItemStack s=p.getInventory().getItem(i);if(s==null||s.isEmpty()||isAmmo(s)||!isGgoOrLegacyCombat(s))continue;
            int free=firstFreeCombat(p);
            if(free>=0){p.getInventory().setItem(free,s);p.getInventory().setItem(i,ItemStack.EMPTY);continue;}
            // Extra weapons are field inventory, never silently deleted.
            if(i>=COMBAT_SLOTS&&i<9){int dst=firstHiddenFree(p);if(dst>=0){p.getInventory().setItem(dst,s);p.getInventory().setItem(i,ItemStack.EMPTY);}}
        }
        // Slots 3..8 do not exist in the GGO UX. Preserve non-empty contents by moving them to field storage.
        for(int i=COMBAT_SLOTS;i<9;i++){
            ItemStack s=p.getInventory().getItem(i);if(s.isEmpty())continue;
            int dst=firstHiddenFree(p);if(dst>=0){p.getInventory().setItem(dst,s);p.getInventory().setItem(i,ItemStack.EMPTY);}
        }
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
