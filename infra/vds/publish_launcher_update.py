#!/usr/bin/env python3
import argparse
import json
import shutil
from datetime import datetime, timezone
from pathlib import Path


def read_sig(path: Path) -> str:
    value = path.read_text(encoding="utf-8").strip()
    if not value:
        raise SystemExit(f"empty signature: {path}")
    return value


def copy_pair(binary: Path, signature: Path, dest: Path) -> tuple[Path, str]:
    if not binary.is_file():
        raise SystemExit(f"missing updater artifact: {binary}")
    if not signature.is_file():
        raise SystemExit(f"missing updater signature: {signature}")
    dest.mkdir(parents=True, exist_ok=True)
    target = dest / binary.name
    shutil.copy2(binary, target)
    return target, read_sig(signature)


def main() -> int:
    parser = argparse.ArgumentParser(description="Publish signed Tauri launcher updater artifacts")
    parser.add_argument("--version", required=True)
    parser.add_argument("--public-dir", type=Path, default=Path("public"))
    parser.add_argument("--site-dir", type=Path, default=Path("site"), help="site directory that receives public downloads.json")
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--windows", type=Path, required=True)
    parser.add_argument("--windows-sig", type=Path, required=True)
    parser.add_argument("--linux", type=Path, required=True)
    parser.add_argument("--linux-sig", type=Path, required=True)
    parser.add_argument("--notes", default="GunGloryOnline Launcher update")
    args = parser.parse_args()

    base = args.base_url.rstrip("/")
    release_dir = args.public_dir.resolve() / "launcher" / args.version
    latest_dir = args.public_dir.resolve() / "launcher"

    win_target, win_sig = copy_pair(args.windows.resolve(), args.windows_sig.resolve(), release_dir)
    linux_target, linux_sig = copy_pair(args.linux.resolve(), args.linux_sig.resolve(), release_dir)

    published_at = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
    windows_url = f"{base}/launcher/{args.version}/{win_target.name}"
    linux_url = f"{base}/launcher/{args.version}/{linux_target.name}"

    payload = {
        "version": args.version,
        "notes": args.notes,
        "pub_date": published_at,
        "platforms": {
            "windows-x86_64": {
                "signature": win_sig,
                "url": windows_url,
            },
            "linux-x86_64": {
                "signature": linux_sig,
                "url": linux_url,
            },
        },
    }

    latest_dir.mkdir(parents=True, exist_ok=True)
    temp = latest_dir / "latest.json.tmp"
    final = latest_dir / "latest.json"
    temp.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    temp.replace(final)

    # Public website downloads are intentionally independent from account login.
    site_dir = args.site_dir.resolve()
    site_dir.mkdir(parents=True, exist_ok=True)
    downloads = {
        "schemaVersion": 1,
        "version": args.version,
        "publishedAt": published_at,
        "windows": {"url": windows_url, "fileName": win_target.name},
        "linux": {"url": linux_url, "fileName": linux_target.name},
    }
    downloads_temp = site_dir / "downloads.json.tmp"
    downloads_final = site_dir / "downloads.json"
    downloads_temp.write_text(json.dumps(downloads, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    downloads_temp.replace(downloads_final)

    print(f"published launcher {args.version}: {final}")
    print(f"public downloads: {downloads_final}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
