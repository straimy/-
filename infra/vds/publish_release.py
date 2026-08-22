#!/usr/bin/env python3
import argparse
import hashlib
import io
import json
import shutil
import sys
import zipfile
from pathlib import Path

OFFICIAL_RESOURCE_PACK_NAME = "GunGloryOnline-Official.zip"


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
    if path.startswith("shaderpacks/"):
        return "asset"
    return "client"


def open_client_archive(package: Path):
    raw = package.read_bytes()
    outer = zipfile.ZipFile(io.BytesIO(raw))
    if any(not i.is_dir() and i.filename.startswith("client/") for i in outer.infolist()):
        return outer

    nested = [i for i in outer.infolist() if not i.is_dir() and i.filename.lower().endswith(".zip")]
    if len(nested) == 1:
        inner_bytes = outer.read(nested[0])
        outer.close()
        inner = zipfile.ZipFile(io.BytesIO(inner_bytes))
        if any(not i.is_dir() and i.filename.startswith("client/") for i in inner.infolist()):
            return inner
        inner.close()

    outer.close()
    raise SystemExit("package contains no client/ tree (directly or in one nested ZIP)")


def manifest_entry(relative: str, target: Path, url: str, version: str) -> dict:
    normalized = relative.replace("\\", "/")
    return {
        "path": normalized,
        "url": url,
        "sha256": sha256_file(target),
        "size": target.stat().st_size,
        "required": True,
        "side": "client",
        "version": version,
        "kind": kind_for(normalized),
    }


def load_previous_manifest(path: Path) -> dict | None:
    if not path.is_file():
        return None
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
        return data if isinstance(data, dict) else None
    except (OSError, json.JSONDecodeError):
        return None


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Publish a GunGloryOnline client build to VDS static content"
    )
    parser.add_argument("package", type=Path, help="READY-PACK/FULL-INSTALL ZIP")
    parser.add_argument("--resource-pack", type=Path, help="new mandatory RP ZIP; omit to inherit the current channel RP")
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
    if args.resource_pack and not args.resource_pack.is_file():
        print(f"resource pack not found: {args.resource_pack}", file=sys.stderr)
        return 2

    files_root = public_dir / "files" / version
    manifest_dir = public_dir / "manifests"
    manifest_path = manifest_dir / f"{args.channel}.json"
    previous = load_previous_manifest(manifest_path)
    staging = public_dir / ".publish-staging" / version
    shutil.rmtree(staging, ignore_errors=True)
    staging.mkdir(parents=True, exist_ok=True)

    manifest_files: list[dict] = []
    archive = open_client_archive(package)
    try:
        candidates = [
            info for info in archive.infolist()
            if not info.is_dir() and info.filename.startswith("client/")
        ]
        for info in candidates:
            relative = info.filename[len("client/"):]
            if not relative or relative.startswith("/") or ".." in Path(relative).parts:
                raise SystemExit(f"unsafe archive path: {info.filename}")
            normalized = relative.replace("\\", "/")
            target = staging / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            with archive.open(info) as src, target.open("wb") as dst:
                shutil.copyfileobj(src, dst)
            manifest_files.append(
                manifest_entry(
                    normalized,
                    target,
                    f"{base_url}/files/{version}/{normalized}",
                    version,
                )
            )
    finally:
        archive.close()

    # Resource packs are independent from the code build. Publish new packs under a
    # stable launcher-managed name so activation does not depend on the source ZIP name.
    manifest_files = [f for f in manifest_files if f["kind"] != "resourcepack"]
    if args.resource_pack:
        rp = args.resource_pack.resolve()
        relative = f"resourcepacks/{OFFICIAL_RESOURCE_PACK_NAME}"
        target = staging / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(rp, target)
        manifest_files.append(
            manifest_entry(relative, target, f"{base_url}/files/{version}/{relative}", version)
        )
    elif previous:
        inherited = [
            f for f in previous.get("files", [])
            if isinstance(f, dict) and f.get("kind") == "resourcepack" and f.get("required") is True
        ]
        manifest_files.extend(inherited)

    core = [f for f in manifest_files if f["path"].startswith("mods/gungloryonline-core-") and f["path"].endswith(".jar")]
    ui = [f for f in manifest_files if f["path"].startswith("mods/gungloryonline-ui-") and f["path"].endswith(".jar")]
    rp = [f for f in manifest_files if f.get("kind") == "resourcepack"]
    if len(core) != 1 or len(ui) != 1:
        raise SystemExit("package must contain exactly one client GGO core and one GGO UI jar")
    if not rp:
        raise SystemExit("no mandatory resource pack: pass --resource-pack on the first publication")

    # Reject duplicate managed paths before touching the live channel.
    paths = [f["path"] for f in manifest_files]
    if len(paths) != len(set(paths)):
        raise SystemExit("duplicate client paths in generated manifest")

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
    temp_manifest = manifest_path.with_suffix(".json.tmp")
    temp_manifest.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    temp_manifest.replace(manifest_path)

    print(f"published {version}: {len(manifest_files)} managed client files")
    print(f"resource pack: {'new' if args.resource_pack else 'inherited'}")
    print(f"manifest: {manifest_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
