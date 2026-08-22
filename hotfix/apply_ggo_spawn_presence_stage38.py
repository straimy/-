#!/usr/bin/env python3
from pathlib import Path
import shutil

ROOT = Path("ga-build/src/main/java/arena/forge")
SOURCE = Path("hotfix/GgoPlayerPresencePolicy.java")
TARGET = ROOT / "GgoPlayerPresencePolicy.java"
GAMEPLAY = ROOT / "MinimalGameplayFixes.java"

if not ROOT.is_dir():
    raise SystemExit("ga-build source tree is missing")
if not SOURCE.is_file():
    raise SystemExit("GgoPlayerPresencePolicy.java is missing")
if not GAMEPLAY.is_file():
    raise SystemExit("MinimalGameplayFixes.java is missing")

shutil.copy2(SOURCE, TARGET)
text = GAMEPLAY.read_text(encoding="utf-8")

old_state = "if(!r.auth().isAuthenticated(p)){clearQueueState(p);continue;}ArenaPlayerState state=r.players().session(p).state();\n            if(state==ArenaPlayerState.LOBBY||state==ArenaPlayerState.QUEUED){p.setInvisible(true);p.setGameMode(GameType.ADVENTURE);selectEmptyHotbarSlot(p);ensureMenuCompass(p);if(state==ArenaPlayerState.LOBBY){JOIN_AT.remove(p.getUUID());LAST_COUNT.remove(p.getUUID());}else JOIN_AT.putIfAbsent(p.getUUID(),now+20L);}\n            else if(state==ArenaPlayerState.ALIVE||state==ArenaPlayerState.SPAWNING){p.setInvisible(false);p.setGameMode(GameType.ADVENTURE);clearQueueState(p);}" 
new_state = "if(!r.auth().isAuthenticated(p)){clearQueueState(p);continue;}ArenaPlayerState state=r.players().session(p).state();GgoPlayerPresencePolicy.apply(p,state,r);\n            if(state==ArenaPlayerState.LOBBY||state==ArenaPlayerState.QUEUED){p.setGameMode(GameType.ADVENTURE);if(state==ArenaPlayerState.LOBBY){JOIN_AT.remove(p.getUUID());LAST_COUNT.remove(p.getUUID());}else JOIN_AT.putIfAbsent(p.getUUID(),now+20L);}\n            else if(state==ArenaPlayerState.ALIVE||state==ArenaPlayerState.SPAWNING){p.setGameMode(GameType.ADVENTURE);clearQueueState(p);}" 
if old_state not in text:
    raise SystemExit("stage38 player-state marker not found")
text = text.replace(old_state, new_state, 1)

old_tnt = "for(ServerPlayer p:level.players()){double d=p.distanceToSqr(x,y,z);if(d<=49)p.hurt(p.damageSources().generic(),8);if(d<=100)p.addEffect(new MobEffectInstance(MobEffects.POISON,60,0));}"
new_tnt = "for(ServerPlayer p:level.players()){if(lobbyProtected(p))continue;double d=p.distanceToSqr(x,y,z);if(d<=49)p.hurt(p.damageSources().generic(),8);if(d<=100)p.addEffect(new MobEffectInstance(MobEffects.POISON,60,0));}"
if old_tnt not in text:
    raise SystemExit("stage38 TNT safety marker not found")
text = text.replace(old_tnt, new_tnt, 1)

old_fire = "for(ServerPlayer p:f.level.players())if(p.distanceToSqr(f.x,f.groundY,f.z)<=25){p.hurt(p.damageSources().onFire(),5);p.setSecondsOnFire(4);}"
new_fire = "for(ServerPlayer p:f.level.players())if(!lobbyProtected(p)&&p.distanceToSqr(f.x,f.groundY,f.z)<=25){p.hurt(p.damageSources().onFire(),5);p.setSecondsOnFire(4);}"
if old_fire not in text:
    raise SystemExit("stage38 fire safety marker not found")
text = text.replace(old_fire, new_fire, 1)

# The menu is virtual (M). Remove the obsolete physical compass interaction path instead of
# continuously injecting a vanilla item which SpawnLoadoutGuard then removes again.
start = text.find("    @SubscribeEvent(priority=EventPriority.HIGHEST,receiveCanceled=true)\n    public static void onRightClickItem")
end = text.find("\n    @SubscribeEvent(priority=EventPriority.HIGHEST,receiveCanceled=true)\n    public static void onEntityInteract", start)
if start < 0 or end < 0:
    raise SystemExit("stage38 legacy compass handler block not found")
legacy_block = text[start:end]
open_main = "    private static void openMain(ServerPlayer p){ArenaRuntime r=GunnerArenaMod.RUNTIME;if(r==null)return;if(!r.auth().isAuthenticated(p)){r.auth().deny(p);return;}ArenaNetwork.openUi(p,ArenaNetwork.UiTarget.MAIN);}\n"
if open_main not in legacy_block:
    raise SystemExit("stage38 openMain marker not found in compass block")
text = text[:start] + open_main + text[end:]

text = text.replace('    private static final String MENU_COMPASS_TAG="gunnerarena_menu_compass";\n', "", 1)

helper_start = text.find("    private static boolean isMenuCompassFor(ServerPlayer p,ItemStack s)")
helper_end = text.find("    private static void spawnLegacyAmmo", helper_start)
if helper_start < 0 or helper_end < 0:
    raise SystemExit("stage38 legacy compass helper block not found")
text = text[:helper_start] + text[helper_end:]

# Fail closed if any old lobby invisibility/menu injection survived.
for forbidden in ["p.setInvisible(true);p.setGameMode", "ensureMenuCompass(p)", "isMenuCompassFor(", "MENU_COMPASS_TAG"]:
    if forbidden in text:
        raise SystemExit(f"stage38 forbidden legacy behavior remains: {forbidden}")

for required in [
    "GgoPlayerPresencePolicy.apply(p,state,r)",
    "if(lobbyProtected(p))continue;double d=",
    "if(!lobbyProtected(p)&&p.distanceToSqr",
]:
    if required not in text:
        raise SystemExit(f"stage38 required behavior missing: {required}")

GAMEPLAY.write_text(text, encoding="utf-8")
print("Applied GGO Stage 38 social-spawn presence and safe-status policy")
