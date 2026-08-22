from pathlib import Path
import shutil

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
TARGET = ROOT / "src/main/java/arena/forge"
TARGET.mkdir(parents=True, exist_ok=True)

for name in (
    "GgoContractRequestGuard.java",
    "GgoContractNetwork.java",
    "GgoContractMapNetwork.java",
    "GgoContractLifecycleHooks.java",
):
    source = Path("hotfix") / name
    if not source.exists():
        raise SystemExit(f"Stage 36: missing {source}")
    shutil.copy2(source, TARGET / name)

guard = (TARGET / "GgoContractRequestGuard.java").read_text(encoding="utf-8")
contracts = (TARGET / "GgoContractNetwork.java").read_text(encoding="utf-8")
contract_map = (TARGET / "GgoContractMapNetwork.java").read_text(encoding="utf-8")
lifecycle = (TARGET / "GgoContractLifecycleHooks.java").read_text(encoding="utf-8")

for marker in ("SNAPSHOT_INTERVAL_TICKS", "TRACK_INTERVAL_TICKS", "ConcurrentHashMap"):
    if marker not in guard:
        raise SystemExit(f"Stage 36: guard marker missing: {marker}")
for marker in ("allowContractSnapshot", "allowTrack", "if(!GgoContractService.track"):
    if marker not in contracts:
        raise SystemExit(f"Stage 36: contract channel marker missing: {marker}")
if "allowMapSnapshot" not in contract_map:
    raise SystemExit("Stage 36: map channel is not rate-limited")
if "GgoContractRequestGuard.clear" not in lifecycle:
    raise SystemExit("Stage 36: request limiter is not cleared on logout")

print("GGO Contract Network Guard Stage 36 applied")
print(" - contract and map snapshot requests are rate-limited per player")
print(" - TRACK requests are independently throttled")
print(" - successful TRACK no longer sends a duplicate snapshot")
print(" - limiter state is cleared on logout")
