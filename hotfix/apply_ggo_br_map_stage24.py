from pathlib import Path

ROOT=Path("ga-build") if Path("ga-build").exists() else Path(".")
JAVA=ROOT/"src/main/java/arena/forge"

br=JAVA/"BattleRoyaleService.java"
if br.exists():
    s=br.read_text()
    anchor='    public static int phase() { return phase; }\n'
    add='    public static double centerX() { return centerX; }\n    public static double centerZ() { return centerZ; }\n'
    if anchor in s and 'public static double centerX()' not in s:s=s.replace(anchor,anchor+add,1)
    br.write_text(s)

net=JAVA/"GgoHudNetwork.java"
if net.exists():
    s=net.read_text().replace('private static final String VERSION="2";','private static final String VERSION="3";')
    old='        b.writeVarInt(Math.max(0,s.zonePhase()));b.writeVarInt(Math.max(0,s.secondsRemaining()));b.writeBoolean(s.playerAlive());\n'
    new=old+'        b.writeDouble(s.zoneCenterX());b.writeDouble(s.zoneCenterZ());b.writeDouble(Math.max(0.0D,s.zoneRadius()));\n'
    if old in s and 's.zoneCenterX()' not in s:s=s.replace(old,new,1)
    old='                b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readBoolean());\n'
    new='                b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readBoolean(),b.readDouble(),b.readDouble(),b.readDouble());\n'
    if old in s:s=s.replace(old,new,1)
    old='                           int alive,int total,int placement,int zonePhase,int secondsRemaining,boolean playerAlive){}\n'
    new='                           int alive,int total,int placement,int zonePhase,int secondsRemaining,boolean playerAlive,\n                           double zoneCenterX,double zoneCenterZ,double zoneRadius){}\n'
    if old in s:s=s.replace(old,new,1)
    net.write_text(s)

state=JAVA/"GgoHudStateService.java"
if state.exists():
    s=state.read_text()
    # Extend all snapshot constructors with authoritative zone geometry.
    s=s.replace('alive,total,placement,0,seconds,playerAlive);','alive,total,placement,0,seconds,playerAlive,BattleRoyaleService.centerX(),BattleRoyaleService.centerZ(),BattleRoyaleService.radius());')
    s=s.replace('alive,total,placement,phase,seconds,playerAlive);','alive,total,placement,phase,seconds,playerAlive,BattleRoyaleService.centerX(),BattleRoyaleService.centerZ(),BattleRoyaleService.radius());')
    s=s.replace('alive,total,placement,phase,seconds,playerAlive);','alive,total,placement,phase,seconds,playerAlive,BattleRoyaleService.centerX(),BattleRoyaleService.centerZ(),BattleRoyaleService.radius());')
    s=s.replace('0,0,0,0,0,true);','0,0,0,0,0,true,0.0D,0.0D,0.0D);')
    s=s.replace('true,0,0,0,0,0,true\n','true,0,0,0,0,0,true,0.0D,0.0D,0.0D\n')
    s=s.replace('return new GgoHudNetwork.Snapshot(activity,"","","",false,0,0,0,0,0,true);','return new GgoHudNetwork.Snapshot(activity,"","","",false,0,0,0,0,0,true,0.0D,0.0D,0.0D);')
    state.write_text(s)

print("GGO BR Map Stage 24 applied")
print(" - HUD protocol v3")
print(" - authoritative BR center/radius exported")
