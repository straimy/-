package arena.forge;

import arena.GunnerArenaMod;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Small OP-only admin surface for GGO. OP status alone does not bypass gameplay restrictions. */
@Mod.EventBusSubscriber(modid="gunnerarena",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class AdminToolsCommands {
    private AdminToolsCommands(){}

    @SubscribeEvent public static void commands(RegisterCommandsEvent e){
        e.getDispatcher().register(Commands.literal("gm").requires(s->s.hasPermission(2))
            .then(Commands.argument("mode", IntegerArgumentType.integer(0,3)).executes(ctx->{
                int mode=IntegerArgumentType.getInteger(ctx,"mode");
                if(mode==2){ctx.getSource().sendFailure(Component.literal("[GGO] Используй /gm 0, /gm 1 или /gm 3."));return 0;}
                ServerPlayer p;try{p=ctx.getSource().getPlayerOrException();}catch(Exception ex){return 0;}
                GameType type=mode==1?GameType.CREATIVE:mode==3?GameType.SPECTATOR:GameType.ADVENTURE;
                p.setGameMode(type);
                p.sendSystemMessage(Component.literal("[GGO] Режим: "+(mode==1?"Креатив":mode==3?"Спектатор":"Игровой (Adventure)"))
                    .withStyle(mode==1?ChatFormatting.AQUA:mode==3?ChatFormatting.GRAY:ChatFormatting.GREEN));
                return 1;
            })));

        e.getDispatcher().register(Commands.literal("crystals").requires(s->s.hasPermission(2))
            .then(Commands.literal("give")
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("amount", LongArgumentType.longArg(1,1_000_000L)).executes(ctx->{
                        ServerPlayer target=EntityArgument.getPlayer(ctx,"player");long amount=LongArgumentType.getLong(ctx,"amount");
                        var r=GunnerArenaMod.RUNTIME;if(r==null)return 0;var profile=r.players().profile(target);if(profile==null)return 0;
                        profile.crystals=profile.crystals>Long.MAX_VALUE-amount?Long.MAX_VALUE:profile.crystals+amount;
                        r.profiles().markDirty(target.getUUID());
                        ctx.getSource().sendSuccess(()->Component.literal("[GGO] +"+amount+" ◆ кристаллов → "+target.getGameProfile().getName()).withStyle(ChatFormatting.AQUA),true);
                        target.sendSystemMessage(Component.literal("◆ Администратор выдал +"+amount+" кристаллов").withStyle(ChatFormatting.AQUA));
                        return 1;
                    })))));

        e.getDispatcher().register(Commands.literal("credits").requires(s->s.hasPermission(2))
            .then(Commands.literal("give")
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("amount", IntegerArgumentType.integer(1,1_000_000)).executes(ctx->{
                        ServerPlayer target=EntityArgument.getPlayer(ctx,"player");int amount=IntegerArgumentType.getInteger(ctx,"amount");
                        var r=GunnerArenaMod.RUNTIME;if(r==null)return 0;
                        Object session=r.players().roundSession(target);
                        if(!addCreditsReflective(session,amount)){
                            ctx.getSource().sendFailure(Component.literal("[GGO] Не удалось изменить матчевые кредиты: несовместимый runtime."));return 0;
                        }
                        ctx.getSource().sendSuccess(()->Component.literal("[GGO] +$"+amount+" кредитов → "+target.getGameProfile().getName()).withStyle(ChatFormatting.GOLD),true);
                        target.sendSystemMessage(Component.literal("$ Администратор выдал +"+amount+" кредитов").withStyle(ChatFormatting.GOLD));
                        return 1;
                    })))));
    }

    private static int saturatingIntAdd(int value,int amount){long sum=(long)value+amount;return (int)Math.min(Integer.MAX_VALUE,Math.max(Integer.MIN_VALUE,sum));}

    /** Runtime versions used by the project changed the round-session API several times; support all known shapes. */
    private static boolean addCreditsReflective(Object session,int amount){
        if(session==null)return false;
        for(String n:new String[]{"addCredits","addRoundCredits","credit","grantCredits"}){
            try{Method m=session.getClass().getMethod(n,int.class);m.invoke(session,amount);return true;}catch(Exception ignored){}
            try{Method m=session.getClass().getMethod(n,long.class);m.invoke(session,(long)amount);return true;}catch(Exception ignored){}
        }
        for(String n:new String[]{"credits","roundCredits"}){
            try{
                Field f=session.getClass().getDeclaredField(n);f.setAccessible(true);Object v=f.get(session);
                if(v instanceof Integer i){f.setInt(session,saturatingIntAdd(i,amount));return true;}
                if(v instanceof Long l){f.setLong(session,l>Long.MAX_VALUE-amount?Long.MAX_VALUE:l+amount);return true;}
            }catch(Exception ignored){}
        }
        try{
            Method get=session.getClass().getMethod("credits");Object v=get.invoke(session);
            for(String setter:new String[]{"credits","setCredits"}){
                if(v instanceof Integer i){try{session.getClass().getMethod(setter,int.class).invoke(session,saturatingIntAdd(i,amount));return true;}catch(Exception ignored){}}
                if(v instanceof Long l){try{session.getClass().getMethod(setter,long.class).invoke(session,l>Long.MAX_VALUE-amount?Long.MAX_VALUE:l+amount);return true;}catch(Exception ignored){}}
            }
        }catch(Exception ignored){}
        return false;
    }
}
