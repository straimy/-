#!/usr/bin/env python3
import argparse
import hashlib
import json
import os
import shutil
from pathlib import Path
from urllib.parse import urlparse, unquote


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open('rb') as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b''):
            h.update(chunk)
    return h.hexdigest()


def main() -> None:
    ap = argparse.ArgumentParser(description='Repair legacy v40 CDN files required by the Stage96 manifest')
    ap.add_argument('--manifest', type=Path, default=Path('/var/www/gungloryonline/content/manifests/beta-stage96-candidate.json'))
    ap.add_argument('--server-mods', type=Path, default=Path('/root/mods'))
    ap.add_argument('--web-root', type=Path, default=Path('/var/www/gungloryonline'))
    args = ap.parse_args()

    if not args.manifest.is_file():
        raise SystemExit(f'manifest missing: {args.manifest}')
    if not args.server_mods.is_dir():
        raise SystemExit(f'server mods dir missing: {args.server_mods}')

    data = json.loads(args.manifest.read_text(encoding='utf-8'))
    v40_root = args.web_root / 'content' / 'files' / 'v40'
    v40_root.mkdir(parents=True, exist_ok=True)

    required = []
    for entry in data.get('files', []):
        url = str(entry.get('url', ''))
        if '/content/files/v40/' not in url:
            continue
        filename = unquote(Path(urlparse(url).path).name)
        required.append((filename, str(entry.get('sha256', '')).lower(), int(entry.get('size', 0) or 0)))

    if not required:
        raise SystemExit('manifest has no v40 dependencies')

    published = []
    already_ok = []
    missing = []
    mismatch = []

    for filename, expected_sha, expected_size in required:
        dst = v40_root / filename
        if dst.is_file():
            actual = sha256(dst)
            if actual == expected_sha and (not expected_size or dst.stat().st_size == expected_size):
                already_ok.append(filename)
                continue
            mismatch.append((filename, 'existing CDN file does not match manifest'))
            continue

        candidates = [p for p in args.server_mods.rglob(filename) if p.is_file()]
        if not candidates:
            missing.append(filename)
            continue

        matched = None
        for src in candidates:
            if expected_size and src.stat().st_size != expected_size:
                continue
            if sha256(src) == expected_sha:
                matched = src
                break

        if matched is None:
            mismatch.append((filename, 'server copy exists but SHA/size differs'))
            continue

        shutil.copy2(matched, dst)
        os.chmod(dst, 0o644)
        if sha256(dst) != expected_sha:
            raise SystemExit(f'post-copy hash mismatch: {filename}')
        published.append(filename)

    print(f'v40 required={len(required)} already_ok={len(already_ok)} published={len(published)}')
    for name in published:
        print(f'PUBLISHED {name}')
    for name in already_ok:
        print(f'OK {name}')
    for name in missing:
        print(f'MISSING {name}')
    for name, reason in mismatch:
        print(f'MISMATCH {name}: {reason}')

    unresolved = len(missing) + len(mismatch)
    print(f'unresolved={unresolved}')
    if unresolved:
        raise SystemExit(2)


if __name__ == '__main__':
    main()
