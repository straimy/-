# GunGloryOnline handoff — 2026-08-22

This is the authoritative recovery point for continuing GunGloryOnline from another ChatGPT account/chat.

## Repository safety

- Repository: `straimy/-`
- **NEVER write to `main`.**
- Working branches only:
  - server: `server/runtime-hardening-v1`
  - client: `client/runtime-migration-v1`
  - launcher/site/auth: `launcher/bootstrap-v0.1`
- Exact CI receipt files are the source of truth. Do not infer green from a commit status, a workflow name, or memory.

## Product direction

GunGloryOnline should present as a standalone tactical shooter/service game. Minecraft 1.20.1 + Forge 47.4.10 + Java 17 are only the hidden **Runtime v1** engine.

Do not destabilize registries/runtime merely to delete engine internals. Instead, fence or replace player-facing Minecraft/Forge behavior, UI and assets.

Primary controls:

- `E` — GGO equipment/backpack inventory.
- `M` — Activities.
- `N` — Full Map.
- hold `TAB` — squad/match overlay.
- MMB — tactical ping.
- `H` by default — rebindable medical radial.
- no physical compass menu.
- only three combat belt slots are player-facing.

## Official entry/auth architecture

Official online entry is launcher-authoritative:

1. Launcher authenticates a GGO Account session.
2. Immediately before Java launch it creates a one-shot `official-online` game ticket.
3. Ticket is passed only to the child Java process as `GGO_GAME_TICKET`; it is not placed in React state, CLI preview, logs, or persistent launcher state.
4. Client forwards it through the Core launch-ticket channel.
5. Server consumes the ticket with `GGO_SERVER_KEY` and binds the GGO identity.
6. Pre-auth gameplay is quarantined.
7. Server returns a boolean verification ACK.
8. Client keeps `VERIFYING GGO ACCOUNT` over gameplay until the ACK completes.

Current unused ticket TTL: **180 seconds**. Ticket remains one-shot/replay protected.
Current launch-ticket network protocol: **2**.
Official route: `play.kvicloud.ru:24842`.

## Exact current verified milestones

### Entry/runtime integration

Stage 62 — full entry stack integration:
- run `32555227197`, successful attempt 2.
- server/client/launcher/auth/assets/package/dedicated Forge smoke all success.

Stage 63 — Recovery Bag visual identity:
- run `32561512306`.
- Core, official RP, package and dedicated Forge smoke success.
- recovery bag uses CustomModelData `720049` and a GGO model/texture.

Stage 64 — RP slim planner:
- run `32561643315`.
- planner is fail-closed/non-destructive.
- **No production RP pruning was performed** because a real authored-world palette is still required.

Stage 65 — first-party UI packet actions:
- run `32562061250`.
- server/client builds, packet contract, package and dedicated smoke success.
- medicine/inventory first-party actions no longer rely on free-form `/ggomed` / `/ggoinv` client commands.
- channel: `gunnerarena:ggo_ui_action`.

Stage 66 — production surface fence:
- run `32562512801`.
- client build and surface contract success.
- debug/FPS/item/effect/score/boss/disc engine surfaces are fenced.
- chat/subtitles are intentionally preserved.
- loading transition lifecycle is not replaced, only covered/presented by GGO where safe.

Stage 67 — GGO chat shell:
- run `32562603117`.
- client build/chat contract success.
- `T`/slash chat receives GGO chrome while vanilla `ChatScreen` remains the hidden transport/history/signing engine.
- messages and command suggestions are preserved.

Stage 68 — cumulative Release Candidate integration:
- run `32562750807`.
- server SHA `f126fd689a2bc26c5eb03477f77ca0f027b39480`.
- client SHA `80dbe934cc2b97edd8c3289323d8e0ba65964581`.
- launcher SHA `d16351d5749d1cb2129664ca35d5edc37892a177`.
- server build, client build, official RP, launcher/auth/platform contract, canonical assets, package and dedicated server smoke = success.
- launch-ticket protocol = `2`.

Stage 69 — player-facing engine-brand fence:
- run `32566750907`.
- client SHA `8a0feb2ae8dc3e38db086b93392bf40540b9d158`.
- client build = success.
- player-facing brand contract = success.
- GGO UI has no visible Minecraft/Forge/Mojang brand copy; hidden Java identifiers/imports are allowed.

### Launcher packages

Package matrix:
- workflow `GunGloryOnline Launcher Packages`
- run `32562889069`
- source `9a358d46051fd823e31e18259d2da20af7e5490c`
- build/auth/website all success.
- artifacts:
  - Windows: EXE, MSI, Portable ZIP.
  - Linux: AppImage, DEB, RPM.
  - website artifact.

Dedicated Windows verify:
- run `32562815120`
- source `f5a487cba395247b546b5f3566f27a031c598b1b`
- install/icons/frontend/rust fmt/rust check/runtime tests/package/verify = success.

Dedicated Linux verify:
- run `32561828672`
- source `b8b680a11669025f33319ebb7b26252610ca2625`
- deps/install/icons/frontend/rust fmt/rust check/runtime tests/package/verify = success.

The cumulative Stage 68 launcher check also proved there were no launcher-directory changes invalidating the verified Linux source.

## Current player-facing runtime state

Already replaced/fenced:

- vanilla title/front-end flow;
- vanilla hotbar/hearts/armor/hunger/xp/air presentation;
- vanilla death screen;
- standard player inventory/crafting presentation for normal players;
- advancements route;
- vanilla TAB player list;
- default crosshair;
- major debug/score/boss/effect/item-name production overlays;
- physical compass menu;
- internal medicine/inventory slash-command transport;
- vanilla chat input chrome (transport engine remains hidden);
- recovery bag vanilla appearance.

Social-hub players remain visible and safe. Do not globally hide players.

## Recovery bag contract

- Protected combat slots / protected ammo / armor remain with owner according to the runtime rule.
- FIELD items become one owner-bound sealed recovery bag.
- Another player may carry the sealed bag but cannot reclaim its contents.
- Only the owner reclaims it.
- Partial reclaim leaves the remaining contents in the same bag.
- Auction/return-market expansion remains deferred.

## Resource pack state

Official launcher-managed filename: `GunGloryOnline-Official.zip`.

The historical RP overrides a large amount of vanilla content. **Do not blindly delete those overrides** because doing so can reveal vanilla fallback assets.

Correct remaining RP workflow:

1. Run the actual authored beta world/map.
2. Export real `ggo-map-palette.txt` telemetry.
3. Run Stage 64 planner against that palette.
4. Review candidate removals.
5. Only then generate/verify a slim production RP.

This is intentionally deferred rather than faked by CI.

## Stage 70 — transferable Closed Beta bundle

Workflow: `.github/workflows/ggo-runtime-stage70-closed-beta-baseline.yml`.

It is intended to produce one downloadable transfer package containing:

- exact-green Stage 68 runtime Core + official RP;
- exact-green Stage 69 brand-clean UI replacing the Stage 68 UI jar;
- Windows launcher EXE/MSI/Portable ZIP;
- Linux AppImage/DEB/RPM;
- website artifact;
- source snapshots of the three working branches;
- handoff/control/OST docs;
- immutable prerequisite receipts;
- checksums and a new-chat prompt.

Before claiming this bundle is ready, read `ci-results/stage70-closed-beta-baseline.txt` and require `result=success`.

## What remains after the transferable beta baseline

These are the high-value remaining tasks for the next account/chat; do not redo already-green stages:

1. Real clean-machine interactive path: launcher -> GGO account -> update/install -> Java start -> server join -> quarantine -> verification ACK -> frontend -> gameplay. CI proves components/build contracts but cannot replace all real GPU/client/user-environment testing.
2. Deploy/verify the chosen public website + manifest/VDS path and confirm downloads resolve to the intended beta artifacts.
3. Run the authored map and export real palette telemetry; then perform RP slimming safely.
4. World/map visual pass and remaining player-facing asset cleanup discovered during actual play.
5. Multiplayer soak, reconnect/race/edge cases, balance and content completion.
6. Matchmaking/social UX/content beyond the current baseline where needed.
7. Performance profiling and clean-machine Windows/Linux QA.
8. Final signing/update/recovery strategy and release-candidate/public-v1 polish.
9. Replace website placeholder captures with real gameplay screenshots after the visual pass.

## New ChatGPT account setup

Minimum:

- connect/authorize the **same GitHub account/repository** so the new assistant can access `straimy/-` and the three working branches.

Usually nothing else needs to be pasted into chat. Repository/CI secrets should remain in GitHub. Never paste server keys, SSH private keys, passwords or auth secrets into the handoff.

If final deployment later requires a direct external VDS/hosting connection not represented in GitHub Actions, configure that connection separately on the new account.

Keep the final Stage 70 ZIP locally as an additional disaster-recovery snapshot even though GitHub remains the main source of truth.

## Prompt for the new chat

> Continue development of GunGloryOnline from GitHub repo `straimy/-`. First read `docs/GGO-HANDOFF-2026-08-22.md` on branch `client/runtime-migration-v1`, then read the latest exact receipts, especially `ci-results/stage70-closed-beta-baseline.txt` if it exists. NEVER write to `main`. Work only on `server/runtime-hardening-v1`, `client/runtime-migration-v1`, and `launcher/bootstrap-v0.1`. Minecraft 1.20.1 + Forge 47.4.10 + Java 17 are hidden Runtime v1; keep removing player-facing engine behavior without destabilizing the runtime. Do not repeat already-green Stages 62–69. Stage 68 is the cumulative release-candidate runtime and Stage 69 is the brand-clean UI. Continue with real clean-machine launcher/auth/server testing, public deployment verification, real-map palette telemetry + safe RP slimming, map/content/balance/multiplayer QA, signing/update/recovery and final release polish. Exact receipt files are the source of truth. When I say `далее`, do actual GitHub work autonomously and keep responses compact in Russian.

## Practical status

The project is now at a **transferable closed-beta / release-candidate technical baseline**, not a finished public 1.0.

Do not represent CI-only validation as proof that a human has completed a full clean-machine graphical play session. Keep that distinction explicit.
