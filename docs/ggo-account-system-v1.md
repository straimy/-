# GunGloryOnline Account System v1

Goal: make the GGO identity independent from Minecraft so the same account survives the future move to a native client.

## Identity model

`ggo_player_id` is the canonical player identity. Minecraft/Microsoft is never the primary database key.

## Player-facing login policy

Official GGO Online uses exactly one primary authentication method:

1. **GGO Account** — required for official online play. Registration, password entry, recovery and account approval happen on the GGO website. The launcher uses browser/device authorization and does not need the user's password.

Microsoft is not shown as an alternative login provider for official GGO Online. It may exist only as an optional linked identity for migration, ownership checks or an official skin source.

Guest/Quick Play is not an online authentication method. A local guest profile may be used only by Training/offline features and never receives authoritative online progression.

## Browser/device login

1. Launcher calls `POST /api/v1/auth/device/start` with a random PKCE verifier/challenge and launcher installation id.
2. Backend returns `device_id`, `verification_uri`, expiration and polling interval.
3. Launcher opens the GGO website in the system browser.
4. User signs in/registers/recover their account on the website and approves this launcher device.
5. Launcher polls `POST /api/v1/auth/device/token` until approved.
6. Backend returns a short-lived access token and rotating refresh token.
7. Long-lived secrets must be stored in the OS keychain, never localStorage and never logs.

No GGO password is typed into the production launcher UI.

## Game Ticket flow

Official online access uses a second short-lived credential separate from the launcher session.

1. Authenticated launcher requests `POST /api/v1/auth/game-ticket`.
2. Auth service creates a cryptographically random, short-lived and preferably one-time ticket bound to the canonical `ggo_player_id` and intended GGO environment/shard.
3. Launcher passes the ticket to the GGO client through the protected launch/session bridge. The ticket must never be written to normal logs.
4. GGO Core performs a custom client handshake when connecting to an official server.
5. Dedicated server validates/consumes the ticket against GGO Auth.
6. Validation returns canonical GGO player id, display name and allowed session metadata.
7. Only after validation does the server admit the player to the official world.

Tickets should have a very short TTL, be single-use where practical and be audience-bound to official GGO services.

## Blocking vanilla/third-party clients

Knowing `play.kvicloud.ru:24842` must not be sufficient to enter official GGO Online.

Official servers require both:

- the expected GGO Core network protocol/handshake version;
- a valid GGO Game Ticket.

A vanilla Minecraft client, Prism instance without the required GGO client module, modified client that skips the handshake, expired ticket, reused ticket or invalid account is disconnected before gameplay access.

This is an application access control layer, not DRM. Client binaries can still be inspected; security relies on server-side ticket validation and authoritative server state, not on hiding secrets inside the launcher.

## Suggested backend endpoints

- `POST /api/v1/auth/register` (website)
- `POST /api/v1/auth/login` (website)
- `POST /api/v1/auth/device/start`
- `POST /api/v1/auth/device/token`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `POST /api/v1/auth/game-ticket`
- `POST /api/v1/auth/game-ticket/consume` (internal/server authenticated)
- `GET /api/v1/me`
- `GET /api/v1/me/identities`
- `POST /api/v1/me/identities/microsoft/link` (optional/advanced)
- `DELETE /api/v1/me/identities/microsoft`
- `GET /api/v1/me/skin`
- `PUT /api/v1/me/skin/source`
- `POST /api/v1/me/skin`
- `DELETE /api/v1/me/skin`

## Skin model

Each GGO profile stores a skin preference:

- `ggo` — use the skin uploaded to GGO.
- `microsoft` — optionally use a linked official skin when available.
- `default` — GGO default character skin.

Microsoft linkage must never grant official GGO Online access by itself.

## Training/offline profile

Training may run without an authenticated GGO session after required assets are installed.

A local profile:

- has no cloud identity authority;
- cannot join official GGO Online;
- cannot earn or mutate authoritative progression, currency, ranking, inventory ownership or competitive statistics;
- may store only local Training preferences/statistics.

## Migration path away from Minecraft

Progression, social data, cosmetics, ranks, inventory and profiles are keyed by `ggo_player_id`. Minecraft UUIDs remain transport/runtime details only while the current client is based on Minecraft.
