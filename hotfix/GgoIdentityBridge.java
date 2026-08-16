package arena.forge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;

/** Stable identity bridge used by new GunGloryOnline systems instead of exposing Minecraft UUIDs. */
public final class GgoIdentityBridge {
    private static final Object LOCK = new Object();
    private static final Properties IDS = new Properties();
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("gunnerarena").resolve("ggo-identities.properties");
    private static boolean loaded;

    private GgoIdentityBridge() {}

    public static UUID idFor(ServerPlayer player) {
        if (player == null) return new UUID(0L, 0L);
        UUID id=idForMinecraft(player.getUUID());
        rememberName(player.getGameProfile().getName(),id);
        publicIdFor(id);
        return id;
    }

    public static UUID idForMinecraft(UUID minecraftId) {
        if (minecraftId == null) return new UUID(0L, 0L);
        synchronized (LOCK) {
            load();
            String key = "minecraft." + minecraftId;
            String value = IDS.getProperty(key);
            if (value != null) {
                try { return UUID.fromString(value); }
                catch (IllegalArgumentException ignored) {}
            }
            UUID ggo = UUID.randomUUID();
            IDS.setProperty(key, ggo.toString());
            publicIdFor(ggo);
            save();
            return ggo;
        }
    }

    public static String publicIdFor(ServerPlayer player){ return publicIdFor(idFor(player)); }

    public static String publicIdFor(UUID ggo){
        if(ggo==null)return "GGO-00000000";
        synchronized(LOCK){
            load(); String key="public."+ggo; String existing=IDS.getProperty(key); if(existing!=null&&!existing.isBlank())return existing;
            long next=1; try{next=Long.parseLong(IDS.getProperty("public.counter","0"))+1;}catch(Exception ignored){}
            String id=String.format(Locale.ROOT,"GGO-%08d",next);
            IDS.setProperty("public.counter",Long.toString(next)); IDS.setProperty(key,id); IDS.setProperty("lookup."+id.toLowerCase(Locale.ROOT),ggo.toString()); save(); return id;
        }
    }

    public static UUID findKnown(String token){
        if(token==null||token.isBlank())return null;
        synchronized(LOCK){
            load(); String t=token.trim().toLowerCase(Locale.ROOT); String v=IDS.getProperty(t.startsWith("ggo-")?"lookup."+t:"name."+t);
            if(v==null){ try{return UUID.fromString(token.trim());}catch(Exception ignored){return null;} }
            try{return UUID.fromString(v);}catch(Exception ignored){return null;}
        }
    }

    private static void rememberName(String name,UUID ggo){
        if(name==null||name.isBlank()||ggo==null)return;
        synchronized(LOCK){load(); IDS.setProperty("name."+name.toLowerCase(Locale.ROOT),ggo.toString()); save();}
    }

    private static void load() {
        if (loaded) return; loaded = true;
        if (!Files.isRegularFile(FILE)) return;
        try (InputStream in = Files.newInputStream(FILE)) { IDS.load(in); }
        catch (IOException ex) { System.err.println("[GGO] identity map load failed: " + ex.getMessage()); }
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent()); Path tmp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            try (OutputStream out = Files.newOutputStream(tmp)) { IDS.store(out, "GunGloryOnline stable account identities"); }
            try { Files.move(tmp, FILE, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException ex) { Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException ex) { System.err.println("[GGO] identity map save failed: " + ex.getMessage()); }
    }
}
