from pathlib import Path
import shutil

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
TARGET = ROOT / "src/main/java/arena/forge"
TARGET.mkdir(parents=True, exist_ok=True)

source = Path("hotfix/GgoContractProgressHooks.java")
if not source.exists():
    raise SystemExit(f"Stage 32: missing {source}")
shutil.copy2(source, TARGET / source.name)

hooks = (TARGET / source.name).read_text(encoding="utf-8")
required = (
    'new ResourceLocation("jeg","bullet")',
    "source.typeHolder().unwrapKey()",
    "victim instanceof Enemy",
    "victim instanceof ServerPlayer",
    "if(!isCombatTarget(victim)||!isJegBullet(source))return;",
)
for marker in required:
    if marker not in hooks:
        raise SystemExit(f"Stage 32: eligibility marker missing: {marker}")
for forbidden in ("victim instanceof Mob", "PlayerLoggedOutEvent"):
    if forbidden in hooks:
        raise SystemExit(f"Stage 32: forbidden legacy eligibility remains: {forbidden}")

print("GGO Contracts Stage 32 applied")
print(" - FIELD TEST counts only authoritative JEG bullet eliminations")
print(" - DISTANCE DRILL uses the same bullet/target eligibility")
print(" - passive mobs and melee/environment kills never count")
