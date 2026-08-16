package arena.forge.loadout;

import arena.loadout.LoadoutManager;
import arena.forge.item.CustomWeaponAvailability;
import arena.profile.RoundSession;
import arena.weapon.WeaponCatalog;
import arena.weapon.WeaponDefinition;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Bridges the arena economy to real Minecraft/JEG ItemStacks. */
public final class ForgeLoadoutService {
    public static final int KNIFE_SLOT=0, SECONDARY_SLOT=1, PRIMARY_SLOT=2;
    private static final String KNIFE_TAG="GunnerArenaKnife", BOUND_TAG="GunnerArenaBound";
    private final WeaponCatalog catalog; private final LoadoutManager loadouts;
    public ForgeLoadoutService(WeaponCatalog catalog,LoadoutManager loadouts){this.catalog=catalog;this.loadouts=loadouts;}

    public void giveKnife(ServerPlayer player){ItemStack knife=new ItemStack(Items.IRON_SWORD);knife.setHoverName(Component.literal("Тактический нож").withStyle(ChatFormatting.AQUA));knife.getOrCreateTag().putBoolean("Unbreakable",true);knife.getOrCreateTag().putBoolean(KNIFE_TAG,true);knife.getOrCreateTag().putBoolean(BOUND_TAG,true);player.getInventory().setItem(KNIFE_SLOT,knife);}
    public boolean isArenaKnife(ItemStack s){return!s.isEmpty()&&s.hasTag()&&s.getTag().getBoolean(KNIFE_TAG);}
    public boolean isArenaBound(ItemStack s){return!s.isEmpty()&&s.hasTag()&&s.getTag().getBoolean(BOUND_TAG);}
    public boolean isAvailable(String id){WeaponDefinition d=catalog.get(id);return d!=null&&registeredItem(d.id())!=null&&CustomWeaponAvailability.isCombatReady(d.id());}

    public LoadoutManager.BuyResult buyAndGive(ServerPlayer player,RoundSession session,String weaponId){
        WeaponDefinition def=catalog.get(weaponId);if(def==null)return LoadoutManager.BuyResult.UNKNOWN_WEAPON;Item item=registeredItem(def.id());if(item==null)return LoadoutManager.BuyResult.UNAVAILABLE_ITEM;
        LoadoutManager.BuyResult result=loadouts.buy(session,weaponId);if(result!=LoadoutManager.BuyResult.OK)return result;
        ItemStack weapon=new ItemStack(item);prepareArenaWeapon(weapon,def);putWeapon(player,def,weapon);ensureAmmo(player,def.ammoItem(),def.startingReserve());return result;
    }

    public void restoreAfterRespawn(ServerPlayer player,RoundSession session){giveKnife(player);loadouts.onRespawn(session);restoreWeapon(player,session.secondaryWeapon());restoreWeapon(player,session.primaryWeapon());}
    public void clearCombatSlots(ServerPlayer player){for(ItemStack s:player.getInventory().items)if(isArenaBound(s))s.setCount(0);for(ItemStack s:player.getInventory().offhand)if(isArenaBound(s))s.setCount(0);player.getInventory().setItem(KNIFE_SLOT,ItemStack.EMPTY);player.getInventory().setItem(SECONDARY_SLOT,ItemStack.EMPTY);player.getInventory().setItem(PRIMARY_SLOT,ItemStack.EMPTY);clearManagedAmmo(player);}

    private void restoreWeapon(ServerPlayer player,String weaponId){if(weaponId==null||weaponId.isBlank())return;WeaponDefinition def=catalog.get(weaponId);if(def==null)return;Item item=registeredItem(def.id());if(item==null)return;ItemStack weapon=new ItemStack(item);prepareArenaWeapon(weapon,def);putWeapon(player,def,weapon);ensureAmmo(player,def.ammoItem(),def.startingReserve());}

    /**
     * JEG 0.13.2 stores the loaded magazine in AmmoCount and checks IgnoreAmmo for true infinite ammo.
     * Arena guns always start with one legitimate full magazine; reserve remains actual inventory ammo.
     * Native JEG owns all further R/manual/mag-fed reload handling.
     */
    private static void prepareArenaWeapon(ItemStack weapon,WeaponDefinition def){
        var tag=weapon.getOrCreateTag();tag.putBoolean(BOUND_TAG,true);
        ResourceLocation id=BuiltInRegistries.ITEM.getKey(weapon.getItem());
        if(id!=null&&"jeg".equals(id.getNamespace())){
            tag.remove("IgnoreAmmo");
            tag.remove("Reloading");tag.remove("ReloadEnd");tag.remove("ReloadTimer");
            tag.putInt("AmmoCount",Math.max(0,def.magazineSize()));
            tag.putString("GGOAmmoAudit","v0.4-beta");
        }
    }

    private void putWeapon(ServerPlayer p,WeaponDefinition d,ItemStack s){p.getInventory().setItem(d.slot()==WeaponDefinition.Slot.PRIMARY?PRIMARY_SLOT:SECONDARY_SLOT,s);}
    private void ensureAmmo(ServerPlayer p,String ammoId,int minimum){if(ammoId==null||ammoId.isBlank()||minimum<=0)return;Item ammo=registeredItem(ammoId);if(ammo==null)return;int missing=minimum-countItem(p,ammo);while(missing>0){int n=Math.min(missing,ammo.getMaxStackSize());ItemStack s=new ItemStack(ammo,n);p.getInventory().add(s);if(!s.isEmpty())break;missing-=n;}}
    private void clearManagedAmmo(ServerPlayer p){java.util.Set<Item>managed=new java.util.HashSet<>();for(WeaponDefinition d:catalog.all()){if(d.ammoItem()==null||d.ammoItem().isBlank())continue;Item i=registeredItem(d.ammoItem());if(i!=null)managed.add(i);}for(ItemStack s:p.getInventory().items)if(managed.contains(s.getItem()))s.setCount(0);for(ItemStack s:p.getInventory().offhand)if(managed.contains(s.getItem()))s.setCount(0);}
    private static int countItem(ServerPlayer p,Item i){int n=0;for(ItemStack s:p.getInventory().items)if(s.is(i))n+=s.getCount();for(ItemStack s:p.getInventory().offhand)if(s.is(i))n+=s.getCount();return n;}
    private static Item registeredItem(String id){ResourceLocation k=ResourceLocation.tryParse(id);if(k==null||!BuiltInRegistries.ITEM.containsKey(k))return null;Item i=BuiltInRegistries.ITEM.get(k);return i==Items.AIR?null:i;}
}
