from pathlib import Path
import shutil

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
TARGET = ROOT / "src/main/java/arena/forge"
TARGET.mkdir(parents=True, exist_ok=True)

source = Path("hotfix/GgoContractService.java")
if not source.exists():
    raise SystemExit(f"Stage 37: missing {source}")
shutil.copy2(source, TARGET / source.name)

service = (TARGET / source.name).read_text(encoding="utf-8")
map_call = "if(includeEconomy)GgoContractMapNetwork.sync(p);"
contract_call = "GgoContractNetwork.sync(p);"
if map_call not in service or contract_call not in service:
    raise SystemExit("Stage 37: reward push calls missing")
if service.index(map_call) > service.index(contract_call, service.index("private static void pushState")):
    raise SystemExit("Stage 37: contract completion is still sent before economy state")

print("GGO Contract Reward Order Stage 37 applied")
print(" - economy/map state is sent before completion state")
print(" - completion popup receives the post-reward balance first")
