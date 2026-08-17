#!/usr/bin/env python3
import argparse
import hashlib
import json
import shutil
import sys
import zipfile
from pathlib import Path


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def kind_for(path: str) -> str:
    if path.startswith("mods/"):
        return "mod"
    if path.startswith("resourcepacks/"):
        return "resourcepack"
    if path.startswith("config/"):
        return "config"
    return "client"


def main() -> int:
    parser = argparse.ArgumentParser(description="Publish a GunGloryOnline FULL-INSTALL ZIP to VDS static content")
    parser.add_argument("package", type=Path)
    parser.add_argument("--public-dir", type=Path, default=Path("public"))
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--channel", default="beta", choices=["beta", "stable"])
    args = parser.parse_args()

    package = args.package.resolve()
    public_dir = args.public_dir.resolve()
    base_url = args.base_url.rstrip("/")
    version = args.version

    if not package.is_file():
        print(f"package not found: {package}", file=sys.stderr)
        return 2

    files_root = public_dir / "files" / version
    manifest_dir = public_dir / "manifests"
    staging = public_dir / ".publish-staging" / version
    shutil.rmtree(staging, ignore_errors=True)
    staging.mkdir(parents=True, exist_ok=True)

    manifest_files = []
    with zipfile.ZipFile(package) as zf:
        candidates = [
            info for info in zf.infolist()
            if not info.is_dir() and info.filename.startswith("client/")
        ]
        if not candidates:
            raise SystemExit("FULL-INSTALL contains no client/ files")

        for info in candidates:
            relative = info.filename[len("client/"):]
            if not relative or relative.startswith("/") or ".." in Path(relative).parts:
                raise SystemExit(f"unsafe archive path: {info.filename}")
            target = staging / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            with zf.open(info) as src, target.open("wb") as dst:
                shutil.copyfileobj(src, dst)
            manifest_files.append({
                "path": relative.replace("\\", "/"),
                "url": f"{base_url}/files/{version}/{relative.replace('\\', '/')}",
                "sha256": sha256_file(target),
                "size": target.stat().st_size,
                "required": True,
                "side": "client",
                "version": version,
                "kind": kind_for(relative.replace("\\", "/")),
            })

    core = [f for f in manifest_files if f["path"].startswith("mods/gungloryonline-core-") and f["path"].endswith(".jar")]
    ui = [f for f in manifest_files if f["path"].startswith("mods/gungloryonline-ui-") and f["path"].endswith(".jar")]
    rp = [f for f in manifest_files if f["path"].startswith("resourcepacks/") and f["path"].endswith(".zip")]
    if len(core) != 1 or len(ui) != 1 or len(rp) < 1:
        raise SystemExit("package must contain one GGO core, one GGO UI and at least one resource pack")

    manifest = {
        "schemaVersion": 1,
        "gameVersion": version,
        "runtime": "minecraft-forge",
        "channel": args.channel,
        "files": sorted(manifest_files, key=lambda item: item["path"]),
    }

    files_root.parent.mkdir(parents=True, exist_ok=True)
    backup = files_root.with_name(files_root.name + ".old")
    shutil.rmtree(backup, ignore_errors=True)
    if files_root.exists():
        files_root.rename(backup)
    staging.rename(files_root)
    shutil.rmtree(backup, ignore_errors=True)

    manifest_dir.mkdir(parents=True, exist_ok=True)
    manifest_path = manifest_dir / f"{args.channel}.json"
    temp_manifest = manifest_path.with_suffix(".json.tmp")
    temp_manifest.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    temp_manifest.replace(manifest_path)

    print(f"published {version}: {len(manifest_files)} files")
    print(f"manifest: {manifest_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
