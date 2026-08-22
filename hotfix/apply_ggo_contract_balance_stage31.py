from pathlib import Path
import shutil

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
TARGET = ROOT / "src/main/java/arena/forge"
TARGET.mkdir(parents=True, exist_ok=True)

for name in ("GgoContractBalance.java", "GgoContractService.java", "GgoContractProgressHooks.java"):
    source = Path("hotfix") / name
    if not source.exists():
        raise SystemExit(f"Stage 31: missing {source}")
    shutil.copy2(source, TARGET / name)

balance = (TARGET / "GgoContractBalance.java").read_text(encoding="utf-8")
service = (TARGET / "GgoContractService.java").read_text(encoding="utf-8")
hooks = (TARGET / "GgoContractProgressHooks.java").read_text(encoding="utf-8")
for marker in ("reward_credits", "min_distance_blocks", "FMLPaths.CONFIGDIR"):
    if marker not in balance:
        raise SystemExit(f"Stage 31: balance marker missing: {marker}")
for marker in ('GgoContractBalance.target("field_test"', 'GgoContractBalance.reward("supply_run"'):
    if marker not in service:
        raise SystemExit(f"Stage 31: service marker missing: {marker}")
if "GgoContractBalance.distanceDrillMeters()" not in hooks:
    raise SystemExit("Stage 31: distance threshold is not server-configured")

print("GGO Contracts Stage 31 applied")
print(" - targets and credit rewards come from server config")
print(" - distance drill range comes from server config")
print(" - server snapshots keep Activities and popup balance authoritative")
