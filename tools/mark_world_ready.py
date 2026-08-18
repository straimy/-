#!/usr/bin/env python3
"""Create the production .ggo-world-ready marker only for a clean audited world."""
from __future__ import annotations

import argparse
import json
import subprocess
import sys
import tempfile
from datetime import datetime, timezone
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser(description="Mark a GGO world production-ready after command-block audit")
    parser.add_argument("world", type=Path, help="Minecraft world directory")
    parser.add_argument("--audit-tool", type=Path, default=Path(__file__).with_name("world_command_audit.py"))
    parser.add_argument("--marker", type=Path, help="marker path; defaults to WORLD_PARENT/.ggo-world-ready")
    args = parser.parse_args()

    world = args.world.expanduser().resolve()
    audit_tool = args.audit_tool.expanduser().resolve()
    if not (world / "level.dat").is_file():
        raise SystemExit(f"not a Minecraft world: {world}")
    if not audit_tool.is_file():
        raise SystemExit(f"audit tool not found: {audit_tool}")

    marker = args.marker.expanduser().resolve() if args.marker else world.parent / ".ggo-world-ready"
    if marker.exists():
        marker.unlink()

    with tempfile.TemporaryDirectory(prefix="ggo-ready-") as tmp:
        report_path = Path(tmp) / "audit.json"
        subprocess.run(
            [sys.executable, str(audit_tool), str(world), "--output", str(report_path)],
            check=True,
        )
        report = json.loads(report_path.read_text(encoding="utf-8"))

    count = int(report.get("commandBlockCount", -1))
    region_errors = report.get("regionErrors") or []
    if count != 0:
        raise SystemExit(f"refusing readiness marker: commandBlockCount={count}")
    if region_errors:
        raise SystemExit(f"refusing readiness marker: audit has {len(region_errors)} region parse errors")

    payload = {
        "schemaVersion": 1,
        "ready": True,
        "auditedAt": datetime.now(timezone.utc).isoformat(),
        "world": world.name,
        "commandBlockCount": 0,
        "functionCommandCount": int(report.get("functionCommandCount", 0)),
    }
    marker.parent.mkdir(parents=True, exist_ok=True)
    marker.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(marker)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
