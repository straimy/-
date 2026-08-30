# GunGloryOnline Training Mode Contract

## Goal
Training is an offline/local gameplay mode that makes GunGloryOnline usable without the production server while keeping online progression authoritative and cheat-resistant.

## Product flow
Launcher exposes two primary actions:

- **PLAY ONLINE** — production GGO session, selected server, server-authoritative progression.
- **TRAINING** — local runtime, no production server required, no online rewards.

Minecraft singleplayer UI is never exposed to the player. Training is launched directly into a GGO-owned training world/session.

## Phase 1 content
- movement tutorial
- shooting range
- weapon/loadout testing
- AI target bots
- recoil/accuracy practice
- damage and DPS readout
- reset/restart exercise

## Phase 2 content
- bot arena
- wave survival
- short PvE operations
- objective drills

## Progression isolation
Training MUST NOT write authoritative online fields:

- ranked rating
- account XP
- account level
- online currency
- paid/premium currency
- unlock ownership
- seasonal campaign contribution
- competitive statistics

Training may store local-only data under `ggo-training/`:

- tutorial completion
- personal best times
- preferred training loadout
- aim/recoil practice statistics
- local difficulty settings

The launcher/runtime must treat these values as untrusted local data and never upload them as rewards.

## Runtime identity
Training can use the active GGO public identity for display name and cosmetic skin, but authentication is optional after assets have been cached. Access/refresh tokens are not copied into the game directory.

## Networking
Phase 1 Training starts with networking disabled except optional read-only cosmetic/content fetches. The game must remain playable if the GGO API is unavailable.

## Open-source security rule
Because the client is open source, no online progression decision may depend on a client-supplied statement such as `training_completed=true`, `kills=10`, or `earned_xp=500`. Only the production game server can award online progression.

## Future native client
Training mission definitions, objectives and local profile format should use GGO-owned schemas rather than vanilla world advancement/scoreboard formats where practical, so the content can be reused by a future native client.
