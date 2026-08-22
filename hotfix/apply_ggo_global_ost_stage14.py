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
    "ggo_track_05": {
        "file": "ggosounds5.ogg",
        "sha256": "3bd0c7d836ebc436abb040f0c93b41effba5312727ee247f76a20e33ecc814a9",
    },
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
            {"name": "ggo/music/ggo_track_05", "stream": True},
        ]

        # Keep vanilla timing/context machinery, but make every normal music event
        # choose from the same GGO pool. This gives Minecraft-like random music and
        # pauses without binding individual songs to biomes, dimensions or sectors.
        music_keys = [key for key in sounds if key.startswith("music.")]
        if not music_keys:
            music_keys = [
                "music.game",
                "music.creative",
                "music.menu",
                "music.end",
                "music.dragon",
                "music.nether.basalt_deltas",
                "music.nether.crimson_forest",
                "music.nether.nether_wastes",
                "music.nether.soul_sand_valley",
                "music.nether.warped_forest",
                "music.overworld.deep_dark",
                "music.overworld.dripstone_caves",
                "music.overworld.frozen_peaks",
                "music.overworld.grove",
                "music.overworld.jagged_peaks",
                "music.overworld.jungle_and_forest",
                "music.overworld.lush_caves",
                "music.overworld.meadow",
                "music.overworld.old_growth_taiga",
                "music.overworld.snowy_slopes",
                "music.overworld.stony_peaks",
            ]

        for key in music_keys:
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

    print("GGO OST Stage 14/55 complete")
    print(" - Digital Horizon / Red Skyline / Lost Signal / GGO Track 04 / GGO Track 05")
    print(" - one global music pool for every normal Minecraft music event")
    print(" - streaming OGG resources")
    print(" - original Minecraft music references replaced in the GGO resource pack")
    print(f" - output: {args.output}")


if __name__ == "__main__":
    main()
