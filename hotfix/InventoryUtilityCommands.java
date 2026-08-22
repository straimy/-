package arena.forge;

import arena.GunnerArenaMod;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server-authoritative actions used by the first-party GGO E inventory. */
@Mod.EventBusSubscriber(modid="gunnerarena",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class InventoryUtilityCommands {
    private static final int COMBAT_FIRST=0,COMBAT_LAST=2;
    private static final int FIELD_FIRST=18,FIELD_LAST=35;

    private InventoryUtilityCommands(){}

    @SubscribeEvent public static void commands(RegisterCommandsEvent e){
        e.getDispatcher().register(Commands.literal("ggoinv")
            .then(Commands.literal("ammo").executes(ctx->{
                ServerPlayer p=ctx.getSource().getPlayerOrException();if(!authorized(p))return 0;
                ArenaBeltGuard.normalizeAmmoSlots(p);msg(p,"✓ Патроны собраны в подсумок.",ChatFormatting.AQUA);return 1;
            }))
            .then(Commands.literal("select")
                .then(Commands.argument("slot",IntegerArgumentType.integer(COMBAT_FIRST,COMBAT_LAST)).executes(ctx->{
                    ServerPlayer p=ctx.getSource().getPlayerOrException();if(!authorized(p))return 0;
                    int slot=IntegerArgumentType.getInteger(ctx,"slot");p.getInventory().selected=slot;p.getInventory().setChanged();return 1;
                })))
            .then(Commands.literal("drop")
                .then(Commands.argument("slot",IntegerArgumentType.integer(0,FIELD_LAST)).executes(ctx->{
                    ServerPlayer p=ctx.getSource().getPlayerOrException();if(!authorized(p))return 0;
                    int slot=IntegerArgumentType.getInteger(ctx,"slot");return dropSlot(p,slot);
                })))
            .then(Commands.literal("clear").executes(ctx->{
                ServerPlayer p=ctx.getSource().getPlayerOrException();if(!authorized(p))return 0;
                int n=dropTrash(p);msg(p,n==0?"✓ Мусора в полевых слотах нет.":"✓ Выброшено предметов: "+n,ChatFormatting.YELLOW);return 1;
            }))
            .then(Commands.literal("dropammo").executes(ctx->{
                ServerPlayer p=ctx.getSource().getPlayerOrException();if(!authorized(p))return 0;
                int n=dropAmmo(p);msg(p,n==0?"✓ Патронов нет.":"✓ Патроны выброшены: "+n,ChatFormatting.YELLOW);return 1;
            })));
    }

    private static boolean authorized(ServerPlayer p){
        ArenaRuntime runtime=GunnerArenaMod.RUNTIME;
        if(runtime!=null&&runtime.auth().isAuthenticated(p))return true;
        msg(p,"GGO inventory is unavailable until authentication completes.",ChatFormatting.RED);
        return false;
    }

    private static int dropSlot(ServerPlayer p,int slot){
        if(slot<0||slot>FIELD_LAST)return 0;
        // Slots 3..8 are intentionally not part of the GGO inventory contract.
        if(slot>COMBAT_LAST&&slot<ArenaBeltGuard.AMMO_FIRST)return 0;
        ItemStack s=p.getInventory().getItem(slot);
        if(s==null||s.isEmpty())return 0;
        if(isBound(s)){msg(p,"Этот предмет закреплён за loadout.",ChatFormatting.RED);return 0;}
        int count=s.getCount();p.drop(s.copy(),false);p.getInventory().setItem(slot,ItemStack.EMPTY);p.getInventory().setChanged();
        msg(p,"Выброшено: "+count,ChatFormatting.YELLOW);return 1;
    }

    private static int dropTrash(ServerPlayer p){
        int count=0;
        for(int i=FIELD_FIRST;i<=FIELD_LAST;i++){
            ItemStack s=p.getInventory().getItem(i);
            if(s.isEmpty()||ArenaBeltGuard.isAmmo(s)||isBound(s)||GgoSupplyExtractionService.isSupply(s))continue;
            count+=s.getCount();p.drop(s.copy(),false);p.getInventory().setItem(i,ItemStack.EMPTY);
        }
        p.getInventory().setChanged();return count;
    }

    private static int dropAmmo(ServerPlayer p){
        int count=0;
        for(int i=ArenaBeltGuard.AMMO_FIRST;i<=ArenaBeltGuard.AMMO_LAST;i++){
            ItemStack s=p.getInventory().getItem(i);if(s.isEmpty()||!ArenaBeltGuard.isAmmo(s))continue;
            count+=s.getCount();p.drop(s.copy(),false);p.getInventory().setItem(i,ItemStack.EMPTY);
        }
        p.getInventory().setChanged();return count;
    }

    private static boolean isBound(ItemStack s){
        return s.hasTag()&&(s.getTag().getBoolean("GunnerArenaBound")||s.getTag().getBoolean("GunnerArenaKnife")||s.getTag().getBoolean("GunGloryBotWeapon"));
    }
    private static void msg(ServerPlayer p,String s,ChatFormatting c){p.sendSystemMessage(Component.literal(s).withStyle(c));}
}
