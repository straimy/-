#!/usr/bin/env python3
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
src = ROOT / "site/content/manifests/beta-stage97-candidate.json"
dst = ROOT / "site/content/manifests/beta-stage98-candidate.json"
manifest = json.loads(src.read_text(encoding="utf-8"))
manifest["gameVersion"] = "v98-candidate"

CORE_SHA = "c3c580b456ad5bd17144188a557d6d50ce2d3c23eee5685f7fdf28b632c1f2a1"
UI_SHA = "26820615d73d27e38490d7dfd8412afbe7da77cd63701aaa028618aba01eb499"
RP_SHA = "ec3c1e83d59195ba5a8fb2a90a0a41b7439f3b98f10970bfc9c359d0f7a22dae"

for item in manifest["files"]:
    path = item["path"]
    if path.startswith("mods/gungloryonline-core-"):
        item.update({
            "path": "mods/gungloryonline-core-runtime-v1-stage98-startup-brand.jar",
            "url": "https://ggo.kvicloud.ru/content/files/v98/gungloryonline-core-runtime-v1-stage98-startup-brand.jar",
            "sha256": CORE_SHA,
            "size": 462019,
            "version": "v98-candidate",
        })
    elif path.startswith("mods/gungloryonline-ui-"):
        item.update({
            "path": "mods/gungloryonline-ui-runtime-v1-stage98-startup-brand.jar",
            "url": "https://ggo.kvicloud.ru/content/files/v98/gungloryonline-ui-runtime-v1-stage98-startup-brand.jar",
            "sha256": UI_SHA,
            "size": 205432,
            "version": "v98-candidate",
        })
    elif path == "resourcepacks/GunGloryOnline-Official.zip":
        item.update({
            "url": "https://ggo.kvicloud.ru/content/files/v98/GunGloryOnline-Official-stage98.zip",
            "sha256": RP_SHA,
            "size": 12738980,
            "version": "v98-candidate",
        })

files = manifest["files"]
assert sum(1 for item in files if item["sha256"] == CORE_SHA and "stage98" in item["path"]) == 1
assert sum(1 for item in files if item["sha256"] == UI_SHA and "stage98" in item["path"]) == 1
assert sum(1 for item in files if item["sha256"] == RP_SHA and item["path"].startswith("resourcepacks/")) == 1
assert all("/v97/" not in item["url"] for item in files)
assert all(item["version"] != "v97-candidate" for item in files)

dst.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(dst)
