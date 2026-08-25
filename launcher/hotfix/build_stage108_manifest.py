#!/usr/bin/env python3
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT.parent / "site/content/manifests/beta-stage100-candidate.json"
DST = ROOT.parent / "site/content/manifests/beta-stage108-candidate.json"

# Stage108 keeps the proven protocol-3 Core bytes and official OST pack, but installs Core/UI
# under one matching stage number so launcher ticket binding resolves build_id=runtime-stage108.
CORE = {
    "path": "mods/gungloryonline-core-runtime-v1-stage108-unified-shell.jar",
    "url": "https://ggo.kvicloud.ru/content/files/v100/gungloryonline-core-runtime-v1-stage100-ost-branding.jar",
    "sha256": "c3c580b456ad5bd17144188a557d6d50ce2d3c23eee5685f7fdf28b632c1f2a1",
    "size": 462019,
    "required": True,
    "side": "client",
    "version": "v108-candidate",
    "kind": "mod",
}
UI = {
    "path": "mods/gungloryonline-ui-runtime-v1-stage108-unified-shell.jar",
    "url": "https://ggo.kvicloud.ru/content/files/v108/gungloryonline-ui-runtime-v1-stage108-unified-shell.jar",
    "sha256": "c1cf3d6d4f2145884d1562d7e6405dc741c092fd4eb9f134def79e0c798dc8aa",
    "size": 200714,
    "required": True,
    "side": "client",
    "version": "v108-candidate",
    "kind": "mod",
}
EXPECTED_RP_SHA = "b10b3228004b8d3068c93122c39e766df9778a1b277fef4a97d835c5ab0005ba"

m = json.loads(SRC.read_text(encoding="utf-8"))
m["gameVersion"] = "v108-candidate"
out = []
seen_core = False
seen_ui = False
for entry in m["files"]:
    path = entry.get("path", "")
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
    # Preserve the Stage100 official OST/resource pack byte-for-byte and all third-party runtime files.
    out.append(entry)

if not seen_core or not seen_ui:
    raise SystemExit("Stage100 manifest does not contain canonical GGO Core/UI entries")

m["files"] = out

core_entries = [e for e in out if e.get("path", "").startswith("mods/gungloryonline-core-runtime")]
ui_entries = [e for e in out if e.get("path", "").startswith("mods/gungloryonline-ui-runtime")]
rp_entries = [e for e in out if e.get("path") == "resourcepacks/GunGloryOnline-Official.zip"]
if core_entries != [CORE] or ui_entries != [UI]:
    raise SystemExit("Stage108 must contain exactly one canonical matching Core/UI pair")
if len(rp_entries) != 1 or rp_entries[0].get("sha256") != EXPECTED_RP_SHA:
    raise SystemExit("Stage108 must preserve the proven Stage100 official OST resource pack")
if "stage108" not in CORE["path"] or "stage108" not in UI["path"]:
    raise SystemExit("Stage108 Core/UI local paths must use the same stage number")

DST.parent.mkdir(parents=True, exist_ok=True)
DST.write_text(json.dumps(m, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

print("Stage108 candidate manifest built:", DST)
print("Core:", CORE["sha256"])
print("UI:", UI["sha256"])
print("Official OST RP preserved:", EXPECTED_RP_SHA)
