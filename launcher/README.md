# GunGloryOnline Launcher

Tauri 2 launcher for GunGloryOnline.

## Architecture

- `src/`: React/TypeScript presentation layer only.
- `src-tauri/src/core/`: game-agnostic launcher services and models.
- `src-tauri/src/runtime/`: runtime adapters. Minecraft/Forge lives here; a future native client can be added beside it.
- `manifests/`: versioned remote-manifest examples.

The current Minecraft stack is treated as `GunGlory Runtime v1`, not as the permanent identity of the game.

## Security

No Microsoft secrets, refresh tokens, signing private keys, or Telegram secrets belong in Git.
Secrets must be provided by environment variables, OS credential storage, or GitHub Secrets.

## Development

Requirements:
- Node.js 22+
- Rust stable
- platform prerequisites for Tauri 2

```bash
npm install
npm run tauri dev
```

Authentication and installation are intentionally not implemented in this bootstrap commit. The Minecraft identity provider will be implemented only against current official Microsoft/Minecraft documentation.
