package arena.forge;

import net.minecraft.server.level.ServerPlayer;

public final class GgoHudStateService {
    private GgoHudStateService(){}

    public static GgoHudNetwork.Snapshot snapshot(ServerPlayer player){
        if(player==null)return new GgoHudNetwork.Snapshot("OPEN WORLD","","","",false);
        BattleRoyaleService.State state=BattleRoyaleService.state();
        if(BattleRoyaleService.isParticipant(player)){
            return switch(state){
                case COUNTDOWN -> new GgoHudNetwork.Snapshot("BATTLE ROYALE","GET READY","Deployment countdown","PHASE 0",true);
                case RUNNING -> new GgoHudNetwork.Snapshot("BATTLE ROYALE","SURVIVE","Stay inside the safe zone","ZONE "+(BattleRoyaleService.phase()+1)+" • "+Math.round(BattleRoyaleService.radius())+"m",true);
                case FINISHED -> new GgoHudNetwork.Snapshot("BATTLE ROYALE","MATCH FINISHED","Returning to GGO","",true);
                default -> new GgoHudNetwork.Snapshot("BATTLE ROYALE","","","",false);
            };
        }
        if(BattleRoyaleService.queued(player.getUUID()))return new GgoHudNetwork.Snapshot("BATTLE ROYALE","MATCHMAKING","Waiting for players","IN QUEUE",true);
        return new GgoHudNetwork.Snapshot("OPEN WORLD","","","",false);
    }
}
