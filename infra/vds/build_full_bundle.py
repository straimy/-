#!/usr/bin/env python3
"""Build the transferable GunGloryOnline FULL-VDS archive without production secrets.

The result is intended to be copied to a fresh VDS and deployed with bootstrap_ubuntu.sh + deploy_all.sh.
It can pre-populate game content, launcher downloads, the server Core and an optional world archive.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import subprocess
import tempfile
import zipfile
from pathlib import Path

DENY_NAMES = {
    ".env",
    "id_rsa",
    "id_ed25519",
    "production.env",
    "tauri.key",
    "private.key",
}
DENY_SUFFIXES = {".pem", ".p12", ".pfx", ".key"}


def safe_extract(zip_path: Path, target: Path) -> None:
    with zipfile.ZipFile(zip_path) as zf:
        root = target.resolve()
        for info in zf.infolist():
            dest = (target / info.filename).resolve()
            if dest != root and root not in dest.parents:
                raise SystemExit(f"unsafe ZIP path: {info.filename}")
        zf.extractall(target)


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def copy_infra(source: Path, target: Path) -> None:
    ignore = shutil.ignore_patterns(
        ".env",
        "*.pyc",
        "__pycache__",
        "backups",
        ".publish-staging",
        "postgres_data",
        "redis_data",
    )
    shutil.copytree(source, target, ignore=ignore)


def ensure_secret_free(root: Path) -> None:
    bad: list[str] = []
    for path in root.rglob("*"):
        if not path.is_file():
            continue
        name = path.name.lower()
        if name in DENY_NAMES or path.suffix.lower() in DENY_SUFFIXES:
            bad.append(str(path.relative_to(root)))
    if bad:
        raise SystemExit("refusing to package possible secrets: " + ", ".join(sorted(bad)))


def copy_world(world_input: Path, destination: Path) -> None:
    if world_input.is_dir():
        if not (world_input / "level.dat").is_file():
            raise SystemExit("world directory has no level.dat")
        shutil.copytree(world_input, destination)
        return
    with tempfile.TemporaryDirectory(prefix="ggo-world-") as tmp:
        temp = Path(tmp)
        safe_extract(world_input, temp)
        candidates = sorted(temp.rglob("level.dat"), key=lambda p: len(p.parts))
        if not candidates:
            raise SystemExit("world ZIP has no level.dat")
        shutil.copytree(candidates[0].parent, destination)


def main() -> int:
    parser = argparse.ArgumentParser(description="Build GunGloryOnline FULL-VDS deployment ZIP")
    parser.add_argument("--ready-pack", type=Path, required=True)
    parser.add_argument("--resource-pack", type=Path, required=True)
    parser.add_argument("--server-core", type=Path, required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--windows-launcher", type=Path)
    parser.add_argument("--linux-launcher", type=Path)
    parser.add_argument("--world", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--base-url", default="https://updates.ggo.kvicloud.ru")
    args = parser.parse_args()

    here = Path(__file__).resolve().parent
    for required in (args.ready_pack, args.resource_pack, args.server_core):
        if not required.expanduser().is_file():
            raise SystemExit(f"missing input: {required}")

    with tempfile.TemporaryDirectory(prefix="ggo-full-vds-") as tmp:
        root = Path(tmp) / "GunGloryOnline-FULL-VDS"
        copy_infra(here, root)

        # Generate immutable client payload + beta manifest using the same production publisher.
        subprocess.run(
            [
                "python3",
                str(root / "publish_release.py"),
                str(args.ready_pack.expanduser().resolve()),
                "--public-dir",
                str(root / "public"),
                "--base-url",
                args.base_url.rstrip("/"),
                "--version",
                args.version,
                "--channel",
                "beta",
                "--resource-pack",
                str(args.resource_pack.expanduser().resolve()),
            ],
            check=True,
        )

        mods = root / "game-server" / "mods"
        mods.mkdir(parents=True, exist_ok=True)
        server_target = mods / args.server_core.name
        shutil.copy2(args.server_core, server_target)

        if args.world:
            world_input = args.world.expanduser().resolve()
            if not world_input.exists():
                raise SystemExit(f"world not found: {world_input}")
            destination = root / "game-server" / "world"
            shutil.rmtree(destination, ignore_errors=True)
            copy_world(world_input, destination)

        # First-download files are public and do not require an account.
        downloads: dict[str, object] = {"schemaVersion": 1, "version": args.version, "platforms": {}}
        bootstrap_dir = root / "public" / "launcher" / "bootstrap" / args.version
        bootstrap_dir.mkdir(parents=True, exist_ok=True)
        for key, binary in (("windows", args.windows_launcher), ("linux", args.linux_launcher)):
            if not binary:
                continue
            source = binary.expanduser().resolve()
            if not source.is_file():
                raise SystemExit(f"launcher not found: {source}")
            target = bootstrap_dir / source.name
            shutil.copy2(source, target)
            downloads["platforms"][key] = {
                "url": f"{args.base_url.rstrip('/')}/launcher/bootstrap/{args.version}/{target.name}",
                "sha256": sha256(target),
                "size": target.stat().st_size,
            }
        (root / "site" / "downloads.json").write_text(
            json.dumps(downloads, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )

        manifest = {
            "schemaVersion": 1,
            "gameVersion": args.version,
            "serverCore": {"file": server_target.name, "sha256": sha256(server_target)},
            "worldIncluded": bool(args.world),
            "productionSecretsIncluded": False,
        }
        (root / "BUNDLE.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
        ensure_secret_free(root)

        output = args.output.expanduser().resolve()
        output.parent.mkdir(parents=True, exist_ok=True)
        if output.exists():
            output.unlink()
        with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as zf:
            for path in sorted(root.rglob("*")):
                if path.is_file():
                    zf.write(path, path.relative_to(root.parent))

    print(f"bundle: {output}")
    print(f"sha256: {sha256(output)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
