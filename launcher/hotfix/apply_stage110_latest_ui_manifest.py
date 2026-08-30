#!/usr/bin/env python3
from pathlib import Path
import json

PATH = Path("site/content/manifests/beta-stage110-candidate.json")
UI_NAME = "gungloryonline-ui-runtime-v1-stage110-button-mixin-hotfix.jar"
UI_SHA = "fe78239b84279151d10c4096e3f8598046d1843296800ba0824038e36a71b5bc"
UI_SIZE = 211701

if not PATH.is_file():
    raise SystemExit(f"missing {PATH}")

manifest = json.loads(PATH.read_text(encoding="utf-8"))
if manifest.get("gameVersion") != "v110-candidate":
    raise SystemExit("Stage110 manifest has unexpected gameVersion")

matches = [
    item for item in manifest.get("files", [])
    if item.get("path", "").startswith("mods/gungloryonline-ui-runtime-v1-stage110")
]
if len(matches) != 1:
    raise SystemExit(f"expected one Stage110 UI entry, found {len(matches)}")

matches[0].update({
    "path": "mods/" + UI_NAME,
    "url": "https://ggo.kvicloud.ru/content/files/v110/" + UI_NAME,
    "sha256": UI_SHA,
    "size": UI_SIZE,
    "required": True,
    "side": "client",
    "version": "v110-candidate",
    "kind": "mod",
})

PATH.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print("Stage110 latest UI manifest canonicalized")
