#!/usr/bin/env python3
"""Plan conservative GGO resource-pack slimming from a real authored-map palette.

This is intentionally a DRY-RUN planner. It never rewrites the pack. Only Minecraft blockstate,
block-model and block-texture overrides can become removal candidates; player-facing item/GUI/
entity/sound/font/lang assets and every non-minecraft namespace are preserved automatically.
"""
from __future__ import annotations

import argparse
import json
from collections import deque
from pathlib import Path
from zipfile import ZipFile

CANDIDATE_PREFIXES = (
    "assets/minecraft/blockstates/",
    "assets/minecraft/models/block/",
    "assets/minecraft/textures/block/",
    "assets/minecraft/textures/blocks/",
)


def parse_palette(path: Path) -> list[str]:
    if not path.is_file():
        raise SystemExit(f"palette file is required and was not found: {path}")
    blocks: list[str] = []
    for raw in path.read_text(encoding="utf-8").splitlines():
        value = raw.strip()
        if not value or value.startswith("#"):
            continue
        if ":" not in value:
            raise SystemExit(f"invalid palette block id: {value!r}")
        blocks.append(value)
    blocks = sorted(set(blocks))
    if not blocks:
        raise SystemExit("palette is empty; refusing to plan any removal")
    return blocks


def resource_parts(value: str, default_namespace: str = "minecraft") -> tuple[str, str]:
    if ":" in value:
        namespace, path = value.split(":", 1)
    else:
        namespace, path = default_namespace, value
    return namespace, path


def model_entry(model_id: str) -> str:
    namespace, path = resource_parts(model_id)
    return f"assets/{namespace}/models/{path}.json"


def texture_entry(texture_id: str) -> str:
    namespace, path = resource_parts(texture_id)
    return f"assets/{namespace}/textures/{path}.png"


def collect_models_from_blockstate(data: object) -> set[str]:
    found: set[str] = set()

    def visit(value: object) -> None:
        if isinstance(value, dict):
            model = value.get("model")
            if isinstance(model, str) and model:
                found.add(model)
            for nested in value.values():
                visit(nested)
        elif isinstance(value, list):
            for nested in value:
                visit(nested)

    visit(data)
    return found


def collect_model_refs(data: object) -> tuple[set[str], set[str]]:
    models: set[str] = set()
    textures: set[str] = set()
    if not isinstance(data, dict):
        return models, textures
    parent = data.get("parent")
    if isinstance(parent, str) and parent and parent not in {"builtin/generated", "builtin/entity"}:
        models.add(parent)
    raw_textures = data.get("textures")
    if isinstance(raw_textures, dict):
        for value in raw_textures.values():
            if isinstance(value, str) and value and not value.startswith("#"):
                textures.add(value)
    return models, textures


def plan(pack: Path, palette: Path, report: Path) -> None:
    if not pack.is_file():
        raise SystemExit(f"resource pack not found: {pack}")
    blocks = parse_palette(palette)

    with ZipFile(pack) as z:
        names = {name for name in z.namelist() if not name.endswith("/")}
        if "pack.mcmeta" not in names:
            raise SystemExit("resource pack is missing pack.mcmeta")

        keep: set[str] = {name for name in names if not name.startswith(CANDIDATE_PREFIXES)}
        missing_blockstates: list[str] = []
        model_queue: deque[str] = deque()

        for block_id in blocks:
            namespace, block_path = resource_parts(block_id)
            if namespace != "minecraft":
                continue
            state = f"assets/minecraft/blockstates/{block_path}.json"
            if state not in names:
                missing_blockstates.append(block_id)
                continue
            keep.add(state)
            try:
                data = json.loads(z.read(state))
            except Exception as exc:
                raise SystemExit(f"cannot parse {state}: {exc}") from exc
            model_queue.extend(sorted(collect_models_from_blockstate(data)))

        seen_models: set[str] = set()
        while model_queue:
            model_id = model_queue.popleft()
            if model_id in seen_models:
                continue
            seen_models.add(model_id)
            entry = model_entry(model_id)
            if entry not in names:
                # Vanilla fallback model outside the RP is valid and requires no local file.
                continue
            keep.add(entry)
            try:
                data = json.loads(z.read(entry))
            except Exception as exc:
                raise SystemExit(f"cannot parse {entry}: {exc}") from exc
            parents, textures = collect_model_refs(data)
            model_queue.extend(sorted(parents))
            for texture_id in textures:
                texture = texture_entry(texture_id)
                if texture in names:
                    keep.add(texture)
                    sidecar = texture + ".mcmeta"
                    if sidecar in names:
                        keep.add(sidecar)

        candidates = sorted(
            name for name in names
            if name.startswith(CANDIDATE_PREFIXES) and name not in keep
        )
        kept_block_assets = sorted(
            name for name in names
            if name.startswith(CANDIDATE_PREFIXES) and name in keep
        )

    minecraft_blocks = [value for value in blocks if value.startswith("minecraft:")]
    modded_blocks = [value for value in blocks if not value.startswith("minecraft:")]
    lines = [
        "stage=64",
        "mode=DRY_RUN_ONLY",
        f"resource_pack={pack}",
        f"palette={palette}",
        f"palette_unique={len(blocks)}",
        f"palette_minecraft={len(minecraft_blocks)}",
        f"palette_modded={len(modded_blocks)}",
        f"missing_minecraft_blockstates={len(missing_blockstates)}",
        f"kept_block_asset_overrides={len(kept_block_assets)}",
        f"candidate_block_asset_removals={len(candidates)}",
        "safety=non-block minecraft assets and all non-minecraft namespaces are always preserved",
        "write_performed=false",
        "",
        "[missing_minecraft_blockstates]",
        *missing_blockstates,
        "",
        "[kept_block_asset_overrides]",
        *kept_block_assets,
        "",
        "[candidate_block_asset_removals]",
        *candidates,
    ]
    report.parent.mkdir(parents=True, exist_ok=True)
    report.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("\n".join(lines[:12]))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("palette", type=Path, help="ggo-map-palette.txt exported by /ggopalette")
    parser.add_argument("--pack", type=Path, default=Path("GunnerArena-ResourcePack-1.20.1-v1.zip"))
    parser.add_argument("--report", type=Path, default=Path("ci-results/stage64-resourcepack-slim-plan.txt"))
    args = parser.parse_args()
    plan(args.pack, args.palette, args.report)


if __name__ == "__main__":
    main()
