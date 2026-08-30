#!/usr/bin/env python3
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT.parent / "site/content/manifests/beta-stage100-candidate.json"
DST = ROOT.parent / "site/content/manifests/beta-stage103-candidate.json"

CORE = {
    "path": "mods/gungloryonline-core-runtime-v1-stage103-server-finalization.jar",
    "url": "https://ggo.kvicloud.ru/content/files/v103/gungloryonline-core-runtime-v1-stage103-server-finalization.jar",
    "sha256": "fe2f4a277fec6609ee5144d5a7e29c13cdb533091c9878eba5f213f1e2aba342",
    "size": 470314,
    "required": True,
    "side": "client",
    "version": "v103-candidate",
    "kind": "mod",
}
UI = {
    "path": "mods/gungloryonline-ui-runtime-v1-stage103-ggo-hub.jar",
    "url": "https://ggo.kvicloud.ru/content/files/v103/gungloryonline-ui-runtime-v1-stage103-ggo-hub.jar",
    "sha256": "fd9d06c65ecb19acba4e0789e7f91c26dcd1af1debea73cd3328cd836bc75c34",
    "size": 204232,
    "required": True,
    "side": "client",
    "version": "v103-candidate",
    "kind": "mod",
}

m = json.loads(SRC.read_text(encoding="utf-8"))
m["gameVersion"] = "v103-candidate"
out=[]; core=False; ui=False
for entry in m["files"]:
    path=entry.get("path","")
    if path.startswith("mods/gungloryonline-core-runtime"):
        if not core: out.append(CORE.copy()); core=True
        continue
    if path.startswith("mods/gungloryonline-ui-runtime"):
        if not ui: out.append(UI.copy()); ui=True
        continue
    out.append(entry)
if not core or not ui:
    raise SystemExit("Stage100 manifest does not contain canonical GGO Core/UI entries")
m["files"] = out
DST.parent.mkdir(parents=True, exist_ok=True)
DST.write_text(json.dumps(m, ensure_ascii=False, indent=2)+"\n", encoding="utf-8")
print("Stage103 candidate manifest built:", DST)
print("Core:", CORE["sha256"])
print("UI:", UI["sha256"])
