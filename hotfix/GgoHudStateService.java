package arena.forge;

import net.minecraft.server.level.ServerPlayer;

public final class GgoHudStateService {
    private GgoHudStateService(){}

    public static GgoHudNetwork.Snapshot snapshot(ServerPlayer player){
        if(player==null)return empty("OPEN WORLD");
        BattleRoyaleService.State state=BattleRoyaleService.state();
        if(BattleRoyaleService.isParticipant(player)){
            int alive=BattleRoyaleService.aliveCount();
            int total=BattleRoyaleService.participantCount();
            int placement=BattleRoyaleService.placement(player.getUUID());
            int phase=BattleRoyaleService.phase()+1;
            int seconds=BattleRoyaleService.remainingSeconds(player.getServer());
            boolean playerAlive=BattleRoyaleService.isAlive(player.getUUID());
            return switch(state){
                case COUNTDOWN -> new GgoHudNetwork.Snapshot(
                        "BATTLE ROYALE","GET READY","Deployment countdown",
                        "START IN "+seconds+"s",true,alive,total,placement,0,seconds,playerAlive);
                case RUNNING -> new GgoHudNetwork.Snapshot(
                        "BATTLE ROYALE",playerAlive?"SURVIVE":"ELIMINATED","Stay inside the safe zone",
                        "ALIVE "+alive+"/"+total+"  •  ZONE "+phase+"  •  "+seconds+"s",true,
                        alive,total,placement,phase,seconds,playerAlive);
                case FINISHED -> new GgoHudNetwork.Snapshot(
                        "BATTLE ROYALE",placement==1?"VICTORY":"MATCH FINISHED","Returning to GGO",
                        placement>0?"PLACEMENT #"+placement:"FINISHED",true,
                        alive,total,placement,phase,seconds,playerAlive);
                default -> empty("BATTLE ROYALE");
            };
        }
        if(BattleRoyaleService.queued(player.getUUID()))return new GgoHudNetwork.Snapshot(
                "BATTLE ROYALE","MATCHMAKING","Waiting for players","IN QUEUE",true,
                0,0,0,0,0,true);
        return empty("OPEN WORLD");
    }

    private static GgoHudNetwork.Snapshot empty(String activity){
        return new GgoHudNetwork.Snapshot(activity,"","","",false,0,0,0,0,0,true);
    }
}
