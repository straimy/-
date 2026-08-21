from pathlib import Path

ROOT=Path("ga-build") if Path("ga-build").exists() else Path(".")
JAVA=ROOT/"client-ui/src/main/java/arena/client/shell"
JAVA.mkdir(parents=True,exist_ok=True)

state=r'''package arena.client.shell;

public final class GgoBrMapState {
    public record Zone(boolean active,double centerX,double centerZ,double radius){}
    private static volatile Zone zone=new Zone(false,0,0,0);
    private GgoBrMapState(){}
    public static void update(boolean active,double x,double z,double radius){zone=new Zone(active,x,z,Math.max(0,radius));}
    public static Zone zone(){return zone;}
}
'''
(JAVA/"GgoBrMapState.java").write_text(state)

hud=JAVA/"GgoRuntimeV1HudAdapter.java"
if hud.exists():
    s=hud.read_text()
    old='            latest = new GgoObjectiveState.Snapshot(\n                    str(packet, "title"), str(packet, "description"), str(packet, "progress"), str(packet, "activity"), bool(packet, "available")\n            );\n'
    new=old+'            double zx=dbl(packet,"zoneCenterX"), zz=dbl(packet,"zoneCenterZ"), zr=dbl(packet,"zoneRadius");\n            GgoBrMapState.update(zr>0.0D,zx,zz,zr);\n'
    if old in s and 'GgoBrMapState.update' not in s:s=s.replace(old,new,1)
    helper='    private static boolean bool(Object o, String n) throws ReflectiveOperationException { return Boolean.TRUE.equals(value(o,n)); }\n'
    if helper in s and 'private static double dbl' not in s:s=s.replace(helper,helper+'    private static double dbl(Object o,String n) throws ReflectiveOperationException { return ((Number)value(o,n)).doubleValue(); }\n',1)
    hud.write_text(s)

screen=JAVA/"GgoShellScreen.java"
if screen.exists():
    s=screen.read_text()
    anchor='        g.fill(px - 3, py - 3, px + 4, py + 4, 0xFFD84855);\n'
    if anchor in s and 'GgoBrMapState.Zone zone' not in s:
        extra=r'''        GgoBrMapState.Zone zone=GgoBrMapState.zone();
        if(zone.active() && zone.radius()>0.0D){
            int zcx=(int)Math.round(px+(zone.centerX()-mc.player.getX())*mapZoom);
            int zcy=(int)Math.round(py+(zone.centerZ()-mc.player.getZ())*mapZoom);
            double rr=zone.radius()*mapZoom;
            for(int i=0;i<72;i++){
                double a=(Math.PI*2.0D*i)/72.0D;
                int sx=(int)Math.round(zcx+Math.cos(a)*rr);
                int sy=(int)Math.round(zcy+Math.sin(a)*rr);
                if(sx>=mapX&&sx<mapX+mapW&&sy>=mapY&&sy<mapY+mapH)g.fill(sx-1,sy-1,sx+2,sy+2,0xFFD34855);
            }
            g.drawString(this.font,"SAFE ZONE  "+(int)Math.round(zone.radius())+"m",mapX+14,mapY+50,0xFFD34855,false);
        }
'''
        s=s.replace(anchor,extra+anchor,1)
    screen.write_text(s)

print("GGO BR Map Client Stage 24 applied")
print(" - Full Navigation renders authoritative safe-zone circle")
print(" - center/radius come from server HUD protocol v3")
