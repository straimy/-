from pathlib import Path

# Canonical Stage 34 entrypoint used by the full contracts compile gate.
ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
TARGET = ROOT / "src/main/java/arena/forge/GgoSupplyExtractionService.java"
if not TARGET.exists():
    raise SystemExit("Stage 34: GgoSupplyExtractionService.java missing; apply Stage 26 first")

text = TARGET.read_text(encoding="utf-8")

text = text.replace(
    "import net.minecraft.server.level.ServerPlayer;\n",
    "import net.minecraft.server.MinecraftServer;\nimport net.minecraft.server.level.ServerLevel;\nimport net.minecraft.server.level.ServerPlayer;\n",
    1,
)
text = text.replace(
    "import net.minecraftforge.eventbus.api.SubscribeEvent;\n",
    "import net.minecraftforge.eventbus.api.SubscribeEvent;\nimport net.minecraftforge.event.server.ServerStoppedEvent;\nimport net.minecraftforge.server.ServerLifecycleHooks;\n",
    1,
)
text = text.replace(
    "import java.nio.file.Path;\n",
    "import java.nio.file.Path;\nimport java.nio.file.AtomicMoveNotSupportedException;\nimport java.nio.file.StandardCopyOption;\n",
    1,
)

old_consume_end = '''        }
        return removed;
    }

    private static synchronized void setPoint'''
new_consume_end = '''        }
        if(removed>0)p.getInventory().setChanged();
        return removed;
    }

    private static synchronized void setPoint'''
if old_consume_end in text:
    text = text.replace(old_consume_end, new_consume_end, 1)
elif "p.getInventory().setChanged()" not in text:
    raise SystemExit("Stage 34: inventory sync anchor missing")

old_set = '''        DATA.setProperty(k+".x",Double.toString(x));DATA.setProperty(k+".y",Double.toString(y));DATA.setProperty(k+".z",Double.toString(z));save();
    }
    private static synchronized void clearPoint(ResourceKey<Level> dimension){
        load();String k=dimension.location().toString();
        DATA.remove(k+".x");DATA.remove(k+".y");DATA.remove(k+".z");save();
    }
'''
new_set = '''        DATA.setProperty(k+".x",Double.toString(x));DATA.setProperty(k+".y",Double.toString(y));DATA.setProperty(k+".z",Double.toString(z));save();syncDimension(dimension);
    }
    private static synchronized void clearPoint(ResourceKey<Level> dimension){
        load();String k=dimension.location().toString();
        DATA.remove(k+".x");DATA.remove(k+".y");DATA.remove(k+".z");save();syncDimension(dimension);
    }
'''
if old_set in text:
    text = text.replace(old_set, new_set, 1)
elif "save();syncDimension(dimension)" not in text:
    raise SystemExit("Stage 34: extraction map sync anchor missing")

old_save = '''    private static void save(){try{Files.createDirectories(FILE.getParent());try(OutputStream out=Files.newOutputStream(FILE)){DATA.store(out,"GunGloryOnline extraction points");}}catch(Exception ignored){}}
    private record Point(double x,double y,double z){}
'''
new_save = '''    private static void save(){
        try{
            Files.createDirectories(FILE.getParent());
            Path temp=FILE.resolveSibling(FILE.getFileName()+".tmp");
            try(OutputStream out=Files.newOutputStream(temp)){DATA.store(out,"GunGloryOnline extraction points");}
            try{Files.move(temp,FILE,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException ignored){Files.move(temp,FILE,StandardCopyOption.REPLACE_EXISTING);}
        }catch(Exception ignored){}
    }

    private static void syncDimension(ResourceKey<Level> dimension){
        MinecraftServer server=ServerLifecycleHooks.getCurrentServer();
        if(server==null||dimension==null)return;
        ServerLevel level=server.getLevel(dimension);
        if(level==null)return;
        for(ServerPlayer player:level.players())GgoContractMapNetwork.sync(player);
    }

    @SubscribeEvent
    public static synchronized void serverStopped(ServerStoppedEvent event){
        DATA.clear();
        loaded=false;
    }

    private record Point(double x,double y,double z){}
'''
if old_save in text:
    text = text.replace(old_save, new_save, 1)
elif "StandardCopyOption.ATOMIC_MOVE" not in text:
    raise SystemExit("Stage 34: atomic save anchor missing")

TARGET.write_text(text, encoding="utf-8")

required = (
    "p.getInventory().setChanged()",
    "save();syncDimension(dimension)",
    "StandardCopyOption.ATOMIC_MOVE",
    "GgoContractMapNetwork.sync(player)",
    "serverStopped(ServerStoppedEvent",
)
for marker in required:
    if marker not in text:
        raise SystemExit(f"Stage 34: marker missing: {marker}")

print("GGO Extraction Runtime Stage 34 applied")
print(" - extraction coordinates save atomically")
print(" - set/clear pushes fresh map state to players in the dimension")
print(" - consumed supplies mark the player inventory dirty")
print(" - static extraction cache resets when the server stops")
