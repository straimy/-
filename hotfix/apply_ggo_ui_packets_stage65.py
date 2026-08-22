#!/usr/bin/env python3
from pathlib import Path
import shutil

ROOT=Path("ga-build") if Path("ga-build").exists() else Path(".")
JAVA=ROOT/"client-ui/src/main/java/arena/client/shell"
HUD=JAVA/"GgoCombatHud.java"
SCREEN=JAVA/"GgoShellScreen.java"
BRIDGE=Path("hotfix/GgoUiActionClientBridge.java")
if not JAVA.is_dir(): raise SystemExit("client-ui source tree is missing")
for path in (HUD,SCREEN,BRIDGE):
    if not path.is_file(): raise SystemExit(f"stage65 missing {path}")
shutil.copy2(BRIDGE,JAVA/BRIDGE.name)

hud=HUD.read_text(encoding="utf-8")
old='''            if(selectedMedicineSlot>=18&&selectedMedicineSlot<=35&&mc.getConnection()!=null){\n                mc.getConnection().sendCommand("ggomed use "+selectedMedicineSlot);\n            }'''
new='''            if(selectedMedicineSlot>=18&&selectedMedicineSlot<=35){\n                GgoUiActionClientBridge.useMedicine(selectedMedicineSlot);\n            }'''
if old not in hud: raise SystemExit("stage65 medicine command marker missing")
hud=hud.replace(old,new,1)
HUD.write_text(hud,encoding="utf-8")

screen=SCREEN.read_text(encoding="utf-8")
replacements={
    'runClientCommand("ggoinv ammo")':'GgoUiActionClientBridge.sortAmmo()',
    'runClientCommand("ggoinv drop "+selectedInventorySlot)':'GgoUiActionClientBridge.dropSlot(selectedInventorySlot)',
    'runClientCommand("ggoinv drop "+slot)':'GgoUiActionClientBridge.dropSlot(slot)',
    'runClientCommand("ggoinv dropammo")':'GgoUiActionClientBridge.dropAmmo()',
    'runClientCommand("ggoinv clear")':'GgoUiActionClientBridge.clearField()',
    'runClientCommand("ggoinv select "+slot)':'GgoUiActionClientBridge.selectSlot(slot)',
    'runClientCommand("ggoinv swap "+selectedInventorySlot+" "+slot)':'GgoUiActionClientBridge.swapSlots(selectedInventorySlot,slot)',
}
for old_value,new_value in replacements.items():
    if old_value in screen: screen=screen.replace(old_value,new_value)
SCREEN.write_text(screen,encoding="utf-8")

bridge=(JAVA/BRIDGE.name).read_text(encoding="utf-8")
for required in [
    'Class.forName("arena.forge.GgoUiActionNetwork")',
    'getMethod("useMedicine",int.class)',
    'getMethod("swapSlots",int.class,int.class)',
]:
    if required not in bridge: raise SystemExit(f"stage65 bridge missing: {required}")
for path in (HUD,SCREEN):
    text=path.read_text(encoding="utf-8")
    for forbidden in ['sendCommand("ggomed','runClientCommand("ggoinv']:
        if forbidden in text: raise SystemExit(f"stage65 player command path survived in {path}: {forbidden}")
for required in [
    'GgoUiActionClientBridge.useMedicine(selectedMedicineSlot)',
]:
    if required not in HUD.read_text(encoding="utf-8"): raise SystemExit(f"stage65 HUD packet path missing: {required}")
for required in [
    'GgoUiActionClientBridge.sortAmmo()',
    'GgoUiActionClientBridge.dropSlot',
    'GgoUiActionClientBridge.selectSlot',
    'GgoUiActionClientBridge.swapSlots',
]:
    if required not in SCREEN.read_text(encoding="utf-8"): raise SystemExit(f"stage65 inventory packet path missing: {required}")
print("Applied GGO Stage 65 client UI packet migration")
print(" - medicine radial sends bounded GGO C2S action")
print(" - E inventory actions send bounded GGO C2S actions")
print(" - no first-party ggomed/ggoinv command strings remain in built client UI")
