from pathlib import Path
import shutil

# Canonical Stage 29 entrypoint used by the full contracts compile gate.
ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
TARGET = ROOT / "src/main/java/arena/forge"
TARGET.mkdir(parents=True, exist_ok=True)

for name in ("GgoContractService.java", "GgoContractPersistence.java"):
    source = Path("hotfix") / name
    if not source.exists():
        raise SystemExit(f"Stage 29: missing {source}")
    shutil.copy2(source, TARGET / name)

service = (TARGET / "GgoContractService.java").read_text(encoding="utf-8")
persistence = (TARGET / "GgoContractPersistence.java").read_text(encoding="utf-8")
required_service = (
    "GgoContractPersistence.load",
    "GgoContractPersistence.save",
    "Claim first, then grant",
    "Durable state remains in the world save",
)
required_persistence = (
    "LevelResource.ROOT",
    "gungloryonline-contracts.properties",
    "ATOMIC_MOVE",
    ".rewarded",
)
for marker in required_service:
    if marker not in service:
        raise SystemExit(f"Stage 29: service marker missing: {marker}")
for marker in required_persistence:
    if marker not in persistence:
        raise SystemExit(f"Stage 29: persistence marker missing: {marker}")

print("GGO Contracts Stage 29 applied")
print(" - progress and tracked contract persist per world")
print(" - completion and reward ledger survive restarts")
print(" - contract state writes use atomic replacement")
print(" - clear(UUID) only unloads session cache")
