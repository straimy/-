#!/usr/bin/env python3
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT.parent / "site/content/manifests/beta-stage100-candidate.json"
DST = ROOT.parent / "site/content/manifests/beta-stage116-candidate.json"

CORE = {
    "path": "mods/gungloryonline-core-runtime-v1-stage116-unified.jar",
    "url": "https://ggo.kvicloud.ru/content/files/v116/gungloryonline-core-runtime-v1-stage116-unified.jar",
    "sha256": "fe2f4a277fec6609ee5144d5a7e29c13cdb533091c9878eba5f213f1e2aba342",
    "size": 470314,
    "required": True,
    "side": "client",
    "version": "v116-candidate",
    "kind": "mod",
}
UI = {
    "path": "mods/gungloryonline-ui-runtime-v1-stage116-unified.jar",
    "url": "https://ggo.kvicloud.ru/content/files/v116/gungloryonline-ui-runtime-v1-stage116-unified.jar",
    "sha256": "673481cc2e0864c3a26710ec8c724f56711dad703c3c241e968eb1d6d87de7df",
    "size": 216554,
    "required": True,
    "side": "client",
    "version": "v116-candidate",
    "kind": "mod",
}
EXPECTED_RP_SHA = "b10b3228004b8d3068c93122c39e766df9778a1b277fef4a97d835c5ab0005ba"

if not SRC.is_file():
    raise SystemExit(f"Stage116 dependency baseline missing: {SRC}")

m = json.loads(SRC.read_text(encoding="utf-8"))
files = m.get("files")
if not isinstance(files, list) or not all(isinstance(e, dict) for e in files):
    raise SystemExit("Stage116 baseline files must be a list of objects")

m["gameVersion"] = "v116-candidate"
out = []
seen_core = False
seen_ui = False
for entry in files:
    path = str(entry.get("path", ""))
    if path.startswith("mods/gungloryonline-core-runtime"):
        if not seen_core:
            out.append(CORE.copy())
            seen_core = True
        continue
    if path.startswith("mods/gungloryonline-ui-runtime"):
        if not seen_ui:
            out.append(UI.copy())
            seen_ui = True
        continue
    out.append(entry)

if not seen_core or not seen_ui:
    raise SystemExit("Stage116 baseline missing canonical GGO Core/UI entries")

m["files"] = out
core = [e for e in out if str(e.get("path", "")).startswith("mods/gungloryonline-core-runtime")]
ui = [e for e in out if str(e.get("path", "")).startswith("mods/gungloryonline-ui-runtime")]
rp = [e for e in out if e.get("path") == "resourcepacks/GunGloryOnline-Official.zip"]
if core != [CORE] or ui != [UI]:
    raise SystemExit("Stage116 must contain exactly one canonical Core/UI pair")
if len(rp) != 1 or rp[0].get("sha256") != EXPECTED_RP_SHA:
    raise SystemExit("Stage116 must preserve the proven GGO OST resource pack")
for entry in (CORE, UI):
    if not entry["url"].startswith("https://ggo.kvicloud.ru/content/files/v116/"):
        raise SystemExit("Stage116 Core/UI must use immutable v116 CDN paths")
    if entry["version"] != m["gameVersion"]:
        raise SystemExit("Stage116 Core/UI version must match gameVersion")

DST.parent.mkdir(parents=True, exist_ok=True)
DST.write_text(json.dumps(m, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print("Stage116 candidate manifest built:", DST)
print("Core:", CORE["sha256"], CORE["size"])
print("UI:", UI["sha256"], UI["size"])
print("Official OST RP:", EXPECTED_RP_SHA)
