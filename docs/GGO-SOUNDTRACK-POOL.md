# GGO Soundtrack Pool

## Core rule

Normal GunGloryOnline music is global, not tied to a biome, city, hub, map, sector, or region.

The same ambient pool is available in the GGO frontend, Training, Open World, and ordinary game modes. Entering another biome, dimension, region, or sector must not select a location-specific track or restart the current song.

The standalone launcher is silent by default. Launcher startup must not start the in-game ambient pool.

## Current official pool

- `digital_horizon` — Digital Horizon
- `red_skyline` — Red Skyline
- `lost_signal` — Lost Signal
- `ggo_track_04` — GGO Track 04; temporary runtime title until an official name is chosen
- `afterglow_protocol` — Afterglow Protocol
- `distant_current` — Distant Current

All six masters are loudness-matched to **-18.0 LUFS integrated** before game packaging. The game ships streaming OGG Vorbis resources; WAV masters are preserved separately.

The fourth packaged source asset remains `GGO_Track4_Normalized(1).ogg`, stereo 44.1 kHz, 246.853 seconds, SHA-256 `eb7ef5655e653bc542862e8a38eb7d314ed109fb8820ff27a1cc150a6c64046d`.

The fifth normalized packaged source asset is `ggosounds5.ogg`, stereo 48 kHz, 232.939 seconds, SHA-256 `3bd0c7d836ebc436abb040f0c93b41effba5312727ee247f76a20e33ecc814a9`. It is packaged in-game as `afterglow_protocol.ogg`. Its original WAV measured approximately -12.7 LUFS / +0.2 dBTP; the normalized master uses a -5.3 dB gain and measures -18.0 LUFS / -5.1 dBTP, preserving the original dynamics rather than applying unnecessary extra compression.

The sixth normalized source asset is `GunGloryOnline_-_Distant_Current.ogg`, stereo 48 kHz, 60.000 seconds, SHA-256 `54cba3c8382e7d956548535e84f88c685e65c7ef8d549f7a3d9eb9fec6a7aad7`. It is packaged in-game as `distant_current.ogg`. The supplied WAV measured approximately -18.4 LUFS / -6.2 dBTP; the normalized master uses a simple +0.4 dB gain and measures -18.0 LUFS / about -5.8 dBTP, preserving the original dynamics without compression.

Binary normalized soundtrack assets are intentionally not stored in Git history; the packer validates the external normalized files by checksum before packaging.

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

`hotfix/apply_ggo_global_ost_stage14.py` validates every source checksum, writes all six files under `assets/minecraft/sounds/ggo/music`, and replaces normal Minecraft music events with the shared six-track pool. Preserve WAV masters separately; package OGG files for the game.
