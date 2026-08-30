# GunGloryOnline Security Incident Playbook

## Purpose

This playbook is for suspected compromise, hostile former contributors, leaked credentials, stolen builds, suspicious launcher/client tampering, or unexplained production changes.

The goal is containment and recovery. Do not rely on being able to contact, identify, locate, or confront a suspected person. Security must hold even when the attacker is anonymous, remote, or technically skilled.

## Immediate rule

Treat knowledge of GGO architecture, downloaded client files, decompiled jars, public endpoints, or old test builds as non-secret. Those facts alone must not grant production access.

Production trust is based on current credentials, server-side authorization, one-shot sessions/tickets, signed/verified release artifacts, and least-privilege roles.

## First 15 minutes

If compromise is suspected:

1. Freeze production deploys and role changes until the scope is known.
2. Record the current production commit/artifact hashes and service status before changing anything.
3. Revoke all active sessions for any account suspected of exposure.
4. Rotate every secret that may have been visible to the suspected party. Rotation is preferred over arguing about whether the secret was actually copied.
5. Remove the person's repository/team/staff access before continuing investigation.
6. Preserve logs and timestamps. Do not delete evidence while trying to clean up.
7. Verify that the public manifest/update channel still points to approved artifacts.
8. Verify that the dedicated game server and auth service are running the expected binaries/entrypoints.

## Secrets to rotate when exposure is plausible

Rotate only the secrets that could actually have been exposed, but when uncertain prefer rotation:

- GGO server/auth shared key;
- launcher or release signing credentials;
- deployment SSH keys/passwords;
- CI/CD deploy tokens;
- administrative API tokens;
- database credentials if introduced later;
- third-party service tokens used by GGO.

Game tickets and access sessions should be revoked rather than treated as long-lived secrets.

Never commit replacement secrets to GitHub. Keep them in the production secret store/environment only.

## Account containment

For a suspicious GGO account:

- revoke all access sessions;
- revoke all refresh tokens;
- revoke all unconsumed game tickets;
- invalidate approved device flows where possible;
- remove staff/admin role unless the protected owner identity is involved;
- review recent support/admin actions and role changes;
- require a fresh login after credentials are changed.

The existing `/api/v1/auth/logout-all` endpoint is the emergency session revocation primitive. It is not a substitute for rotating a password or infrastructure secret that was actually exposed.

## Repository and contributor containment

A former contributor should have no durable production capability merely because they once helped with code or assets.

On offboarding or suspicion:

- remove repository/team permissions;
- remove staff/admin roles;
- remove deploy permissions;
- rotate any credential they could have seen;
- verify protected branches/release workflows still enforce the expected gates;
- search recent commits/releases for unexpected binaries, callbacks, self-updaters or secret material;
- rebuild security-sensitive artifacts from reviewed source rather than trusting a binary supplied by the person.

Do not make geography, real-world reachability, friendship, popularity, or personal relationships part of the technical trust model.

## Production integrity checks

Verify at minimum:

- expected GGO Auth secure entrypoint is active;
- owner public registration remains blocked;
- staff/admin permissions are server-authoritative;
- auth brute-force/rate-limit protections are active;
- production manifest contains only approved generation pairs;
- Core/UI/resource-pack hashes match the approved release receipt;
- client/server Core identities match when required;
- no unexpected GGO jars or duplicate protocol owners are present;
- no secret values are present in public artifacts/logs;
- update channel has not been redirected to an unapproved host/artifact;
- game server still requires the GGO auth/ticket boundary before gameplay.

If any identity check fails, stop rollout and restore the last known-good release rather than trying to patch the running installation in place.

## Anti-cheat compromise model

Assume an attacker can read/decompile every file shipped to the player.

Therefore:

- exact server-side thresholds and scoring logic should remain server-side where practical;
- client integrity reports are signals, not proof of honesty;
- gameplay validation remains server-authoritative;
- one detector cannot issue a permanent ban by itself during beta;
- anti-cheat modules from outside contributors start in report-only mode;
- evidence must be bounded, reviewable and attributable to a detector/build version;
- tampered or unsupported builds can eventually be denied Online once the integrity protocol is mature.

## Recovery order

1. Contain accounts and credentials.
2. Establish the last known-good source commits and artifacts.
3. Rebuild/redeploy auth first if identity/session security is in doubt.
4. Verify website/account/admin access controls.
5. Verify launcher/update manifest integrity.
6. Verify dedicated server runtime and GGO auth handshake.
7. Restore Online only after the whole chain is coherent.
8. Keep anti-cheat enforcement conservative until post-incident telemetry is trusted again.

## After the incident

Record:

- what was exposed or suspected;
- how access was obtained;
- which credentials were rotated;
- which sessions were revoked;
- which commits/artifacts were verified or replaced;
- timeline of production changes;
- evidence retained;
- the control that would have prevented or shortened the incident.

Convert the root cause into an automated CI/runtime gate where possible.

## Current beta stance

For the first public beta, the strongest practical protections are:

- very narrow production-admin access;
- no contributor-held production secrets;
- one-shot game tickets and pre-auth quarantine;
- hardened auth service with owner protection and rate limits;
- verified launcher/runtime artifacts and manifest hashes;
- server-authoritative anti-cheat in report-only mode while calibrated;
- repository secret scanning;
- reproducible reviewed releases with rollback receipts.

A copied client or knowledgeable former contributor should therefore be an operational risk to monitor, not a single point of failure for the whole platform.
