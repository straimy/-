from pathlib import Path

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
SRC = ROOT / "src/main/java"

# Forge 47.4.x PacketDistributor requires a Supplier<ServerPlayer>.
network = SRC / "arena/forge/net/ArenaNetwork.java"
if network.exists():
    text = network.read_text(encoding="utf-8")
    text = text.replace("PacketDistributor.PLAYER.with(player)", "PacketDistributor.PLAYER.with(() -> player)")
    network.write_text(text, encoding="utf-8")

# The archived baseline accepts a dimension in HazardManager.spawn(), but drops
# it before creating ActiveHazard while ForgeHazardExecutor expects dimension().
# Preserve the original dimension end-to-end instead of forcing overworld.
active_path = SRC / "arena/hazard/ActiveHazard.java"
manager_path = SRC / "arena/hazard/HazardManager.java"
executor_path = SRC / "arena/forge/hazard/ForgeHazardExecutor.java"
for required in (active_path, manager_path, executor_path):
    if not required.exists():
        raise SystemExit(f"Stage 27 baseline: missing {required}")

active = active_path.read_text(encoding="utf-8")
if "public String dimension()" not in active:
    field_anchor = "    private final UUID id;\n"
    if field_anchor not in active:
        raise SystemExit("Stage 27 baseline: ActiveHazard id field anchor missing")
    active = active.replace(field_anchor, field_anchor + "    private final String dimension;\n", 1)

    first_ctor = "    public ActiveHazard(HazardSpec spec, long createdTick) {\n        this(UUID.randomUUID(), spec, createdTick, createdTick + spec.lifetimeTicks());\n    }\n"
    first_replacement = "    public ActiveHazard(String dimension, HazardSpec spec, long createdTick) {\n        this(UUID.randomUUID(), dimension, spec, createdTick, createdTick + spec.lifetimeTicks());\n    }\n"
    if first_ctor not in active:
        raise SystemExit("Stage 27 baseline: ActiveHazard primary constructor anchor missing")
    active = active.replace(first_ctor, first_replacement, 1)

    second_ctor = "    public ActiveHazard(UUID id, HazardSpec spec, long createdTick, long expiresTick) {\n        this.id = id;\n        this.spec = spec;\n"
    second_replacement = "    public ActiveHazard(UUID id, String dimension, HazardSpec spec, long createdTick, long expiresTick) {\n        this.id = id;\n        this.dimension = dimension;\n        this.spec = spec;\n"
    if second_ctor not in active:
        raise SystemExit("Stage 27 baseline: ActiveHazard full constructor anchor missing")
    active = active.replace(second_ctor, second_replacement, 1)

    accessor_anchor = "    public UUID id() { return id; }\n"
    if accessor_anchor not in active:
        raise SystemExit("Stage 27 baseline: ActiveHazard accessor anchor missing")
    active = active.replace(accessor_anchor, accessor_anchor + "    public String dimension() { return dimension; }\n", 1)
    active_path.write_text(active, encoding="utf-8")

manager = manager_path.read_text(encoding="utf-8")
old_spawn = "new ActiveHazard(spec, nowTick)"
new_spawn = "new ActiveHazard(dimension, spec, nowTick)"
if old_spawn in manager:
    manager = manager.replace(old_spawn, new_spawn)
elif new_spawn not in manager:
    raise SystemExit("Stage 27 baseline: HazardManager spawn constructor anchor missing")
manager_path.write_text(manager, encoding="utf-8")

# Fail early if another main-source call still targets one of the removed constructors.
remaining = []
for path in SRC.rglob("*.java"):
    text = path.read_text(encoding="utf-8", errors="replace")
    if path != active_path and "new ActiveHazard(spec," in text:
        remaining.append(str(path))
if remaining:
    raise SystemExit("Stage 27 baseline: stale ActiveHazard constructors in " + ", ".join(remaining))

# One archived debug command mutates `real` while counting connections, then
# captures it in Brigadier's lazy message lambda. Snapshot the final count at
# the send site so Java 17 sees an effectively-final value without changing the
# calculation or command output.
commands_path = SRC / "arena/forge/GunnerCommands.java"
if commands_path.exists():
    commands = commands_path.read_text(encoding="utf-8")
    marker = '+ " real=" + real + " desired=" + desired + " reserved=" + reserved + " connected=" + connected'
    if marker in commands and "final int realSnapshot = real;" not in commands:
        marker_pos = commands.index(marker)
        send_pos = commands.rfind("source.sendSuccess(", 0, marker_pos)
        if send_pos < 0:
            raise SystemExit("Stage 27 baseline: GunnerCommands sendSuccess anchor missing")
        line_start = commands.rfind("\n", 0, send_pos) + 1
        indent = commands[line_start:send_pos]
        commands = commands[:line_start] + indent + "final int realSnapshot = real;\n" + commands[line_start:]
        commands = commands.replace(marker, '+ " real=" + realSnapshot + " desired=" + desired + " reserved=" + reserved + " connected=" + connected', 1)
    elif marker in commands and "final int realSnapshot = real;" in commands:
        commands = commands.replace(marker, '+ " real=" + realSnapshot + " desired=" + desired + " reserved=" + reserved + " connected=" + connected', 1)
    commands_path.write_text(commands, encoding="utf-8")

print("GGO Stage 27 compile baseline applied")
print(" - PacketDistributor uses Supplier<ServerPlayer>")
print(" - ActiveHazard preserves its source dimension")
print(" - command connection count is snapshotted before lazy feedback")
