# GGO Soundtrack Pool

## Core rule

Normal GunGloryOnline music is global, not tied to a biome, city, hub, map, sector, or region.

The same ambient pool is available in the GGO frontend, Training, Open World, and ordinary game modes. Entering another biome, dimension, region, or sector must not select a location-specific track or restart the current song.

The standalone launcher is silent by default. Launcher startup must not start the in-game ambient pool.

## Current official pool

- `digital_horizon` — Digital Horizon
- `red_skyline` — Red Skyline
- `lost_signal` — Lost Signal
- `ggo_track_04` — GGO Track 04; temporary title until an official name is chosen

All shipped tracks use streaming OGG Vorbis resources. The fourth source asset is `GGO_Track4_Normalized(1).ogg`, stereo 44.1 kHz, 246.853 seconds, SHA-256 `eb7ef5655e653bc542862e8a38eb7d314ed109fb8820ff27a1cc150a6c64046d`.

## Playback behavior

- shuffle from one global ambient pool;
- avoid immediate repeats;
- use long randomized silence gaps between tracks;
- target approximately 2–8 minutes of silence;
- keep music quieter than weapon, movement, UI, and voice audio;
- default in-game Music Volume target: about 65%;
- do not force a new track when entering another normal region;
- do not restart a track because the player crossed a sector boundary.

## Exceptions

Dedicated music may be used only for genuinely special game states, for example:

- Battle Royale countdown/deployment;
- final circle / endgame state;
- boss encounters or large raids;
- major seasonal or scripted events.

When the special state ends, return to the normal global ambient pool after an appropriate quiet gap rather than immediately forcing another ambient song.

## Packaging

`hotfix/apply_ggo_global_ost_stage14.py` validates every source checksum, writes all four files under `assets/minecraft/sounds/ggo/music`, and replaces normal Minecraft music events with the shared four-track pool. Preserve WAV masters separately; package OGG files for the game.
