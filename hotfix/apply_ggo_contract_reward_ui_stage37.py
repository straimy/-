from pathlib import Path

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
JAVA = ROOT / "client-ui/src/main/java/arena/client/shell"
COMPLETION = JAVA / "GgoContractCompletionState.java"
HUD = JAVA / "GgoCombatHud.java"
for path in (COMPLETION, HUD):
    if not path.exists():
        raise SystemExit(f"Stage 37: missing {path}; apply Stage 35 first")

completion = COMPLETION.read_text(encoding="utf-8")
completion = completion.replace(
    "public record Popup(String id,String title,int rewardCredits,long expiresAt){}",
    "public record Popup(String id,String title,int rewardCredits,long balanceAfter,long expiresAt){}",
)
completion = completion.replace(
    "popup=new Popup(entry.id(),entry.title(),entry.rewardCredits(),now+5500L);",
    "popup=new Popup(entry.id(),entry.title(),entry.rewardCredits(),GgoSupplyMapState.snapshot().creditBalance(),now+5500L);",
)
if "long balanceAfter" not in completion or "creditBalance(),now+5500L" not in completion:
    raise SystemExit("Stage 37: completion balance snapshot patch failed")
COMPLETION.write_text(completion, encoding="utf-8")

hud = HUD.read_text(encoding="utf-8")
old_reward = 'String reward="+"+popup.rewardCredits()+" CREDITS  •  BALANCE "+GgoSupplyMapState.snapshot().creditBalance();'
new_reward = 'String reward="+"+popup.rewardCredits()+" CREDITS  •  BALANCE "+popup.balanceAfter();'
if old_reward in hud:
    hud = hud.replace(old_reward, new_reward, 1)
elif new_reward not in hud:
    raise SystemExit("Stage 37: HUD reward balance anchor missing")
HUD.write_text(hud, encoding="utf-8")

print("GGO Contract Reward UI Stage 37 applied")
print(" - completion popup snapshots the authoritative post-reward balance")
print(" - popup balance remains stable while it is visible")
print(" - reconnect reset behavior from Stage 35 remains intact")
