# GGO Official Online Security Contract

## Objective
Official GunGloryOnline servers must not treat possession of a Minecraft client, the server address, a nickname, or a modified launcher as proof of identity.

Official online access is granted only after server-side validation of a GGO session.

## Required checks

A connecting client must complete both:

1. **GGO protocol handshake** — proves that the required GGO Core network protocol is present and compatible.
2. **Game Ticket validation** — proves that GGO Auth issued a current session for a canonical GGO account.

If either step fails, gameplay access is denied.

## Game Ticket properties

Tickets should be:

- cryptographically random;
- short-lived;
- single-use where practical;
- bound to environment/audience;
- optionally bound to target region/shard;
- never used as a long-term account credential;
- never printed to launcher or game logs.

The server consumes/validates a ticket through an authenticated server-to-auth-service request and receives canonical account data such as `ggo_player_id` and display name.

## Client flow

1. User authenticates to GGO Account through the website/device authorization flow.
2. Launcher obtains a short-lived launcher access token.
3. `ENTER GGO` requests a Game Ticket.
4. Launcher passes that ticket to the GGO client through a launch/session bridge.
5. GGO Core includes the ticket only in the custom login handshake.
6. Official server validates and consumes the ticket.
7. Server admits the canonical GGO identity.

## Third-party / vanilla clients

The following must not be enough to join official online gameplay:

- vanilla Minecraft;
- Forge started manually;
- Prism/MultiMC instance without GGO Core;
- copied modpack without a current GGO session;
- knowing `play.kvicloud.ru:24842`;
- choosing another player's nickname;
- replaying an expired/consumed ticket.

A third-party launcher may technically run the same open client files, but it still needs a legitimate current GGO account session and protocol-compatible client. Security is server-side, not secrecy-based DRM.

## Guest and Microsoft policy

- Guest/local identity: Training/offline only.
- Microsoft identity: optional linked identity only; it does not grant official GGO Online access.
- GGO Account: canonical online identity.

## Migration from legacy auth

Do not remove SAuth or `/login` / `/register` on the dedicated production server until Game Ticket validation has passed real smoke tests.

Migration order:

1. implement auth ticket issue endpoint;
2. implement dedicated-server consume endpoint and service authentication;
3. implement GGO Core client/server handshake;
4. test valid, invalid, expired and replayed tickets;
5. test direct vanilla/Forge/Prism rejection;
6. test reconnect/region routing;
7. enable ticket-required mode;
8. only then remove legacy chat-command authentication.

## Server authority

Even after successful authentication, all progression, currency, rank, inventory ownership, competitive results and rewards remain server-authoritative. The launcher/frontend displays these values but cannot award or modify them.
