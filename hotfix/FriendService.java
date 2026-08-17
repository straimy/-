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

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

@Mod.EventBusSubscriber(modid="gunnerarena",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class FriendService {
    private static final Object LOCK=new Object();
    private static final Properties DATA=new Properties();
    private static final Path FILE=FMLPaths.CONFIGDIR.get().resolve("gunnerarena").resolve("friends.properties");
    private static final int MAX_CHAT=40;
    private static boolean loaded;
    private FriendService(){}

    /** Kept for compatibility; the old Add action is now a request, never an instant friendship. */
    public static void addFriend(ServerPlayer owner,String token){sendRequest(owner,token);}

    public static void sendRequest(ServerPlayer owner,String token){
        UUID self=GgoIdentityBridge.idFor(owner),target=GgoIdentityBridge.findKnown(token);
        if(target==null){message(owner,"✦ Игрок не найден. Используй ник или GGO-ID.",ChatFormatting.RED);sendSnapshot(owner);return;}
        if(self.equals(target)){message(owner,"✦ Себя добавить нельзя.",ChatFormatting.GRAY);return;}
        synchronized(LOCK){
            if(getFriends(self).contains(target)){message(owner,"✦ Этот игрок уже у тебя в друзьях.",ChatFormatting.GRAY);sendSnapshot(owner);return;}
            if(!allowRequests(target)){message(owner,"✦ Игрок запретил заявки в друзья.",ChatFormatting.RED);sendSnapshot(owner);return;}
            Set<UUID> incoming=getPending(target);
            if(incoming.add(self)){putSet("pending.",target,incoming);rememberLabel(owner);save();}
        }
        message(owner,"✦ Заявка в друзья отправлена.",ChatFormatting.AQUA);
        ServerPlayer online=findOnline(owner.getServer(),target);
        if(online!=null){message(online,"✦ Новая заявка в друзья от "+owner.getGameProfile().getName(),ChatFormatting.LIGHT_PURPLE);sendSnapshot(online);}
        sendSnapshot(owner);
    }

    public static void accept(ServerPlayer owner,String token){
        UUID self=GgoIdentityBridge.idFor(owner),target=GgoIdentityBridge.findKnown(token);if(target==null){sendSnapshot(owner);return;}
        boolean accepted=false;
        synchronized(LOCK){Set<UUID> p=getPending(self);if(p.remove(target)){putSet("pending.",self,p);Set<UUID>a=getFriends(self),b=getFriends(target);a.add(target);b.add(self);putSet("friends.",self,a);putSet("friends.",target,b);save();accepted=true;}}
        if(accepted){message(owner,"✓ Друг добавлен.",ChatFormatting.GREEN);ServerPlayer online=findOnline(owner.getServer(),target);if(online!=null){message(online,"✓ "+owner.getGameProfile().getName()+" принял вашу заявку.",ChatFormatting.GREEN);sendSnapshot(online);}}
        sendSnapshot(owner);
    }

    public static void decline(ServerPlayer owner,String token){
        UUID self=GgoIdentityBridge.idFor(owner),target=GgoIdentityBridge.findKnown(token);if(target==null){sendSnapshot(owner);return;}
        synchronized(LOCK){Set<UUID> p=getPending(self);p.remove(target);putSet("pending.",self,p);save();}
        sendSnapshot(owner);
    }

    public static void removeFriend(ServerPlayer owner,String token){
        UUID self=GgoIdentityBridge.idFor(owner),target=GgoIdentityBridge.findKnown(token);if(target==null){sendSnapshot(owner);return;}
        synchronized(LOCK){Set<UUID>a=getFriends(self),b=getFriends(target);a.remove(target);b.remove(self);putSet("friends.",self,a);putSet("friends.",target,b);save();}
        sendSnapshot(owner);ServerPlayer online=findOnline(owner.getServer(),target);if(online!=null)sendSnapshot(online);
    }

    public static void setAllowRequests(ServerPlayer owner,boolean allow){synchronized(LOCK){load();DATA.setProperty("allowRequests."+GgoIdentityBridge.idFor(owner),Boolean.toString(allow));save();}sendSnapshot(owner);}
    public static boolean areFriends(ServerPlayer a,ServerPlayer b){if(a==null||b==null)return false;UUID ga=GgoIdentityBridge.idFor(a),gb=GgoIdentityBridge.idFor(b);synchronized(LOCK){return getFriends(ga).contains(gb)&&getFriends(gb).contains(ga);}}

    public static void sendMessage(ServerPlayer owner,String token,String raw){
        UUID self=GgoIdentityBridge.idFor(owner),target=GgoIdentityBridge.findKnown(token);String text=raw==null?"":raw.strip();
        if(target==null||text.isBlank())return;if(text.length()>160)text=text.substring(0,160);
        synchronized(LOCK){if(!getFriends(self).contains(target)||!getFriends(target).contains(self)){message(owner,"✦ Сообщения доступны только друзьям.",ChatFormatting.RED);return;}List<FriendNetwork.ChatLine> lines=chat(self,target);lines.add(new FriendNetwork.ChatLine(GgoIdentityBridge.publicIdFor(self),owner.getGameProfile().getName(),text,System.currentTimeMillis()));while(lines.size()>MAX_CHAT)lines.remove(0);putChat(self,target,lines);save();}
        sendChat(owner,GgoIdentityBridge.publicIdFor(target));ServerPlayer online=findOnline(owner.getServer(),target);if(online!=null){message(online,"✉ "+owner.getGameProfile().getName()+": "+text,ChatFormatting.AQUA);sendChat(online,GgoIdentityBridge.publicIdFor(self));}
    }

    public static void sendChat(ServerPlayer owner,String token){
        UUID self=GgoIdentityBridge.idFor(owner),target=GgoIdentityBridge.findKnown(token);if(target==null)return;
        List<FriendNetwork.ChatLine> lines;synchronized(LOCK){if(!getFriends(self).contains(target))return;lines=new ArrayList<>(chat(self,target));}
        FriendNetwork.sendChat(owner,new FriendNetwork.ChatSnapshot(GgoIdentityBridge.publicIdFor(target),lines));
    }

    public static void sendSnapshot(ServerPlayer p){
        UUID self=GgoIdentityBridge.idFor(p);rememberLabel(p);String selfId=GgoIdentityBridge.publicIdFor(self);List<FriendNetwork.Row> rows=new ArrayList<>(),pending=new ArrayList<>();MinecraftServer server=p.getServer();
        synchronized(LOCK){
            for(UUID id:getFriends(self))rows.add(row(server,id));
            for(UUID id:getPending(self))pending.add(row(server,id));
        }
        rows.sort(Comparator.comparing((FriendNetwork.Row r)->rank(r.status())).thenComparing(FriendNetwork.Row::name,String.CASE_INSENSITIVE_ORDER));
        pending.sort(Comparator.comparing(FriendNetwork.Row::name,String.CASE_INSENSITIVE_ORDER));
        FriendNetwork.send(p,new FriendNetwork.Snapshot(selfId,allowRequests(self),rows,pending));
    }

    private static FriendNetwork.Row row(MinecraftServer server,UUID id){ServerPlayer online=findOnline(server,id);String name=online==null?displayName(id):online.getGameProfile().getName();String status="OFFLINE";if(online!=null){ArenaRuntime r=GunnerArenaMod.RUNTIME;status=r!=null&&r.players().session(online).state()==ArenaPlayerState.ALIVE?"BATTLE":"ONLINE";}return new FriendNetwork.Row(GgoIdentityBridge.publicIdFor(id),name,status,"default");}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p){GgoIdentityBridge.idFor(p);rememberLabel(p);for(ServerPlayer q:p.getServer().getPlayerList().getPlayers())sendSnapshot(q);}}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p&&p.getServer()!=null){for(ServerPlayer q:p.getServer().getPlayerList().getPlayers())if(q!=p)sendSnapshot(q);}}

    private static int rank(String s){return "ONLINE".equals(s)?0:"BATTLE".equals(s)?1:2;}
    private static boolean allowRequests(UUID id){load();return Boolean.parseBoolean(DATA.getProperty("allowRequests."+id,"true"));}
    private static ServerPlayer findOnline(MinecraftServer server,UUID ggo){if(server==null)return null;for(ServerPlayer p:server.getPlayerList().getPlayers())if(GgoIdentityBridge.idFor(p).equals(ggo))return p;return null;}
    private static String displayName(UUID ggo){load();String n=DATA.getProperty("label."+ggo);return n==null?GgoIdentityBridge.publicIdFor(ggo):n;}
    public static void rememberLabel(ServerPlayer p){UUID id=GgoIdentityBridge.idFor(p);synchronized(LOCK){load();DATA.setProperty("label."+id,p.getGameProfile().getName());save();}}
    private static Set<UUID> getFriends(UUID id){return getSet("friends.",id);}
    private static Set<UUID> getPending(UUID id){return getSet("pending.",id);}
    private static Set<UUID> getSet(String prefix,UUID id){load();Set<UUID>s=new LinkedHashSet<>();String v=DATA.getProperty(prefix+id,"");for(String x:v.split(","))if(!x.isBlank())try{s.add(UUID.fromString(x.trim()));}catch(Exception ignored){}return s;}
    private static void putSet(String prefix,UUID id,Set<UUID>s){DATA.setProperty(prefix+id,String.join(",",s.stream().map(UUID::toString).toList()));}
    private static String pair(UUID a,UUID b){String x=a.toString(),y=b.toString();return x.compareTo(y)<=0?x+"_"+y:y+"_"+x;}
    private static List<FriendNetwork.ChatLine> chat(UUID a,UUID b){load();List<FriendNetwork.ChatLine> out=new ArrayList<>();String raw=DATA.getProperty("chat."+pair(a,b),"");for(String entry:raw.split(",")){if(entry.isBlank())continue;try{String[]p=entry.split(":",4);long t=Long.parseLong(p[0]);UUID sender=UUID.fromString(p[1]);String name=new String(Base64.getUrlDecoder().decode(p[2]),StandardCharsets.UTF_8);String text=new String(Base64.getUrlDecoder().decode(p[3]),StandardCharsets.UTF_8);out.add(new FriendNetwork.ChatLine(GgoIdentityBridge.publicIdFor(sender),name,text,t));}catch(Exception ignored){}}return out;}
    private static void putChat(UUID a,UUID b,List<FriendNetwork.ChatLine> lines){List<String> encoded=new ArrayList<>();for(FriendNetwork.ChatLine l:lines){UUID sender=GgoIdentityBridge.findKnown(l.senderId());if(sender==null)continue;String name=Base64.getUrlEncoder().withoutPadding().encodeToString(l.senderName().getBytes(StandardCharsets.UTF_8));String text=Base64.getUrlEncoder().withoutPadding().encodeToString(l.text().getBytes(StandardCharsets.UTF_8));encoded.add(l.time()+":"+sender+":"+name+":"+text);}DATA.setProperty("chat."+pair(a,b),String.join(",",encoded));}
    private static void message(ServerPlayer p,String s,ChatFormatting color){p.sendSystemMessage(Component.literal(s).withStyle(color));}
    private static void load(){if(loaded)return;loaded=true;if(!Files.isRegularFile(FILE))return;try(InputStream in=Files.newInputStream(FILE)){DATA.load(in);}catch(IOException ex){System.err.println("[GGO] friends load failed: "+ex.getMessage());}}
    private static void save(){try{Files.createDirectories(FILE.getParent());Path tmp=FILE.resolveSibling(FILE.getFileName()+".tmp");try(OutputStream out=Files.newOutputStream(tmp)){DATA.store(out,"GunGloryOnline social");}Files.move(tmp,FILE,StandardCopyOption.REPLACE_EXISTING);}catch(IOException ex){System.err.println("[GGO] friends save failed: "+ex.getMessage());}}
}
