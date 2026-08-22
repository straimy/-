from pathlib import Path
import shutil

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
TARGET = ROOT / "src/main/java/arena/forge"
TARGET.mkdir(parents=True, exist_ok=True)

source = Path("hotfix/GgoContractService.java")
if not source.exists():
    raise SystemExit("Stage 28: GgoContractService.java missing")
shutil.copy2(source, TARGET / "GgoContractService.java")

text = (TARGET / "GgoContractService.java").read_text(encoding="utf-8")
for anchor in ("pushState(p,true)", "GgoContractNetwork.sync(p)", "GgoContractMapNetwork.sync(p)", "boolean balanceChanged"):
    if anchor not in text:
        raise SystemExit(f"Stage 28: missing push-sync anchor: {anchor}")

print("GGO Contracts Stage 28 server applied")
print(" - contract state pushes immediately after track/progress")
print(" - economy snapshot pushes immediately after a successful reward")
print(" - polling remains a reconnect/fallback safety net")
