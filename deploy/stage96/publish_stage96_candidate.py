#!/usr/bin/env python3
import argparse
import hashlib
import json
import os
import shutil
import tempfile
import zipfile
from pathlib import Path

CORE_NAME = "gungloryonline-core-runtime-v1-stage96-channel-sync.jar"
UI_NAME = "gungloryonline-ui-runtime-v1-stage96.jar"
RP_NAME = "GunGloryOnline-Official.zip"
CORE_SHA = "c3c580b456ad5bd17144188a557d6d50ce2d3c23eee5685f7fdf28b632c1f2a1"
UI_SHA = "783b0a6c572de0f98cd2e882eb3f98b2014e760062f50818110cea5300ee2852"
RP_SHA = "ec3c1e83d59195ba5a8fb2a90a0a41b7439f3b98f10970bfc9c359d0f7a22dae"
BUILD_ID = "runtime-stage96"


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def atomic_json(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp = tempfile.mkstemp(prefix=path.name + ".", dir=path.parent)
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
            f.write("\n")
        os.replace(tmp, path)
    except Exception:
        try:
            os.unlink(tmp)
        except FileNotFoundError:
            pass
        raise


def main() -> None:
    ap = argparse.ArgumentParser(description="Publish GGO Stage96 closed-beta client candidate")
    ap.add_argument("artifact", type=Path, help="Stage96 GitHub artifact ZIP")
    ap.add_argument("--web-root", type=Path, default=Path("/var/www/gungloryonline"))
    ap.add_argument("--base-manifest", default="beta-stage85-candidate.json")
    ap.add_argument("--output-manifest", default="beta-stage96-candidate.json")
    args = ap.parse_args()

    content = args.web_root / "content"
    files_dir = content / "files" / "v96"
    manifests = content / "manifests"
    base_manifest = manifests / args.base_manifest
    output_manifest = manifests / args.output_manifest

    if not args.artifact.is_file():
        raise SystemExit(f"artifact missing: {args.artifact}")
    if not base_manifest.is_file():
        raise SystemExit(f"base manifest missing: {base_manifest}")

    with tempfile.TemporaryDirectory(prefix="ggo-stage96-") as td:
        temp = Path(td)
        with zipfile.ZipFile(args.artifact) as zf:
            zf.extractall(temp)

        found = {}
        for p in temp.rglob("*"):
            if p.is_file() and p.name in {CORE_NAME, UI_NAME, RP_NAME}:
                found[p.name] = p

        required = {CORE_NAME: CORE_SHA, UI_NAME: UI_SHA, RP_NAME: RP_SHA}
        for name, expected in required.items():
            src = found.get(name)
            if src is None:
                raise SystemExit(f"artifact member missing: {name}")
            actual = sha256(src)
            if actual != expected:
                raise SystemExit(f"SHA-256 mismatch for {name}: {actual} != {expected}")

        files_dir.mkdir(parents=True, exist_ok=True)
        for name in required:
            shutil.copy2(found[name], files_dir / name)

    data = json.loads(base_manifest.read_text(encoding="utf-8"))
    data["gameVersion"] = "v96-candidate"

    replacements = {
        "core": (CORE_NAME, CORE_SHA),
        "ui": (UI_NAME, UI_SHA),
        "resourcepack": (RP_NAME, RP_SHA),
    }
    replaced = {"core": False, "ui": False, "resourcepack": False}

    for entry in data.get("files", []):
        path = str(entry.get("path", ""))
        kind = str(entry.get("kind", ""))
        key = None
        if path.startswith("mods/gungloryonline-core-runtime-v1-"):
            key = "core"
        elif path.startswith("mods/gungloryonline-ui-runtime-v1-"):
            key = "ui"
        elif kind == "resourcepack" and path.endswith("GunGloryOnline-Official.zip"):
            key = "resourcepack"
        if key is None:
            continue

        name, digest = replacements[key]
        prefix = "mods" if key in {"core", "ui"} else "resourcepacks"
        target = files_dir / name
        entry["path"] = f"{prefix}/{name}"
        entry["url"] = f"https://ggo.kvicloud.ru/content/files/v96/{name}"
        entry["sha256"] = digest
        entry["size"] = target.stat().st_size
        entry["version"] = "v96-candidate"
        replaced[key] = True

    missing = [k for k, ok in replaced.items() if not ok]
    if missing:
        raise SystemExit("base manifest did not contain replaceable entries: " + ", ".join(missing))

    atomic_json(output_manifest, data)

    # Final on-disk verification after publication.
    for name, expected in required.items():
        actual = sha256(files_dir / name)
        if actual != expected:
            raise SystemExit(f"published SHA-256 mismatch for {name}")

    print("Stage96 candidate published")
    print(f"manifest={output_manifest}")
    print(f"build_id={BUILD_ID}")
    print(f"core_sha256={CORE_SHA}")
    print(f"ui_sha256={UI_SHA}")
    print(f"resource_pack_sha256={RP_SHA}")
    print("server allowlist additions:")
    print(f"  GGO_ALLOWED_CLIENT_BUILDS=...,{BUILD_ID}")
    print(f"  GGO_ALLOWED_CORE_SHA256=...,{CORE_SHA}")
    print(f"  GGO_ALLOWED_UI_SHA256=...,{UI_SHA}")


if __name__ == "__main__":
    main()
