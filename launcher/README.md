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
- use native ZIP pickers for the supplied GunGloryOnline v29 package and Resource Pack v5;
- verify the pinned size and SHA256 of the GGO client files before installing them;
- install/verify Minecraft 1.20.1 + Forge 47.4.10 Runtime v1;
- authenticate through the Microsoft/Minecraft provider;
- launch with configured RAM, resolution/fullscreen and the GGO server target.

Without a production manifest/VDS, the local ZIP import path is used for GGO game files.

## Development

Requirements:

- Node.js 22+
- Rust stable
- platform prerequisites for Tauri 2

```bash
npm install
npm run tauri dev
```

## Test packages

`.github/workflows/ggo-launcher-packages.yml` builds downloadable unsigned test artifacts on the native target OS:

- Windows: NSIS `.exe`
- Linux: `.AppImage` built on Ubuntu 22.04 for a conservative glibc baseline

These are test builds. Production releases still need signing, the production artifact manifest/CDN, and updater signing.

## Security

No Microsoft client secret, refresh token, Minecraft access token, signing private key, or Telegram secret belongs in Git. Runtime access tokens stay in the Rust backend and are not exposed to React.
