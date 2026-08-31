#!/usr/bin/env python3
from pathlib import Path
import shutil

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
SHELL_DIR = ROOT / "client-ui/src/main/java/arena/client/shell"
SHELL = SHELL_DIR / "GgoShellScreen.java"
HOOKS = SHELL_DIR / "GgoShellHooks.java"
ROUTER = SHELL_DIR / "GgoRouteController.java"

for required in [SHELL, HOOKS]:
    if not required.is_file():
        raise SystemExit(f"Stage118 missing generated source: {required}")
source = Path("hotfix/GgoRouteController.java")
if not source.is_file():
    raise SystemExit("Stage118 missing canonical GgoRouteController.java")
shutil.copy2(source, ROUTER)

shell = SHELL.read_text(encoding="utf-8")
hooks = HOOKS.read_text(encoding="utf-8")

# All GgoShellScreen page buttons use the same controller as keyboard navigation.
shell = shell.replace('button -> open(target)', 'button -> GgoRouteController.open(target)')
for page in ["ACTIVITIES", "INVENTORY", "SHOP", "PROFILE", "SOCIAL", "HOME"]:
    shell = shell.replace(f'b -> open(Page.{page})', f'b -> GgoRouteController.open(Page.{page})')

# Pause/settings paths also remain first-party and use the canonical router.
shell = shell.replace(
    'b -> Minecraft.getInstance().setScreen(new GgoSettingsScreen(this))',
    'b -> GgoRouteController.settings(this)',
)

# Stage114 migrates vanilla Button -> GgoButton, so accept either representation. This patch runs
# after Stage114 and must therefore primarily rewrite the first-party widget form.
for widget in ["GgoButton", "Button"]:
    shell = shell.replace(
        f'{widget}.builder(Component.literal("TRAINING"), b -> runClientCommand("play"))',
        f'{widget}.builder(Component.literal("TRAINING"), b -> GgoRouteController.training())',
    )
    shell = shell.replace(
        f'{widget} br = {widget}.builder(Component.literal("BATTLE ROYALE — SOON"), b -> {{}}).bounds(x + w + 16, y, w, 28).build(); br.active = false; addRenderableWidget(br);',
        f'{widget} br = {widget}.builder(Component.literal("BATTLE ROYALE · PREPARING"), b -> GgoRouteController.battleRoyale()).bounds(x + w + 16, y, w, 28).build(); addRenderableWidget(br);',
    )
    shell = shell.replace(
        f'{widget} events = {widget}.builder(Component.literal("EVENTS — SOON"), b -> GgoRouteController.open(Page.SEASON)).bounds(x + (w + 16) * 2, y, w, 28).build(); events.active = false; addRenderableWidget(events);',
        f'{widget} events = {widget}.builder(Component.literal("EVENTS"), b -> GgoRouteController.events()).bounds(x + (w + 16) * 2, y, w, 28).build(); addRenderableWidget(events);',
    )
    shell = shell.replace(
        f'{widget} events = {widget}.builder(Component.literal("EVENTS — SOON"), b -> open(Page.SEASON)).bounds(x + (w + 16) * 2, y, w, 28).build(); events.active = false; addRenderableWidget(events);',
        f'{widget} events = {widget}.builder(Component.literal("EVENTS"), b -> GgoRouteController.events()).bounds(x + (w + 16) * 2, y, w, 28).build(); addRenderableWidget(events);',
    )

# Retained real data screens also enter via the same router, rather than each button knowing the
# legacy bridge implementation.
shell = shell.replace('b -> GgoLegacyUiBridge.openShop()', 'b -> GgoRouteController.store()')
shell = shell.replace('b -> GgoLegacyUiBridge.openProfile()', 'b -> GgoRouteController.profile()')
shell = shell.replace('b -> GgoLegacyUiBridge.openSkills()', 'b -> GgoRouteController.skills()')
shell = shell.replace('b -> runClientCommand("friends")', 'b -> GgoRouteController.friends()')
shell = shell.replace('b -> runClientCommand("clan")', 'b -> GgoRouteController.clans()')

# Canonical hotkeys now call exactly the same routes as buttons. M/N/J/G are direct gameplay key
# routes. E and ESC are engine-open interceptions, so they use the controller's screen factory.
for page, method in [
    ("HOME", "hub"),
    ("MAP", "navigation"),
    ("ACTIVITIES", "activities"),
]:
    hooks = hooks.replace(
        f'mc.setScreen(new GgoShellScreen(GgoShellScreen.Page.{page}));',
        f'GgoRouteController.{method}();',
    )
hooks = hooks.replace('GgoLegacyUiBridge.openShop();', 'GgoRouteController.store();')
hooks = hooks.replace(
    'event.setNewScreen(new GgoShellScreen(GgoShellScreen.Page.INVENTORY));',
    'event.setNewScreen(GgoRouteController.screen(GgoShellScreen.Page.INVENTORY));',
)
hooks = hooks.replace(
    'event.setNewScreen(new GgoShellScreen(GgoShellScreen.Page.PAUSE));',
    'event.setNewScreen(GgoRouteController.screen(GgoShellScreen.Page.PAUSE));',
)

SHELL.write_text(shell, encoding="utf-8")
HOOKS.write_text(hooks, encoding="utf-8")

shell = SHELL.read_text(encoding="utf-8")
hooks = HOOKS.read_text(encoding="utf-8")
router = ROUTER.read_text(encoding="utf-8")

checks = {
    "router copied": "public final class GgoRouteController" in router and "public static GgoShellScreen screen" in router,
    "top nav router": "button -> GgoRouteController.open(target)" in shell,
    "training button": "GgoRouteController.training()" in shell,
    "BR button interactive": "BATTLE ROYALE · PREPARING" in shell and "GgoRouteController.battleRoyale()" in shell and "br.active = false" not in shell,
    "events button interactive": 'Component.literal("EVENTS")' in shell and "GgoRouteController.events()" in shell and "events.active = false" not in shell,
    "store button router": "GgoRouteController.store()" in shell,
    "profile button router": "GgoRouteController.profile()" in shell,
    "skills button router": "GgoRouteController.skills()" in shell,
    "M keyboard parity": "GgoRouteController.hub();" in hooks,
    "E keyboard parity": "GgoRouteController.screen(GgoShellScreen.Page.INVENTORY)" in hooks,
    "ESC keyboard parity": "GgoRouteController.screen(GgoShellScreen.Page.PAUSE)" in hooks,
    "N keyboard parity": "GgoRouteController.navigation();" in hooks,
    "J keyboard parity": "GgoRouteController.activities();" in hooks,
    "G keyboard parity": "GgoRouteController.store();" in hooks,
    "no vanilla routing": "TitleScreen" not in router and "OptionsScreen" not in router and "SelectWorldScreen" not in router,
}
for label, ok in checks.items():
    if not ok:
        raise SystemExit(f"Stage118 route parity failed: {label}")

print("Applied GGO Stage118 button/hotkey route parity")
print(" - M/E/N/J/G/ESC and visible GGO buttons share one navigation controller")
print(" - Training remains the real playable activity")
print(" - Battle Royale button is interactive but stays PREPARING until the authoritative map stage")
print(" - Events opens the first-party Season/Events surface")
print(" - no route can open vanilla Minecraft navigation")
