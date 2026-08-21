# GGO Soundtrack Pool

## Core rule

Normal GunGloryOnline music is global, not tied to a biome, city, hub, map, sector, or region.

The player hears the same ambient soundtrack pool across the normal GGO experience. Context may influence probability later, but it must not create hard location-specific themes.

## Current prototype pool

- `ggo_ambient_01` — prototype ambient track
- `ggo_ambient_02` — prototype ambient track
- `ggo_ambient_03` — prototype ambient track

The shipped game format is OGG Vorbis. WAV 48 kHz / 24-bit remains the master/archive format.

## Playback behavior

- shuffle from one global ambient pool;
- avoid immediate repeats;
- use long silence gaps between tracks;
- target approximately 2–8 minutes of silence, randomized;
- music remains intentionally quieter than weapon, movement, UI, and voice audio;
- default Music Volume target: about 65%;
- do not force a new track when entering another normal region;
- do not restart a track because the player crossed a sector boundary.

## Exceptions

Dedicated music may be used only for genuinely special game states, for example:

- Battle Royale countdown/deployment;
- final circle / endgame state;
- boss encounters or large raids;
- major seasonal or scripted events.

When the special state ends, return to the normal global ambient pool after an appropriate quiet gap rather than immediately forcing another ambient song.

## Mixing direction

Prototype tracks should be loudness-matched before release rather than forcing the composer to remix every project manually. Preserve the original WAV masters and create game OGG encodes from those masters.

The intended feeling is similar to a game-wide soundtrack: the music belongs to GunGloryOnline as a whole, not to individual locations.
