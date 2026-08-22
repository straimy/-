#!/usr/bin/env python3
from pathlib import Path

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
PATH = ROOT / "src/main/java/arena/forge/net/ArenaNetwork.java"
if not PATH.is_file():
    raise SystemExit(f"missing {PATH}")

s = PATH.read_text(encoding="utf-8")

import_anchor = "import net.minecraftforge.network.simple.SimpleChannel;\n"
if import_anchor not in s:
    raise SystemExit("ArenaNetwork SimpleChannel import marker missing")
if "net.minecraftforge.api.distmarker.Dist" not in s:
    s = s.replace(
        import_anchor,
        import_anchor + "import net.minecraftforge.api.distmarker.Dist;\nimport net.minecraftforge.fml.loading.FMLEnvironment;\n",
        1,
    )

old_channel = '''    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(\n        CHANNEL_ID,\n        () -> PROTOCOL_VERSION,\n        PROTOCOL_VERSION::equals,\n        PROTOCOL_VERSION::equals\n    );'''
new_channel = '''    /**\n     * The legacy gunnerarena:ui protocol is split by physical process:\n     * dedicated server -> Core owns the channel and server handlers;\n     * game client -> gunnerarena_ui owns the same wire protocol and client handlers.\n     *\n     * Never create both halves in the same client JVM: Forge rejects duplicate\n     * channel ids before the GGO runtime can finish CONSTRUCT.\n     */\n    public static final SimpleChannel CHANNEL = FMLEnvironment.dist == Dist.DEDICATED_SERVER\n        ? NetworkRegistry.newSimpleChannel(\n            CHANNEL_ID,\n            () -> PROTOCOL_VERSION,\n            PROTOCOL_VERSION::equals,\n            PROTOCOL_VERSION::equals\n        )\n        : null;'''
if old_channel not in s and new_channel not in s:
    raise SystemExit("ArenaNetwork channel declaration marker missing")
if old_channel in s:
    s = s.replace(old_channel, new_channel, 1)

old_register = '''    public static synchronized void register() {\n        if (registered) return;'''
new_register = '''    public static synchronized void register() {\n        // On a physical game client ArenaClientNetwork owns gunnerarena:ui.\n        // This prevents duplicate NetworkRegistry registration during CONSTRUCT.\n        if (FMLEnvironment.dist != Dist.DEDICATED_SERVER) return;\n        if (registered) return;'''
if old_register not in s and new_register not in s:
    raise SystemExit("ArenaNetwork register marker missing")
if old_register in s:
    s = s.replace(old_register, new_register, 1)

PATH.write_text(s, encoding="utf-8")

check = PATH.read_text(encoding="utf-8")
required = [
    "FMLEnvironment.dist == Dist.DEDICATED_SERVER",
    "FMLEnvironment.dist != Dist.DEDICATED_SERVER",
    "NetworkRegistry.newSimpleChannel",
]
for marker in required:
    if marker not in check:
        raise SystemExit(f"stage71 marker missing after patch: {marker}")

print("Applied GGO Stage 71 UI channel ownership fix")
print(" - dedicated server Core owns gunnerarena:ui")
print(" - physical client Core does not create duplicate gunnerarena:ui")
print(" - client UI remains wire-compatible protocol v5 owner")
