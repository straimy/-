package arena.forge;

import arena.GunnerArenaMod;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
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
    private static final int FIELD_FIRST=18,FIELD_LAST=35;
    private static final Map<UUID,PendingDeath> PENDING=new HashMap<>();
    private GgoRecoveryBagService(){}

    /** Runs late so a death canceled by another game mechanic never creates a bag. */
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void death(LivingDeathEvent event){
        if(!(event.getEntity() instanceof ServerPlayer p))return;
        ArenaRuntime r=GunnerArenaMod.RUNTIME;if(r==null||!r.auth().isAuthenticated(p))return;
        ProtectedLoadout protectedLoadout=ProtectedLoadout.capture(p);
        ListTag contents=new ListTag();int stacks=0,items=0;
        for(int i=FIELD_FIRST;i<=FIELD_LAST;i++){
            ItemStack s=p.getInventory().getItem(i);if(s.isEmpty())continue;
            CompoundTag entry=new CompoundTag();s.save(entry);contents.add(entry);stacks++;items+=s.getCount();p.getInventory().setItem(i,ItemStack.EMPTY);
        }
        ItemStack bag=ItemStack.EMPTY;
        if(!contents.isEmpty()){
            bag=new ItemStack(Items.BUNDLE);
            bag.setHoverName(Component.literal("RECOVERY BAG // "+p.getGameProfile().getName()).withStyle(ChatFormatting.GOLD));
            CompoundTag tag=bag.getOrCreateTag();tag.putBoolean(BAG_TAG,true);tag.putBoolean("ggo_keep_vanilla",true);tag.putUUID(OWNER_TAG,p.getUUID());tag.put(CONTENTS_TAG,contents);tag.putInt("GgoRecoveryStacks",stacks);tag.putInt("GgoRecoveryItems",items);tag.putLong("GgoRecoveryCreatedTick",r.serverTick());
            ItemEntity entity=new ItemEntity(p.serverLevel(),p.getX(),p.getY()+0.25,p.getZ(),bag.copy());
            entity.setPickUpDelay(20);p.serverLevel().addFreshEntity(entity);
        }
        p.getInventory().setChanged();
        PENDING.put(p.getUUID(),new PendingDeath(protectedLoadout,bag));
    }

    /** Suppress Minecraft's loose death pile; the GGO bag was spawned explicitly above. */
    @SubscribeEvent public static void drops(LivingDropsEvent event){
        if(!(event.getEntity() instanceof ServerPlayer p))return;
        if(PENDING.containsKey(p.getUUID()))event.getDrops().clear();
    }

    /** New player entity receives the protected combat loadout after a real death clone. */
    @SubscribeEvent public static void clone(PlayerEvent.Clone event){
        if(!event.isWasDeath()||!(event.getEntity() instanceof ServerPlayer next))return;
        PendingDeath pending=PENDING.remove(event.getOriginal().getUUID());if(pending==null)return;
        pending.loadout.restore(next);next.getInventory().setChanged();
    }

    /**
     * A carrier cannot inspect another player's bag. The owner may reclaim it; recovery is atomic
     * per stack and field-capacity safe. Any overflow stays serialized inside the same bag.
     */
    @SubscribeEvent public static void use(PlayerInteractEvent.RightClickItem event){
        ItemStack bag=event.getItemStack();if(!isRecoveryBag(bag))return;
        event.setCanceled(true);event.setCancellationResult(InteractionResult.FAIL);
        if(!(event.getEntity() instanceof ServerPlayer p))return;
        UUID owner=owner(bag);
        if(owner==null||!owner.equals(p.getUUID())){
            p.displayClientMessage(Component.literal("SEALED RECOVERY BAG // ONLY THE OWNER CAN OPEN IT").withStyle(ChatFormatting.GOLD),true);
            return;
        }
        int restored=recoverOwnerContents(p,bag);
        if(restored<=0){
            p.displayClientMessage(Component.literal("RECOVERY BAG // FIELD INVENTORY FULL").withStyle(ChatFormatting.YELLOW),true);
            return;
        }
        p.getInventory().setChanged();
        if(contents(bag).isEmpty()){
            bag.shrink(1);
            p.displayClientMessage(Component.literal("RECOVERY COMPLETE // +"+restored+" ITEMS").withStyle(ChatFormatting.GREEN),true);
        }else{
            refreshCounts(bag);
            p.displayClientMessage(Component.literal("PARTIAL RECOVERY // +"+restored+" ITEMS // BAG STILL CONTAINS LOOT").withStyle(ChatFormatting.YELLOW),true);
        }
    }

    private static int recoverOwnerContents(ServerPlayer p,ItemStack bag){
        ListTag source=contents(bag);if(source.isEmpty())return 0;
        ListTag remaining=new ListTag();int restored=0;
        for(int i=0;i<source.size();i++){
            CompoundTag raw=source.getCompound(i).copy();ItemStack stack=ItemStack.of(raw);if(stack.isEmpty())continue;
            int before=stack.getCount();storeField(p,stack);restored+=before-stack.getCount();
            if(!stack.isEmpty()){CompoundTag left=new CompoundTag();stack.save(left);remaining.add(left);}
        }
        bag.getOrCreateTag().put(CONTENTS_TAG,remaining);
        return restored;
    }

    private static void storeField(ServerPlayer p,ItemStack stack){
        for(int i=FIELD_FIRST;i<=FIELD_LAST&&!stack.isEmpty();i++){
            ItemStack dst=p.getInventory().getItem(i);if(dst.isEmpty()||!ItemStack.isSameItemSameTags(dst,stack))continue;
            int room=dst.getMaxStackSize()-dst.getCount();int move=Math.min(room,stack.getCount());if(move>0){dst.grow(move);stack.shrink(move);}
        }
        for(int i=FIELD_FIRST;i<=FIELD_LAST&&!stack.isEmpty();i++)if(p.getInventory().getItem(i).isEmpty()){
            int move=Math.min(stack.getCount(),stack.getMaxStackSize());ItemStack part=stack.copy();part.setCount(move);p.getInventory().setItem(i,part);stack.shrink(move);
        }
    }

    private static ListTag contents(ItemStack bag){
        CompoundTag tag=bag.getTag();return tag!=null&&tag.contains(CONTENTS_TAG,Tag.TAG_LIST)?tag.getList(CONTENTS_TAG,Tag.TAG_COMPOUND):new ListTag();
    }
    private static void refreshCounts(ItemStack bag){
        ListTag list=contents(bag);int items=0;for(int i=0;i<list.size();i++)items+=Math.max(0,ItemStack.of(list.getCompound(i)).getCount());
        CompoundTag tag=bag.getOrCreateTag();tag.putInt("GgoRecoveryStacks",list.size());tag.putInt("GgoRecoveryItems",items);
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
