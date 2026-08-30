# GunGloryOnline Battle Royale / World Architecture

## Current world split
GGO should not keep the hub, Training and Battle Royale in one Minecraft world.

Target logical worlds:
- `ggo_hub_winter` — seasonal winter hub / social spawn.
- `ggo_training` — local Training world. No online progression and no lobby authentication.
- `ggo_br_dropzone` — temporary Battle Royale arena based on the uploaded `Точка Высадки` map.
- `ggo_br_mini_pubg` — second temporary Battle Royale arena based on the uploaded `MINI PUBG MINECRAF MAP` map.
- `ggo_winter_event` — seasonal event world based on uploaded `world_3`; do not treat it as a normal BR template yet.

The downloaded maps are temporary development placeholders. Verify their redistribution/licensing terms before shipping them inside a public GGO build. Long term they should be rebuilt/re-authored into original GGO maps.

## Uploaded map intake
All three supplied worlds report Minecraft 1.20.1 / DataVersion 3465.

### Dropzone (`Точка Высадки`)
- level name: `Точка Высадки`
- spawn from level.dat: `(28, 23, -41)`
- region footprint in archive: 16 overworld region files, region grid roughly `x=-2..1`, `z=-2..1`
- archive also contains third-party mods/datapack content; only the world template should be imported after compatibility review.
- GGO role: BR map A / urban-landscape mixed map.

### Mini PUBG
- level name: `MINI PUBG`
- spawn from level.dat: `(999, 1, 1019)`
- region footprint in archive: 12 overworld region files, roughly `x=-1..2`, `z=-1..2`
- archive contains its own mod/config bundle; do not merge these mods directly into GGO Client.
- GGO role: BR map B / compact alternative arena.

### Winter world (`world_3`)
- level name: `The pursuit fo gifts: battle royale`
- spawn from level.dat: `(-126, -59, -177)`
- archive size about 6.8 MB
- region footprint: 12 overworld region files, roughly `x=-2..0`, `z=-2..1`
- includes a `map_core` datapack with `play`, `reset`, `stop` functions and gift/event systems.
- GGO role: seasonal Winter Event / temporary winter hub candidate. Preserve it separately until its datapack is audited.

## Match architecture
Do not run one permanent world per individual BR match and do not modify the source map while a match is active.

Use immutable world templates plus temporary match instances:
- template: `templates/br/dropzone`
- running instance: `instances/br/dropzone/<match-id>`
- template: `templates/br/mini_pubg`
- running instance: `instances/br/mini_pubg/<match-id>`

At match creation, clone/copy-on-write a clean template into a disposable instance. At match end, unload and delete/reset the instance. This guarantees loot, destroyed blocks and player changes never leak into the next round.

Initial implementation may keep one active instance per BR worker process. Later the gateway/matchmaker can start multiple BR worker servers and route players automatically.

## Queue and round state machine
Recommended Battle Royale state machine:

1. `IDLE`
2. `WAITING_FOR_PLAYERS`
3. `COUNTDOWN`
4. `PREPARING_INSTANCE`
5. `DEPLOYMENT`
6. `ACTIVE`
7. `FINAL_ZONE`
8. `FINISHED`
9. `RESETTING`

Initial beta values:
- minimum players to arm countdown: 3
- normal target: 10–24 depending on map testing
- countdown after minimum reached: 45 seconds
- if player count falls below minimum during early countdown, pause/cancel countdown
- final 10 seconds: visible title/action-bar countdown
- late join after `DEPLOYMENT`: spectator or next-match queue, not active contestant

Do not permanently design the mode around 3 players; `3` is only the beta start threshold so matches can be tested with a small community.

## Map selection
For the first beta, `/play` opens a GGO mode selection menu:
- Training
- Battle Royale
- later: Operations / Events / Arena

Selecting Battle Royale should default to `Quick Match`: player joins a common BR queue and the matchmaker chooses a compatible available map.

Optional advanced choices later:
- Quick Match
- Dropzone
- Mini PUBG
- seasonal map rotation

Do not split a small player population into separate mandatory map queues at first. One shared queue avoids empty matches.

## Multiple matches like PUBG
The correct long-term model is multiple parallel match instances, not multiple rounds occupying the same physical blocks of one world.

Example:
- `br-eu-01` runs Dropzone match #184
- `br-eu-02` runs Mini PUBG match #185
- `br-eu-03` starts a second Dropzone match #186 when queue pressure requires it

The GGO gateway/matchmaker assigns players to a worker based on region, queue state, capacity and map rotation.

## Admin / building workflow
Administrators must retain Minecraft-level building power because Minecraft is still the world authoring engine under GGO.

Provide an admin-only builder flow:
- `/ggo admin builder on|off`
- Creative + flight + operator tools only for authorized GGO admin IDs
- open/import a map template in maintenance mode
- set lobby point
- set deployment/drop points or deployment region
- set playable bounds
- set initial/final zone centers and radius presets
- mark loot spawn nodes
- mark vehicle/airdrop/event nodes later
- validate template
- publish a new immutable map revision

Never edit the live match instance as the source-of-truth map. Builder changes publish to a new template revision.

Suggested future map metadata file (`ggo-map.json`):
- `id`
- `displayName`
- `revision`
- `worldFolder`
- `minPlayers`
- `maxPlayers`
- `waitingSpawn`
- `deploymentArea`
- `playBounds`
- `zonePresets`
- `lootNodes`
- `spectatorSpawn`
- `seasonal`

## Lobby / hub separation
The main GGO hub is a persistent social world. BR contestants should not physically wait inside a BR arena before enough players exist.

Recommended flow:
1. player is in main GGO hub/open world;
2. player selects Battle Royale from `/play` / Activities UI;
3. queue status appears in HUD;
4. when a match is reserved, party is transferred to a short BR staging area or directly to the match waiting spawn;
5. countdown begins;
6. deployment starts;
7. after death player spectates or returns to hub when desired;
8. after match all players are routed back to hub/open world.

## Loot and deployment
Imported map loot/datapacks are not authoritative GGO systems. Replace them gradually with GGO Core systems.

For beta:
- random weapon/ammo/armor loot nodes defined by GGO map metadata;
- tiered loot areas;
- shrinking zone controlled by GGO Core;
- air drops controlled by GGO Core;
- deterministic reset through disposable instances;
- map-specific imported command blocks/datapacks disabled unless explicitly audited.

## Winter map
Do not make `world_3` the permanent production hub immediately. It contains its own game datapack and was authored as a minigame.

Use it first as:
- December/New Year seasonal hub;
- limited Winter Event;
- visual reference for a future original GGO winter city/hub.

If it becomes a hub, strip/disable the original minigame datapack after preserving a backup, then move the GGO spawn and hub NPC/UI anchors explicitly.

## Near-term implementation order
1. Import both BR maps into a private staging environment without their bundled third-party mod sets.
2. Audit/remove command blocks and foreign datapacks that conflict with GGO Core.
3. Add GGO map metadata for spawn, bounds and loot nodes.
4. Convert current `/play` into the mode-selection menu; rename current arena to Training.
5. Implement shared BR queue with minimum 3 players and 45-second countdown.
6. Implement disposable match-instance reset lifecycle.
7. Add Quick Match map rotation across Dropzone / Mini PUBG.
8. Add spectator/death/end-of-match routing back to hub.
9. Audit `world_3` separately for Winter Event use.
10. Replace temporary downloaded maps with original GGO revisions before broad public distribution where licensing requires it.
