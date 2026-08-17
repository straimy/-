package arena.forge;

import arena.GunnerArenaMod;
import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Locale;

/**
 * Cancels only legacy command-block commands that directly target players with inventory/teleport/
 * state-changing commands. Map command blocks that regenerate blocks/entities remain untouched.
 */
@Mod.EventBusSubscriber(modid="gunnerarena",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class LegacyCommandBlockFence {
    private LegacyCommandBlockFence(){}

    @SubscribeEvent public static void command(CommandEvent e){
        ParseResults<CommandSourceStack> parsed=e.getParseResults();if(parsed==null)return;
        CommandSourceStack src=parsed.getContext().getSource();
        if(src==null||!isCommandBlockSource(src))return;
        ArenaRuntime r=GunnerArenaMod.RUNTIME;if(r==null)return;

        String raw=parsed.getReader().getString();if(raw==null)return;
        String cmd=raw.trim().toLowerCase(Locale.ROOT);if(cmd.startsWith("/"))cmd=cmd.substring(1);
        if(!isDangerousPlayerCommand(cmd)||!targetsPlayer(cmd))return;
        e.setCanceled(true);
    }

    /** CommandSourceStack does not expose its raw source in Mojmap 1.20.1; resolve it by source position. */
    private static boolean isCommandBlockSource(CommandSourceStack src){
        try{
            BlockPos pos=BlockPos.containing(src.getPosition());
            BlockState state=src.getLevel().getBlockState(pos);
            return state.is(Blocks.COMMAND_BLOCK)||state.is(Blocks.CHAIN_COMMAND_BLOCK)||state.is(Blocks.REPEATING_COMMAND_BLOCK);
        }catch(Exception ignored){return false;}
    }

    private static boolean isDangerousPlayerCommand(String c){
        return c.startsWith("give ")||c.startsWith("clear ")||c.startsWith("item ")||c.startsWith("replaceitem ")||
            c.startsWith("tp ")||c.startsWith("teleport ")||c.startsWith("gamemode ")||c.startsWith("effect ")||
            c.startsWith("kill ")||c.startsWith("execute ")&&(c.contains(" run give ")||c.contains(" run clear ")||
            c.contains(" run item ")||c.contains(" run replaceitem ")||c.contains(" run tp ")||c.contains(" run teleport ")||
            c.contains(" run gamemode ")||c.contains(" run effect ")||c.contains(" run kill "));
    }

    private static boolean targetsPlayer(String c){
        if(c.contains("@a")||c.contains("@p")||c.contains("@r")||c.contains("type=player"))return true;
        var server=ServerLifecycleHooks.getCurrentServer();if(server==null)return false;
        for(ServerPlayer p:server.getPlayerList().getPlayers()){
            String n=p.getGameProfile().getName();if(n!=null&&!n.isBlank()&&c.contains(n.toLowerCase(Locale.ROOT)))return true;
        }
        return false;
    }
}
