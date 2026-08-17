# GunGloryOnline runtime mod cleanup plan — imported server 2026-08-17

This plan is based on the full server archive supplied on 2026-08-17. The original archive is evidence and must not be modified in place.

## Goal

Stop distributing/copying an undifferentiated Minecraft mod folder. Production has two explicit sets:

- **server runtime** — only server/gameplay dependencies required by the authoritative GGO server;
- **client runtime** — only rendering/input/client gameplay dependencies managed by the GGO launcher.

Minecraft/Forge dependencies remain Runtime v1 implementation details and should not define GGO game architecture.

## Current classification

| Jar | Current role | Plan |
|---|---|---|
| `gungloryonline-core-0.9.7-v40.jar` | GGO authoritative core | **KEEP / evolve** |
| `JEG - s1queence minigame changes.jar` | current weapon implementation | **KEEP temporarily; MIGRATE later to GGO weapon layer** |
| `framework-forge-1.20.1-0.8.0.jar` | JEG dependency | **KEEP while JEG exists** |
| `geckolib-forge-1.20.1-4.7.jar` | JEG/model dependency | **KEEP while required** |
| `FramedBlocks-9.4.3.jar` | map geometry; imported world contains `framedblocks:framed_slope` | **KEEP until map geometry is baked/replaced** |
| `tinyinv-s1queence minigame changes.jar` | current inventory/loadout behavior | **KEEP until GGO inventory is fully authoritative** |
| `squakeport_1_20-1_20_1-1_04.jar` | movement behavior | **KEEP until movement is owned by GGO runtime** |
| `player-animation-lib-forge-1.0.2-rc1-1.20.jar` | player animation dependency | **KEEP while current animations use it** |
| `ragdollified-1.20.1-0.4.0-BETA.jar` | death/corpse presentation with server data present in world | **REVIEW before removal** |
| `bloodybits-1.3.4-1.20.1.jar` | blood presentation | **REVIEW; likely presentation-only replacement candidate** |
| `cloth-config-11.1.136-forge.jar` | library/config dependency | **KEEP only if dependency graph requires it** |
| `collective-1.20.1-7.93.jar` | library | **REVIEW dependency graph** |
| `dash-1.20.1-1.1.1.jar` | movement/gameplay utility | **REVIEW before removal** |
| `huge-structure-blocks-1.0.9-forge.jar` | likely legacy Classic Arena generation tooling | **MIGRATE THEN REMOVE** after `ClassicArenaMapGenerator` replaces structure-command workflow |
| `SAuth-2.0.0.jar` | server-only registration/auth layer used with `online-mode=false` | **MIGRATE THEN REMOVE** after verified GGO server-session authentication is live |
| `embeddium-0.3.31-mc1.20.1.jar` | client renderer optimization (`CLIENT`) | **REMOVE FROM SERVER**, keep client only if desired |
| `damagenumbers-1.4.0-forge.jar` | presentation/client HUD feature | **REMOVE FROM SERVER after compatibility smoke**, replace with GGO UI when practical |

## Non-mod archive cleanup

Never copy the following into a clean production deployment unless explicitly requested for diagnostics/migration:

- `logs/` and old compressed logs;
- `crash-reports/`;
- nested historical `.tar.gz` / `.zip` packages;
- duplicate `1/` server/world copy;
- `desktop.ini`;
- installer logs;
- stale generated caches;
- old patch bundles.

Backups are created by the production VDS backup service, not carried inside the live runtime directory.

## Important dependencies discovered in the imported world

The current world contains at least one FramedBlocks block state (`framedblocks:framed_slope`), so removing FramedBlocks now can corrupt/replace map geometry.

The legacy map contains 992 command blocks and a large structure-driven Classic Arena system. `Huge Structure Blocks` must therefore not be removed merely because it looks like a builder tool; first migrate and smoke-test `ClassicArenaMapGenerator`.

The imported server also contains old `plugins/Citizens` and `plugins/Denizen` data, but the current Forge server log does not show these as an active plugin runtime. Treat these directories as migration evidence/legacy data, not production dependencies, unless a later audit proves otherwise.

## Authentication cleanup

Current imported server configuration uses `online-mode=false` with `SAuth`. This is temporary compatibility infrastructure, not the final GGO identity model.

Target:

1. launcher authenticates GGO account / linked Microsoft identity;
2. launcher receives a short-lived server join credential;
3. game server verifies it server-side;
4. server maps the session to stable `ggo_player_id`;
5. SAuth is removed;
6. no client-provided UUID/name assertion is trusted for progression.

## Removal rule

A dependency may be removed only after:

1. source/config/world dependency scan;
2. dedicated Forge startup smoke;
3. representative map load;
4. login + loadout + weapon + movement smoke;
5. no missing registry entries in server/client logs.
