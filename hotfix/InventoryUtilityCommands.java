package arena.forge;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Small safe inventory actions used by the E-screen buttons. */
@Mod.EventBusSubscriber(modid="gunnerarena",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class InventoryUtilityCommands {
    private InventoryUtilityCommands(){}

    @SubscribeEvent public static void commands(RegisterCommandsEvent e){
        e.getDispatcher().register(Commands.literal("ggoinv")
            .then(Commands.literal("ammo").executes(ctx->{ServerPlayer p=ctx.getSource().getPlayerOrException();ArenaBeltGuard.normalizeAmmoSlots(p);msg(p,"✓ Патроны собраны в специальные слоты.",ChatFormatting.AQUA);return 1;}))
            .then(Commands.literal("clear").executes(ctx->{ServerPlayer p=ctx.getSource().getPlayerOrException();int n=dropTrash(p);msg(p,n==0?"✓ Мусора в инвентаре нет.":"✓ Выброшено предметов: "+n,ChatFormatting.YELLOW);return 1;}))
            .then(Commands.literal("dropammo").executes(ctx->{ServerPlayer p=ctx.getSource().getPlayerOrException();int n=dropAmmo(p);msg(p,n==0?"✓ Патронов нет.":"✓ Патроны выброшены: "+n,ChatFormatting.YELLOW);return 1;})));
    }

    private static int dropTrash(ServerPlayer p){int count=0;for(int i=18;i<36;i++){ItemStack s=p.getInventory().getItem(i);if(s.isEmpty()||ArenaBeltGuard.isAmmo(s)||isBound(s))continue;count+=s.getCount();p.drop(s.copy(),false);p.getInventory().setItem(i,ItemStack.EMPTY);}p.getInventory().setChanged();return count;}
    private static int dropAmmo(ServerPlayer p){int count=0;for(int i=ArenaBeltGuard.AMMO_FIRST;i<=ArenaBeltGuard.AMMO_LAST;i++){ItemStack s=p.getInventory().getItem(i);if(s.isEmpty()||!ArenaBeltGuard.isAmmo(s))continue;count+=s.getCount();p.drop(s.copy(),false);p.getInventory().setItem(i,ItemStack.EMPTY);}p.getInventory().setChanged();return count;}
    private static boolean isBound(ItemStack s){return s.hasTag()&&(s.getTag().getBoolean("GunnerArenaBound")||s.getTag().getBoolean("GunnerArenaKnife"));}
    private static void msg(ServerPlayer p,String s,ChatFormatting c){p.sendSystemMessage(Component.literal(s).withStyle(c));}
}
