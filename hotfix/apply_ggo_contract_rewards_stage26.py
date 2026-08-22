from pathlib import Path
import shutil

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
TARGET = ROOT / "src/main/java/arena/forge"
TARGET.mkdir(parents=True, exist_ok=True)

FILES = (
    "GgoObjectiveService.java",
    "GgoContractService.java",
    "GgoContractNetwork.java",
    "GgoContractProgressHooks.java",
    "GgoContractRewardBridge.java",
    "GgoSupplyExtractionService.java",
)

for name in FILES:
    src = Path("hotfix") / name
    if not src.exists():
        raise SystemExit(f"missing {src}")
    shutil.copy2(src, TARGET / name)

print("GGO Contracts Stage 26 applied")
print(" - authoritative contract catalog/progress")
print(" - one-shot completion rewards")
print(" - Runtime v1 economy adapter")
print(" - tagged supply extraction")
print(" - persistent per-dimension extraction points")
