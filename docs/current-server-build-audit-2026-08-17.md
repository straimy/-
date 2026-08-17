# GunGloryOnline current server build audit — 2026-08-17

Source: full server archive supplied for migration review.

## High-level inventory

- Forge 1.20.1 / 47.4.10 server layout.
- Current Core: `gungloryonline-core-0.9.7-v40.jar`.
- World present under `world/`.
- 19 entries under `mods/`, including one nested archive and `desktop.ini`.
- Legacy `plugins/Citizens` and `plugins/Denizen` data exists, but neither plugin is active in the current Forge startup log.
- Duplicate/backup content exists (`1/`, nested `.tar.gz` archives, older map patch archives) and should not enter production FULL-VDS bundles.

## Current server properties that must change for production GGO

- `enable-command-block=true` -> target `false` after command migration.
- `online-mode=false` + SAuth -> temporary compatibility only; target verified GGO/Microsoft account bridge.
- `gamemode=survival` -> GGO server rules enforce game-owned interaction; normal players should not rely on vanilla survival rules.
- Runtime was observed starting on Java 25; production target stays Java 17 for Forge 1.20.1 consistency.

## World command-block audit

Exact Anvil/NBT scan found **992 command blocks**:

- 498 chain command blocks
- 452 impulse command blocks
- 42 repeating command blocks
- 506 have `auto=1`
- 567 unique exact command strings

Command prefix counts:

- 419 `execute`
- 278 `setblock`
- 118 `summon`
- 68 direct `scoreboard` commands (many more scoreboard operations occur inside `execute`)
- 23 `kill`
- 20 `title`
- 10 `fill`
- 6 `tag`
- 5 `effect`
- 4 `tp`
- plus gamerule/gamemode/team/spawnpoint/tellraw/playsound/data/clear.

A very large command-machine cluster exists around X=32..63, Z=0..31 (about 700 command blocks). This is the main legacy game-control room rather than 700 independent gameplay systems.

## Recovered legacy Classic Arena behavior

The command system contains a complete old match state machine:

1. Lobby / start trigger.
2. Require at least two players.
3. Reset teams/scoreboards/player state.
4. Procedurally generate the arena from random structure/chunk templates.
5. Select three guns and associated ammo limits/respawn timings.
6. Countdown 3-2-1.
7. Match begins.
8. Win target is `players_count * 10` kills.
9. Winner presentation.
10. Cleanup, return players to lobby, remove temporary game state.

Recovered presentation text explicitly describes the mode as:

- kill target based on player count;
- ammo and health pickups on the map;
- arena auto-generated before each game;
- bunnyhop + dash movement;
- projectile travel requiring leading targets.

This should be preserved as **Classic Arena** and reimplemented as typed Core services rather than deleted.

## Major command-driven subsystems

### Legacy map generator

Uses `cg_random_chunk` marker entities, structure blocks, random IDs, redstone/setblock/fill chains and counters such as:

- `#cg_chunk_count`
- `#empty_cg_chunks_count`
- `#s_health_cg_chunks_count`
- `#gun_cg_chunks_count`

Target: `ClassicArenaMapGenerator` / declarative structure-pool data.

### Item / ammo spawners

Uses marker entities tagged `item_spawner`, dynamically generated command strings, scoreboard timers and item entities for:

- `gun_1_ammo`
- `gun_2_ammo`
- `gun_3_ammo`
- `random_gun_ammo`
- health pickups.

Current Core already has `config/gunnerarena/ammo-points.properties`, but the supplied file has `count=0`, confirming the legacy map is still authoritative for these pickups.

Target: `LootSpawnService`, with map-defined spawn points and typed loot tables.

### Ammo/reload state

Legacy chains inspect JEG item NBT `AmmoCount`, calculate total ammo, apply full-ammo tags and display reload/no-ammo titles.

Target: GGO weapon/loadout authority and client HUD events. No dynamic rewriting of command-block `Command` NBT.

### Match state / score

Legacy scoreboards include kills/deaths, respawn ticks, selected-gun IDs, kill target and map-generation counters.

Target: typed `ClassicArenaSession` state in Core. Scoreboards may remain presentation-only during compatibility, never authoritative storage.

## Map mod dependency audit

Scanning all world section palettes found only one non-vanilla block namespace in the actual map geometry:

- `framedblocks:framed_slope`

FramedBlocks is therefore a real map dependency at present.

No blocks from JEG, Huge Structure Blocks, Embeddium, DamageNumbers, etc. were found in chunk palettes.

This does **not** mean item/entity/gameplay mods can be removed automatically; JEG is clearly a gameplay dependency and must remain until weapons are replaced. It does mean the server should not retain unrelated mods merely because the world geometry might need them.

## Initial mod cleanup classification

### Definitely keep on server for now

- GGO Core
- JEG custom minigame build
- Framework (JEG dependency)
- GeckoLib where required by JEG/runtime
- TinyInv while current loadout/inventory logic depends on it
- FramedBlocks while the current map contains framed slopes
- movement/gameplay dependencies that are proven required by current server behavior

### Strong candidates for client-only removal from dedicated server

- Embeddium
- DamageNumbers

### Must verify before removal

- Cloth Config
- Bloody Bits
- Ragdollified
- Dash
- Squake port
- player-animation-lib
- Collective
- Huge Structure Blocks

`Huge Structure Blocks` appears to be a build/admin utility in the startup log. Since no Huge Structure Blocks namespace appears in the world palette, it is a strong candidate for removal from production after confirming no runtime structure-generation dependency uses its altered limits.

### Remove from production bundle regardless

- nested `.tar.gz` archives
- `desktop.ini`
- duplicate `1/` server/world snapshot
- old patch/setup archives not needed by the running server
- stale logs/crash reports except intentionally retained diagnostics
- inactive Citizens/Denizen data after one final migration check.

## Existing Core data already replacing map logic

The supplied config already contains:

- lobby coordinates
- 14 arena spawns
- safe regions
- clans/friends data
- GGO identity bootstrap data
- balances/stats

This is evidence that migration away from command blocks is already underway and should continue service-by-service rather than rebuilding everything from scratch.

## Next implementation sequence

1. Introduce `GgoGameModeRegistry` and `/play` routing.
2. Register current always-on map as `ARENA`.
3. Register recovered legacy mode as `CLASSIC_ARENA` in migration state.
4. Reimplement Classic Arena match state/countdown/win target in Core.
5. Migrate item/ammo/health spawners to `LootSpawnService`.
6. Migrate random arena generation to a typed structure-pool generator.
7. Validate behavior with command blocks disabled.
8. Remove legacy command machinery from a clean copy of the world.
9. Use the cleaned world in FULL-VDS production bundle.

The original supplied archive must remain untouched; all cleanup/migration is performed into generated clean runtime bundles.