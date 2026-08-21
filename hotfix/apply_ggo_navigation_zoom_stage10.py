from pathlib import Path

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
JAVA = ROOT / "client-ui/src/main/java/arena/client/shell"
screen = JAVA / "GgoShellScreen.java"

if not screen.exists():
    raise SystemExit("GgoShellScreen.java not found; apply stages 1-9 first")

s = screen.read_text()

# Per-screen map zoom. Keep the normal UI scale independent from Navigation zoom.
field_anchor = "    private final Page page;\n"
if field_anchor in s and "private double mapZoom" not in s:
    s = s.replace(field_anchor, field_anchor + "    private double mapZoom = 0.35;\n", 1)

# Stage 9 uses a fixed map scale; make it zoom-aware.
s = s.replace("            double scale = 0.35;\n", "            double scale = mapZoom;\n", 1)

# Put zoom status into the map header.
header = '''        g.drawString(this.font, "SECTOR " + sector + "   " + x + " / " + y + " / " + z + "   FACING " + facing, mapX + 14, mapY + 14, 0xFF9AA6B7, false);'''
header_new = '''        int zoomPercent = (int)Math.round((mapZoom / 0.35) * 100.0);\n        g.drawString(this.font, "SECTOR " + sector + "   " + x + " / " + y + " / " + z + "   FACING " + facing + "   ZOOM " + zoomPercent + "%", mapX + 14, mapY + 14, 0xFF9AA6B7, false);'''
if header in s and "ZOOM \" + zoomPercent" not in s:
    s = s.replace(header, header_new, 1)

# Better hint now that wheel zoom is implemented.
s = s.replace('"MMB  PLACE PING    SHIFT+MMB  CLEAR"', '"WHEEL  ZOOM    MMB  PLACE PING    SHIFT+MMB  CLEAR"')

# Screen-level wheel handling only affects Navigation.
method_anchor = "    @Override\n    public boolean isPauseScreen() {\n"
zoom_method = r'''    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (page == Page.MAP && delta != 0.0) {
            double factor = delta > 0.0 ? 1.16 : (1.0 / 1.16);
            mapZoom = Math.max(0.15, Math.min(1.40, mapZoom * factor));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

'''
if method_anchor in s and "public boolean mouseScrolled(double mouseX, double mouseY, double delta)" not in s:
    s = s.replace(method_anchor, zoom_method + method_anchor, 1)

screen.write_text(s)
print("GGO Navigation Zoom Stage 10 applied")
print(" - mouse wheel zoom on full Navigation screen")
print(" - zoom range 0.15..1.40")
print(" - waypoint projection follows map zoom")
print(" - minimap scale remains independent")
