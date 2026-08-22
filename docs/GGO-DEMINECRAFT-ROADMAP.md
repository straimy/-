# GunGloryOnline — de-Minecraft runtime roadmap

Goal: keep Minecraft/Forge/JEG as a hidden engine/runtime while removing vanilla Minecraft from the player-facing product as quickly and safely as possible.

## Rules

- Never remove or mutate Minecraft registries/classes just to make them disappear from UX. Forge/mod internals may depend on them.
- Remove the *player-facing path* first: UI, inputs, survival rules, loot, visible items, map interactions, then assets.
- `main` is not an integration target during this migration. Work stays on the dedicated server/client/launcher branches until explicit release work.
- GGO server state remains authoritative.
- Physical menu items are forbidden. `M` is the GGO menu/Activities route.
- Spawn/social hub players are visible and protected. Invisibility is explicit per-mode opt-in only.

## Implemented runtime layers

### Social spawn / presence
- Social spawn players visible by default.
- Spawn/safe region clears fire/fall hazards and is excluded from legacy hazard damage/status effects.
- Legacy physical compass menu injection/interaction removed.
- Existing compass menu artifacts are purged.
- Explicit mode-only invisibility policy exists.

### Vanilla survival fence
- Normal authenticated GGO players cannot break/place map blocks.
- Vanilla crafting/container/bed interactions are fenced.
- Hunger loop is inert.
- Vanilla XP progression and XP drops are disabled.
- Admin creative/spectator maintenance escape hatch remains.

### Vanilla loot/content fence
- Raw vanilla mob drops are removed.
- Stray raw `minecraft:*` pickups are removed.
- Normal player inventory periodically removes unapproved raw `minecraft:*` items.
- GGO-tagged supplies/bound items/resource-pack proxy items remain allowed.
- JEG/GGO/modded items remain allowed.

### First-party GGO inventory
- `E` opens the GGO shell, not vanilla InventoryScreen.
- 3 combat slots.
- 9 ammo-pouch slots.
- field-item storage.
- server-authoritative select/swap/drop/ammo/trash actions.
- supply items are protected from trash cleanup.
- offhand is normalized back into GGO compartments.
- extra weapons are preserved in field storage instead of being deleted.

### Client UX fence
- Vanilla title/pause/disconnect replaced.
- Vanilla death screen replaced with GGO KIA/respawn.
- Advancements route to Activities.
- Vanilla container/crafting screens are fenced for normal users.
- Vanilla hotbar/hearts/armor/hunger/XP/air/TAB list are suppressed by GGO HUD/squad UI.
- Q, offhand swap, pick-block and hotbar 4..9 shortcuts are consumed; only 3 combat slots remain in normal play.

### Launcher
- Linux package matrix verified.
- Windows NSIS EXE, MSI and portable ZIP have a dedicated real-Windows verification gate.
- Launcher remains silent by default; soundtrack is in-game, not launcher-owned.

## Asset migration

Stage 44 resource-pack audit showed the current pack is still a historical full Minecraft texture pack: thousands of `assets/minecraft` textures/models/UI assets are overridden. This must become a narrow GGO pack.

Do **not** blindly delete block textures. If a production map still uses that block, it would fall back to vanilla Minecraft art.

Next asset pipeline:
1. collect a palette of blocks/entities/items actually visible in each production GGO world;
2. classify each visible ID as `GGO replacement`, `temporary proxy`, or `remove from map`;
3. replace visible map palette with GGO-owned textures/models/material language;
4. prune unused vanilla block/item/model overrides from the resource pack;
5. remove old vanilla mob/classic sound overrides after their entities/effects are no longer used;
6. keep only intentional `minecraft:*` proxy assets marked by the GGO visible-item policy.

## Remaining high-impact work

1. Cross-branch Runtime-v1 integration package: build latest server + latest client together and verify new classes inside final JARs.
2. Production-world palette telemetry/audit so resource-pack pruning is data-driven.
3. Replace visible vanilla block palette map-by-map; prefer a small intentional set of GGO construction materials instead of thousands of Minecraft blocks.
4. Remove/replace vanilla passive mobs and any remaining vanilla entity visuals where a GGO mode does not explicitly need them.
5. Replace remaining vanilla chat/loading/toast/debug presentation that can appear in normal play without breaking networking/loading semantics.
6. Move remaining command-driven client actions to narrow GGO network packets where practical.
7. Consolidate old vXX hotfix packaging workflows so historical v40 code cannot accidentally become the release source of truth.
8. Final Runtime-v1 dedicated server/client smoke, launcher install smoke and release manifest; only then prepare release artifacts.

## Engine boundary

Minecraft 1.20.1 + Forge 47.4.10 may remain underneath as the engine during this phase. The target is that a normal player launches GunGloryOnline and sees GGO screens, GGO inventory, GGO maps/content, GGO rules and GGO assets without needing to understand or interact with Minecraft survival systems.
