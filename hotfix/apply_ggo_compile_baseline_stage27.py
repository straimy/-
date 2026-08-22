from pathlib import Path
import re

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
SRC = ROOT / "src/main/java"

# Forge 47.4.x PacketDistributor requires a Supplier<ServerPlayer>.
network = SRC / "arena/forge/net/ArenaNetwork.java"
if network.exists():
    text = network.read_text(encoding="utf-8")
    text = text.replace("PacketDistributor.PLAYER.with(player)", "PacketDistributor.PLAYER.with(() -> player)")
    network.write_text(text, encoding="utf-8")

# The archived baseline used an obsolete ActiveHazard accessor. Resolve the
# actual record/component name from the extracted source instead of guessing.
executor = SRC / "arena/forge/hazard/ForgeHazardExecutor.java"
if not executor.exists():
    raise SystemExit("Stage 27 baseline: ForgeHazardExecutor.java missing")
executor_text = executor.read_text(encoding="utf-8")
if "hazard.dimension()" in executor_text:
    declarations = []
    for path in SRC.rglob("*.java"):
        candidate = path.read_text(encoding="utf-8", errors="replace")
        if "ActiveHazard" in candidate and path != executor:
            declarations.append((path, candidate))

    accessor = None
    preferred = ("dimensionId", "dimensionKey", "levelKey", "level", "world", "dimension")
    for _, candidate in declarations:
        record = re.search(r"record\s+ActiveHazard\s*\((.*?)\)\s*\{", candidate, re.S)
        if record:
            component_names = re.findall(r"\b([A-Za-z_$][A-Za-z0-9_$]*)\s*(?:,|$)", record.group(1).replace("\n", " "))
            for name in preferred:
                if name in component_names and name != "dimension":
                    accessor = name
                    break
        if accessor:
            break
        for name in preferred:
            if name != "dimension" and re.search(r"\b" + re.escape(name) + r"\s*\(\s*\)", candidate):
                accessor = name
                break
        if accessor:
            break

    if accessor is None:
        snippets = []
        for path, candidate in declarations:
            pos = candidate.find("ActiveHazard")
            snippets.append(f"--- {path}\n{candidate[max(0,pos-250):pos+900]}")
        raise SystemExit("Stage 27 baseline: unresolved ActiveHazard dimension accessor\n" + "\n".join(snippets))

    executor_text = executor_text.replace("hazard.dimension()", f"hazard.{accessor}()")
    executor.write_text(executor_text, encoding="utf-8")
    print(f"Stage 27 baseline: ActiveHazard accessor -> {accessor}()")

print("GGO Stage 27 compile baseline applied")
