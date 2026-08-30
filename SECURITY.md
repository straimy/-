# GunGloryOnline Security Policy

GunGloryOnline is intended to be developed in the open. Public source code must never be treated as a security boundary.

## Principles

- Client and launcher code may be inspected, modified and rebuilt by anyone.
- The server is authoritative for competitive state: currency, XP, rank, rewards, inventory ownership, matchmaking results and anti-abuse decisions.
- Never trust a value only because it came from the official launcher or client.
- Secrets must never be committed to the repository, bundled into the launcher, written to manifests, or sent to the game client unless strictly required for a short-lived session.
- Production signing keys, SSH keys, database passwords, JWT secrets and provider credentials belong in deployment/GitHub secret stores only.
- Public identifiers such as `ggo_player_id`, display name and skin hashes are not credentials.
- Access tokens should be short-lived; refresh tokens should be revocable and stored only where required.
- Downloaded executable/game content must be verified by hash; launcher self-updates must also be cryptographically signed.
- PostgreSQL and Redis must remain private to the VDS/container network.
- Offline Training must never be able to mint authoritative online currency, XP, rank or competitive rewards.

## Open-source boundaries

Expected to be public:

- launcher source;
- client UI/runtime source;
- update/manifest format;
- account protocol and public API contracts;
- most gameplay logic and shared models;
- deployment templates with placeholder/example values.

Never publish:

- production private signing keys;
- real `.env` files;
- database backups containing account data;
- live JWT/refresh tokens;
- SSH private keys or passwords;
- third-party secrets/client secrets;
- private anti-abuse evidence containing user-sensitive data.

Server-side anti-cheat/abuse heuristics may remain private where publishing exact thresholds would materially weaken enforcement, but security must not depend on obscurity alone.

## Vulnerability reports

Do not open a public issue containing an active exploit, credential, private user data or bypass that would put live users at risk. Use a private security-reporting channel when one is configured. Until then, contact the project maintainers privately and include reproduction steps with secrets removed.

## Incident response

If a secret is exposed, rotate/revoke it immediately; deleting it from a later Git commit is not sufficient. If an updater/signing key is compromised, revoke the affected release path and ship a new trust root through a controlled recovery process.
