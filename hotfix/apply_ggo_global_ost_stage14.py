#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import tempfile
import zipfile
from pathlib import Path

TRACKS = {
    "digital_horizon": {
        "file": "Digital_Horizon_GGO.ogg",
        "sha256": "1f09224c33c8e3de5ff6838352cb7c7fa66585d1c3e3f1625e8f8860b0a473b9",
    },
    "red_skyline": {
        "file": "Red_Skyline_GGO.ogg",
        "sha256": "854005622845355187588e8a87c87bc38f48bd83d2e93e4aada7761cdeec805a",
    },
    "lost_signal": {
        "file": "Lost_Signal_GGO.ogg",
        "sha256": "a1dd994c93852f4166ce0b6da19dbe4d31dbceeadb6172dfa80ff13540d5b41c",
    },
    "ggo_track_04": {
        "file": "GGO_Track4_Normalized(1).ogg",
        "sha256": "eb7ef5655e653bc542862e8a38eb7d314ed109fb8820ff27a1cc150a6c64046d",
    },
    "afterglow_protocol": {
        "file": "ggosounds5.ogg",
        "sha256": "3bd0c7d836ebc436abb040f0c93b41effba5312727ee247f76a20e33ecc814a9",
    },
    "distant_current": {
        "file": "GunGloryOnline_-_Distant_Current.ogg",
        "sha256": "54cba3c8382e7d956548535e84f88c685e65c7ef8d549f7a3d9eb9fec6a7aad7",
    },
}

# Java Edition 1.20.1 normal background-music events.  Do not rely on the
# source resource pack already declaring these: a missing override falls back
# to vanilla assets and lets a Minecraft track leak through when the player
# enters the corresponding biome/dimension.
VANILLA_MUSIC_EVENTS = {
    "music.creative",
    "music.credits",
    "music.dragon",
    "music.end",
    "music.game",
    "music.menu",
    "music.nether.basalt_deltas",
    "music.nether.crimson_forest",
    "music.nether.nether_wastes",
    "music.nether.soul_sand_valley",
    "music.nether.warped_forest",
    "music.overworld.badlands",
    "music.overworld.bamboo_jungle",
    "music.overworld.cherry_grove",
    "music.overworld.deep_dark",
    "music.overworld.desert",
    "music.overworld.dripstone_caves",
    "music.overworld.flower_forest",
    "music.overworld.forest",
    "music.overworld.frozen_peaks",
    "music.overworld.grove",
    "music.overworld.jagged_peaks",
    "music.overworld.jungle",
    "music.overworld.lush_caves",
    "music.overworld.meadow",
    "music.overworld.old_growth_taiga",
    "music.overworld.snowy_slopes",
    "music.overworld.sparse_jungle",
    "music.overworld.stony_peaks",
    "music.overworld.swamp",
    "music.under_water",
}


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser(description="Inject the official GGO OST into a Minecraft 1.20.1 resource pack.")
    parser.add_argument("--pack", required=True, type=Path)
    parser.add_argument("--audio-dir", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    if not args.pack.is_file():
        raise SystemExit(f"resource pack not found: {args.pack}")

    sources: dict[str, Path] = {}
    for track_id, meta in TRACKS.items():
        source = args.audio_dir / meta["file"]
        if not source.is_file():
            raise SystemExit(f"missing OST file: {source}")
        actual = sha256(source)
        if actual != meta["sha256"]:
            raise SystemExit(f"checksum mismatch for {source.name}: {actual}")
        sources[track_id] = source

    with tempfile.TemporaryDirectory(prefix="ggo-ost-") as tmp_name:
        root = Path(tmp_name)
        with zipfile.ZipFile(args.pack) as archive:
            archive.extractall(root)

        sounds_path = root / "assets/minecraft/sounds.json"
        sounds_path.parent.mkdir(parents=True, exist_ok=True)
        sounds = {}
        if sounds_path.exists():
            sounds = json.loads(sounds_path.read_text(encoding="utf-8-sig"))

        global_pool = [
            {"name": "ggo/music/digital_horizon", "stream": True},
            {"name": "ggo/music/red_skyline", "stream": True},
            {"name": "ggo/music/lost_signal", "stream": True},
            {"name": "ggo/music/ggo_track_04", "stream": True},
            {"name": "ggo/music/afterglow_protocol", "stream": True},
            {"name": "ggo/music/distant_current", "stream": True},
        ]

        # Replace BOTH events already declared by the pack and every normal
        # Java 1.20.1 background-music event.  Explicitly declaring the full
        # set is important: otherwise an undeclared biome event falls through
        # to Minecraft's built-in sounds.json and can play one vanilla track.
        music_keys = {key for key in sounds if key.startswith("music.")}
        music_keys.update(VANILLA_MUSIC_EVENTS)
        for key in sorted(music_keys):
            sounds[key] = {"replace": True, "sounds": global_pool}

        sounds_path.write_text(json.dumps(sounds, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

        target_dir = root / "assets/minecraft/sounds/ggo/music"
        target_dir.mkdir(parents=True, exist_ok=True)
        for track_id, source in sources.items():
            shutil.copy2(source, target_dir / f"{track_id}.ogg")

        pack_meta = root / "pack.mcmeta"
        if pack_meta.exists():
            data = json.loads(pack_meta.read_text(encoding="utf-8-sig"))
            if isinstance(data.get("pack"), dict):
                data["pack"]["description"] = "GunGloryOnline — Official OST + GGO Assets"
                pack_meta.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

        args.output.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(args.output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as out:
            for path in sorted(root.rglob("*")):
                if path.is_file():
                    out.write(path, path.relative_to(root).as_posix())

    print("GGO OST Stage 14/102 complete")
    print(" - Digital Horizon / Red Skyline / Lost Signal / GGO Track 04 / Afterglow Protocol / Distant Current")
    print(f" - {len(VANILLA_MUSIC_EVENTS)} explicit Java 1.20.1 music events replaced")
    print(" - existing resource-pack music.* events replaced too")
    print(" - no normal biome/dimension/menu music event may fall back to vanilla")
    print(" - streaming OGG resources")
    print(f" - output: {args.output}")


if __name__ == "__main__":
    main()
