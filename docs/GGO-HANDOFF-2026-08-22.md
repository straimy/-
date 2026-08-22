# GunGloryOnline handoff — 2026-08-22

Authoritative recovery point for continuing GunGloryOnline from another ChatGPT account/chat.

## Repository safety

- Repository: `straimy/-`
- **NEVER write to `main`.**
- Work only on:
  - server: `server/runtime-hardening-v1`
  - client: `client/runtime-migration-v1`
  - launcher/site/auth: `launcher/bootstrap-v0.1`
- Exact CI receipt files are the source of truth. Never infer green from memory or a generic commit status.

## Product direction

GunGloryOnline should present as a standalone tactical shooter/service game. Minecraft 1.20.1 + Forge 47.4.10 + Java 17 are hidden **Runtime v1** only.

Player-facing controls:
- `E` — GGO equipment/backpack inventory
- `M` — Activities
- `N` — Full Map
- hold `TAB` — squad/match overlay
- MMB — tactical ping
- `H` default — rebindable medical radial
- no physical compass menu
- three player-facing combat belt slots

Social-hub players remain visible and safe. Do not globally hide players.

## Official entry/auth architecture

Launcher-authoritative path:
1. launcher authenticates GGO Account session;
2. immediately before Java launch it creates a one-shot `official-online` ticket;
3. ticket is passed only to child Java as `GGO_GAME_TICKET`;
4. client forwards ticket through Core launch-ticket channel;
5. server consumes ticket with `GGO_SERVER_KEY` and binds GGO identity;
6. pre-auth gameplay is quarantined;
7. server sends boolean verification ACK;
8. client keeps `VERIFYING GGO ACCOUNT` over gameplay until ACK.

Current contract:
- unused game-ticket TTL: **180 seconds**
- replay/race protected, one-shot
- launch-ticket network protocol: **2**
- official route: `play.kvicloud.ru:24842`
- auth base: `https://ggo.kvicloud.ru/api/v1`

Fresh standalone auth proof:
- receipt `.ci/auth/32567151493.txt`
- run `32567151493`
- source `971463f8ab6842349b46da901b6894d3ee786aa1`
- syntax/smoke/ticket-contract/deploy = success
- TTL 180, replay protected, race tested
- result = success

## Exact verified runtime milestones

- Stage 62 entry-stack integration: run `32555227197`, successful attempt 2; server/client/launcher/auth/assets/package/dedicated smoke success.
- Stage 63 Recovery Bag visual: run `32561512306`; CustomModelData `720049`, GGO model/texture, dedicated smoke success.
- Stage 64 RP slim planner: run `32561643315`; fail-closed/non-destructive; no real prune without authored-map palette.
- Stage 65 first-party UI packets: run `32562061250`; server/client/packet/package/dedicated smoke success; channel `gunnerarena:ggo_ui_action`; no normal-player `/ggomed`/`/ggoinv` transport.
- Stage 66 production-surface fence: run `32562512801`; debug/FPS/item/effect/score/boss/disc engine surfaces fenced; chat/subtitles preserved.
- Stage 67 GGO chat shell: run `32562603117`; GGO chat chrome, hidden vanilla ChatScreen remains only transport/history/signing engine.
- Stage 68 cumulative release-candidate: run `32562750807`; server SHA `f126fd689a2bc26c5eb03477f77ca0f027b39480`, client SHA `80dbe934cc2b97edd8c3289323d8e0ba65964581`, launcher SHA `d16351d5749d1cb2129664ca35d5edc37892a177`; server/client/RP/launcher-auth/assets/package/dedicated smoke all success.
- Stage 69 player-facing engine-brand fence: run `32566750907`; client SHA `8a0feb2ae8dc3e38db086b93392bf40540b9d158`; client build + brand contract success; no visible Minecraft/Forge/Mojang brand copy in GGO UI.

## Stage 70 — transferable Closed Beta baseline — GREEN

Receipt: `ci-results/stage70-closed-beta-baseline.txt`

Use the **final second run**:
- workflow `GGO Runtime Stage 70 Closed Beta Baseline`
- run `32567000478`
- server head `f75fe309629cc4b1dfd19581708c68f7d54ddd99`
- client head `d92616f90b5d09e8bb2204f83b0169f3efaeaf04`
- launcher head `a46e79514b3e3b135759ec5ef2f7b7e665df7b1f`
- Stage 68 runtime run `32562750807`
- Stage 69 UI run `32566750907`
- launcher package run `32562889069`
- Windows verify run `32562815120`
- Linux verify run `32561828672`
- prerequisite receipts/bundle/upload = success
- ZIP bytes `147768867`
- ZIP SHA256 `f71ec514ae9df25cd85d20f0ebbfde768024ac5bb781cae983811225204b8687`
- result = success

Artifact: `GunGloryOnline-Closed-Beta-Baseline-2026-08-22`.

It contains:
- matching server/client runtime;
- Stage 69 brand-clean UI;
- `GunGloryOnline-Official.zip`;
- Windows EXE/MSI/portable ZIP;
- Linux AppImage/DEB/RPM;
- website artifact;
- source snapshots of all three working branches;
- receipts, docs, checksums and new-chat prompt.

Exact GGO-owned beta client files:
- Core `gungloryonline-core-runtime-v1-stage68.jar`, SHA256 `a4c8e4707566319f5be00ca1e422f7281c455299e228e4d2ab9bcdb51795f5dc`, size `433242`
- UI `gungloryonline-ui-runtime-v1-stage69.jar`, SHA256 `c40ab7eac425fc247801c16b91c98887c12e1be2c943b17bdba257c176340a04`, size `191206`
- RP `GunGloryOnline-Official.zip`, SHA256 `962296934ff311e20ebc40fb437832c6dbf38114d3247b0dea5a819d9ae0594e`, size `11663399`

## Launcher package proofs

Last exact-green package matrix before current manifest refresh:
- `.ci/launcher/32562889069.txt`
- source `9a358d46051fd823e31e18259d2da20af7e5490c`
- Windows/Linux build, auth and website success.

Dedicated Windows verify:
- `.ci/windows/32562815120.txt`
- source `f5a487cba395247b546b5f3566f27a031c598b1b`
- install/icons/frontend/rust fmt/rust check/runtime tests/package/verify success.

Dedicated Linux verify:
- `.ci/linux/32561828672.txt`
- source `b8b680a11669025f33319ebb7b26252610ca2625`
- deps/install/icons/frontend/rust fmt/rust check/runtime tests/package/verify success.

A new package matrix was triggered after switching the repo beta manifest to Stage68/69 + canonical RP. Before calling that new package run green, read its new `.ci/launcher/<run>.txt` receipt.

## Resource pack state

Official launcher-managed filename: `GunGloryOnline-Official.zip`.

Do **not** blindly delete the historical vanilla overrides: missing overrides can reveal vanilla fallback assets.

Remaining safe workflow:
1. run actual authored beta world/map;
2. export real `ggo-map-palette.txt`;
3. run Stage 64 planner;
4. review candidate removals;
5. only then create/verify a slim production RP.

## Production beta manifest in repository

`launcher/bootstrap-v0.1:site/content/manifests/beta.json` has now been switched to the Closed Beta GGO-owned files:
- Stage68 Core
- Stage69 UI
- canonical `resourcepacks/GunGloryOnline-Official.zip`

Other third-party dependency entries are preserved.

The launcher package workflow contract was updated to require these new names and reject the old Swittie RP.

## Public/live reachability — NOT green yet

Latest corrected public probe:
- receipt `.ci/public/32567346737.txt`
- workflow `GGO Public Beta Reachability`
- run `32567346737`
- source `b686c2742ca2b4f2973973fc519db527008c9e4b`

Results:
- website HTTPS = success
- auth health HTTPS = failure
- beta manifest HTTPS = failure
- `play.kvicloud.ru` DNS = failure
- official hostname TCP = failure
- origin `2.26.100.125:24842` TCP = failure
- overall = failure

Exact diagnosis from the run logs:
- `https://ggo.kvicloud.ru/` is live.
- `/api/v1/health` currently does not return the expected GGO auth JSON; live VDS appears to have an old nginx/auth deployment.
- `/content/manifests/beta.json` is reachable but the live copy is stale and still listed old Core/UI and `GunGloryOnline-ResourcePack-1.20.1-v5-swittie-social.zip` at probe time.
- `play.kvicloud.ru` does not resolve.
- `2.26.100.125:24842` returns connection refused, so the game server is not listening there at probe time.

Do not represent the public online path as ready until a later public reachability receipt is fully green.

## Stage 72 — VDS deployment payload — GREEN

Receipt `.ci/deploy/32567496437.txt` on launcher branch:
- workflow `GGO Stage 72 VDS Deploy Payload`
- run `32567496437`
- source `ba9d230ab6f1f75dc4c16a8013cb14ed8aebb82f`
- baseline Stage70 run `32567000478`
- auth receipt run `32567151493`
- public pre-deploy probe run `32567346737`
- payload = success
- upload = success
- bytes `122401864`
- SHA256 `4265f34b5ef75fb237de715c4def83b384509004eae7ae73f3c6a0b1889ffa7d`
- result = success

Artifact: `GunGloryOnline-Stage72-VDS-Deploy-Payload-2026-08-22`.

It contains:
- current site + nginx + auth service source;
- Windows/Linux launcher packages under web downloads;
- Stage68 Core + Stage69 UI + canonical RP under content files;
- server Stage68 Core;
- exact receipts/checksums;
- deployment instructions.

Deployment requirements that remain outside GitHub CI:
1. back up `/var/lib/ggo-auth/auth.db` on VDS;
2. deploy current `site/install-site.sh` payload so nginx `/api/v1/` proxies to auth service;
3. create/verify DNS A record `play` -> `2.26.100.125`;
4. start/repair Forge 1.20.1 / 47.4.10 server listening on TCP `24842` with current Core;
5. provide server-side `GGO_SERVER_KEY` and `GGO_AUTH_API_URL=https://ggo.kvicloud.ru/api/v1` without exposing secrets to client/repo;
6. rerun `GGO Public Beta Reachability` until every check is success;
7. then perform a real clean-machine launcher -> account -> install/update -> game -> server -> verification ACK -> gameplay session.

There is currently no proven authorized SSH/DNS connector/workflow for changing the real VDS/DNS automatically. Do not claim deployment occurred unless actual access is added and verified.

## Current player-facing runtime state

Already replaced/fenced:
- vanilla title/front-end flow
- vanilla hotbar/hearts/armor/hunger/xp/air presentation
- vanilla death screen
- standard player inventory/crafting presentation for normal players
- advancements route
- vanilla TAB player list
- default crosshair
- major debug/score/boss/effect/item-name production overlays
- physical compass menu
- internal medicine/inventory slash-command transport
- vanilla chat input chrome (hidden transport remains)
- recovery bag vanilla appearance

## Recovery bag contract

- protected combat/ammo/armor stays according to the runtime rule;
- FIELD items become one owner-bound sealed recovery bag;
- another player may carry but cannot reclaim it;
- only owner reclaims;
- partial reclaim leaves the rest in the same bag;
- auction/return-market remains deferred.

## What remains after the transferable beta baseline

Do not redo green Stages 62–72.

Priority order:
1. deploy Stage72 payload + fix `play` DNS + bring server listener online;
2. rerun public reachability until fully green;
3. real clean-machine graphical launcher/auth/install/join/ACK/gameplay test;
4. authored-map run + real palette telemetry + safe RP slimming;
5. world/map visual pass and remaining player-facing asset cleanup found in play;
6. multiplayer soak/reconnect/race/edge QA, balance and content completion;
7. matchmaking/social UX/content polish;
8. performance profiling and Windows/Linux clean-machine QA;
9. signing/update/recovery strategy and public-v1 polish;
10. replace website placeholder captures with real gameplay screenshots.

## New ChatGPT account setup

Minimum:
- connect/authorize the **same GitHub account/repository** so the new assistant can access `straimy/-` and the three working branches.

Keep secrets in GitHub/VDS. Never paste server keys, SSH private keys, passwords, tokens or auth DB into chat.

If the new account is also expected to perform live VDS/DNS changes, connect the relevant hosting/DNS provider or an authorized SSH/deployment mechanism separately. GitHub access alone is sufficient to continue code/build work but not to mutate the currently unconnected live infrastructure.

Keep the Stage70 Closed Beta ZIP and Stage72 VDS Deploy ZIP locally as disaster-recovery snapshots.

## Prompt for the new chat

> Continue GunGloryOnline from GitHub repo `straimy/-`. First read `docs/GGO-HANDOFF-2026-08-22.md` on `client/runtime-migration-v1`, then read `ci-results/stage70-closed-beta-baseline.txt`, `.ci/deploy/32567496437.txt`, `.ci/auth/32567151493.txt`, and the latest `.ci/public/*.txt` / `.ci/launcher/*.txt` on `launcher/bootstrap-v0.1`. NEVER write to `main`. Work only on `server/runtime-hardening-v1`, `client/runtime-migration-v1`, and `launcher/bootstrap-v0.1`. Minecraft 1.20.1 + Forge 47.4.10 + Java 17 are hidden Runtime v1. Do not repeat green Stages 62–72. The transferable Closed Beta is already built; next priority is deploy Stage72 to the live VDS, configure `play.kvicloud.ru -> 2.26.100.125`, bring the game server up on `24842`, rerun public reachability until green, then do a real clean-machine launcher -> auth -> update -> join -> verification ACK -> gameplay test. After that continue real-map palette/RP slimming, map/content/balance/multiplayer QA, signing/update/recovery and final polish. Exact receipt files are the source of truth. When I say `далее`, do actual GitHub work autonomously, keep responses compact in Russian, and do not touch `main`.

## Practical status

The project has reached the intended **transferable Closed Beta / release-candidate technical baseline**.

The code/build/package side is ready enough to hand off. The live public online path is still blocked by deployment/DNS/server-listener work and must not be called ready until the public reachability gate is green and a real human clean-machine play session succeeds.
