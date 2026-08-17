# GunGloryOnline Runtime Migration Roadmap

Goal: make GunGloryOnline feel like a standalone game while keeping Minecraft 1.20.1 + Forge 47.4.10 as Runtime v1 until replacing it is economically justified.

## Immediate priorities

### Phase 1 — Remove vanilla UX from the player path
Status: IN PROGRESS

- Launcher is the only server browser.
- Auto-connect to the selected GGO server.
- Replace vanilla title screen with GGO shell.
- Replace pause screen with GGO shell.
- Hide Singleplayer, Multiplayer, Realms and Mods from the normal player flow.
- Keep only: Back to Game, Game Files, Settings, Exit Game.
- Replace loading/connecting text with GGO wording where feasible.
- Prevent accidental navigation back into vanilla menus.

Done when: a normal player can launch, connect, configure and exit without using a vanilla Minecraft menu.

### Phase 2 — Replace vanilla presentation/content
Status: NEXT

- GGO-owned maps and arena presentation.
- GGO HUD and menu visuals.
- GGO weapon/entity models, sounds and animations.
- Mandatory managed resource pack delivered by launcher manifest.
- Reduce visible vanilla item/block terminology.
- Introduce GGO asset namespaces for all new content.

Done when: gameplay screenshots no longer read visually as a normal Minecraft server.

### Phase 3 — GGO identity and backend-owned progression
Status: NEXT

- Primary identity: ggo_player_id UUID.
- Minecraft UUID becomes ExternalIdentity(provider=minecraft).
- Backend owns XP, level, credits, currencies, stats, cosmetics, bans and account settings.
- Server-authoritative progression; local client stores no critical progression.
- Compatibility link for existing Minecraft profiles.
- API contract intentionally independent from Forge classes.

Done when: changing the runtime/provider no longer means losing the player's GGO account or progression.

### Phase 4 — Forge client shell
Status: PLANNED

- Own boot/loading shell.
- Own settings screens.
- Own HUD and overlays.
- Own server/session transitions.
- Launcher and client exchange only GGO concepts, not Minecraft profile/version UI concepts.
- Minecraft remains an implementation detail: renderer, world/entity sync, input and networking runtime.

Done when: the player rarely sees Minecraft branding or vanilla screens during normal use.

### Phase 5 — Reduce dependency on block-world gameplay
Status: PLANNED

- Lock down building/mining/inventory flows not used by GGO.
- Use authored arenas/maps rather than normal survival-world interaction.
- Prefer custom interaction, movement, weapons and entities over vanilla equivalents.
- Create runtime-neutral domain models for match, arena, loadout, player, weapon, team and progression.
- Keep world/entity networking adapters behind interfaces.

Done when: game rules no longer assume Minecraft blocks/items as the domain model.

### Phase 6 — Native runtime option
Status: FUTURE

- Prototype runtime-native adapter in Godot/Unity/Bevy or another chosen engine.
- Reuse GGO backend, ggo_player_id, progression API, manifests and asset concepts.
- Keep Minecraft Runtime v1 available during transition.
- Migrate feature-by-feature rather than rewriting the live game at once.

Done when: native client can join a GGO backend/session without Minecraft-specific account/progression assumptions.

## Architecture rules from now on

1. New business logic must not depend directly on Forge/Minecraft classes unless it is an adapter.
2. Core models should be runtime-neutral where practical.
3. Launcher manifests own client files: mods, configs, resource packs, future assets and native client files.
4. Server is authoritative for progression and competitive state.
5. User-owned files such as screenshots/shaders are never deleted by the managed updater.
6. Minecraft is called `GunGlory Runtime v1` in GGO-facing architecture.
7. Native migration must not require account resets.

## Workstreams

- `launcher/bootstrap-v0.1`: launcher, VDS content delivery, updater, auth shell.
- `client/runtime-migration-v1`: client-shell and runtime decoupling work.
- `main`: active server/gameplay development; avoid unrelated migration experiments here.
