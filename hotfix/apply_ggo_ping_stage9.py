from pathlib import Path

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
CLIENT = ROOT / "client-ui"
JAVA = CLIENT / "src/main/java/arena/client/shell"
JAVA.mkdir(parents=True, exist_ok=True)

hooks = JAVA / "GgoShellHooks.java"
if hooks.exists():
    s = hooks.read_text()
    if "import net.minecraft.world.phys.BlockHitResult;" not in s:
        s = s.replace("import net.minecraft.client.Minecraft;\n", "import net.minecraft.client.Minecraft;\nimport net.minecraft.world.phys.BlockHitResult;\n")

    anchor = "    @SubscribeEvent\n    public static void onPlayerListOverlay(RenderGuiOverlayEvent.Pre event) {"
    mouse_handler = r'''    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) return;
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_MIDDLE || event.getAction() != GLFW.GLFW_PRESS) return;

        // Shift + MMB clears the current waypoint. Plain MMB places a contextual
        // world ping on the block face currently under the crosshair.
        if (mc.player.isShiftKeyDown()) {
            GgoNavigationState.clearWaypoint();
            event.setCanceled(true);
            return;
        }

        if (mc.hitResult instanceof BlockHitResult hit) {
            var pos = hit.getBlockPos().relative(hit.getDirection());
            GgoNavigationState.setWaypoint(pos.getX(), pos.getY(), pos.getZ(), "PING");
            event.setCanceled(true);
        }
    }

'''
    if anchor in s and "public static void onMouseButton(InputEvent.MouseButton.Pre event)" not in s:
        s = s.replace(anchor, mouse_handler + anchor, 1)
    hooks.write_text(s)

hud = JAVA / "GgoCombatHud.java"
if hud.exists():
    s = hud.read_text()
    # Add waypoint marker to the optional minimap after the player marker.
    anchor = '''        g.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFFD34855);\n        String sector = sectorFor(mc.player.getBlockX(), mc.player.getBlockZ());'''
    replacement = '''        g.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFFD34855);\n\n        GgoNavigationState.Waypoint waypoint = GgoNavigationState.waypoint();\n        if (waypoint != null) {\n            double dx = waypoint.x() - mc.player.getX();\n            double dz = waypoint.z() - mc.player.getZ();\n            double scale = 0.20; // five world blocks per minimap pixel\n            int max = size / 2 - 10;\n            int ox = (int)Math.round(Math.max(-max, Math.min(max, dx * scale)));\n            int oy = (int)Math.round(Math.max(-max, Math.min(max, dz * scale)));\n            int wx = cx + ox;\n            int wy = cy + oy;\n            g.fill(wx - 2, wy - 2, wx + 3, wy + 3, 0xFFF0C75E);\n            int distance = (int)Math.round(GgoNavigationState.distanceTo(mc.player.getX(), mc.player.getY(), mc.player.getZ()));\n            String text = distance + "m";\n            g.drawString(mc.font, text, wx + 5, wy - 4, 0xFFF0D27A, false);\n        }\n\n        String sector = sectorFor(mc.player.getBlockX(), mc.player.getBlockZ());'''
    if anchor in s and "five world blocks per minimap pixel" not in s:
        s = s.replace(anchor, replacement, 1)
    hud.write_text(s)

screen = JAVA / "GgoShellScreen.java"
if screen.exists():
    s = screen.read_text()
    # Render waypoint relative to player at the center of the full Navigation map.
    anchor = '''        g.fill(px - 3, py - 3, px + 4, py + 4, 0xFFD84855);\n        g.drawString(this.font, "YOU", px + 10, py - 4, 0xFFF0F2F5, false);'''
    replacement = '''        g.fill(px - 3, py - 3, px + 4, py + 4, 0xFFD84855);\n        g.drawString(this.font, "YOU", px + 10, py - 4, 0xFFF0F2F5, false);\n\n        GgoNavigationState.Waypoint mapWaypoint = GgoNavigationState.waypoint();\n        if (mapWaypoint != null) {\n            double dx = mapWaypoint.x() - mc.player.getX();\n            double dz = mapWaypoint.z() - mc.player.getZ();\n            double scale = 0.35;\n            int maxX = mapW / 2 - 24;\n            int maxY = mapH / 2 - 34;\n            int ox = (int)Math.round(Math.max(-maxX, Math.min(maxX, dx * scale)));\n            int oy = (int)Math.round(Math.max(-maxY, Math.min(maxY, dz * scale)));\n            int wx = px + ox;\n            int wy = py + oy;\n            g.fill(wx - 4, wy - 4, wx + 5, wy + 5, 0xFFF0C75E);\n            int distance = (int)Math.round(GgoNavigationState.distanceTo(mc.player.getX(), mc.player.getY(), mc.player.getZ()));\n            g.drawString(this.font, mapWaypoint.label() + "  " + distance + "m", wx + 9, wy - 4, 0xFFF3D482, false);\n        }'''
    if anchor in s and "GgoNavigationState.Waypoint mapWaypoint" not in s:
        s = s.replace(anchor, replacement, 1)

    # Improve bottom hint now that MMB is functional.
    s = s.replace('"MMB  PLACE PING    No active waypoint"', '"MMB  PLACE PING    SHIFT+MMB  CLEAR"')
    screen.write_text(s)

print("GGO Ping / Waypoint Stage 9 applied")
print(" - MMB places contextual block-face waypoint")
print(" - Shift+MMB clears waypoint")
print(" - waypoint marker rendered on optional minimap")
print(" - waypoint marker rendered on full Navigation map")
print(" - HUD keeps shared waypoint distance/status from Stage 8")
