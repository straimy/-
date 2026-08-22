package arena.forge;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Server-owned, restart-reloadable contract balance configuration. */
public final class GgoContractBalance {
    private static final Path FILE=FMLPaths.CONFIGDIR.get().resolve("gunnerarena").resolve("ggo-contract-balance.properties");
    private static final Properties DATA=new Properties();
    private static boolean loaded;

    private GgoContractBalance(){}

    public static int target(String contractId,int fallback){
        load();
        return clampInt(DATA.getProperty(contractId+".target"),fallback,1,100000);
    }
    public static int reward(String contractId,int fallback){
        load();
        return clampInt(DATA.getProperty(contractId+".reward_credits"),fallback,0,1000000);
    }
    public static double distanceDrillMeters(){
        load();
        return clampDouble(DATA.getProperty("distance_drill.min_distance_blocks"),24.0D,8.0D,512.0D);
    }

    private static synchronized void load(){
        if(loaded)return;
        loaded=true;
        defaults();
        try{
            Files.createDirectories(FILE.getParent());
            if(Files.exists(FILE)){
                try(InputStream in=Files.newInputStream(FILE)){DATA.load(in);}
            }else{
                try(OutputStream out=Files.newOutputStream(FILE)){DATA.store(out,"GunGloryOnline contract balance v1");}
            }
        }catch(Exception ignored){
            defaults();
        }
    }
    private static void defaults(){
        DATA.setProperty("field_test.target","10");
        DATA.setProperty("field_test.reward_credits","1200");
        DATA.setProperty("supply_run.target","5");
        DATA.setProperty("supply_run.reward_credits","900");
        DATA.setProperty("distance_drill.target","8");
        DATA.setProperty("distance_drill.reward_credits","700");
        DATA.setProperty("distance_drill.min_distance_blocks","24.0");
    }
    private static int clampInt(String value,int fallback,int min,int max){
        try{return Math.max(min,Math.min(max,Integer.parseInt(value)));}
        catch(RuntimeException ignored){return Math.max(min,Math.min(max,fallback));}
    }
    private static double clampDouble(String value,double fallback,double min,double max){
        try{return Math.max(min,Math.min(max,Double.parseDouble(value)));}
        catch(RuntimeException ignored){return Math.max(min,Math.min(max,fallback));}
    }
}
