package arena.forge;

import arena.GunnerArenaMod;
import arena.forge.player.ArenaPlayerState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * GunGlory combat belt: slots 1-5 are the active combat bar.
 * In battle the menu item disappears completely. In lobby/safe spawn slot 6 is a protected menu token.
 * M remains the fast menu key, so the token is discoverability rather than a required Minecraft mechanic.
 */
@Mod.EventBusSubscriber(modid="gunnerarena",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class ArenaBeltGuard {
    private static final int COMBAT_SLOTS=5;
    private static final int MENU_SLOT=5;
    private static final String COMPASS_TAG="gunnerarena_menu_compass";
    private ArenaBeltGuard(){}

    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e){
        if(e.phase!=TickEvent.Phase.END||e.player.level().isClientSide||!(e.player instanceof ServerPlayer p)||(p.tickCount%2)!=0)return;
        ArenaRuntime r=GunnerArenaMod.RUNTIME;if(r==null||!r.auth().isAuthenticated(p))return;
        boolean alive=r.players().session(p).state()== ArenaPlayerState.ALIVE;
        if(alive){removeMenuTokens(p);packCombatItems(p);}
        else if(r.safeRegions().isSafe(p)){protectMenuSlot(p);}
    }

    private static void removeMenuTokens(ServerPlayer p){
        for(int i=0;i<9;i++)if(isMenuCompass(p.getInventory().getItem(i)))p.getInventory().setItem(i,ItemStack.EMPTY);
    }
    private static void packCombatItems(ServerPlayer p){
        for(int i=COMBAT_SLOTS;i<9;i++){
            ItemStack s=p.getInventory().getItem(i);if(!isCombat(s))continue;
            int free=firstFreeCombat(p);if(free<0)continue;
            p.getInventory().setItem(free,s);p.getInventory().setItem(i,ItemStack.EMPTY);
        }
    }
    private static int firstFreeCombat(ServerPlayer p){for(int i=0;i<COMBAT_SLOTS;i++)if(p.getInventory().getItem(i).isEmpty())return i;return -1;}
    private static void protectMenuSlot(ServerPlayer p){
        for(int i=0;i<9;i++)if(i!=MENU_SLOT&&isMenuCompass(p.getInventory().getItem(i)))p.getInventory().setItem(i,ItemStack.EMPTY);
        ItemStack current=p.getInventory().getItem(MENU_SLOT);
        if(!current.isEmpty()&&!isMenuCompass(current)){
            int dest=firstFreeOutsideHotbar(p);if(dest>=0)p.getInventory().setItem(dest,current);
        }
        if(!isMenuCompass(p.getInventory().getItem(MENU_SLOT))){
            ItemStack c=new ItemStack(Items.COMPASS);c.getOrCreateTag().putBoolean(COMPASS_TAG,true);c.setHoverName(Component.literal("✦ МЕНЮ  •  M"));p.getInventory().setItem(MENU_SLOT,c);
        }
    }
    private static int firstFreeOutsideHotbar(ServerPlayer p){for(int i=9;i<p.getInventory().getContainerSize();i++)if(p.getInventory().getItem(i).isEmpty())return i;return -1;}
    private static boolean isMenuCompass(ItemStack s){return s!=null&&!s.isEmpty()&&s.is(Items.COMPASS)&&s.hasTag()&&s.getTag().getBoolean(COMPASS_TAG);}
    private static boolean isCombat(ItemStack s){
        if(s==null||s.isEmpty())return false;if(s.hasTag()&&(s.getTag().getBoolean("GunnerArenaBound")||s.getTag().getBoolean("GunnerArenaKnife")))return true;
        ResourceLocation id=ForgeRegistries.ITEMS.getKey(s.getItem());return id!=null&&"jeg".equals(id.getNamespace());
    }
}
