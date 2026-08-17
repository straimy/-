# GunGloryOnline — game direction

GunGloryOnline should not settle as "Minecraft with guns". Minecraft/Forge is Runtime v1: rendering, networking, world/entity infrastructure and compatibility while the player-facing game becomes first-party GGO.

## Core identity

The primary experience is a session-based PvP action game with a persistent GGO profile and a world state that reacts to the community between matches.

### Match layer

- Fast objective-driven PvP rounds rather than endless vanilla survival.
- Teams fight over objectives, resources, extraction zones, terminals and temporary advantages.
- Winning should require more than kills: map control, economy, timing and completing contracts matter.
- Weapons are loadout choices with tradeoffs, not a simple vertical power ladder.

### Persistent layer

- `ggo_player_id` is the permanent identity.
- Persistent XP, ranks, cosmetics, mastery, statistics and achievements live in the GGO backend, not Minecraft player data.
- Factions/companies can compete over seasonal objectives.
- Match outcomes contribute to a lightweight global campaign state visible in the launcher/site.
- Seasons can reset competitive territory while retaining account progression/cosmetics according to policy.

### Contracts

Contracts are a signature system between pure PvP and progression:

- Personal contracts: win with restrictions, extract an item, defend a point, assist teammates, survive a streak.
- Squad contracts: coordinated objectives that reward teamwork rather than farming kills.
- Server/global contracts: community goals that change the world or unlock a temporary event.
- Contract choices should create different play styles and risk/reward decisions.

### Dynamic operations

Instead of one static queue forever, the server can rotate Operations:

- standard team PvP;
- extraction operation;
- escort/convoy;
- control sectors;
- PvPvE event with a neutral boss/objective;
- limited weapon rules;
- faction campaign battle.

The launcher presents the active Operation as part of the game, not as a Minecraft server entry.

## Offline mode

An offline mode is desirable, but should be intentionally separate from the authoritative online economy.

### Offline Training

- playable without the GGO backend after required assets are installed;
- local map with bots, shooting range and movement/combat practice;
- weapon testing and tutorial missions;
- local settings/loadout experimentation;
- no authoritative XP, currency, ranked stats or market rewards;
- local progress is explicitly Training progress and cannot be imported into competitive online progression.

This gives the product a real offline game mode without creating a trivial cheat path into the online economy.

### Later: offline operations

Once bot AI and mission scripting are mature, add small PvE/co-op-style local missions. They can award local medals/cosmetic tutorial unlocks but not competitive currency unless later validated server-side.

## Runtime migration priorities

1. Remove vanilla Minecraft UX from the player path.
2. Own identity, accounts and skins through GGO.
3. Replace vanilla HUD/loading/settings with GGO equivalents.
4. Own progression/economy in the backend.
5. Reduce dependence on vanilla inventory/block gameplay.
6. Introduce first-party map/objective abstractions independent of Minecraft concepts.
7. Keep backend/protocols portable so a future native client can reuse accounts, progression and game services.

## What makes GGO distinct

The combination should be recognizable:

**fast PvP operations + persistent GGO identity + contracts + a community campaign + first-party launcher/client presentation.**

Do not compete by adding hundreds of random mechanics. Prefer a small number of systems that interact deeply and are visible to players every session.
