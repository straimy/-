#!/usr/bin/env python3
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT.parent / "site/content/manifests/beta-stage100-candidate.json"
DST = ROOT.parent / "site/content/manifests/beta-stage108-candidate.json"

# Stage108 is a pure client/runtime unification step. It MUST preserve the current
# launcher-only/auth-hardened Stage103 Core bytes and combine them with the refreshed
# Stage107 UI. Stage100 is used only as the dependency catalogue because the live
# Stage103 manifest was generated on the VDS and is intentionally not treated as a
# mutable repository source of truth.
CORE = {
    "path": "mods/gungloryonline-core-runtime-v1-stage108-unified.jar",
    "url": "https://ggo.kvicloud.ru/content/files/v108/gungloryonline-core-runtime-v1-stage108-unified.jar",
    "sha256": "fe2f4a277fec6609ee5144d5a7e29c13cdb533091c9878eba5f213f1e2aba342",
    "size": 470314,
    "required": True,
    "side": "client",
    "version": "v108-candidate",
    "kind": "mod",
}
UI = {
    "path": "mods/gungloryonline-ui-runtime-v1-stage108-unified.jar",
    "url": "https://ggo.kvicloud.ru/content/files/v108/gungloryonline-ui-runtime-v1-stage108-unified.jar",
    "sha256": "a02c2d6506dce09985012e0d92781f31ffb46d61c4782b8077b17c7bdf3a120b",
    "size": 212488,
    "required": True,
    "side": "client",
    "version": "v108-candidate",
    "kind": "mod",
}
EXPECTED_RP_SHA = "b10b3228004b8d3068c93122c39e766df9778a1b277fef4a97d835c5ab0005ba"
FORBIDDEN_OLD_CORE_SHA = "c3c580b456ad5bd17144188a557d6d50ce2d3c23eee5685f7fdf28b632c1f2a1"
FORBIDDEN_OLD_UI_SHA = "c1cf3d6d4f2145884d1562d7e6405dc741c092fd4eb9f134def79e0c798dc8aa"

if CORE["sha256"] == FORBIDDEN_OLD_CORE_SHA:
    raise SystemExit("Stage108 refuses the pre-Stage103 Core regression")
if UI["sha256"] == FORBIDDEN_OLD_UI_SHA:
    raise SystemExit("Stage108 refuses the stale Stage104 UI regression")
if not SRC.is_file():
    raise SystemExit(f"Stage108 dependency baseline missing: {SRC}")

m = json.loads(SRC.read_text(encoding="utf-8"))
files = m.get("files")
if not isinstance(files, list):
    raise SystemExit(f"Stage108 baseline 'files' must be a list, got {type(files).__name__}")
if not all(isinstance(entry, dict) for entry in files):
    bad = [type(entry).__name__ for entry in files if not isinstance(entry, dict)]
    raise SystemExit(f"Stage108 baseline contains non-object file entries: {bad}")

m["gameVersion"] = "v108-candidate"
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
    raise SystemExit("Stage108 dependency baseline does not contain canonical GGO Core/UI entries")

m["files"] = out
core_entries = [e for e in out if str(e.get("path", "")).startswith("mods/gungloryonline-core-runtime")]
ui_entries = [e for e in out if str(e.get("path", "")).startswith("mods/gungloryonline-ui-runtime")]
rp_entries = [e for e in out if e.get("path") == "resourcepacks/GunGloryOnline-Official.zip"]
if core_entries != [CORE] or ui_entries != [UI]:
    raise SystemExit("Stage108 must contain exactly one canonical matching Core/UI pair")
if len(rp_entries) != 1 or rp_entries[0].get("sha256") != EXPECTED_RP_SHA:
    raise SystemExit("Stage108 must preserve the proven official OST resource pack")
if "stage108" not in CORE["path"] or "stage108" not in UI["path"]:
    raise SystemExit("Stage108 Core/UI local paths must use the same stage number")
if CORE["version"] != m["gameVersion"] or UI["version"] != m["gameVersion"]:
    raise SystemExit("Stage108 Core/UI version metadata must match gameVersion")
for entry in (CORE, UI):
    if not entry["url"].startswith("https://ggo.kvicloud.ru/content/files/v108/"):
        raise SystemExit("Stage108 Core/UI must resolve from immutable v108 CDN paths")

# Stage108 must not accidentally reintroduce another GGO runtime path alongside
# the canonical pair. Non-GGO dependencies remain byte-for-byte identical to the
# proven baseline.
for entry in out:
    path = str(entry.get("path", ""))
    if "gungloryonline-" in path and entry not in (CORE, UI):
        raise SystemExit(f"Stage108 found unexpected legacy GGO runtime entry: {path}")

DST.parent.mkdir(parents=True, exist_ok=True)
DST.write_text(json.dumps(m, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

print("Stage108 candidate manifest built:", DST)
print("Core:", CORE["sha256"], CORE["size"])
print("UI:", UI["sha256"], UI["size"])
print("Official OST RP preserved:", EXPECTED_RP_SHA)
