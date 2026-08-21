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
- `4` — first quick consumable slot
- `5` — second quick consumable slot
- Mouse wheel — weapon cycling; independently disableable
- `LMB` — fire / primary action
- `RMB` — aim / secondary action
- `MMB` — contextual ping / marker
- `LEFT ALT` (hold) — freelook when the current weapon/mode allows it

## First-party GGO UI
- `E` — GGO Inventory / Equipment
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
- Holding MMB later may open a radial ping wheel: enemy, move, danger, loot, defend, regroup.
- Ping information is server-authoritative in competitive modes.

## Inventory philosophy
E opens a first-party GGO inventory rather than the vanilla inventory.

The normal player should see:
- equipped weapons;
- armor;
- backpack/capacity;
- gadgets;
- ammo;
- consumables;
- quick slots;
- item/weapon stats.

Crafting and vanilla recipe-book UI are not part of the normal GGO path.

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
- spawn/loot/zone editor.

These should live behind an Admin Panel/keybind and not consume normal-player shortcuts by default.
