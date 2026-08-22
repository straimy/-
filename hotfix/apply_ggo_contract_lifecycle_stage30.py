from pathlib import Path
import shutil

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
TARGET = ROOT / "src/main/java/arena/forge"
TARGET.mkdir(parents=True, exist_ok=True)

for name in ("GgoContractService.java", "GgoContractLifecycleHooks.java"):
    source = Path("hotfix") / name
    if not source.exists():
        raise SystemExit(f"Stage 30: missing {source}")
    shutil.copy2(source, TARGET / name)

service = (TARGET / "GgoContractService.java").read_text(encoding="utf-8")
hooks = (TARGET / "GgoContractLifecycleHooks.java").read_text(encoding="utf-8")
for marker in ("syncPlayer(ServerPlayer p)", "flush(ServerPlayer p)", "pushState(p,true)"):
    if marker not in service:
        raise SystemExit(f"Stage 30: service marker missing: {marker}")
for marker in ("PlayerLoggedInEvent", "PlayerLoggedOutEvent", "PlayerRespawnEvent", "PlayerChangedDimensionEvent"):
    if marker not in hooks:
        raise SystemExit(f"Stage 30: lifecycle marker missing: {marker}")

print("GGO Contracts Stage 30 applied")
print(" - login restores and pushes complete contract state")
print(" - respawn and dimension changes refresh map/economy state")
print(" - logout flushes durable state before cache eviction")
