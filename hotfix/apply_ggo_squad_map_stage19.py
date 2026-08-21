from pathlib import Path

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
JAVA = ROOT / "client-ui/src/main/java/arena/client/shell"
JAVA.mkdir(parents=True, exist_ok=True)

state = r'''package arena.client.shell;

import java.util.List;

public final class GgoSquadMapState {
    public record Member(String name, double x, double y, double z, String dimension, boolean downed, boolean leader) {}
    private static volatile List<Member> members = List.of();
    private GgoSquadMapState() {}
    public static void accept(List<Member> next) { members = next == null ? List.of() : List.copyOf(next); }
    public static List<Member> members() { return members; }
}
'''
(JAVA / "GgoSquadMapState.java").write_text(state)

adapter = JAVA / "GgoRuntimeV1NetworkAdapter.java"
if adapter.exists():
    s = adapter.read_text()
    old = '''            List<GgoSquadSyncBridge.MemberSnapshot> mapped = new ArrayList<>();\n            for (Object m : list) {\n                mapped.add(new GgoSquadSyncBridge.MemberSnapshot(\n                        str(m, "name"), flt(m, "health"), flt(m, "maxHealth"), bool(m, "downed"),\n                        integer(m, "pingMs"), bool(m, "voiceActive"), bool(m, "leader"), str(m, "sector"), str(m, "activity")\n                ));\n            }\n            GgoSquadSyncBridge.receive(mapped);'''
    new = '''            List<GgoSquadSyncBridge.MemberSnapshot> mapped = new ArrayList<>();\n            List<GgoSquadMapState.Member> mapMembers = new ArrayList<>();\n            for (Object m : list) {\n                mapped.add(new GgoSquadSyncBridge.MemberSnapshot(\n                        str(m, "name"), flt(m, "health"), flt(m, "maxHealth"), bool(m, "downed"),\n                        integer(m, "pingMs"), bool(m, "voiceActive"), bool(m, "leader"), str(m, "sector"), str(m, "activity")\n                ));\n                mapMembers.add(new GgoSquadMapState.Member(\n                        str(m, "name"), dbl(m, "x"), dbl(m, "y"), dbl(m, "z"), str(m, "dimension"), bool(m, "downed"), bool(m, "leader")\n                ));\n            }\n            GgoSquadSyncBridge.receive(mapped);\n            GgoSquadMapState.accept(mapMembers);'''
    if old in s:
        s = s.replace(old, new, 1)
    helper = '    private static float flt(Object o, String n) throws ReflectiveOperationException { return ((Number)value(o,n)).floatValue(); }\n'
    if helper in s and 'private static double dbl(' not in s:
        s = s.replace(helper, helper + '    private static double dbl(Object o, String n) throws ReflectiveOperationException { return ((Number)value(o,n)).doubleValue(); }\n', 1)
    adapter.write_text(s)

hud = JAVA / "GgoCombatHud.java"
if hud.exists():
    s = hud.read_text()
    anchor = '''        GgoNavigationState.Waypoint waypoint = GgoNavigationState.waypoint();'''
    squad_block = '''        String currentDimension = mc.level.dimension().location().toString();\n        for (GgoSquadMapState.Member member : GgoSquadMapState.members()) {\n            if (!currentDimension.equals(member.dimension())) continue;\n            if (member.name().equals(mc.player.getGameProfile().getName())) continue;\n            double sdx = member.x() - mc.player.getX();\n            double sdz = member.z() - mc.player.getZ();\n            double squadScale = 0.20;\n            int squadMax = size / 2 - 10;\n            int sox = (int)Math.round(Math.max(-squadMax, Math.min(squadMax, sdx * squadScale)));\n            int soy = (int)Math.round(Math.max(-squadMax, Math.min(squadMax, sdz * squadScale)));\n            int sx = cx + sox;\n            int sy = cy + soy;\n            int squadColor = member.downed() ? 0xFFE35B65 : 0xFF75C7FF;\n            g.fill(sx - 2, sy - 2, sx + 3, sy + 3, squadColor);\n            String tag = member.leader() ? "L" : member.name().substring(0, Math.min(1, member.name().length())).toUpperCase();\n            if (!tag.isBlank()) g.drawString(mc.font, tag, sx + 4, sy - 4, squadColor, false);\n        }\n\n'''
    if anchor in s and 'GgoSquadMapState.members()' not in s:
        s = s.replace(anchor, squad_block + anchor, 1)
    hud.write_text(s)

screen = JAVA / "GgoShellScreen.java"
if screen.exists():
    s = screen.read_text()
    anchor = '''        GgoNavigationState.Waypoint mapWaypoint = GgoNavigationState.waypoint();'''
    squad_block = '''        String mapDimension = mc.level.dimension().location().toString();\n        for (GgoSquadMapState.Member member : GgoSquadMapState.members()) {\n            if (!mapDimension.equals(member.dimension())) continue;\n            if (member.name().equals(mc.player.getGameProfile().getName())) continue;\n            double sdx = member.x() - mc.player.getX();\n            double sdz = member.z() - mc.player.getZ();\n            double squadScale = 0.35 * navigationZoom;\n            int squadMaxX = mapW / 2 - 24;\n            int squadMaxY = mapH / 2 - 34;\n            int sox = (int)Math.round(Math.max(-squadMaxX, Math.min(squadMaxX, sdx * squadScale)));\n            int soy = (int)Math.round(Math.max(-squadMaxY, Math.min(squadMaxY, sdz * squadScale)));\n            int sx = px + sox;\n            int sy = py + soy;\n            int squadColor = member.downed() ? 0xFFE35B65 : 0xFF75C7FF;\n            g.fill(sx - 4, sy - 4, sx + 5, sy + 5, squadColor);\n            String squadLabel = (member.leader() ? "★ " : "") + member.name();\n            g.drawString(this.font, squadLabel, sx + 8, sy - 4, squadColor, false);\n        }\n\n'''
    if anchor in s and 'String mapDimension = mc.level.dimension()' not in s:
        s = s.replace(anchor, squad_block + anchor, 1)
    screen.write_text(s)

print("GGO Squad Map Stage 19 applied")
print(" - authoritative squad x/y/z + dimension state")
print(" - allies render on optional minimap")
print(" - allies render on full Navigation map")
print(" - downed allies use warning marker")
print(" - players in other dimensions are hidden")
