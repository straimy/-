#!/usr/bin/env python3
"""Build the official GGO RP with a dedicated recovery-bag model.

The recovery bag remains a Bundle only as an internal engine proxy. Player-facing art is selected
by CustomModelData 720049 and lives in the GGO namespace so vanilla Bundle visuals never leak.
"""
from __future__ import annotations

import argparse
import binascii
import hashlib
import json
import struct
import zlib
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo

MODEL_ID = 720049
BUNDLE_MODEL = "assets/minecraft/models/item/bundle.json"
GGO_MODEL = "assets/gunnerarena/models/item/recovery_bag.json"
GGO_TEXTURE = "assets/gunnerarena/textures/item/recovery_bag.png"


def png_chunk(kind: bytes, payload: bytes) -> bytes:
    body = kind + payload
    return struct.pack(">I", len(payload)) + body + struct.pack(">I", binascii.crc32(body) & 0xFFFFFFFF)


def recovery_bag_png() -> bytes:
    """Generate a tiny deterministic 16x16 tactical bag icon with transparent background."""
    width = height = 16
    transparent = (0, 0, 0, 0)
    outline = (33, 34, 36, 255)
    body = (71, 75, 68, 255)
    body_light = (91, 96, 85, 255)
    strap = (181, 133, 55, 255)
    metal = (185, 187, 182, 255)
    pixels = [[transparent for _ in range(width)] for _ in range(height)]

    # Handle / shoulder strap.
    for x in range(5, 11):
        pixels[2][x] = outline
    for x in range(4, 12):
        pixels[3][x] = strap if 5 <= x <= 10 else outline

    # Main field bag silhouette.
    for y in range(4, 14):
        for x in range(2, 14):
            border = x in (2, 13) or y in (4, 13)
            pixels[y][x] = outline if border else body
    for y in range(6, 10):
        for x in range(4, 12):
            pixels[y][x] = body_light

    # Front pouch and high-visibility recovery strap.
    for y in range(9, 13):
        for x in range(4, 12):
            border = x in (4, 11) or y in (9, 12)
            pixels[y][x] = outline if border else body
    for x in range(3, 13):
        pixels[7][x] = strap
    pixels[7][7] = metal
    pixels[7][8] = metal

    raw = bytearray()
    for row in pixels:
        raw.append(0)  # PNG filter type 0
        for rgba in row:
            raw.extend(rgba)
    signature = b"\x89PNG\r\n\x1a\n"
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    return signature + png_chunk(b"IHDR", ihdr) + png_chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + png_chunk(b"IEND", b"")


def bundle_model(existing: bytes | None) -> bytes:
    if existing:
        data = json.loads(existing.decode("utf-8"))
    else:
        data = {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": "minecraft:item/bundle"},
        }
    overrides = data.setdefault("overrides", [])
    if not isinstance(overrides, list):
        raise SystemExit("bundle model overrides must be a list")
    kept = []
    for entry in overrides:
        try:
            value = entry.get("predicate", {}).get("custom_model_data")
        except AttributeError:
            value = None
        if value != MODEL_ID:
            kept.append(entry)
    kept.append({
        "predicate": {"custom_model_data": MODEL_ID},
        "model": "gunnerarena:item/recovery_bag",
    })
    data["overrides"] = kept
    return (json.dumps(data, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def ggo_model() -> bytes:
    data = {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "gunnerarena:item/recovery_bag"},
    }
    return (json.dumps(data, indent=2) + "\n").encode("utf-8")


def build(source: Path, output: Path) -> None:
    if not source.is_file():
        raise SystemExit(f"resource pack not found: {source}")
    replacements: dict[str, bytes] = {}
    with ZipFile(source, "r") as zin:
        names = zin.namelist()
        if "pack.mcmeta" not in names:
            raise SystemExit("source resource pack has no pack.mcmeta")
        existing_bundle = zin.read(BUNDLE_MODEL) if BUNDLE_MODEL in names else None
        replacements[BUNDLE_MODEL] = bundle_model(existing_bundle)
        replacements[GGO_MODEL] = ggo_model()
        replacements[GGO_TEXTURE] = recovery_bag_png()

        output.parent.mkdir(parents=True, exist_ok=True)
        with ZipFile(output, "w", compression=ZIP_DEFLATED, compresslevel=9) as zout:
            written: set[str] = set()
            for name in names:
                if name.endswith("/") or name in replacements or name in written:
                    continue
                info = ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
                info.compress_type = ZIP_DEFLATED
                info.external_attr = 0o644 << 16
                zout.writestr(info, zin.read(name))
                written.add(name)
            for name, payload in sorted(replacements.items()):
                info = ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
                info.compress_type = ZIP_DEFLATED
                info.external_attr = 0o644 << 16
                zout.writestr(info, payload)

    with ZipFile(output, "r") as verify:
        names = verify.namelist()
        if len(names) != len(set(names)):
            raise SystemExit("output resource pack contains duplicate file names")
        for required in ("pack.mcmeta", BUNDLE_MODEL, GGO_MODEL, GGO_TEXTURE):
            if required not in names:
                raise SystemExit(f"missing output RP entry: {required}")
        model = json.loads(verify.read(BUNDLE_MODEL))
        matches = [
            item for item in model.get("overrides", [])
            if item.get("predicate", {}).get("custom_model_data") == MODEL_ID
        ]
        if matches != [{"predicate": {"custom_model_data": MODEL_ID}, "model": "gunnerarena:item/recovery_bag"}]:
            raise SystemExit(f"recovery bag override mismatch: {matches}")
        texture = verify.read(GGO_TEXTURE)
        if not texture.startswith(b"\x89PNG\r\n\x1a\n"):
            raise SystemExit("recovery bag texture is not a PNG")

    digest = hashlib.sha256(output.read_bytes()).hexdigest()
    print("Built GGO Stage 63 recovery-bag resource pack")
    print(f" source={source}")
    print(f" output={output}")
    print(f" custom_model_data={MODEL_ID}")
    print(f" sha256={digest}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", nargs="?", type=Path, default=Path("GunnerArena-ResourcePack-1.20.1-v1.zip"))
    parser.add_argument("output", nargs="?", type=Path, default=Path("GunGloryOnline-Official-Stage63.zip"))
    args = parser.parse_args()
    build(args.source, args.output)


if __name__ == "__main__":
    main()
