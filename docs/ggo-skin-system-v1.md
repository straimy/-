# GGO Skin System v1

GGO skins are first-party GunGloryOnline profile assets. They must not depend on Mojang skin hosting.

## Storage

- User uploads a PNG through `ggo.kvicloud.ru`.
- API accepts only valid PNG images, initially `64x64` or legacy `64x32`, with a strict size limit.
- Server normalizes/validates the image and calculates SHA256 over the stored bytes.
- File is stored as `/data/skins/<sha256>.png` on the single GGO VDS.
- Database maps `ggo_player_id -> skin_hash`.
- Public immutable URL: `https://ggo.kvicloud.ru/skins/<sha256>.png`.
- Because the hash is part of the URL, clients may cache it indefinitely.

## Profile selection

`skin_source` values:

- `ggo`: use the GGO uploaded asset when present.
- `microsoft`: use the linked official Minecraft skin while Runtime v1 still supports it.
- `default`: use a bundled GGO default skin.

The selected source belongs to the GGO profile, not to the Minecraft installation.

## Client path

Runtime Migration client mod gets a small signed/public profile document by `ggo_player_id`, resolves the selected skin asset, downloads it asynchronously, validates the image/hash and registers it with Minecraft's texture manager. The player renderer then chooses the GGO texture instead of the Mojang texture when `skin_source=ggo`.

This layer is intentionally separate from Minecraft account authentication so it can be reused by a future native GGO client.
