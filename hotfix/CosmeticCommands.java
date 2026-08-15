package arena.forge;

import arena.GunnerArenaMod;
import arena.profile.PlayerProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.LinkedHashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid="gunnerarena", bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class CosmeticCommands {
    private static final Map<String,Integer> PRICES = new LinkedHashMap<>();
    static { PRICES.put("neon_pulse",8); PRICES.put("crimson_grid",12); PRICES.put("void_ice",18); }
    private CosmeticCommands(){}

    @SubscribeEvent public static void commands(RegisterCommandsEvent e){
        e.getDispatcher().register(Commands.literal("skinbuy").then(Commands.argument("id",StringArgumentType.word()).executes(c->buy(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"id")))));
        e.getDispatcher().register(Commands.literal("skinuse").then(Commands.argument("id",StringArgumentType.word()).executes(c->equip(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"id")))));
        e.getDispatcher().register(Commands.literal("skins").executes(c->list(c.getSource().getPlayerOrException())));
    }

    private static PlayerProfile profile(ServerPlayer p){ ArenaRuntime r=GunnerArenaMod.RUNTIME; return r==null?null:r.players().profile(p); }
    private static int buy(ServerPlayer p,String id){
        id=id.toLowerCase(); Integer price=PRICES.get(id); PlayerProfile pr=profile(p); if(price==null||pr==null)return 0;
        if(pr.cosmetics.contains(id)){ p.sendSystemMessage(Component.literal("[GGO] Этот скин уже твой навсегда.").withStyle(ChatFormatting.AQUA)); return 1; }
        if(pr.crystals<price){ p.sendSystemMessage(Component.literal("[GGO] Нужно "+price+"◆. Сейчас: "+pr.crystals+"◆").withStyle(ChatFormatting.RED)); return 0; }
        pr.crystals-=price; pr.cosmetics.add(id); GunnerArenaMod.RUNTIME.profiles().markDirty(p.getUUID());
        p.sendSystemMessage(Component.literal("[GGO] Скин куплен навсегда: "+pretty(id)+" • -"+price+"◆").withStyle(ChatFormatting.LIGHT_PURPLE)); return 1;
    }
    private static int equip(ServerPlayer p,String id){
        id=id.toLowerCase(); PlayerProfile pr=profile(p); if(pr==null)return 0;
        if("none".equals(id)){ pr.equippedSkin="NONE"; GunnerArenaMod.RUNTIME.profiles().markDirty(p.getUUID()); return 1; }
        if(!pr.cosmetics.contains(id)){ p.sendSystemMessage(Component.literal("[GGO] Сначала купи этот скин.").withStyle(ChatFormatting.RED)); return 0; }
        pr.equippedSkin=id; GunnerArenaMod.RUNTIME.profiles().markDirty(p.getUUID()); applyMarker(p.getMainHandItem(),id);
        p.sendSystemMessage(Component.literal("[GGO] Выбран скин: "+pretty(id)).withStyle(ChatFormatting.AQUA)); return 1;
    }
    private static int list(ServerPlayer p){ PlayerProfile pr=profile(p); if(pr==null)return 0; p.sendSystemMessage(Component.literal("[GGO] Скины: "+String.join(", ",pr.cosmetics)+" • выбран: "+pr.equippedSkin)); return 1; }
    private static void applyMarker(ItemStack stack,String id){ if(stack==null||stack.isEmpty())return; stack.getOrCreateTag().putString("GunGlorySkin",id); }
    private static String pretty(String id){ return switch(id){case "neon_pulse"->"Neon Pulse";case "crimson_grid"->"Crimson Grid";case "void_ice"->"Void Ice";default->id;}; }
}
