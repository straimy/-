# GunGloryOnline Audio Direction

## Goal
GunGloryOnline should gradually replace the default Minecraft music with an original soundtrack that feels calm, spacious and memorable without copying Minecraft/C418 melodies.

## Production approach
Start simple and grow with the project. The soundtrack should be human-composed and use instruments/samples that are either original or licensed for commercial use.

Recommended beginner toolchain:
- LMMS — free DAW for composing and arranging.
- Vital — free synthesizer for pads, drones and ambient textures.
- Optional later: REAPER for more advanced mixing/editing.

## File formats
- Keep project masters/archive exports as WAV, 48 kHz, 24-bit.
- Ship game music as OGG Vorbis, not MP3.
- Target OGG quality around q5-q7 for music unless testing shows a smaller/larger setting is preferable.
- Keep seamless loops with clean loop points when the track is meant to repeat.

## Initial soundtrack groups
1. Frontend / launcher ambience
   - sparse piano or bell-like notes
   - wide soft pads
   - subtle mechanical / atmospheric textures
   - dark but calm GGO identity

2. Main GGO world / hub
   - warm, exploratory ambient
   - slow development
   - enough silence to avoid fatigue

3. Open-world danger zones
   - darker drones
   - restrained pulse
   - sparse percussion only when needed

4. Battle Royale waiting / deployment
   - low-intensity tension while waiting
   - stronger pulse during countdown/deployment

5. Battle Royale active / final phase
   - dynamic combat layers
   - final-circle music should be much more urgent than general exploration

6. Training
   - minimal neutral ambience
   - should not feel like the main persistent online world

7. Seasonal events
   - separate motifs and instrumentation while preserving GGO identity

## Style rules
- Do not reuse or closely imitate copyrighted Minecraft melodies.
- Avoid constant music. Silence and ambience are part of the soundtrack.
- Prefer short recognizable GGO motifs that can be varied across menu, hub, events and combat.
- Combat music should be adaptive later rather than one track looping continuously.
- Volume categories should eventually be exposed as Music / Ambience / Combat / UI in GGO Settings.

## Implementation phases
Phase 1: one original frontend track + one hub ambience track.
Phase 2: Training and BR waiting/deployment tracks.
Phase 3: adaptive combat/final-circle music.
Phase 4: biome/region themes, seasonal music and dynamic transitions.
