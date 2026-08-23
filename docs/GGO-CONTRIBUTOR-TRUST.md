# GunGloryOnline Contributor Trust and Access Policy

## Purpose

GunGloryOnline must not give production trust simply because a contributor can write code, produce assets, or claims to have security experience. Contributions are evaluated by reproducible results, provenance, communication quality, and least-privilege access.

This policy applies to programmers, anti-cheat contributors, testers, support staff, artists, composers, contractors, and temporary collaborators.

## Core rule: contribution is not administration

A contributor does not need production administrator access to contribute useful work.

Default external contributor access:
- no root/VDS access;
- no production SSH credentials;
- no production auth database;
- no GGO server/auth signing keys;
- no launcher release signing material;
- no raw user session/game tickets;
- no direct production deploy permissions;
- no ability to promote their own account or other accounts to admin;
- no ability to bypass CI or release verification.

Give only the smallest access needed for the current task. Expand access only after sustained, verifiable work and only when the new permission is actually required.

## Recommended roles

### Tester
Can receive beta builds, submit bugs and reproducible test reports. No source or infrastructure access is required by default.

### Contributor / Developer
Can work on a scoped branch, patch, module or asset package. Changes must pass review and CI before entering a release candidate. This role is not a site or infrastructure administrator.

### Anti-Cheat Developer
Can work on detector modules, test harnesses and sanitized evidence fixtures. Exact production secrets, player private data and production infrastructure remain inaccessible. New detectors begin in report-only mode unless explicitly promoted after telemetry review.

### Technical Support
Can answer, open and close support tickets through the staff console. Support access does not imply code, server or infrastructure access.

### Administrator
Reserved for trusted operators who actually need account/role-management or production operational privileges. Administrator is not a reward for contributing code or assets.

## Code contribution requirements

Security-sensitive code must be reviewable and reproducible.

Required before acceptance:
- complete source for the contributed component;
- clear build instructions or a reproducible CI path;
- dependency list and license compatibility;
- no hidden binary-only helper unless there is a documented reason and source/provenance is independently verified;
- no embedded credentials, tokens, private endpoints or signing secrets;
- no network callbacks unrelated to documented GGO services;
- no self-update path that bypasses the GGO launcher/update channel;
- no anti-cheat action that can silently ban users without the documented evidence/enforcement path.

For native security components, require source review, deterministic or independently reproducible build evidence where practical, signed release artifacts, and a narrowly documented privilege surface.

## Asset and music provenance

Official GGO assets must have clear provenance.

For externally supplied music, models, textures, UI, sound or other creative work, keep enough project/source material to establish authorship and permit maintenance where reasonably expected for the role. Exact requirements vary by medium, but an unexplained opaque export is not sufficient for a critical official asset when editability or provenance is required.

Before shipping a third-party contribution:
- confirm the contributor has the right to license it to GGO;
- record the license/permission;
- preserve editable source/project files when they are part of the agreed deliverable;
- record third-party samples/assets/plugins that materially affect redistribution rights;
- do not accept generated or copied material whose rights/provenance cannot be established.

## Anti-cheat contribution boundary

External anti-cheat code is treated as untrusted until reviewed.

Integration order:
1. isolated branch/module;
2. source review;
3. CI/build verification;
4. test environment;
5. report-only production telemetry if approved;
6. false-positive review;
7. only then consider enforcement.

No contributor-provided detector gets automatic ban authority merely because it appears to work in a local test.

## Production release boundary

Production deployment is performed through the GGO release/deployment process, not from a contributor workstation.

A release candidate should have:
- pinned source commit(s);
- CI receipts;
- artifact hashes;
- dependency/runtime identity;
- database backup/migration plan where relevant;
- rollback path;
- no production secrets inside public or CI artifacts.

Where practical, the person contributing a sensitive change should not be the only person/process able to approve and deploy it.

## Communication and trust

Technical disagreement is normal. Refusal to provide reasonable verification, repeated evasiveness around provenance/build steps, pressure for broader access, or hostility to review are reasons to reduce trust, not to weaken review requirements.

Do not argue indefinitely. Keep the technical boundary simple: if a contribution cannot be verified to the standard required for its risk level, it does not enter the production chain.

## Current beta stance

For the first public beta:
- keep production owner/admin scope narrow;
- keep anti-cheat report-only while detectors are calibrated;
- keep external contributors away from production credentials and user data;
- require source + CI/reproducibility for security-sensitive contributions;
- use the existing Support role for support work only;
- create additional scoped roles later only when there is a real operational need.
