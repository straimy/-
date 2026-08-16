package arena.forge;

import arena.GunnerArenaMod;
import arena.forge.player.ArenaPlayerState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.io.*;
import java.nio.file.*;
import java.util.*;

@Mod.EventBusSubscriber(modid="gunnerarena",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class FriendService {
    private static final Object LOCK=new Object();
    private static final Properties DATA=new Properties();
    private static final Path FILE= FMLPaths.CONFIGDIR.get().resolve("gunnerarena").resolve("friends.properties");
    private static boolean loaded;
    private FriendService(){}

    public static void addFriend(ServerPlayer owner,String token){
        UUID self=GgoIdentityBridge.idFor(owner), target=GgoIdentityBridge.findKnown(token);
        if(target==null){owner.sendSystemMessage(Component.literal("✦ Игрок не найден. Используй ник или GGO-ID.").withStyle(ChatFormatting.RED));sendSnapshot(owner);return;}
        if(self.equals(target)){owner.sendSystemMessage(Component.literal("✦ Себя добавить нельзя.").withStyle(ChatFormatting.GRAY));return;}
        synchronized(LOCK){Set<UUID> set=get(self);set.add(target);put(self,set);save();}
        owner.sendSystemMessage(Component.literal("✦ Друг добавлен.").withStyle(ChatFormatting.AQUA));sendSnapshot(owner);
    }
    public static void removeFriend(ServerPlayer owner,String token){
        UUID self=GgoIdentityBridge.idFor(owner), target=GgoIdentityBridge.findKnown(token);
        if(target==null){sendSnapshot(owner);return;}
        synchronized(LOCK){Set<UUID> set=get(self);set.remove(target);put(self,set);save();}
        sendSnapshot(owner);
    }
    public static boolean areFriends(ServerPlayer a,ServerPlayer b){if(a==null||b==null)return false;UUID ga=GgoIdentityBridge.idFor(a),gb=GgoIdentityBridge.idFor(b);synchronized(LOCK){return get(ga).contains(gb)||get(gb).contains(ga);}}
    public static void sendSnapshot(ServerPlayer p){
        UUID self=GgoIdentityBridge.idFor(p);String selfId=GgoIdentityBridge.publicIdFor(self);List<FriendNetwork.Row> rows=new ArrayList<>();MinecraftServer server=p.getServer();
        for(UUID id:get(self)){
            ServerPlayer online=findOnline(server,id);String name=online==null?displayName(id):online.getGameProfile().getName();String status="OFFLINE";
            if(online!=null){ArenaRuntime r= GunnerArenaMod.RUNTIME;status=r!=null&&r.players().session(online).state()== ArenaPlayerState.ALIVE?"BATTLE":"ONLINE";}
            rows.add(new FriendNetwork.Row(GgoIdentityBridge.publicIdFor(id),name,status));
        }
        rows.sort(Comparator.comparing((FriendNetwork.Row r)->rank(r.status())).thenComparing(FriendNetwork.Row::name,String.CASE_INSENSITIVE_ORDER));
        FriendNetwork.send(p,new FriendNetwork.Snapshot(selfId,rows));
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p){GgoIdentityBridge.idFor(p);for(ServerPlayer q:p.getServer().getPlayerList().getPlayers())sendSnapshot(q);}}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p&&p.getServer()!=null){for(ServerPlayer q:p.getServer().getPlayerList().getPlayers())if(q!=p)sendSnapshot(q);}}
    private static int rank(String s){return "ONLINE".equals(s)?0:"BATTLE".equals(s)?1:2;}
    private static ServerPlayer findOnline(MinecraftServer server,UUID ggo){if(server==null)return null;for(ServerPlayer p:server.getPlayerList().getPlayers())if(GgoIdentityBridge.idFor(p).equals(ggo))return p;return null;}
    private static String displayName(UUID ggo){synchronized(LOCK){load();String n=DATA.getProperty("label."+ggo);return n==null?GgoIdentityBridge.publicIdFor(ggo):n;}}
    public static void rememberLabel(ServerPlayer p){UUID id=GgoIdentityBridge.idFor(p);synchronized(LOCK){load();DATA.setProperty("label."+id,p.getGameProfile().getName());save();}}
    private static Set<UUID> get(UUID id){load();Set<UUID>s=new LinkedHashSet<>();String v=DATA.getProperty("friends."+id,"");for(String x:v.split(","))if(!x.isBlank())try{s.add(UUID.fromString(x.trim()));}catch(Exception ignored){}return s;}
    private static void put(UUID id,Set<UUID>s){DATA.setProperty("friends."+id,String.join(",",s.stream().map(UUID::toString).toList()));}
    private static void load(){if(loaded)return;loaded=true;if(!Files.isRegularFile(FILE))return;try(InputStream in=Files.newInputStream(FILE)){DATA.load(in);}catch(IOException ex){System.err.println("[GGO] friends load failed: "+ex.getMessage());}}
    private static void save(){try{Files.createDirectories(FILE.getParent());Path tmp=FILE.resolveSibling(FILE.getFileName()+".tmp");try(OutputStream out=Files.newOutputStream(tmp)){DATA.store(out,"GunGloryOnline friends");}Files.move(tmp,FILE,StandardCopyOption.REPLACE_EXISTING);}catch(IOException ex){System.err.println("[GGO] friends save failed: "+ex.getMessage());}}
}
