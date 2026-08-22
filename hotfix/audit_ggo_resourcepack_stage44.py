#!/usr/bin/env python3
from pathlib import Path
import hashlib, zipfile

PACK=Path("GunnerArena-ResourcePack-1.20.1-v1.zip")
OUT=Path("ci-results/stage44-resourcepack-audit.txt")
LIMIT=120
if not PACK.is_file(): raise SystemExit(f"missing {PACK}")

sha=hashlib.sha256(PACK.read_bytes()).hexdigest()
with zipfile.ZipFile(PACK) as z:
    names=sorted(n for n in z.namelist() if not n.endswith("/"))

mc=[n for n in names if n.startswith("assets/minecraft/")]
textures=[n for n in mc if "/textures/" in n and n.endswith(".png")]
block_textures=[n for n in textures if "/textures/block/" in n or "/textures/blocks/" in n]
item_textures=[n for n in textures if "/textures/item/" in n or "/textures/items/" in n]
gui_textures=[n for n in textures if "/textures/gui/" in n]
entity_textures=[n for n in textures if "/textures/entity/" in n]
models=[n for n in mc if "/models/" in n and n.endswith(".json")]
blockstates=[n for n in mc if "/blockstates/" in n and n.endswith(".json")]
lang=[n for n in mc if "/lang/" in n and n.endswith(".json")]
sounds=[n for n in mc if n.endswith("sounds.json") or "/sounds/" in n]

def section(title,values):
    shown=values[:LIMIT]
    out=["",f"[{title}]",f"count={len(values)}",f"shown={len(shown)}"]
    out.extend(shown)
    if len(values)>LIMIT: out.append(f"... omitted={len(values)-LIMIT}")
    return out

lines=[
    "stage=44",
    f"resource_pack={PACK.name}",
    f"resource_pack_sha256={sha}",
    f"total_files={len(names)}",
    f"minecraft_override_files={len(mc)}",
    f"minecraft_texture_overrides={len(textures)}",
    f"minecraft_block_texture_overrides={len(block_textures)}",
    f"minecraft_item_texture_overrides={len(item_textures)}",
    f"minecraft_gui_texture_overrides={len(gui_textures)}",
    f"minecraft_entity_texture_overrides={len(entity_textures)}",
    f"minecraft_model_overrides={len(models)}",
    f"minecraft_blockstate_overrides={len(blockstates)}",
    f"minecraft_lang_files={len(lang)}",
    f"minecraft_sound_entries={len(sounds)}",
]
lines += section("block_textures",block_textures)
lines += section("item_textures",item_textures)
lines += section("gui_textures",gui_textures)
lines += section("entity_textures",entity_textures)
lines += section("models",models)
lines += section("blockstates",blockstates)
lines += section("language",lang)
lines += section("sounds",sounds)
OUT.parent.mkdir(parents=True,exist_ok=True)
OUT.write_text("\n".join(lines)+"\n",encoding="utf-8")
print("\n".join(lines[:14]))
