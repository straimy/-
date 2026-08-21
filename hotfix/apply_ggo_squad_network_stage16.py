from pathlib import Path
import shutil

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
TARGET = ROOT / "src/main/java/arena/forge"
TARGET.mkdir(parents=True, exist_ok=True)

for name in ("GgoSquadService.java", "GgoSquadNetwork.java"):
    src = Path("hotfix") / name
    if not src.exists():
        raise SystemExit(f"missing {src}")
    shutil.copy2(src, TARGET / name)

print("GGO Squad Network Stage 16 applied")
print(" - Forge SimpleChannel: gunnerarena:ggo_squad")
print(" - server-authoritative scoreboard-team squad adapter")
print(" - ping distance/rate/type validation")
print(" - squad snapshot request/response")
