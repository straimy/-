package arena.forge;

import arena.GunnerArenaMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Player-visible item policy for the de-Minecraft runtime.
 *
 * Modded items are allowed. Raw minecraft:* items are hidden unless they are explicitly used as a
 * GGO-tagged runtime object. This lets resource-pack proxy items keep working without allowing
 * accidental bread/dirt/tools/legacy menu items back into the product UX.
 */
@Mod.EventBusSubscriber(modid="gunnerarena",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoVisibleItemPolicy {
    public static final String KEEP_VANILLA_TAG="ggo_keep_vanilla";
    private GgoVisibleItemPolicy(){}

    public static boolean allowed(ItemStack stack){
        if(stack==null||stack.isEmpty())return true;
        ResourceLocation id=ForgeRegistries.ITEMS.getKey(stack.getItem());
        if(id==null||!"minecraft".equals(id.getNamespace()))return true;
        if(GgoSupplyExtractionService.isSupply(stack))return true;
        var tag=stack.getTag();
        return tag!=null&&(tag.getBoolean(KEEP_VANILLA_TAG)
            ||tag.getBoolean("GunnerArenaBound")
            ||tag.getBoolean("GunnerArenaKnife")
            ||tag.getBoolean("GunGloryBotWeapon"));
    }

    public static ItemStack markVanillaProxy(ItemStack stack){
        if(stack!=null&&!stack.isEmpty())stack.getOrCreateTag().putBoolean(KEEP_VANILLA_TAG,true);
        return stack;
    }

    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||event.player.level().isClientSide||!(event.player instanceof ServerPlayer p)||p.tickCount%20!=0)return;
        ArenaRuntime runtime=GunnerArenaMod.RUNTIME;
        if(runtime==null||!runtime.auth().isAuthenticated(p)||maintenance(p))return;
        boolean changed=false;
        for(int i=0;i<p.getInventory().items.size();i++){
            ItemStack stack=p.getInventory().items.get(i);
            if(allowed(stack))continue;
            p.getInventory().items.set(i,ItemStack.EMPTY);changed=true;
        }
        ItemStack offhand=p.getOffhandItem();
        if(!allowed(offhand)){p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);changed=true;}
        if(changed)p.getInventory().setChanged();
    }

    private static boolean maintenance(ServerPlayer p){
        return p.hasPermissions(2)&&(p.gameMode.getGameModeForPlayer().isCreative()||p.isSpectator());
    }
}
