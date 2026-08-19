# GunGloryOnline command-block migration

Command blocks are treated as prototype-only infrastructure. Production GGO gameplay should not depend on hidden world command chains.

## Target

Move gameplay authority from map command blocks into versioned server/Core code while keeping maps mostly declarative content.

A production map may contain geometry, spawn markers, named regions, decorative entities and explicit GGO metadata. It should not contain critical match state, economy, damage, rewards, authentication, progression or anti-cheat logic in command blocks.

## Current migration evidence — supplied 2026-08-17 world

The supplied full server/world was audited at the Anvil/NBT level and contained **992 command blocks**.

A runtime copy was then started with:

- the green server-hardening Core;
- the supplied real world and gameplay mods;
- `enable-command-block=false`.

The dedicated server reached `Done (4.596s)`, proving the imported world itself can load with command execution disabled.

On a separate disposable copy of that world, the OP-only migration cleaner was executed through the real dedicated-server console:

1. `/ggo legacy commandblocks status`;
2. `/ggo legacy commandblocks strip CONFIRM`;
3. `save-all`;
4. `stop`.

The server reported:

- **992 legacy command blocks removed**.

A second direct region/NBT scan of the saved copy reported:

- `command_block_entities = 0`;
- `commands = 0`.

This proves the removal mechanism itself. It does **not** by itself mean every legacy gameplay behavior is already migrated; the clean copy remains an integration target until Classic Arena and remaining map presentation/trigger services pass gameplay smoke.

Latest validation rule: every newly migrated Classic subsystem must pass both clean Forge 1.20.1 / Java 17 CI and a real imported-world smoke with command execution disabled before Classic is promoted from `MIGRATING` to `ACTIVE`.

Classic has now passed its imported-world startup gate with command execution disabled. Duels follows the same evidence-first rule: the server-owned fallback arena must pass `[GGO-DUELS-REALWORLD-SMOKE] result=PASS` on the supplied world before its availability is promoted beyond `PLANNED`.

## Already migrated from the legacy command graph

- Classic 8×8 procedural arena generation -> `ClassicArenaMapGenerator` using `StructureTemplate` directly;
- recovered generator bridge no longer toggles redstone or reads generator scoreboards;
- random ammo point distribution 4/3/3 -> Java generator state;
- health + three Classic ammo slots -> `ClassicArenaPickupService`;
- respawn delay / locked spawn-marker logic -> `ClassicArenaSpawnService`;
- Classic participants/countdown/kills target/winner/reset -> `ClassicArenaMatchService`;
- jump pads -> `ClassicArenaJumpPadService` direct velocity implementation (in validation);
- normal-player block breaking/placement and legacy command-block interaction -> `GgoServerRulesGuard`;
- migration-only stripping -> `GgoLegacyCommandBlockCleaner` with explicit OP `CONFIRM`.

## Migration order

1. Inventory/loadout grants -> server loadout service.
2. Round start/end, timers and win conditions -> round service.
3. Teleports/spawns/checkpoints -> map/region service.
4. Scoreboards used as hidden state -> typed player/match state.
5. Kill/reward/XP commands -> authoritative reward service.
6. Doors, triggers and scripted map events -> GGO map trigger system.
7. NPC/boss/special-event commands -> server encounter service.
8. Cosmetic/title/sound command chains -> client/server presentation events.
9. Remaining utility command blocks -> remove or convert to explicit map metadata.

## Map import audit

When a world ZIP is supplied, audit:

- `level.dat` and dimensions;
- region files (`region/*.mca` and dimension region files);
- command block block entities and their command strings;
- structure blocks / jigsaws if used;
- datapacks/functions under `datapacks/`;
- scoreboard objectives and teams;
- repeating/chain command-block topology where recoverable;
- coordinates and nearby named/marker entities so each command can be tied to a gameplay purpose.

Produce a migration inventory containing:

- command/location;
- trigger type (impulse/repeating/chain/function);
- inferred purpose;
- dependency on scoreboard/tags/entities;
- target GGO service/class;
- migration status;
- validation test.

## Replacement architecture

### `GgoMapDefinition`
Declarative per-map data: spawn points, regions, objectives, trigger volumes, doors, encounter markers and presentation IDs.

### `GgoMapTriggerService`
Server-owned trigger evaluation. Trigger definitions are data; actions are allow-listed typed actions, not arbitrary Minecraft commands.

Example actions:

- `TELEPORT_TO_SPAWN`
- `OPEN_DOOR`
- `START_OBJECTIVE`
- `SPAWN_ENCOUNTER`
- `PLAY_GGO_SOUND`
- `SET_MATCH_PHASE`

### `GgoRoundAuthority`
Owns match phase, timers, score, win/loss and round transitions.

### `GgoRewardAuthority`
Only server-side trusted match outcomes can mint XP/currency/progression.

## Security rule

Never replace command blocks with a generic remote command executor. The migration target is typed server APIs with validation. Maps must not be able to execute arbitrary console commands from untrusted downloaded content.

## Compatibility period

During migration a map may temporarily retain command blocks, but production builds should log their presence and eventually refuse critical command-block execution once the equivalent GGO service is enabled.

## Definition of done

A map is command-block independent when the game can remove/disable all command blocks and datapack command functions without changing match flow, rewards, objectives or player progression.
