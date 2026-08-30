# GGO Frontend and Graphics UX

## Goal
The player should experience GunGloryOnline as a standalone online game. Minecraft/Forge remain implementation details and are hidden from normal navigation.

## Game frontend
After launcher authentication, the game opens into a GGO frontend instead of the vanilla Minecraft title screen.

Primary layout:
- player character / selected loadout in the center;
- GGO profile card: display name, account ID, rank, level, region, season progress;
- currencies and notifications in the top-right;
- squad/friends panel on the left;
- current world/event card and one large ENTER GGO button;
- secondary actions: Training, Events, Loadout, Inventory, Settings, Exit.

The frontend is account-aware. It must never ask for `/login` or `/register` in the world. Authentication originates on the website/launcher and is carried into the game by the GGO session ticket system.

## Online entry flow
1. launcher signs into GGO Account;
2. launcher requests a short-lived game ticket;
3. game frontend already displays the authenticated GGO profile;
4. ENTER GGO resolves the best official region/shard;
5. game connects directly;
6. server validates the ticket and loads server-authoritative profile/progression.

The player does not see a generic Minecraft server browser in the standard flow.

## Modes visible from frontend
- ENTER GGO — persistent online world;
- TRAINING — local simulation, no online rewards;
- BATTLE ROYALE — matchmaking queue, map rotation;
- EVENTS — seasonal modes such as Winter Event;
- OPERATIONS — later PvE/PvPvE missions;
- COMMUNITY — later curated community worlds.

## Graphics UX
Normal users configure `GGO Graphics`, not Minecraft shader internals.

Top-level presets:
- Performance — shaders off;
- Competitive — lightweight shader / high visibility;
- Balanced — recommended default;
- High — stronger shadows/reflections;
- Cinematic — atmospheric/volumetric visual profile;
- Custom — advanced shader controls.

Player-facing controls:
- render distance;
- shadow quality;
- reflections;
- volumetric lighting;
- water quality;
- anti-aliasing;
- particles;
- post-processing;
- brightness;
- field of view;
- frame limit / VSync;
- fullscreen / resolution.

Minecraft/Oculus/Embeddium-specific pages are hidden behind Settings -> Advanced -> Rendering Backend.

## Shader backend
Forge 1.20.1 client uses Embeddium today. Shader support is introduced through an Oculus-compatible backend only after compatibility testing with the full GGO mod set.

Do not make the availability of shaders a requirement for joining official servers. Graphics are client-local and must not affect gameplay authority.

## Shader pack policy
Candidate packs supplied for evaluation:

### Miniature Shader
Potential role: Competitive/Balanced/High. It exposes LOWEST, LOW, NORMAL and HIGH profiles and is lightweight enough to be useful as the competitive base. Distribution rights must be confirmed before the launcher hosts the ZIP.

### Photon
Potential role: Cinematic. Its included license permits redistribution as part of a modpack while requiring the license to remain included. Any bundled copy must preserve its LICENSE and attribution.

### Complementary Unbound
Potential optional pack. Its included license does not permit direct-file redistribution as an ordinary modpack asset; enabled-by-default modpack distribution must use supported Modrinth/CurseForge mechanisms and attribution. Do not upload its ZIP to GGO CDN as a normal bundled file.

## Character/profile frontend evolution
Phase 1:
- display name;
- avatar/skin preview;
- online state;
- region;
- client version;
- Play/Training/BR buttons.

Phase 2:
- rank;
- account level;
- currencies;
- equipped primary/secondary weapon;
- cosmetic outfit;
- season progress;
- recent notifications.

Phase 3:
- 3D animated player preview;
- loadout switching without entering a world;
- friends/party;
- clan/faction;
- mission/event panel;
- matchmaking state integrated into the frontend.

## Settings visual style
Replace vanilla option lists with a GGO settings shell inspired by modern tactical/MMO interfaces:
- left-side categories;
- dark graphite panels;
- red accent states;
- clear sliders and segmented controls;
- live description panel for the selected option;
- VRAM/RAM/GPU hints where available;
- presets at the top of Graphics;
- Apply / Revert / Reset page controls.

Categories:
- Game;
- Audio;
- Graphics;
- Controls;
- Interface;
- Accessibility;
- Network;
- Advanced.

## Separation from Minecraft
Normal UI should use:
- GGO Client v40, not Minecraft 1.20.1;
- GGO Graphics, not Shader Packs;
- GGO Worlds / Network, not Multiplayer;
- Training, not Singleplayer;
- GGO Data, not `.minecraft`;
- GGO Diagnostics, not Minecraft Log.

Technical Minecraft, Forge, Java, Oculus and Embeddium versions remain visible in Diagnostics for transparency and support.
