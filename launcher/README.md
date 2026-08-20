# GunGloryOnline Launcher

Tauri 2 launcher for GunGloryOnline.

## Architecture

- `src/`: React/TypeScript presentation layer.
- `src-tauri/src/core/`: game-agnostic launcher services and models.
- `src-tauri/src/runtime/`: runtime adapters. Minecraft/Forge lives here; a future native client can be added beside it.
- `manifests/`: versioned remote-manifest examples.

The current Minecraft stack is treated as `GunGlory Runtime v1`, not as the permanent identity of the game.

## Current test flow

The launcher can currently:

- use a native folder picker for the game directory;
- use native ZIP pickers for a local GunGloryOnline fallback package;
- verify pinned size and SHA256 values before installing game files;
- install/verify Minecraft 1.20.1 + Forge 47.4.10 Runtime v1;
- authenticate through the Microsoft/Minecraft provider;
- launch with configured RAM, resolution/fullscreen and the GGO server target;
- load the production server/news catalogs from the public GGO site when production content is available.

Production Runtime v1 server target: `2.26.100.125:24842`.

## Development

Requirements:

- Node.js 22+
- Rust stable
- platform prerequisites for Tauri 2

```bash
npm install
npm run tauri dev
```

## Launcher packages

`.github/workflows/ggo-launcher-packages.yml` builds downloadable unsigned beta artifacts on the native target OS:

- Windows: NSIS `.exe`
- Linux: `.AppImage` built on Ubuntu 22.04 for a conservative glibc baseline
- Website: static `site/` bundle with download page, guide and launcher content catalogs

The CI output names are normalized to:

- `GunGloryOnline-Launcher-Windows.exe`
- `GunGloryOnline-Launcher-Linux.AppImage`

Production releases still need signing and updater signing. The game-content manifest is intentionally fail-closed until final client artifacts are published.

## Security

No Microsoft client secret, refresh token, Minecraft access token, signing private key, or Telegram secret belongs in Git. Runtime access tokens stay in the Rust backend and are not exposed to React.
