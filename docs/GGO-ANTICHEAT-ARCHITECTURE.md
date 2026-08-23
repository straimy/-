# GunGloryOnline Anti-Cheat Architecture

## Decision

GunGloryOnline should not rely on a custom Java build, launcher secrecy, hidden download paths, DLL blocking, obfuscation, or closed client source as the primary anti-cheat boundary.

The official client is an untrusted environment. Anything shipped to a player's machine can eventually be inspected, copied, modified or emulated. The durable security boundary is the official GGO backend and game server.

A launcher-managed Java 17 runtime is still useful for compatibility, support and reducing casual tampering, but it is not proof that the client is clean.

## Layered model

### 1. Server-authoritative gameplay — highest priority

The server owns and validates competitive state. It must reject impossible or suspicious client claims rather than merely report them.

Required areas:
- movement speed, acceleration, teleport distance and impossible position deltas;
- fire rate, reload timing, magazine/ammo transitions and weapon-state sequencing;
- damage, hit validation and line-of-sight/range constraints where technically feasible;
- inventory ownership, item creation/destruction and equip transitions;
- interaction/reach limits;
- cooldowns and ability timing;
- currency, XP, rank, loot, rewards and progression;
- match/BR/extraction results.

Do not make a ban solely from one noisy movement sample. Use confidence, repeated evidence and server context.

### 2. GGO session + protocol trust

Keep the existing one-shot GGO Game Ticket and custom protocol handshake. Official Online requires both. A copied modpack or known server address must not be enough to join.

Later, bind sessions to a runtime/client build identifier supplied by the launcher, but treat it as an integrity signal rather than identity proof.

### 3. Launcher-managed integrity

The launcher should manage a known Java 17 runtime and official GGO files through signed/versioned manifests.

Recommended:
- download a pinned supported JRE distribution into GGO Data rather than use arbitrary system Java by default;
- verify JRE package hash/signature before use;
- verify required Core/UI/resource files before Online;
- use atomic downloads and replacement;
- remove stale managed GGO jars only after the complete replacement set is available;
- do not print auth tokens, game tickets, signing material or sensitive local paths into normal player-facing UI;
- keep detailed file/runtime paths inside Diagnostics where support actually needs them.

The user should still be able to inspect Diagnostics. Hiding all paths is cosmetic, not a security boundary.

### 4. Client integrity telemetry — secondary signal

The GGO client may send a concise integrity report during Online entry, for example:
- GGO Core/UI build IDs;
- expected managed-file hashes or manifest version;
- Java vendor/version/runtime build ID;
- loaded required GGO module list;
- presence of unsupported injection/agent configuration that the process can reliably observe.

The server should compare this with an allowed build policy. Failure can deny competitive Online or mark the session for investigation.

Never send broad process lists, unrelated files, browser data or invasive machine inventories merely for anti-cheat. Collect only what is necessary and document it in the privacy policy.

### 5. Native anti-tamper — optional later layer

A Windows native component can make common DLL injection, debugging and memory tampering more expensive, but it must be treated as an additional signal, not the core security model.

If implemented later:
- signed GGO native module only;
- no kernel driver for the first releases;
- no arbitrary process killing;
- no scanning unrelated user files;
- fail clearly when integrity cannot be established;
- maintain Linux compatibility separately because Windows DLL concepts do not map directly to Linux.

A custom Java wrapper/runtime can participate in this layer, but forking and maintaining an entire JDK primarily for anti-cheat is not recommended for the beta. It creates a large update/security-maintenance burden while remaining bypassable by a sufficiently motivated attacker.

### 6. Detection + enforcement service

Introduce a server-side anti-abuse event stream before sophisticated automatic bans.

Suggested event shape:
- GGO player ID;
- session/match ID;
- detector code;
- server timestamp;
- severity/confidence;
- compact evidence values;
- GGO client/server build IDs.

Enforcement stages:
1. observe/log;
2. alert staff;
3. shadow/restrict competitive queue where justified;
4. temporary action for high-confidence repeated evidence;
5. permanent action only under defined policy/evidence.

Staff-facing evidence should not expose secret detector thresholds to ordinary users.

## Source-code policy

GGO does not gain meaningful protection simply by hiding every client source file. Any shipped Java bytecode can be decompiled.

Recommended split:

Public or publishable:
- launcher/client UI code where desired;
- protocol formats;
- manifest format;
- general anti-cheat architecture;
- shared game models that do not reveal sensitive enforcement thresholds.

Keep private where useful:
- exact server-side detector thresholds;
- anti-abuse scoring weights;
- exploit signatures under active use;
- private enforcement/evidence tooling;
- production signing keys, auth/server keys and infrastructure secrets.

Obfuscation may be applied to release client jars to increase reverse-engineering cost, but do not design security around it.

## Launcher console policy

Seeing where normal game files are downloaded is not a vulnerability. URLs and local file paths cannot be kept secret from a machine controlled by the player.

For release UX:
- normal Home should show high-level states such as Downloading, Verifying, Updating and Ready;
- detailed URLs, hashes, Java command lines and local paths belong in Diagnostics;
- tickets/tokens/passwords must never be logged anywhere;
- provide an explicit diagnostic export that redacts credentials.

## Beta implementation order

1. Finish the current Stage77/78 runtime + launcher beta chain and first real smoke test.
2. Add a launcher-managed pinned Java 17 runtime with hash/signature verification and system-Java fallback only in Advanced/Diagnostics.
3. Add a server-issued/allowed client build policy tied to GGO Online sessions.
4. Add GGO Core integrity handshake containing only GGO-managed build identifiers/hashes.
5. Implement first server-authoritative detectors for movement, weapon timing and inventory/progression mutations in report-only mode.
6. Add staff anti-cheat evidence view to the site admin panel with role-gated access.
7. Add sanctions/audit log with immutable actor/reason/timestamp records.
8. After telemetry is proven, enforce incompatible/tampered official Online builds.
9. Evaluate optional signed native Windows anti-tamper only after the server-side system is mature.
10. Do not fork the full JDK for beta unless a concrete capability cannot be achieved with a managed standard JRE + small signed native helper.

## Non-goals for first beta

- kernel anti-cheat driver;
- pretending copied client files can be made impossible to obtain;
- trusting the launcher because it is official;
- banning solely because a player has developer tools or unrelated software open;
- hiding Minecraft/Forge/Java legal/technical attribution where it is required; keep implementation details in Diagnostics instead of falsifying them.
