# GunGloryOnline de-Minecrafting plan

## Decision
Do not fork Forge for the first public beta.

Forge is infrastructure, not player-facing identity. Replacing or maintaining a custom Forge fork would create a large compatibility and security burden while giving little visible benefit. Keep Minecraft 1.20.1 + Forge 47.4.10 as the hidden runtime substrate until there is a concrete blocker that cannot be solved in GGO Core/UI/launcher.

## Player-facing goal
A normal player should experience GunGloryOnline as a standalone game:

- install/update/play from the GGO launcher;
- GGO Account, not Microsoft account, is authoritative;
- no vanilla title screen, multiplayer list, Direct Connect, Realms, singleplayer/create-world flow or Minecraft settings branding in the normal path;
- GGO frontend owns Online, Training, Activities, profile, inventory, progression and settings;
- official Online connects only to GGO-managed shards;
- launcher manages Java and the game files;
- errors and diagnostics use GGO language and surfaces;
- server-authoritative gameplay, progression and anti-cheat;
- GGO art, audio, models, UI and maps replace the visible Minecraft identity over time.

## What to change before touching Forge

1. Launcher-managed runtime
   - provision the supported Java 17 runtime automatically;
   - signed/hashed manifest and atomic updates;
   - no user-selected Java or arbitrary JVM args in normal mode;
   - technical paths/URLs only in Diagnostics.

2. Frontend/runtime fence
   - replace title/options/server browser/disconnect/death/pause/loading surfaces;
   - route every normal navigation path back into GGO screens;
   - remove vanilla server list and manual address entry;
   - keep emergency diagnostics/recovery paths separate from player UX.

3. Identity
   - GGO Account is authoritative;
   - bind player presentation to GGO identity rather than Minecraft username/session state;
   - server assigns the trusted identity after one-shot ticket verification.

4. World and gameplay identity
   - replace visible vanilla loot, recipes, advancements, commands and progression;
   - GGO-owned inventory rules, weapon state, rewards and economies;
   - custom maps, props, NPCs, operator models, sounds and music;
   - prevent command blocks/vanilla mechanics from becoming production dependencies.

5. Networking/security
   - official shard routing owned by GGO;
   - authenticated pre-play quarantine;
   - allowed-build policy and integrity attestation;
   - anti-cheat remains server-authoritative and report-only until calibrated;
   - never trust client-provided economy, damage, inventory or progression results.

6. Branding and diagnostics
   - remove Minecraft/Forge names from normal player-facing surfaces where licensing permits;
   - preserve required legal/licensing notices in an About/Licenses area;
   - crash/error reports should show a GGO incident id and friendly message, with raw Forge/Minecraft logs only in Diagnostics.

## What not to fork yet

### Forge
Do not fork now. A Forge fork means owning loader compatibility, mappings-sensitive patches, security updates and every mod interoperability regression. Only revisit if a required GGO feature is impossible or unsafe to implement via Core, mixins, normal Forge hooks or launcher/runtime control.

### OpenJDK
Do not maintain a full JDK fork for beta. Ship a pinned, verified Java 17 distribution as "GGO Runtime". A small native helper may later provide process-integrity signals on supported platforms, but it must not be treated as the trust root.

### Minecraft engine
Do not attempt a source-level engine rewrite before the game design is proven. Hide and replace player-facing systems first. If GGO eventually outgrows Minecraft networking/rendering/world assumptions, migration to a dedicated engine should be a separate product-generation project rather than an incremental beta patch.

## Revisit criteria for a Forge fork
A Forge fork becomes worth evaluating only if at least one of these is true:

- a security boundary cannot be enforced from the server/Core/launcher;
- a required runtime feature cannot be implemented with supported hooks or tightly-scoped mixins;
- upstream loader behavior causes recurring production failures that cannot be isolated;
- removal of a player-visible Minecraft surface requires invasive loader-level changes;
- the maintenance cost is demonstrably lower than continued compatibility work.

Any fork must have reproducible builds, an upstream rebase policy, patch inventory, automated compatibility tests and a clear owner. "Harder to decompile" is not a valid reason to fork Forge.

## Migration order

Beta 1: hide Minecraft, stabilize GGO auth/launcher/runtime, custom frontend, server authority, anti-cheat telemetry.

Beta 2: GGO-managed Java runtime, allowed builds/integrity, custom operator/profile identity, broader replacement of vanilla UI/content.

Beta 3: custom training world and activities, custom maps/assets/audio, remove remaining player-visible vanilla workflows.

Post-beta: measure remaining engine/loader limitations and decide whether to keep Forge hidden, fork a minimal runtime layer, or plan a future dedicated-engine generation.
