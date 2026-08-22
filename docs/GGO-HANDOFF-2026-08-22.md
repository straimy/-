# GunGloryOnline handoff — 2026-08-22

This file is the recovery point for continuing the project from a new ChatGPT account or a new chat.

## Non-negotiable repository rule

- Repository: `straimy/-`
- DO NOT write to `main`.
- `main` is intentionally left on the old v40-era history.
- Current working branches:
  - server: `server/runtime-hardening-v1`
  - client: `client/runtime-migration-v1`
  - launcher/site/auth: `launcher/bootstrap-v0.1`

## Product direction

GunGloryOnline must feel like a standalone tactical shooter/service game even though Minecraft 1.20.1 + Forge 47.4.10 + Java 17 remain the hidden Runtime v1 engine.

Player-facing Minecraft behavior must keep disappearing rather than exposing vanilla mechanics. Do not remove registries merely for cosmetic reasons if doing so would destabilize Runtime v1.

Important controls / UX:

- `E` — GGO equipment/backpack inventory.
- `M` — Activities.
- `N` — Full Map.
- hold `TAB` — squad/match overlay.
- MMB — ping.
- medical radial uses a rebindable Forge KeyMapping, default `H`.
- no physical compass menu.
- only 3 combat belt slots are player-facing.
- vanilla hotbar/hearts/hunger/xp/etc are hidden/replaced.

## Auth / entry architecture

Official entry is launcher-authoritative:

1. Launcher authenticates the GGO account.
2. Launcher issues a one-shot game ticket.
3. Client joins the official server carrying the ticket only in the native process environment, never through React.
4. Server consumes the ticket using the server key.
5. Until verification finishes, gameplay is quarantined.
6. Server sends explicit verification ACK.
7. Client keeps the full-screen `VERIFYING GGO ACCOUNT` overlay until ACK.

Unused one-shot ticket TTL is now 180 seconds to tolerate a slow first Forge startup. Replay after a successful consume remains forbidden.

## Exact verified milestones

### Server

Stage 59 pre-auth quarantine:
- run `32554022179`
- source `9c08709257510a838fb89ebb429380e568da57b5`
- apply/bootstrap/compile/verify/result = success.

Stage 61 verification ACK:
- run `32554247055`
- source `cb9272be793c1e616fae531695f50eab4023fd95`
- apply/bootstrap/compile/verify/result = success.

### Client

Stage 61 verification overlay:
- run `32554233574`
- source `0c445bb82eb3624ee351ec3184a6fbfbba9e4a7c`
- apply/bootstrap/compile/verify/result = success.

### Historical validated gameplay/runtime baseline

Earlier exact-green gates cover contracts, persistence, supply/extraction, network guard, social spawn, world fence, vanilla-screen/loot fences, GGO inventory, visible-item policy, input fence, combat HUD, medicine, death recap, recovery bag, immutable-map guard, map palette audit, official-auth integration and launcher packaging. Prefer current cumulative gates over old isolated receipts when both exist.

## Stage 62 — current integration target

Workflow:
`.github/workflows/ggo-runtime-stage62-entry-stack-integration.yml`

It checks out all three working branches and verifies together:

- server Core through Stage 61;
- client UI through Stage 61;
- auth smoke including 180-second ticket TTL;
- React launcher build;
- Rust fmt/check/tests;
- launcher-only secure ticket injection;
- canonical launcher-managed resource pack;
- release-manifest RP publishing;
- matching Core/UI package;
- dedicated Forge 1.20.1 / 47.4.10 server smoke.

Do not call Stage 62 green until `ci-results/stage62-entry-stack-integration.txt` exists and every recorded step is `success`.

## Resource pack / de-Minecraft state

The current historical RP is very large and overrides thousands of vanilla resources. Do NOT blindly delete those files because vanilla fallback would become visible.

Correct path:

1. Use runtime map palette telemetry on the actual authored world.
2. Export the palette.
3. Build a slim-pack dry-run report from the real used block/model/sound set.
4. Keep intentional GGO proxy items and authored map dependencies.
5. Prune only after the report proves the removed vanilla resources cannot reappear in normal gameplay.

Remaining visible cleanup includes custom recovery-bag asset, residual vanilla chat/loading/toasts/debug presentation, and replacing more command-driven UI actions with narrow GGO packets.

## Recovery bag rules

- Combat gear, armor and protected ammo remain with the player.
- FIELD ITEMS become one sealed recovery bag on death.
- Other players may carry the sealed bag but cannot open/reclaim it.
- Only the owner can recover contents.
- Reclaim goes only into FIELD slots.
- Partial reclaim keeps remaining contents in the same bag.
- Auction/return-market behavior is deferred until the base lifecycle is stable.

## Launcher / packages

Launcher branch: `launcher/bootstrap-v0.1`.

Required package targets:

- Windows NSIS `.exe`
- Windows MSI `.msi`
- Windows portable `.zip`
- Linux AppImage
- Linux `.deb`
- Linux `.rpm`

Production target is `play.kvicloud.ru:24842`.
Official RP filename is `GunGloryOnline-Official.zip` and is launcher-managed.

Do not send or publish an intermediate installer as if it were a release. Only use exact CI receipts/artifacts from a meaningful cumulative gate.

## What to finish on 2026-08-22

Priority order for a transferable closed-beta baseline:

1. Make Stage 62 exact green; fix exact failing job if red.
2. Build one current matching beta runtime artifact from exact server/client SHAs.
3. Verify launcher package gate again on current launcher HEAD.
4. Verify Windows and Linux launch/install artifacts on current HEAD where CI supports it.
5. Produce/update a release manifest that contains the current Core, UI and official RP.
6. Do a real clean-start test path: launcher -> auth -> install/update -> Play -> server join -> quarantine -> verified ACK -> GGO frontend/gameplay.
7. Verify site points at the correct beta manifest/downloads and does not advertise fake gameplay screenshots.
8. Add custom visual recovery-bag asset if safe to integrate without exposing vanilla fallback.
9. Create a final downloadable project/beta handoff package and update this document with exact artifact/run IDs.
10. Leave a short remaining-work list for the next account: world visual pass, RP slimming, content/balance, multiplayer QA, signing/release polish.

## New ChatGPT account setup

Minimum required connection: connect the SAME GitHub account/repository so the assistant can read/write the three working branches.

If site/VDS deployment is fully performed by GitHub Actions with repository secrets, no secret needs to be pasted into chat. If final deployment requires direct SSH/VDS access outside GitHub, configure that separately; never store passwords/private keys in this document or in Git.

When starting the new chat, upload any final local ZIP/beta package produced today if available and paste the continuation prompt below.

## Continuation prompt for a new chat

> Continue development of GunGloryOnline from repo `straimy/-`. First read `docs/GGO-HANDOFF-2026-08-22.md` from branch `client/runtime-migration-v1`, then inspect the latest exact CI receipts before making claims. Working branches are `server/runtime-hardening-v1`, `client/runtime-migration-v1`, and `launcher/bootstrap-v0.1`. NEVER write to `main`. Do not ask unnecessary questions; work autonomously when I say `далее`. Keep Minecraft/Forge as hidden Runtime v1 but remove player-facing Minecraft behavior/UI/assets. Prioritize a runnable closed-beta flow: launcher -> GGO auth -> install/update -> official server -> pre-auth quarantine -> verification ACK -> GGO UI/gameplay. Only call a gate green when an exact receipt proves it. Fix failing integration gates before adding unrelated features. Then continue de-Minecraft/RP cleanup, packet migration, content, QA and release packaging. Communicate in Russian, compactly, and do not send intermediate installers as releases.

## How many iterations remain

A realistic estimate from this recovery point:

- ~25–50 focused `далее` iterations: enough to push toward a solid transferable closed-beta baseline if CI does not expose a major architecture issue.
- ~60–100 focused iterations: likely enough for broad release-candidate polish and QA.
- 100+ only if substantial new maps/content/features or deep multiplayer/release issues are added.

The iteration count is not a promise; exact CI failures and manual world/art requirements can change it.
