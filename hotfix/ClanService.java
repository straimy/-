package arena.forge;

import arena.GunnerArenaMod;
import arena.forge.player.ArenaPlayerState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Persistent server-authoritative GunGloryOnline clan system. All member references are GGO account ids. */
public final class ClanService {
    public static final int CREATE_COST=500;
    public static final int RENAME_COST=100;
    private static final Object LOCK=new Object();
    private static final Properties DATA=new Properties();
    private static final Path FILE=FMLPaths.CONFIGDIR.get().resolve("gunnerarena").resolve("clans.properties");
    private static boolean loaded;
    private ClanService(){}

    public static void create(ServerPlayer p,String rawName,String rawDescription,int entryPrice){
        UUID self=GgoIdentityBridge.idFor(p);String name=clean(rawName,24),desc=clean(rawDescription,100);entryPrice=clamp(entryPrice,0,1000);
        if(name.length()<3){msg(p,"✦ Название клана: 3–24 символа.",ChatFormatting.RED);sendSnapshot(p,"","WEALTH");return;}
        synchronized(LOCK){
            load();if(clanOf(self)!=null){msg(p,"✦ Сначала покиньте текущий клан.",ChatFormatting.RED);sendSnapshot(p,"","WEALTH");return;}
            if(nameExists(name)){msg(p,"✦ Такое название уже занято.",ChatFormatting.RED);sendSnapshot(p,"","WEALTH");return;}
            if(!takeCrystals(p,CREATE_COST)){msg(p,"✦ Для создания клана нужно ◆ "+CREATE_COST+" кристаллов.",ChatFormatting.RED);sendSnapshot(p,"","WEALTH");return;}
            String id=nextClanId();
            DATA.setProperty("clan."+id+".name",name);DATA.setProperty("clan."+id+".description",desc);
            DATA.setProperty("clan."+id+".owner",self.toString());DATA.setProperty("clan."+id+".deputy","");
            DATA.setProperty("clan."+id+".entryPrice",Integer.toString(entryPrice));DATA.setProperty("clan."+id+".treasury","0");
            DATA.setProperty("clan."+id+".members",self.toString());DATA.setProperty("member."+self,id);DATA.setProperty("role."+self,"OWNER");
            DATA.setProperty("label."+self,p.getGameProfile().getName());save();
            msg(p,"✦ Клан «"+name+"» создан. ID: "+id,ChatFormatting.LIGHT_PURPLE);
        }
        sendSnapshot(p,"","WEALTH");
    }

    public static void join(ServerPlayer p,String rawClanId){
        UUID self=GgoIdentityBridge.idFor(p);String id=rawClanId==null?"":rawClanId.trim().toUpperCase(Locale.ROOT);
        synchronized(LOCK){
            load();if(clanOf(self)!=null){msg(p,"✦ Вы уже состоите в клане.",ChatFormatting.RED);return;}
            if(!exists(id)){msg(p,"✦ Клан не найден.",ChatFormatting.RED);return;}
            int price=intProp("clan."+id+".entryPrice",0);if(!takeCrystals(p,price)){msg(p,"✦ Для вступления нужно ◆ "+price+".",ChatFormatting.RED);return;}
            Set<UUID> members=members(id);if(members.size()>=50){refundCrystals(p,price);msg(p,"✦ В клане уже 50 участников.",ChatFormatting.RED);return;}
            members.add(self);putMembers(id,members);DATA.setProperty("member."+self,id);DATA.setProperty("role."+self,"MEMBER");DATA.setProperty("label."+self,p.getGameProfile().getName());
            DATA.setProperty("clan."+id+".treasury",Long.toString(longProp("clan."+id+".treasury",0)+price));save();
            msg(p,"✓ Вы вступили в «"+name(id)+"».",ChatFormatting.GREEN);notifyClan(p.getServer(),id,"✦ "+p.getGameProfile().getName()+" вступил в клан.",ChatFormatting.AQUA);
        }
        sendSnapshot(p,"","WEALTH");
    }

    public static void leave(ServerPlayer p){
        UUID self=GgoIdentityBridge.idFor(p);synchronized(LOCK){load();String id=clanOf(self);if(id==null){sendSnapshot(p,"","WEALTH");return;}
            String role=role(self);if("OWNER".equals(role)){msg(p,"✦ Владелец не может выйти. Сначала назначьте заместителя владельцем (TRANSFER).",ChatFormatting.RED);return;}
            removeMember(id,self);save();msg(p,"✦ Вы покинули «"+name(id)+"».",ChatFormatting.GRAY);notifyClan(p.getServer(),id,"✦ "+p.getGameProfile().getName()+" покинул клан.",ChatFormatting.GRAY);
        }sendSnapshot(p,"","WEALTH");
    }

    public static void updateSetting(ServerPlayer p,String rawField,String rawValue){
        UUID self=GgoIdentityBridge.idFor(p);String field=rawField==null?"":rawField.trim().toUpperCase(Locale.ROOT),value=rawValue==null?"":rawValue.strip();
        synchronized(LOCK){load();String id=clanOf(self);if(id==null)return;String r=role(self);boolean owner="OWNER".equals(r),manager=owner||"DEPUTY".equals(r);
            if("NAME".equals(field)){
                if(!owner){denied(p);return;}String n=clean(value,24);if(n.length()<3||nameExistsExcept(n,id)){msg(p,"✦ Некорректное или занятое название.",ChatFormatting.RED);return;}if(!takeCrystals(p,RENAME_COST)){msg(p,"✦ Смена названия стоит ◆ "+RENAME_COST+".",ChatFormatting.RED);return;}DATA.setProperty("clan."+id+".name",n);
            }else if("DESCRIPTION".equals(field)){
                if(!manager){denied(p);return;}DATA.setProperty("clan."+id+".description",clean(value,100));
            }else if("ENTRY_PRICE".equals(field)){
                if(!manager){denied(p);return;}int v;try{v=Integer.parseInt(value);}catch(Exception ex){v=0;}DATA.setProperty("clan."+id+".entryPrice",Integer.toString(clamp(v,0,1000)));
            }else return;save();msg(p,"✓ Настройки клана обновлены.",ChatFormatting.GREEN);
        }sendSnapshot(p,"","WEALTH");
    }

    public static void memberAction(ServerPlayer actor,String token,String rawAction){
        UUID self=GgoIdentityBridge.idFor(actor),target=GgoIdentityBridge.findKnown(token);String action=rawAction==null?"":rawAction.trim().toUpperCase(Locale.ROOT);if(target==null)return;
        synchronized(LOCK){load();String id=clanOf(self);if(id==null||!id.equals(clanOf(target))||self.equals(target))return;String mine=role(self),theirs=role(target);boolean owner="OWNER".equals(mine),deputy="DEPUTY".equals(mine);
            if("KICK".equals(action)){
                if(!(owner||deputy)||"OWNER".equals(theirs)||(!owner&&"DEPUTY".equals(theirs))){denied(actor);return;}removeMember(id,target);save();msg(actor,"✓ Участник исключён.",ChatFormatting.YELLOW);ServerPlayer online=findOnline(actor.getServer(),target);if(online!=null){msg(online,"✦ Вы были исключены из «"+name(id)+"».",ChatFormatting.RED);sendSnapshot(online,"","WEALTH");}
            }else if("VETERAN".equals(action)||"MEMBER".equals(action)){
                if(!(owner||deputy)||"OWNER".equals(theirs)||(!owner&&"DEPUTY".equals(theirs))){denied(actor);return;}DATA.setProperty("role."+target,action);save();msg(actor,"✓ Роль изменена: "+action,ChatFormatting.AQUA);
            }else if("DEPUTY".equals(action)){
                if(!owner||"OWNER".equals(theirs)){denied(actor);return;}String old=DATA.getProperty("clan."+id+".deputy","");if(!old.isBlank())try{DATA.setProperty("role."+UUID.fromString(old),"VETERAN");}catch(Exception ignored){}DATA.setProperty("clan."+id+".deputy",target.toString());DATA.setProperty("role."+target,"DEPUTY");save();msg(actor,"✓ Назначен заместитель владельца.",ChatFormatting.GOLD);
            }else if("TRANSFER".equals(action)){
                if(!owner){denied(actor);return;}DATA.setProperty("role."+self,"DEPUTY");DATA.setProperty("role."+target,"OWNER");DATA.setProperty("clan."+id+".owner",target.toString());DATA.setProperty("clan."+id+".deputy",self.toString());save();msg(actor,"✦ Владелец клана изменён.",ChatFormatting.GOLD);
            }
        }sendSnapshot(actor,"","WEALTH");
    }

    public static void sendSnapshot(ServerPlayer p,String query,String sort){
        UUID self=GgoIdentityBridge.idFor(p);synchronized(LOCK){load();DATA.setProperty("label."+self,p.getGameProfile().getName());String id=clanOf(self);List<ClanNetwork.Member> ms=new ArrayList<>();
            if(id!=null){for(UUID u:members(id)){ServerPlayer online=findOnline(p.getServer(),u);String label=online!=null?online.getGameProfile().getName():DATA.getProperty("label."+u,GgoIdentityBridge.publicIdFor(u));String status="OFFLINE";if(online!=null){ArenaRuntime runtime=GunnerArenaMod.RUNTIME;status=runtime!=null&&runtime.players().session(online).state()==ArenaPlayerState.ALIVE?"BATTLE":"ONLINE";}ms.add(new ClanNetwork.Member(GgoIdentityBridge.publicIdFor(u),label,role(u),status));}}
            ms.sort(Comparator.comparingInt(m->roleRank(m.role())));
            List<ClanNetwork.ClanCard> cards=search(query,sort);ClanNetwork.send(p,new ClanNetwork.Snapshot(id!=null,id==null?"NONE":role(self),id==null?"":id,id==null?"":name(id),id==null?"":DATA.getProperty("clan."+id+".description",""),id==null?0:intProp("clan."+id+".entryPrice",0),id==null?0:longProp("clan."+id+".treasury",0),CREATE_COST,RENAME_COST,ms,cards));save();
        }
    }

    public static String chatTag(ServerPlayer p){UUID id=GgoIdentityBridge.idFor(p);synchronized(LOCK){load();String clan=clanOf(id);if(clan==null)return "";String r=role(id),mark="OWNER".equals(r)?"♛":"DEPUTY".equals(r)?"♕":"VETERAN".equals(r)?"✦":"";return "["+name(clan)+(mark.isEmpty()?"":" "+mark)+"]";}}

    private static List<ClanNetwork.ClanCard> search(String query,String sort){String q=query==null?"":query.strip().toLowerCase(Locale.ROOT);List<ClanNetwork.ClanCard> out=new ArrayList<>();for(String id:clanIds()){String n=name(id),d=DATA.getProperty("clan."+id+".description","");if(!q.isBlank()&&!n.toLowerCase(Locale.ROOT).contains(q)&&!id.toLowerCase(Locale.ROOT).contains(q))continue;out.add(new ClanNetwork.ClanCard(id,n,d,members(id).size(),longProp("clan."+id+".treasury",0),intProp("clan."+id+".entryPrice",0)));}
        Comparator<ClanNetwork.ClanCard> c=switch(sort==null?"":sort.toUpperCase(Locale.ROOT)){case "MEMBERS"->Comparator.comparingInt(ClanNetwork.ClanCard::members).reversed();case "CHEAP"->Comparator.comparingInt(ClanNetwork.ClanCard::entryPrice);case "NAME"->Comparator.comparing(ClanNetwork.ClanCard::name,String.CASE_INSENSITIVE_ORDER);default->Comparator.comparingLong(ClanNetwork.ClanCard::treasury).reversed();};out.sort(c.thenComparing(ClanNetwork.ClanCard::name,String.CASE_INSENSITIVE_ORDER));return out.size()>30?new ArrayList<>(out.subList(0,30)):out;}
    private static Set<String> clanIds(){load();Set<String>s=new TreeSet<>();for(String k:DATA.stringPropertyNames())if(k.startsWith("clan.")&&k.endsWith(".name")){String x=k.substring(5,k.length()-5);if(!x.isBlank())s.add(x);}return s;}
    private static String nextClanId(){long n=longProp("counter",0)+1;DATA.setProperty("counter",Long.toString(n));return String.format(Locale.ROOT,"CLAN-%06d",n);}
    private static boolean exists(String id){return id!=null&&DATA.containsKey("clan."+id+".name");}
    private static String name(String id){return DATA.getProperty("clan."+id+".name",id);}
    private static boolean nameExists(String n){return nameExistsExcept(n,null);}private static boolean nameExistsExcept(String n,String except){for(String id:clanIds())if(!Objects.equals(id,except)&&name(id).equalsIgnoreCase(n))return true;return false;}
    private static String clanOf(UUID id){load();String v=DATA.getProperty("member."+id);return v==null||v.isBlank()?null:v;}
    private static String role(UUID id){return DATA.getProperty("role."+id,"MEMBER");}
    private static Set<UUID> members(String id){Set<UUID>s=new LinkedHashSet<>();String raw=DATA.getProperty("clan."+id+".members","");for(String x:raw.split(","))if(!x.isBlank())try{s.add(UUID.fromString(x));}catch(Exception ignored){}return s;}
    private static void putMembers(String id,Set<UUID>s){DATA.setProperty("clan."+id+".members",String.join(",",s.stream().map(UUID::toString).toList()));}
    private static void removeMember(String id,UUID u){Set<UUID>s=members(id);s.remove(u);putMembers(id,s);DATA.remove("member."+u);DATA.remove("role."+u);String dep=DATA.getProperty("clan."+id+".deputy","");if(dep.equals(u.toString()))DATA.setProperty("clan."+id+".deputy","");}
    private static int roleRank(String r){return "OWNER".equals(r)?0:"DEPUTY".equals(r)?1:"VETERAN".equals(r)?2:3;}
    private static void notifyClan(MinecraftServer s,String id,String text,ChatFormatting color){if(s==null)return;for(ServerPlayer p:s.getPlayerList().getPlayers())if(id.equals(clanOf(GgoIdentityBridge.idFor(p))))msg(p,text,color);}
    private static ServerPlayer findOnline(MinecraftServer s,UUID ggo){if(s==null)return null;for(ServerPlayer p:s.getPlayerList().getPlayers())if(GgoIdentityBridge.idFor(p).equals(ggo))return p;return null;}
    private static boolean takeCrystals(ServerPlayer p,long amount){if(amount<=0)return true;var r=GunnerArenaMod.RUNTIME;if(r==null)return false;var profile=r.players().profile(p);if(profile==null||profile.crystals<amount)return false;profile.crystals-=amount;r.profiles().markDirty(p.getUUID());return true;}
    private static void refundCrystals(ServerPlayer p,long amount){if(amount<=0)return;var r=GunnerArenaMod.RUNTIME;if(r==null)return;var profile=r.players().profile(p);if(profile==null)return;profile.crystals=Math.min(Long.MAX_VALUE-amount,profile.crystals)+amount;r.profiles().markDirty(p.getUUID());}
    private static void denied(ServerPlayer p){msg(p,"✦ Недостаточно прав в клане.",ChatFormatting.RED);}private static void msg(ServerPlayer p,String s,ChatFormatting c){p.sendSystemMessage(Component.literal(s).withStyle(c));}
    private static String clean(String s,int max){String x=s==null?"":s.replace('\n',' ').replace('\r',' ').strip();return x.length()>max?x.substring(0,max):x;}
    private static int clamp(int v,int a,int b){return Math.max(a,Math.min(b,v));}
    private static int intProp(String k,int d){try{return Integer.parseInt(DATA.getProperty(k,Integer.toString(d)));}catch(Exception e){return d;}}private static long longProp(String k,long d){try{return Long.parseLong(DATA.getProperty(k,Long.toString(d)));}catch(Exception e){return d;}}
    private static void load(){if(loaded)return;loaded=true;if(!Files.isRegularFile(FILE))return;try(InputStream in=Files.newInputStream(FILE)){DATA.load(in);}catch(IOException e){System.err.println("[GGO] clans load failed: "+e.getMessage());}}
    private static void save(){try{Files.createDirectories(FILE.getParent());Path tmp=FILE.resolveSibling(FILE.getFileName()+".tmp");try(OutputStream out=Files.newOutputStream(tmp)){DATA.store(out,"GunGloryOnline clans");}Files.move(tmp,FILE,StandardCopyOption.REPLACE_EXISTING);}catch(IOException e){System.err.println("[GGO] clans save failed: "+e.getMessage());}}
}
