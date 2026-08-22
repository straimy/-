# GunGloryOnline Audio Direction

## Core idea
GunGloryOnline should gradually replace the default Minecraft music with an original soundtrack that feels calm, spacious, technological and slightly melancholic without copying Minecraft/C418 melodies.

The default soundtrack is NOT split rigidly into menu music, hub music and open-world music. Most tracks belong to one shared GGO soundtrack pool and may play across the frontend, hub and normal exploration. Context only changes weighting and timing.

Dedicated music is reserved for special gameplay states such as BR countdown, final circle, boss/event sequences or seasonal events.

## GGO musical identity
Primary style: dark electronic ambient / minimal ambient.

Core ingredients:
- sparse piano or electric-piano notes;
- soft synth pads and long evolving chords;
- subtle digital textures;
- very quiet radio/static/mechanical ambience;
- occasional bell, pluck or glass-like tones;
- restrained bass;
- rare soft electronic percussion or breakbeat elements;
- large amounts of space and silence.

Desired feeling:
- a huge virtual world;
- solitude without hopelessness;
- technology and artificial reality;
- calm exploration;
- slight tension underneath;
- memorable but simple motifs.

Avoid making the normal soundtrack sound like constant shooter/action music. GunGloryOnline should still feel pleasant to inhabit for hours.

## First soundtrack: 8 tracks
The first useful release only needs around eight original tracks.

1. `Digital Horizon`
   - 3-4 min
   - soft pad + sparse piano
   - very calm
   - establishes the main GGO motif

2. `Empty Server Lights`
   - 3-5 min
   - electric piano, distant synth texture, subtle noise
   - lonely late-night virtual-world feeling

3. `Red Skyline`
   - 3-4 min
   - darker chord progression, soft bass, occasional bell/pluck
   - slightly more serious without becoming combat music

4. `Login at 03:17`
   - 2-4 min
   - minimal digital ambience, warm pad, tiny melodic fragments
   - deliberately feels like entering an old online world late at night

5. `Open Sector`
   - 4-6 min
   - wider ambient piece for exploration
   - little or no percussion
   - slow evolution rather than a conventional song structure

6. `Lost Signal`
   - 3-5 min
   - darker drone, radio/static texture, sparse melody
   - suitable for dangerous zones but still part of the general soundtrack pool

7. `After the Match`
   - 3-4 min
   - quiet reflective piano/synth track
   - can play anywhere, not only after a match

8. `Neon Rain`
   - 3-5 min
   - soft electronic pulse / restrained breakbeat
   - the most rhythmic track in the normal pool
   - still calm enough for frontend or exploration

These names are working titles and may change.

## Special-state music later
These tracks/layers are separate from the normal pool:
- BR match found / countdown;
- deployment;
- final circle;
- boss or raid phases;
- major seasonal event sequences.

Normal combat should not immediately trigger loud battle music every time a player fires a weapon. Later, adaptive layers can fade in only for sustained high-intensity encounters.

## Playback behaviour
Normal GGO music should feel semi-random and non-intrusive:
- choose from the shared soundtrack pool;
- do not immediately start another track after one ends;
- use random silence intervals, roughly 2-8 minutes initially;
- avoid repeating the same track twice in a row;
- slightly weight darker tracks in dangerous areas and calmer tracks in safe areas;
- frontend/menu can use the same pool rather than having a mandatory menu theme;
- allow rare long periods with ambience only.

This intentionally follows the strength of Minecraft's approach: the soundtrack belongs to the whole world rather than constantly announcing the current screen.

## First track to compose
Start with `Digital Horizon`.

Beginner target:
- tempo: 68-76 BPM;
- length: about 3 minutes;
- key: any minor key that feels comfortable, for example D minor or A minor;
- 3-4 simple chords;
- one soft pad;
- one piano/electric-piano instrument;
- one very quiet texture layer;
- no drums for the first version;
- use a short 3-5 note motif and leave large gaps between phrases.

The goal is not complexity. The goal is atmosphere and a recognizable GGO motif.

## Production approach
Start simple and grow with the project. The soundtrack should be human-composed and use instruments/samples that are either original or licensed for commercial use.

Recommended beginner toolchain:
- LMMS — free DAW for composing and arranging;
- Vital — free synthesizer for pads, drones and ambient textures;
- optional later: REAPER for more advanced mixing/editing.

## File formats
- Keep project masters/archive exports as WAV, 48 kHz, 24-bit.
- Ship game music as OGG Vorbis, not MP3.
- Target OGG quality around q5-q7 for music unless testing shows a smaller/larger setting is preferable.
- Preserve project files and stems so tracks can later be remixed into adaptive layers.

## Style rules
- Do not reuse or closely imitate copyrighted Minecraft/C418 melodies.
- Inspiration may come from pacing, restraint, silence and simplicity rather than copying melody or harmony.
- Avoid constant music. Silence and environmental ambience are part of the soundtrack.
- Prefer a small recognizable GGO motif that can occasionally reappear in different tracks.
- Keep normal-world music calmer than BR/event music.
- Volume categories should eventually be exposed as Music / Ambience / Combat / UI in GGO Settings.

## Implementation phases
Phase 1: shared 6-8 track GGO soundtrack pool.
Phase 2: random silence and contextual track weighting.
Phase 3: dedicated BR countdown/deployment/final-circle layers.
Phase 4: region motifs, seasonal music and adaptive combat transitions.
