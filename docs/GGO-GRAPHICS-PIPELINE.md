# GGO Graphics Pipeline

## Goal
GGO Graphics is a first-party player-facing graphics system for GunGloryOnline. Players select visual quality and gameplay-oriented presets, not Minecraft shader packs.

Third-party renderers or shader loaders may be used temporarily as implementation backends, but GGO must not copy, disguise, or repackage third-party shader code without a compatible license.

## Player-facing profiles

- **Low-end PC** — maximum FPS, no expensive post effects.
- **Competitive** — high visibility and stable frame rate for PvP/BR.
- **Balanced** — recommended default.
- **High** — improved shadows, reflections and atmosphere.
- **Cinematic** — maximum visuals for exploration/screenshots.
- **Custom** — individual controls.

The normal UI never needs to expose Oculus, Embeddium, GLSL pack names or Minecraft video-setting terminology. Those details belong in Diagnostics / Advanced.

## First-party visual stack target

Implement desired effects independently and incrementally:

1. GGO color grading / tone mapping
2. distance and atmospheric fog
3. bloom / emissive response
4. soft shadow presentation
5. water surface and reflections
6. volumetric light / god rays
7. ambient lighting / contact shading
8. FXAA/TAA-style edge smoothing where technically appropriate
9. post-processing intensity and accessibility controls
10. gameplay visibility safeguards for Competitive mode

The implementation must avoid copying shader source from third-party packs unless the exact license explicitly permits incorporation and all attribution/distribution requirements are followed.

## Architecture phases

### Phase A — abstraction
Create a `GgoGraphicsProfile` model shared by launcher and game client. It maps user choices to renderer settings while keeping backend names hidden from normal UI.

### Phase B — compatibility backend
Use a proven Forge 1.20.1 renderer/shader backend only for development validation. Profiles configure the backend, but it is not the product identity.

### Phase C — first-party post pipeline
Move generic effects that can be implemented safely into GGO-owned code/resources. Keep the output configurable from the same profile model.

### Phase D — reduce backend dependency
As first-party rendering coverage increases, remove backend-specific options from normal operation. A compatibility backend may remain available under Advanced for community content.

## Settings screen

Top-level categories:

- Game
- Audio
- Graphics
- Controls
- Interface
- Accessibility
- Network
- Advanced

Graphics contains:

- Quality preset
- World Detail Distance
- Shadow Quality
- Reflections
- Volumetric Lighting
- Water Quality
- Edge Smoothing
- Effects Density
- Post Processing
- Brightness / gamma
- Frame limit / VSync

Advanced may expose renderer backend, debug data and compatibility switches.

## Competitive integrity

Graphics presets must not create a competitive advantage by removing gameplay-critical obscurants or effects beyond server-approved limits. The server may publish allowed ranges for competitive playlists.

## Hardware detection

The launcher may recommend a profile using GPU/RAM/driver capabilities, but never silently lock the user to it. Recommended mapping is advisory and can be changed at any time.

## Distribution policy

Third-party packs used during development are not automatically redistributed through the GGO CDN. Each dependency requires a license review and a documented redistribution path before being included in a public manifest.
