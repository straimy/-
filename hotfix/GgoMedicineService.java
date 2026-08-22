package arena.forge;

import arena.GunnerArenaMod;
import arena.forge.player.ArenaPlayerState;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative field medicine used by the GGO H radial wheel. */
@Mod.EventBusSubscriber(modid="gunnerarena", bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoMedicineService {
    public static final int FIELD_FIRST=18, FIELD_LAST=35;
    private static final long COOLDOWN_TICKS=30L;
    private static final Map<UUID,Long> NEXT_USE=new HashMap<>();
    private GgoMedicineService(){}

    /** Debug/admin fallback only. Normal GGO clients use GgoUiActionNetwork. */
    @SubscribeEvent public static void commands(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("ggomed").requires(s->s.hasPermission(2))
            .then(Commands.literal("use")
                .then(Commands.argument("slot", IntegerArgumentType.integer(FIELD_FIRST,FIELD_LAST))
                    .executes(ctx->useFromUi(ctx.getSource().getPlayerOrException(),IntegerArgumentType.getInteger(ctx,"slot"))))));
    }

    /** Narrow server-authoritative entry point shared by the packet path and admin fallback. */
    public static int useFromUi(ServerPlayer p,int slot){
        if(p==null||slot<FIELD_FIRST||slot>FIELD_LAST)return 0;
        ArenaRuntime runtime=GunnerArenaMod.RUNTIME;
        if(runtime==null||!runtime.auth().isAuthenticated(p)||runtime.players().session(p).state()!=ArenaPlayerState.ALIVE)return 0;
        long now=runtime.serverTick();
        long next=NEXT_USE.getOrDefault(p.getUUID(),0L);
        if(now<next){p.displayClientMessage(Component.literal("MEDICAL COOLDOWN").withStyle(ChatFormatting.GRAY),true);return 0;}
        ItemStack stack=p.getInventory().getItem(slot);
        Medicine med=medicine(stack);
        if(med==null){p.displayClientMessage(Component.literal("NO MEDICINE IN SLOT").withStyle(ChatFormatting.GRAY),true);return 0;}
        if(p.getHealth()>=p.getMaxHealth()){p.displayClientMessage(Component.literal("HEALTH FULL").withStyle(ChatFormatting.GRAY),true);return 0;}
        float before=p.getHealth();
        p.heal(Math.min(med.heal,p.getMaxHealth()-before));
        stack.shrink(1);
        if(stack.isEmpty())p.getInventory().setItem(slot,ItemStack.EMPTY);
        p.getInventory().setChanged();
        NEXT_USE.put(p.getUUID(),now+COOLDOWN_TICKS);
        int restored=Math.max(1,Math.round(p.getHealth()-before));
        p.displayClientMessage(Component.literal("+"+restored+" HP  //  "+med.label).withStyle(ChatFormatting.GREEN),true);
        return 1;
    }

    /** Explicit GgoMedicine NBT is preferred; path matching keeps current modded medicine compatible. */
    public static Medicine medicine(ItemStack stack){
        if(stack==null||stack.isEmpty())return null;
        if(stack.hasTag()&&stack.getTag().contains("GgoMedicine")){
            String type=stack.getTag().getString("GgoMedicine").toLowerCase(Locale.ROOT);
            return fromType(type);
        }
        ResourceLocation id=ForgeRegistries.ITEMS.getKey(stack.getItem());
        if(id==null)return null;
        String p=id.getPath().toLowerCase(Locale.ROOT);
        if(p.contains("bandage"))return new Medicine("BANDAGE",4f);
        if(p.contains("medkit")||p.contains("first_aid")||p.contains("firstaid"))return new Medicine("MEDKIT",10f);
        if(p.contains("syringe")||p.contains("stim")||p.contains("injector"))return new Medicine("STIM",6f);
        return null;
    }

    private static Medicine fromType(String type){
        if(type.contains("bandage"))return new Medicine("BANDAGE",4f);
        if(type.contains("stim")||type.contains("syringe")||type.contains("inject"))return new Medicine("STIM",6f);
        if(type.contains("med")||type.contains("aid"))return new Medicine("MEDKIT",10f);
        return null;
    }

    public record Medicine(String label,float heal){}
}
