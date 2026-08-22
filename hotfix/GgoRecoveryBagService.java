package arena.forge;

import arena.GunnerArenaMod;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Extraction-style death loss: equipped combat gear, ammo pouch and armor are retained;
 * FIELD ITEMS 18..35 become one sealed owner-bound recovery bag.
 */
@Mod.EventBusSubscriber(modid="gunnerarena",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoRecoveryBagService {
    public static final String BAG_TAG="GgoRecoveryBag";
    public static final String OWNER_TAG="GgoRecoveryOwner";
    public static final String CONTENTS_TAG="GgoRecoveryContents";
    private static final Map<UUID,PendingDeath> PENDING=new HashMap<>();
    private GgoRecoveryBagService(){}

    @SubscribeEvent public static void death(LivingDeathEvent event){
        if(!(event.getEntity() instanceof ServerPlayer p))return;
        ArenaRuntime r=GunnerArenaMod.RUNTIME;if(r==null||!r.auth().isAuthenticated(p))return;
        ProtectedLoadout protectedLoadout=ProtectedLoadout.capture(p);
        ListTag contents=new ListTag();int stacks=0,items=0;
        for(int i=18;i<=35;i++){
            ItemStack s=p.getInventory().getItem(i);if(s.isEmpty())continue;
            CompoundTag entry=new CompoundTag();s.save(entry);contents.add(entry);stacks++;items+=s.getCount();p.getInventory().setItem(i,ItemStack.EMPTY);
        }
        ItemStack bag=ItemStack.EMPTY;
        if(!contents.isEmpty()){
            bag=new ItemStack(Items.BUNDLE);
            bag.setHoverName(Component.literal("RECOVERY BAG // "+p.getGameProfile().getName()).withStyle(ChatFormatting.GOLD));
            CompoundTag tag=bag.getOrCreateTag();tag.putBoolean(BAG_TAG,true);tag.putBoolean("ggo_keep_vanilla",true);tag.putUUID(OWNER_TAG,p.getUUID());tag.put(CONTENTS_TAG,contents);tag.putInt("GgoRecoveryStacks",stacks);tag.putInt("GgoRecoveryItems",items);tag.putLong("GgoRecoveryCreatedTick",r.serverTick());
        }
        p.getInventory().setChanged();
        PENDING.put(p.getUUID(),new PendingDeath(protectedLoadout,bag));
    }

    /** Suppress Minecraft's loose death pile and substitute at most one sealed GGO bag. */
    @SubscribeEvent public static void drops(PlayerDropsEvent event){
        if(!(event.getEntity() instanceof ServerPlayer p))return;
        PendingDeath pending=PENDING.get(p.getUUID());if(pending==null)return;
        event.getDrops().clear();
        if(!pending.bag.isEmpty()){
            ItemEntity entity=new ItemEntity(p.serverLevel(),p.getX(),p.getY()+0.25,p.getZ(),pending.bag.copy());
            entity.setPickUpDelay(20);event.getDrops().add(entity);
        }
    }

    /** New player entity receives the protected combat loadout after a real death clone. */
    @SubscribeEvent public static void clone(PlayerEvent.Clone event){
        if(!event.isWasDeath()||!(event.getEntity() instanceof ServerPlayer next))return;
        PendingDeath pending=PENDING.remove(event.getOriginal().getUUID());if(pending==null)return;
        pending.loadout.restore(next);next.getInventory().setChanged();
    }

    /** Recovery bags are sealed: neither owner nor carrier can invoke vanilla bundle/item behavior. */
    @SubscribeEvent public static void use(PlayerInteractEvent.RightClickItem event){
        ItemStack stack=event.getItemStack();if(!isRecoveryBag(stack))return;
        event.setCanceled(true);event.setCancellationResult(InteractionResult.FAIL);
        if(event.getEntity() instanceof ServerPlayer p){
            UUID owner=owner(stack);String who=owner!=null&&owner.equals(p.getUUID())?"YOUR RECOVERY BAG":"SEALED RECOVERY BAG";
            p.displayClientMessage(Component.literal(who+" // RETURN MARKET COMING LATER").withStyle(ChatFormatting.GOLD),true);
        }
    }

    public static boolean isRecoveryBag(ItemStack stack){return stack!=null&&!stack.isEmpty()&&stack.hasTag()&&stack.getTag().getBoolean(BAG_TAG)&&stack.getTag().hasUUID(OWNER_TAG);}
    public static UUID owner(ItemStack stack){return isRecoveryBag(stack)?stack.getTag().getUUID(OWNER_TAG):null;}

    private record PendingDeath(ProtectedLoadout loadout,ItemStack bag){}
    private record ProtectedLoadout(ItemStack[] combat,ItemStack[] ammo,ItemStack[] armor,ItemStack offhand){
        static ProtectedLoadout capture(ServerPlayer p){
            ItemStack[] combat=new ItemStack[3];for(int i=0;i<3;i++)combat[i]=p.getInventory().getItem(i).copy();
            ItemStack[] ammo=new ItemStack[9];for(int i=0;i<9;i++)ammo[i]=p.getInventory().getItem(9+i).copy();
            ItemStack[] armor=new ItemStack[p.getInventory().armor.size()];for(int i=0;i<armor.length;i++)armor[i]=p.getInventory().armor.get(i).copy();
            ItemStack off=p.getInventory().offhand.isEmpty()?ItemStack.EMPTY:p.getInventory().offhand.get(0).copy();
            return new ProtectedLoadout(combat,ammo,armor,off);
        }
        void restore(ServerPlayer p){
            for(int i=0;i<3;i++)p.getInventory().setItem(i,combat[i].copy());
            for(int i=0;i<9;i++)p.getInventory().setItem(9+i,ammo[i].copy());
            for(int i=0;i<Math.min(armor.length,p.getInventory().armor.size());i++)p.getInventory().armor.set(i,armor[i].copy());
            if(!p.getInventory().offhand.isEmpty())p.getInventory().offhand.set(0,offhand.copy());
        }
    }
}
