# GunGloryOnline Default Controls

## Goal
GunGloryOnline keeps familiar shooter controls where muscle memory helps, while replacing Minecraft-specific UX with GGO actions. Every non-critical binding remains remappable in Settings > Controls.

## Core movement / combat
- `WASD` — move
- `SPACE` — jump / vault when supported
- `LEFT SHIFT` — sprint
- `LEFT CTRL` — crouch (toggle/hold configurable)
- `Z` — prone when supported by the current mode
- `F` — interact / use
- `R` — reload
- `G` — grenade / throwable
- `1` — primary weapon
- `2` — secondary weapon
- `3` — sidearm
- Mouse wheel — cycle only primary / secondary / sidearm; vanilla hotbar slots 4–9 are not part of normal GGO play
- `H` (hold, remappable) — GGO Medical Wheel; release to use the selected field medicine
- `LMB` — fire / primary action
- `RMB` — aim / secondary action
- `MMB` — contextual ping / marker
- `LEFT ALT` (hold) — freelook when the current weapon/mode allows it

The medical wheel appears under the GunGloryOnline key category and is a normal Forge key mapping, not a hard-coded GLFW check.

## First-party GGO UI
- `E` — GGO Equipment / Backpack
- `M` — GGO Menu / Activities
- `N` — Navigation / Full Map
- `TAB` (hold) — Squad / Match Tactical Overlay
- `ESC` — close current screen; otherwise GGO Pause Hub

## Optional direct shortcuts
These should exist but do not need to be prominent or required:
- `J` — Contracts / Journal, configurable and disabled by default if M already provides fast access
- `P` — Social / Party, configurable
- `K` — Loadout, configurable

Avoid forcing too many single-purpose keys on new players. The M menu and ESC shell remain the discoverable navigation paths.

## TAB philosophy
TAB is a temporary information overlay, not a menu and not a full map.

Normal world:
- squad status;
- current region/sector;
- active objective;
- waypoint distance/bearing;
- connection/voice indicators.

Battle Royale:
- squad;
- kills/assists;
- alive count;
- placement when available;
- circle phase/time;
- objective/status.

The screen center remains mostly unobstructed. A full all-player list is reserved for Admin/Debug.

## Map / ping philosophy
- N opens the large map.
- M remains Activities.
- MMB places a contextual ping in the world or on the map.
- Holding MMB opens the radial tactical ping wheel: enemy, move, danger, loot, defend, regroup.
- Ping information is server-authoritative in competitive modes.

## Inventory philosophy
E opens a first-party GGO equipment/backpack screen rather than the vanilla inventory.

Normal GGO compartments:
- three protected combat weapon slots: Primary / Secondary / Sidearm;
- protected worn armor;
- protected ammo pouch;
- field backpack slots for supplies, medicine and extracted/looted items.

Field items are the risk layer: on KIA they are packed into one owner-bound Recovery Bag instead of creating a loose Minecraft death pile. Combat weapons, ammo pouch and worn armor are retained. Crafting and the vanilla recipe-book/armor-grid UI are not part of the normal GGO path.

## Pause Hub
ESC opens:
- Resume
- Inventory
- Social
- Activities
- Settings
- Leave GGO

Do not expose vanilla Singleplayer/Multiplayer/Realms/Mods screens from the normal Pause Hub.

## Accessibility / configuration
Settings > Controls must support:
- complete rebinding;
- hold/toggle aim;
- hold/toggle crouch;
- hold/toggle sprint;
- mouse sensitivity and ADS multiplier;
- invert Y;
- disable mouse-wheel weapon cycling;
- medical-wheel binding;
- ping-wheel hold time;
- key-conflict warnings;
- reset per category, not only reset-all.

## Admin / development controls
Admin functionality must be separated from normal GGO controls and available only to authorized admins.

Examples:
- debug player list;
- world-edit/build tools;
- teleport/world selector;
- match controls;
- map marker editor;
- spawn/loot/zone editor;

These should live behind an Admin Panel/keybind and not consume normal-player shortcuts by default.
