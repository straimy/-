#!/usr/bin/env python3
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
src = ROOT / "site/content/manifests/beta-stage98-candidate.json"
dst = ROOT / "site/content/manifests/beta-stage100-candidate.json"
manifest = json.loads(src.read_text(encoding="utf-8"))
manifest["gameVersion"] = "v100-candidate"

CORE_SHA = "c3c580b456ad5bd17144188a557d6d50ce2d3c23eee5685f7fdf28b632c1f2a1"
UI_SHA = "75259d89f12e0f36add0bd82f02674aaad3313e7f807ae38ef2791026ca9e4fc"
RP_SHA = "b10b3228004b8d3068c93122c39e766df9778a1b277fef4a97d835c5ab0005ba"
CORE_SIZE = 462019
UI_SIZE = 209288
RP_SIZE = 29149214
BASE = "https://ggo.kvicloud.ru/content/files/v100"
VERSION = "v100-candidate"

changed = {"core": 0, "ui": 0, "rp": 0}
for item in manifest["files"]:
    path = item["path"]
    if path.startswith("mods/gungloryonline-core-"):
        item.update({
            "path": "mods/gungloryonline-core-runtime-v1-stage100-ost-branding.jar",
            "url": f"{BASE}/gungloryonline-core-runtime-v1-stage100-ost-branding.jar",
            "sha256": CORE_SHA,
            "size": CORE_SIZE,
            "version": VERSION,
            "required": True,
            "side": "client",
            "kind": "mod",
        })
        changed["core"] += 1
    elif path.startswith("mods/gungloryonline-ui-"):
        item.update({
            "path": "mods/gungloryonline-ui-runtime-v1-stage100-ost-branding.jar",
            "url": f"{BASE}/gungloryonline-ui-runtime-v1-stage100-ost-branding.jar",
            "sha256": UI_SHA,
            "size": UI_SIZE,
            "version": VERSION,
            "required": True,
            "side": "client",
            "kind": "mod",
        })
        changed["ui"] += 1
    elif path == "resourcepacks/GunGloryOnline-Official.zip":
        item.update({
            "url": f"{BASE}/GunGloryOnline-Official-stage100.zip",
            "sha256": RP_SHA,
            "size": RP_SIZE,
            "version": VERSION,
            "required": True,
            "side": "client",
            "kind": "resourcepack",
        })
        changed["rp"] += 1

assert changed == {"core": 1, "ui": 1, "rp": 1}, changed
files = manifest["files"]
assert sum(1 for item in files if item.get("sha256") == CORE_SHA and "stage100" in item.get("path", "")) == 1
assert sum(1 for item in files if item.get("sha256") == UI_SHA and "stage100" in item.get("path", "")) == 1
assert sum(1 for item in files if item.get("sha256") == RP_SHA and item.get("path") == "resourcepacks/GunGloryOnline-Official.zip") == 1
assert all("/v98/" not in item.get("url", "") for item in files if item.get("path", "").startswith("mods/gungloryonline-"))
assert all(item.get("version") != "v98-candidate" for item in files if item.get("path", "").startswith("mods/gungloryonline-"))
assert all(item.get("required") is True for item in files)

# This helper deliberately does not activate or publish the manifest. The v100
# files must first exist at BASE with the exact hashes above.
dst.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(dst)
