# GunGloryOnline Map System

## Goal
Replace vanilla-style navigation/player-list UX with a first-party tactical map system that works across the persistent world, Battle Royale, Training and events.

## Key bindings
- `M` — open/close the GGO Menu / Activities screen. This remains the primary game-mode/events/contracts menu.
- `N` — open/close the full GGO Navigation Map.
- `TAB` (hold) — fast squad/match status overlay. It must stay lightweight and must not cover the player with a second full map.
- Minimap — optional HUD element, disabled by default and enabled in Settings > Interface > Map.
- `MMB` — context ping / waypoint.
- `ESC` — closes the current GGO screen first, then opens the GGO Pause Hub.

## Full map (`N`)
The full map is a dedicated GGO Navigation screen, not a vanilla Minecraft map UI.

Common features:
- player position and facing;
- squad member positions when game rules allow it;
- manual pings and waypoints;
- selected objective/contract;
- points of interest;
- zoom and pan;
- region/sector labels;
- safe/hostile/contested-area visualization;
- contextual legend;
- optional fog of war later.

### Persistent GGO world
Show:
- discovered regions;
- settlements/hubs;
- safe zones;
- dangerous zones;
- active world events;
- contracts/objectives;
- extraction points where applicable;
- squad pings;
- navigation waypoint.

The map should feel like a tactical PDA rather than an overhead Minecraft world viewer.

### Battle Royale
Show:
- current safe zone;
- next zone after it becomes known;
- player/squad position;
- squad pings;
- deployment route / insertion information when relevant;
- alive-player/squad count;
- match phase and timer;
- event markers such as airdrops if game rules reveal them.

Enemies are never shown by default. Enemy positions may only appear through explicit game mechanics such as UAV/recon/spotting.

### Training
Use a simplified map with training stations, ranges, targets and tutorial objectives. Do not display online-world progression concepts.

## Minimap
The minimap is intentionally optional and OFF by default.

Settings:
- enabled / disabled;
- corner position;
- size: small / medium / large;
- opacity;
- rotate with player vs north-up;
- objective markers;
- squad markers;
- zone border;
- combat auto-hide (optional later).

Default minimap content should remain minimal:
- local player arrow;
- immediate squad;
- active objective;
- waypoint;
- BR zone boundary when applicable.

Avoid displaying every POI/icon simultaneously.

## TAB tactical overlay
`TAB` no longer opens Minecraft's all-player list and it is not a second map screen.

While held, TAB displays a compact edge-aligned overlay with:
- squad members;
- health/downed/alive state;
- ping/connection quality;
- voice status;
- squad leader;
- current activity/match;
- current sector/region text;
- waypoint bearing/distance when one exists;
- current objective and important match counters.

The center of the screen should remain readable during combat.

### Battle Royale additions
- kills/assists;
- alive count;
- squad placement when known;
- zone phase/timer;
- squad member status.

### Persistent world additions
- current sector/region;
- active contract;
- local event state;
- party/squad information.

The complete server player list is not part of normal player UX. It belongs in Admin/Debug tools.

## GGO Menu (`M`)
`M` remains the high-level Activities entry point rather than navigation.

Suggested sections:
- Enter/Open World status;
- Training;
- Battle Royale / Quick Match;
- Events;
- Contracts;
- Party activity;
- current queue/matchmaking state.

Do not duplicate Inventory, Social or Settings inside every menu. Those remain globally accessible from their own screens / Pause Hub.

## Information security / game rules
- Never expose hidden enemies merely because the map renderer can access their entities.
- Server decides which markers a player is allowed to know.
- Client renders only authorized marker data.
- Competitive marker updates should be server-authoritative.

## Technical direction
Do not make the UI domain depend on Minecraft map-item data structures.

Use runtime-neutral concepts such as:
- `MapRegion`
- `MapMarker`
- `MapMarkerType`
- `MapVisibilityRule`
- `MapZone`
- `SquadMarker`
- `ObjectiveMarker`
- `Waypoint`

Forge/Minecraft coordinates are adapted into this model by Runtime v1 code.

## Implementation order
1. Stage 1: keybind shell for `M`, `N` and hold-`TAB`, placeholder GGO screens.
2. Stage 2: player position, facing, squad and objective marker model.
3. Stage 3: BR zone/ring and match-state integration.
4. Stage 4: optional minimap with settings.
5. Stage 5: persistent-world regions, POIs, events and contracts.
6. Stage 6: fog-of-war/discovery and advanced recon mechanics.

## Visual direction
- dark graphite base;
- desaturated terrain imagery;
- red GGO accents;
- cyan/white squad/navigation markers;
- subtle grid/sector lines;
- minimal iconography;
- map occupies most of the screen with small contextual panels, not Minecraft-style stone GUI panels.
