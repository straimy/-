#!/usr/bin/env python3
import argparse
import hashlib
import json
import zipfile
from pathlib import Path

TRACKS = {
    "digital_horizon": "1f09224c33c8e3de5ff6838352cb7c7fa66585d1c3e3f1625e8f8860b0a473b9",
    "red_skyline": "854005622845355187588e8a87c87bc38f48bd83d2e93e4aada7761cdeec805a",
    "lost_signal": "a1dd994c93852f4166ce0b6da19dbe4d31dbceeadb6172dfa80ff13540d5b41c",
    "ggo_track_04": "eb7ef5655e653bc542862e8a38eb7d314ed109fb8820ff27a1cc150a6c64046d",
    "afterglow_protocol": "3bd0c7d836ebc436abb040f0c93b41effba5312727ee247f76a20e33ecc814a9",
    "distant_current": "54cba3c8382e7d956548535e84f88c685e65c7ef8d549f7a3d9eb9fec6a7aad7",
}
HASH_TO_TRACK = {digest: name for name, digest in TRACKS.items()}
DEFAULT_MUSIC_KEYS = [
    "music.game", "music.creative", "music.menu", "music.end", "music.dragon",
    "music.nether.basalt_deltas", "music.nether.crimson_forest", "music.nether.nether_wastes",
    "music.nether.soul_sand_valley", "music.nether.warped_forest", "music.overworld.deep_dark",
    "music.overworld.dripstone_caves", "music.overworld.frozen_peaks", "music.overworld.grove",
    "music.overworld.jagged_peaks", "music.overworld.jungle_and_forest", "music.overworld.lush_caves",
    "music.overworld.meadow", "music.overworld.old_growth_taiga", "music.overworld.snowy_slopes",
    "music.overworld.stony_peaks",
]


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def discover_from_file(path: Path, found: dict[str, bytes]) -> None:
    if not path.is_file():
        return
    if path.suffix.lower() == ".ogg":
        data = path.read_bytes()
        track = HASH_TO_TRACK.get(digest(data))
        if track:
            found.setdefault(track, data)
        return
    if path.suffix.lower() != ".zip":
        return
    try:
        with zipfile.ZipFile(path) as archive:
            for info in archive.infolist():
                if info.is_dir() or not info.filename.lower().endswith(".ogg"):
                    continue
                data = archive.read(info)
                track = HASH_TO_TRACK.get(digest(data))
                if track:
                    found.setdefault(track, data)
    except zipfile.BadZipFile:
        pass


def discover(source: Path) -> dict[str, bytes]:
    found: dict[str, bytes] = {}
    if source.is_file():
        discover_from_file(source, found)
    elif source.is_dir():
        for path in sorted(source.rglob("*")):
            discover_from_file(path, found)
    missing = [name for name in TRACKS if name not in found]
    if missing:
        raise SystemExit("Stage100 OST: missing verified tracks: " + ", ".join(missing))
    for name, expected in TRACKS.items():
        actual = digest(found[name])
        if actual != expected:
            raise SystemExit(f"Stage100 OST: hash mismatch for {name}: {actual}")
    return found


def load_json(data: bytes) -> dict:
    return json.loads(data.decode("utf-8-sig"))


def dump_json(data: dict) -> bytes:
    return (json.dumps(data, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def build(base: Path, source: Path, output: Path) -> None:
    tracks = discover(source)
    with zipfile.ZipFile(base) as archive:
        files = {info.filename: archive.read(info) for info in archive.infolist() if not info.is_dir()}

    sounds_path = "assets/minecraft/sounds.json"
    sounds = load_json(files[sounds_path]) if sounds_path in files else {}
    pool = [{"name": f"ggo/music/{name}", "stream": True} for name in TRACKS]
    music_keys = sorted(key for key in sounds if key.startswith("music.")) or DEFAULT_MUSIC_KEYS
    for key in music_keys:
        sounds[key] = {"replace": True, "sounds": pool}
    files[sounds_path] = dump_json(sounds)

    for name, data in tracks.items():
        files[f"assets/minecraft/sounds/ggo/music/{name}.ogg"] = data

    if "pack.mcmeta" in files:
        meta = load_json(files["pack.mcmeta"])
        if isinstance(meta.get("pack"), dict):
            meta["pack"]["description"] = "GunGloryOnline — Official OST + GGO Assets"
        files["pack.mcmeta"] = dump_json(meta)

    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name in sorted(files):
            info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, files[name], compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)

    with zipfile.ZipFile(output) as archive:
        for name, expected in TRACKS.items():
            entry = f"assets/minecraft/sounds/ggo/music/{name}.ogg"
            actual = digest(archive.read(entry))
            if actual != expected:
                raise SystemExit(f"Stage100 OST: output verification failed for {entry}: {actual}")
        patched = load_json(archive.read(sounds_path))
        for key in music_keys:
            if patched.get(key) != {"replace": True, "sounds": pool}:
                raise SystemExit(f"Stage100 OST: music event not replaced: {key}")

    print(f"Stage100 OST restored: {output}")
    print(f"sha256={digest(output.read_bytes())}")
    for name in TRACKS:
        print(f"track={name} sha256={TRACKS[name]}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", required=True, type=Path)
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    if not args.base.is_file():
        raise SystemExit(f"Stage100 OST: missing base pack: {args.base}")
    build(args.base, args.source, args.output)


if __name__ == "__main__":
    main()
