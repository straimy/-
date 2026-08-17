# GunGloryOnline Account System v1

Goal: make the GGO identity independent from Minecraft so the same account survives the future move to a native client.

## Identity model

`ggo_player_id` is the canonical player identity. Minecraft/Microsoft is an optional linked identity, not the primary database key.

Login methods shown by the launcher:

1. **GGO Account** — recommended. Registration and confirmation happen on `https://ggo.kvicloud.ru`; the launcher uses a browser/device authorization flow and never asks for the GGO password directly.
2. **Microsoft** — official Microsoft/Minecraft OAuth. A Microsoft identity can be linked to an existing GGO account.
3. **Guest / Quick Play** — nickname-only local/temporary GGO identity. It must never impersonate a Microsoft/Mojang account. Guest progression may be restricted and can later be upgraded into a GGO account.

## Browser/device login

Proposed flow:

1. Launcher calls `POST /api/v1/auth/device/start` with a random PKCE verifier/challenge and launcher installation id.
2. Backend returns `device_id`, a short user code, `verification_uri`, expiration and polling interval.
3. Launcher opens `https://ggo.kvicloud.ru/activate?...` in the system browser.
4. User signs in/registers on the website and approves the launcher.
5. Launcher polls `POST /api/v1/auth/device/token` until approved.
6. Backend returns a short-lived access token and rotating refresh token.
7. Long-lived secrets must later be stored in the OS keychain, never localStorage and never logs.

No GGO password is typed into the launcher.

## Suggested backend endpoints

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/device/start`
- `POST /api/v1/auth/device/token`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `GET /api/v1/me`
- `GET /api/v1/me/identities`
- `POST /api/v1/me/identities/microsoft/link`
- `DELETE /api/v1/me/identities/microsoft`
- `GET /api/v1/me/skin`
- `PUT /api/v1/me/skin/source`
- `POST /api/v1/me/skin`
- `DELETE /api/v1/me/skin`

## Skin model

Each GGO profile stores a skin preference:

- `ggo` — use the skin uploaded to GGO.
- `microsoft` — use the linked official Minecraft skin when available.
- `default` — GGO default character skin.

GGO skins are stored on the VDS/object storage and served by immutable content hashes. The client UI/runtime mod resolves player skins using signed GGO profile metadata, so GGO skins work even when the player is not using a Mojang skin.

The launcher Account Hub controls the preference; the actual in-game rendering belongs to the GGO client mod.

## Guest mode

Guest mode is a first-party GGO guest session, not an authentication bypass. It gets its own generated `ggo_player_id`/guest id and display name. Servers decide what guest users may access. Suggested restrictions until upgrade:

- no cloud purchases;
- no account recovery;
- optional progression cap;
- no custom skin upload;
- visible `Guest` account badge.

## Migration path away from Minecraft

Because progression, social data, cosmetics and skins are keyed by `ggo_player_id`, future clients can authenticate against the same backend without Minecraft UUIDs. Microsoft becomes an optional linked provider rather than a core dependency.