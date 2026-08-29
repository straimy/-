#!/usr/bin/env python3
from pathlib import Path
import re, shutil, runpy

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
SHELL_DIR = ROOT / "client-ui/src/main/java/arena/client/shell"
SHELL = SHELL_DIR / "GgoShellScreen.java"
HOOKS = SHELL_DIR / "GgoShellHooks.java"
FRONTEND = Path("hotfix/GgoFrontEndScreen.java")

if not SHELL.is_file() or not HOOKS.is_file() or not FRONTEND.is_file():
    raise SystemExit("Stage111 requires generated Stage110 shell plus canonical frontend")

# Final canonical frontend: launcher PLAY auto-connects; no second PLAY ONLINE click.
shutil.copy2(FRONTEND, SHELL_DIR / "GgoFrontEndScreen.java")

# M is a local UI action. It must never wait for /menu over VPN/high-latency links.
hooks = HOOKS.read_text(encoding="utf-8")
hooks = hooks.replace(
    'mc.player.connection.sendCommand("menu");',
    'mc.setScreen(new GgoShellScreen(GgoShellScreen.Page.HOME));',
)

# Own TAB in one final subscriber below. Remove any older handler so it cannot double-render or be
# overwritten by intermediate shell patches.
hooks = re.sub(
    r'\n\s*@SubscribeEvent\n\s*public static void onPlayerListOverlay\(RenderGuiOverlayEvent\.Pre event\) \{.*?\n\s*\}\n',
    '\n',
    hooks,
    count=1,
    flags=re.S,
)
HOOKS.write_text(hooks, encoding="utf-8")

# EXIT TO GGO means leave the engine process and return to the supervising launcher. This avoids a
# disconnect-screen race and also matches the one-application lifecycle.
shell = SHELL.read_text(encoding="utf-8")
shell = re.sub(
    r'    private void exitToGgo\(\) \{.*?\n    \}\n',
    '''    private void exitToGgo() {\n        Minecraft.getInstance().stop();\n    }\n''',
    shell,
    count=1,
    flags=re.S,
)
SHELL.write_text(shell, encoding="utf-8")

# Final TAB owner. Only squad state is shown; never enumerate the server's player list.
tab = r'''package arena.client.shell;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoStage111TabOverlay {
    private GgoStage111TabOverlay() {}

    @SubscribeEvent
    public static void onPlayerList(RenderGuiOverlayEvent.Pre event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.PLAYER_LIST.id())) return;
        event.setCanceled(true);

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.options.keyPlayerList.isDown()) return;
        var members = GgoSquadOverlayState.current(mc);
        GuiGraphics g = event.getGuiGraphics();
        int width = mc.getWindow().getGuiScaledWidth();
        int boxW = Math.min(440, width - 32);
        int rows = Math.max(1, Math.min(6, members.size()));
        int x = (width - boxW) / 2;
        int y = 16;
        int boxH = 34 + rows * 24 + 22;

        g.fill(x, y, x + boxW, y + boxH, 0xEE080B11);
        g.fill(x, y, x + 3, y + boxH, 0xFFD54855);
        g.drawString(mc.font, "SQUAD", x + 14, y + 11, 0xFFF2F5F8, false);
        g.drawString(mc.font, "GGO ONLINE", x + boxW - 74, y + 11, 0xFF72C391, false);

        int rowY = y + 34;
        if (members.isEmpty()) {
            g.drawString(mc.font, "NO SQUAD", x + 14, rowY + 7, 0xFF697688, false);
        } else {
            for (var m : members.stream().limit(6).toList()) {
                String leader = m.leader() ? "◆ " : "  ";
                String hp = m.downed() ? "DOWN" : Math.max(0, m.health()) + "/" + Math.max(1, m.maxHealth());
                g.drawString(mc.font, leader + m.name(), x + 14, rowY + 7, m.downed() ? 0xFFE18A55 : 0xFFE9EDF2, false);
                g.drawString(mc.font, hp, x + 190, rowY + 7, 0xFF9DA8B8, false);
                g.drawString(mc.font, Math.max(0, m.ping()) + " ms", x + 258, rowY + 7, 0xFF7D8999, false);
                if (m.voiceActive()) g.drawString(mc.font, "VOICE", x + 318, rowY + 7, 0xFF72C391, false);
                g.drawString(mc.font, m.sector(), x + boxW - 54, rowY + 7, 0xFF7D8999, false);
                rowY += 24;
            }
        }
        g.drawString(mc.font, "TAB  HOLD", x + 14, y + boxH - 15, 0xFF596575, false);
    }
}
'''
(SHELL_DIR / "GgoStage111TabOverlay.java").write_text(tab, encoding="utf-8")

# Merge the retained real-data Shop/Profile/Skills screens into the same graphite/red GGO shell.
runpy.run_path("hotfix/apply_ggo_stage111_legacy_style.py", run_name="__main__")

# Final invariants.
final_hooks = HOOKS.read_text(encoding="utf-8")
final_shell = SHELL.read_text(encoding="utf-8")
final_frontend = (SHELL_DIR / "GgoFrontEndScreen.java").read_text(encoding="utf-8")
assert 'sendCommand("menu")' not in final_hooks
assert 'new GgoShellScreen(GgoShellScreen.Page.HOME)' in final_hooks
assert 'Minecraft.getInstance().stop();' in final_shell
assert 'GgoEntryExperience.requestReturnToFrontend();' not in final_shell
assert 'mc.execute(this::connectOfficial);' in final_frontend
assert 'PLAY ONLINE' not in final_frontend
print("Applied GGO Stage111 UX shell")
print(" - launcher PLAY auto-connects to official server")
print(" - M opens GGO Hub locally with zero server round-trip")
print(" - TAB is GGO squad-only overlay; vanilla player list is always canceled")
print(" - EXIT TO GGO terminates engine cleanly for launcher supervisor")
print(" - retained Shop/Profile/Skills are visually merged into GGO")
