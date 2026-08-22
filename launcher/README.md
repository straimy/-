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

`.github/workflows/ggo-launcher-packages.yml` builds downloadable unsigned beta artifacts on the native target OS. Windows is built and verified on a real Windows runner; Linux is built on Ubuntu 22.04 for a conservative glibc baseline.

Windows outputs:

- NSIS installer: `GunGloryOnline-Launcher-Windows.exe`
- Windows Installer package: `GunGloryOnline-Launcher-Windows.msi`
- portable archive: `GunGloryOnline-Launcher-Windows-Portable.zip`

Linux outputs:

- universal desktop package: `GunGloryOnline-Launcher-Linux.AppImage`
- Ubuntu/Debian package: `GunGloryOnline-Launcher-Ubuntu-Debian.deb`
- Fedora/RHEL package: `GunGloryOnline-Launcher-Fedora-RHEL.rpm`

The static website bundle is built separately in the same package gate. Production releases still require signing and updater signing. The game-content manifest is intentionally fail-closed until final client artifacts are published.

## Cross-platform runtime

Runtime v1 does not launch Minecraft through shell scripts. The Rust backend selects `java.exe` on Windows and `java` elsewhere, builds classpaths with OS-aware path handling, selects native libraries for the active OS, and launches Java with `std::process::Command`. Forge installation uses the same cross-platform process path.

## Security

No Microsoft client secret, refresh token, Minecraft access token, signing private key, or Telegram secret belongs in Git. Runtime access tokens stay in the Rust backend and are not exposed to React.
