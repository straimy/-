# GunGloryOnline Platform Roadmap

## Product direction
GunGloryOnline is presented to players as a standalone online tactical game. Minecraft 1.20.1, Forge 47.4.10 and Java 17 remain implementation details hidden behind Advanced / Diagnostics.

Core inspirations: Gun Gale Online, STALCRAFT, large-scale battle royale zones and persistent open-world MMO spaces.

## Player-facing structure
- ENTER GGO: connect to the best official GGO shard/region automatically.
- TRAINING: local/offline simulation with no progression, lobby auth or online rewards.
- EVENTS: time-limited large-map PvP/PvPvE modes, including battle-royale-scale matches.
- OPERATIONS: instanced raids, missions and squad content launched from the persistent world.
- COMMUNITY WORLDS: later, curated community-hosted GGO servers with explicit compatibility rules.

Vanilla Singleplayer, Realms and generic Minecraft Multiplayer are not part of the normal GGO user path.

## World model
The main game is one persistent GGO universe composed of regional shards. Each region can contain:
- social/city hubs;
- open PvE/PvP territories;
- extraction/high-risk zones;
- faction/territory conflict;
- instanced dungeons/operations;
- arena queues;
- large temporary battle maps.

The launcher chooses the closest healthy official shard by latency and capacity. Advanced users may see shard/region selection, but normal users press one Play button.

## Server strategy
Primary experience: official GGO infrastructure.

Recommended architecture:
- Velocity-style proxy/gateway layer later for routing between regions/services;
- dedicated Forge-compatible GGO game servers behind it;
- GGO Auth/Session service;
- shared profile/progression service;
- event/matchmaking service later.

Do not switch the production core to a Forge/Bukkit hybrid such as Mohist/Arclight merely for plugin compatibility. Hybrid cores increase mod/plugin compatibility risk and make production debugging harder.

For official GGO features, prefer:
1. GGO Core server modules;
2. Forge server-side mods;
3. a small documented GGO server extension API later.

If community servers become a major feature, provide a GGO Server SDK / extension API instead of exposing Bukkit as a hard platform dependency.

## Modding strategy
GGO remains moddable, but two tiers are separated:

### Official GGO Client
- launcher-managed required mods;
- server-provided official resource pack;
- optional local shader packs;
- optional cosmetic/client-only approved mods;
- integrity checks for required online components.

### Community Worlds
Later, creators can host compatible GGO servers using an exported GGO Server Runtime. Community worlds may declare additional content packs or approved mods. The launcher creates an isolated profile per community world so the official client is not polluted.

## Accounts
GGO Account becomes the primary identity.

Target flow:
1. user registers on GGO website;
2. launcher signs into GGO Account;
3. launcher requests a short-lived one-time Game Ticket;
4. game receives the ticket through a protected launch/session bridge;
5. dedicated GGO server validates the ticket against GGO Auth;
6. server receives canonical GGO account ID, display name and session data;
7. legacy /login, /register and SAuth are removed.

Never remove legacy server authentication before ticket validation is active while online-mode=false.

## Branding separation from Minecraft
Player-facing replacements:
- Minecraft Launcher -> GunGloryOnline Launcher
- Minecraft profile -> GGO Client
- Minecraft folder -> GGO Data
- mods -> GGO Library / Mods
- logs -> GGO Diagnostics
- Multiplayer -> GGO Network / Worlds
- Singleplayer -> Training
- version string on Home -> GGO Client v40

Minecraft, Forge, MCP and Java versions remain visible only in Diagnostics / Advanced for transparency and support.

The game window and custom menus should use GunGloryOnline branding. Do not falsely replace Mojang legal attribution with KVICloud. KVICloud may appear as infrastructure/hosting attribution such as "Infrastructure by KVICloud" where appropriate.

## Main menu target
Normal GGO title screen:
- ENTER GGO
- TRAINING
- EVENTS (when available)
- SETTINGS
- EXIT

No vanilla Realms button. No generic Singleplayer / Multiplayer buttons in normal mode.

## Official server discovery
Current temporary production endpoint: play.kvicloud.ru:24842.

Long term the client should not expose a fixed Minecraft address. Launcher/API discovery returns the best healthy endpoint based on region, latency, capacity and maintenance state.

The official GunGloryOnline entry is restored in servers.dat for compatibility, without deleting user-added entries. In the final UX this list is mostly an Advanced/Community feature; ENTER GGO uses service discovery directly.

## Anti-cheat and client integrity

Official GGO Online treats the launcher and client machine as untrusted. The anti-cheat model is layered:

- server-authoritative movement, combat, weapon timing, inventory and progression validation;
- current GGO Game Ticket + protocol handshake;
- launcher-managed signed/versioned runtime files;
- pinned supported Java 17 runtime managed by the launcher;
- limited client integrity/build telemetry as an additional signal;
- server-side evidence/scoring and staff review tooling;
- optional signed native anti-tamper later, not as the primary security boundary.

Do not fork an entire JDK only to hide the game or pretend DLL/native injection can be made impossible. Release obfuscation and a managed runtime can raise the cost of casual tampering, but durable enforcement remains server-side. See `docs/GGO-ANTICHEAT-ARCHITECTURE.md`.

## Contributor trust and production access

A contributor, tester, composer, anti-cheat developer or support worker does not automatically need production administrator access.

Use least privilege:
- scoped source/test access first;
- no production SSH/root, auth database, signing keys or server/auth secrets for ordinary contributors;
- support permissions stay separate from code/infrastructure permissions;
- security-sensitive contributions require reviewable source, reproducible build/CI and provenance;
- externally supplied anti-cheat code enters through isolated review/test/report-only stages before enforcement;
- official music/assets need clear rights/provenance and editable source/project material when that is part of the agreed deliverable;
- production deployment remains controlled by the GGO release chain, not a contributor workstation.

See `docs/GGO-CONTRIBUTOR-TRUST.md`.

## Community server roadmap
Not part of the first stable release.

Phase 1: official servers only.
Phase 2: private/community GGO Server Runtime for trusted creators.
Phase 3: launcher-integrated Create Server wizard.
Phase 4: curated Community Worlds directory with moderation, versions, compatibility manifests and isolated profiles.

This preserves the open/moddable spirit of Minecraft without turning the main GGO experience back into a generic server browser.

## Near-term implementation order
1. Finish the current Stage77/78 runtime, Stage79 portal/staff and launcher beta chain, then perform the first real smoke test.
2. Complete production GGO Game Ticket validation and fresh-session/reconnect architecture.
3. Add a launcher-managed pinned Java 17 runtime with verified package hashes/signatures; keep arbitrary system Java as an Advanced fallback, not the normal path.
4. Add an allowed GGO client-build policy and a minimal integrity handshake for required Core/UI/runtime build IDs.
5. Add first server-authoritative anti-cheat detectors for movement, weapon timing and inventory/progression mutations in report-only mode.
6. Add anti-cheat evidence and sanctions/audit views to the site staff console with role-gated access.
7. Enforce contributor least-privilege/reproducibility/provenance gates for security-sensitive code and official assets before accepting external work into release candidates.
8. Remove legacy /login and /register only after ticket-required production smoke tests are green.
9. Add shard discovery / health API and one-button best-region connection.
10. Replace remaining Minecraft-facing surfaces with GGO equivalents while leaving legal/technical runtime attribution available in Diagnostics.
11. Add persistent open-world systems, Operations and Events incrementally.
12. Evaluate an optional signed native Windows anti-tamper helper after server-side detection is mature; do not make a custom JDK fork a beta blocker.
13. Add Community Worlds only after the official ecosystem is stable.
