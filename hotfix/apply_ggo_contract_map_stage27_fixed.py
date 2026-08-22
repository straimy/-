from pathlib import Path

# Execute the Stage 27 patch with a structural HUD insertion instead of relying
# on one exact null-guard string after the earlier UI stages have rewritten it.
source_path = Path("hotfix/apply_ggo_contract_map_stage27.py")
source = source_path.read_text(encoding="utf-8")

old = '''mc_anchor = "        if (mc.player == null || mc.level == null || mc.screen != null) return;\\n"
if mc_anchor in h and "GgoRuntimeV1ContractMapAdapter.tick();" not in h:
    h = h.replace(mc_anchor, mc_anchor + "        GgoRuntimeV1ContractAdapter.tick();\\n        GgoRuntimeV1ContractMapAdapter.tick();\\n        GgoContractCompletionState.poll();\\n", 1)
elif "GgoRuntimeV1ContractMapAdapter.tick();" not in h:
    raise SystemExit("Stage 27: HUD tick anchor missing")
'''

new = '''if "GgoRuntimeV1ContractMapAdapter.tick();" not in h:
    lines = h.splitlines(keepends=True)
    insert_at = -1
    indent = "        "
    for index, line in enumerate(lines):
        compact = line.replace(" ", "")
        if "if(mc.player==null" in compact and "return;" in compact:
            insert_at = index + 1
            indent = line[:len(line) - len(line.lstrip())]
            break
    if insert_at < 0:
        for index, line in enumerate(lines):
            if "Minecraft mc = Minecraft.getInstance();" in line:
                insert_at = index + 1
                indent = line[:len(line) - len(line.lstrip())]
                break
    if insert_at < 0:
        raise SystemExit("Stage 27: HUD render context missing")
    lines[insert_at:insert_at] = [
        indent + "GgoRuntimeV1ContractAdapter.tick();\\n",
        indent + "GgoRuntimeV1ContractMapAdapter.tick();\\n",
        indent + "GgoContractCompletionState.poll();\\n",
    ]
    h = "".join(lines)
'''

if old not in source:
    raise SystemExit("Stage 27 fixed wrapper: expected HUD block not found")
source = source.replace(old, new, 1)
exec(compile(source, str(source_path), "exec"), {"__name__": "__main__", "__file__": str(source_path)})
