#!/usr/bin/env python3
"""Audit Minecraft Java worlds for command-block/datapack logic without third-party packages.

Supports modern Anvil region files (.mca), common NBT tag types, plain/zipped world input,
and emits JSON suitable for the GGO command-block migration inventory.
"""
from __future__ import annotations

import argparse
import gzip
import io
import json
import shutil
import struct
import tempfile
import zipfile
import zlib
from pathlib import Path
from typing import Any, BinaryIO


class NbtError(RuntimeError):
    pass


def read_exact(stream: BinaryIO, length: int) -> bytes:
    data = stream.read(length)
    if len(data) != length:
        raise NbtError(f"unexpected EOF: wanted {length}, got {len(data)}")
    return data


def u8(stream: BinaryIO) -> int:
    return read_exact(stream, 1)[0]


def i8(stream: BinaryIO) -> int:
    return struct.unpack(">b", read_exact(stream, 1))[0]


def i16(stream: BinaryIO) -> int:
    return struct.unpack(">h", read_exact(stream, 2))[0]


def u16(stream: BinaryIO) -> int:
    return struct.unpack(">H", read_exact(stream, 2))[0]


def i32(stream: BinaryIO) -> int:
    return struct.unpack(">i", read_exact(stream, 4))[0]


def i64(stream: BinaryIO) -> int:
    return struct.unpack(">q", read_exact(stream, 8))[0]


def f32(stream: BinaryIO) -> float:
    return struct.unpack(">f", read_exact(stream, 4))[0]


def f64(stream: BinaryIO) -> float:
    return struct.unpack(">d", read_exact(stream, 8))[0]


def nbt_string(stream: BinaryIO) -> str:
    size = u16(stream)
    return read_exact(stream, size).decode("utf-8", errors="replace")


def parse_payload(stream: BinaryIO, tag_type: int) -> Any:
    if tag_type == 0:
        return None
    if tag_type == 1:
        return i8(stream)
    if tag_type == 2:
        return i16(stream)
    if tag_type == 3:
        return i32(stream)
    if tag_type == 4:
        return i64(stream)
    if tag_type == 5:
        return f32(stream)
    if tag_type == 6:
        return f64(stream)
    if tag_type == 7:
        length = i32(stream)
        if length < 0:
            raise NbtError("negative byte-array length")
        return list(read_exact(stream, length))
    if tag_type == 8:
        return nbt_string(stream)
    if tag_type == 9:
        child_type = u8(stream)
        length = i32(stream)
        if length < 0:
            raise NbtError("negative list length")
        return [parse_payload(stream, child_type) for _ in range(length)]
    if tag_type == 10:
        value: dict[str, Any] = {}
        while True:
            child_type = u8(stream)
            if child_type == 0:
                return value
            child_name = nbt_string(stream)
            value[child_name] = parse_payload(stream, child_type)
    if tag_type == 11:
        length = i32(stream)
        if length < 0:
            raise NbtError("negative int-array length")
        return [i32(stream) for _ in range(length)]
    if tag_type == 12:
        length = i32(stream)
        if length < 0:
            raise NbtError("negative long-array length")
        return [i64(stream) for _ in range(length)]
    raise NbtError(f"unsupported tag type {tag_type}")


def parse_nbt(data: bytes) -> dict[str, Any]:
    stream = io.BytesIO(data)
    root_type = u8(stream)
    if root_type != 10:
        raise NbtError(f"root tag must be compound (10), got {root_type}")
    _root_name = nbt_string(stream)
    root = parse_payload(stream, 10)
    if not isinstance(root, dict):
        raise NbtError("invalid NBT root")
    return root


def decompress_chunk(payload: bytes, compression: int) -> bytes:
    if compression == 1:
        return gzip.decompress(payload)
    if compression == 2:
        return zlib.decompress(payload)
    if compression == 3:
        return payload
    raise NbtError(f"unsupported region compression {compression}")


def iter_region_chunks(path: Path):
    with path.open("rb") as stream:
        header = read_exact(stream, 8192)
        locations = header[:4096]
        for index in range(1024):
            entry = locations[index * 4:(index + 1) * 4]
            sector_offset = int.from_bytes(entry[:3], "big")
            sector_count = entry[3]
            if sector_offset == 0 or sector_count == 0:
                continue
            stream.seek(sector_offset * 4096)
            length = struct.unpack(">I", read_exact(stream, 4))[0]
            if length <= 1 or length > sector_count * 4096:
                continue
            compression = u8(stream)
            payload = read_exact(stream, length - 1)
            local_x = index % 32
            local_z = index // 32
            try:
                yield local_x, local_z, parse_nbt(decompress_chunk(payload, compression))
            except Exception as exc:
                yield local_x, local_z, {"__ggo_parse_error__": str(exc)}


def find_block_entities(root: dict[str, Any]) -> list[dict[str, Any]]:
    candidates: list[Any] = []
    for key in ("block_entities", "TileEntities"):
        if isinstance(root.get(key), list):
            candidates.extend(root[key])
    level = root.get("Level")
    if isinstance(level, dict):
        for key in ("block_entities", "TileEntities"):
            if isinstance(level.get(key), list):
                candidates.extend(level[key])
    return [value for value in candidates if isinstance(value, dict)]


def command_block_record(entity: dict[str, Any], region: Path, dimension: str) -> dict[str, Any] | None:
    entity_id = str(entity.get("id", ""))
    if "command_block" not in entity_id:
        return None
    command = entity.get("Command")
    return {
        "dimension": dimension,
        "region": str(region),
        "id": entity_id,
        "x": entity.get("x"),
        "y": entity.get("y"),
        "z": entity.get("z"),
        "command": command if isinstance(command, str) else "",
        "auto": entity.get("auto"),
        "powered": entity.get("powered"),
        "conditionMet": entity.get("conditionMet"),
        "successCount": entity.get("SuccessCount"),
        "trackOutput": entity.get("TrackOutput"),
        "lastExecution": entity.get("LastExecution"),
    }


def dimension_for(world: Path, region: Path) -> str:
    try:
        relative = region.relative_to(world).as_posix()
    except ValueError:
        return "unknown"
    if relative.startswith("DIM-1/"):
        return "minecraft:the_nether"
    if relative.startswith("DIM1/"):
        return "minecraft:the_end"
    if relative.startswith("dimensions/"):
        parts = relative.split("/")
        if len(parts) >= 4:
            return f"{parts[1]}:{parts[2]}"
    return "minecraft:overworld"


def scan_regions(world: Path) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    commands: list[dict[str, Any]] = []
    errors: list[dict[str, Any]] = []
    for region in sorted(world.rglob("*.mca")):
        if region.parent.name != "region":
            continue
        dimension = dimension_for(world, region)
        try:
            for local_x, local_z, chunk in iter_region_chunks(region):
                if "__ggo_parse_error__" in chunk:
                    errors.append({"region": str(region.relative_to(world)), "chunk": [local_x, local_z], "error": chunk["__ggo_parse_error__"]})
                    continue
                for entity in find_block_entities(chunk):
                    record = command_block_record(entity, region.relative_to(world), dimension)
                    if record:
                        commands.append(record)
        except Exception as exc:
            errors.append({"region": str(region.relative_to(world)), "error": str(exc)})
    return commands, errors


def scan_functions(world: Path) -> list[dict[str, Any]]:
    found: list[dict[str, Any]] = []
    for path in sorted(world.rglob("*.mcfunction")):
        if "datapacks" not in path.parts:
            continue
        try:
            lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
        except OSError:
            continue
        commands = [line.strip() for line in lines if line.strip() and not line.lstrip().startswith("#")]
        found.append({"path": str(path.relative_to(world)), "commands": commands})
    for archive in sorted(world.rglob("*.zip")):
        if "datapacks" not in archive.parts:
            continue
        try:
            with zipfile.ZipFile(archive) as zf:
                for name in sorted(zf.namelist()):
                    if not name.endswith(".mcfunction"):
                        continue
                    lines = zf.read(name).decode("utf-8", errors="replace").splitlines()
                    commands = [line.strip() for line in lines if line.strip() and not line.lstrip().startswith("#")]
                    found.append({"path": f"{archive.relative_to(world)}!/{name}", "commands": commands})
        except (OSError, zipfile.BadZipFile):
            continue
    return found


def scan_scoreboard(world: Path) -> dict[str, Any]:
    path = world / "data" / "scoreboard.dat"
    if not path.is_file():
        return {"objectives": [], "teams": []}
    try:
        raw = gzip.decompress(path.read_bytes())
        root = parse_nbt(raw)
        data = root.get("data", root)
        objectives = []
        teams = []
        if isinstance(data, dict):
            for obj in data.get("Objectives", []) if isinstance(data.get("Objectives"), list) else []:
                if isinstance(obj, dict):
                    objectives.append({"name": obj.get("Name"), "criteria": obj.get("CriteriaName"), "display": obj.get("DisplayName")})
            for team in data.get("Teams", []) if isinstance(data.get("Teams"), list) else []:
                if isinstance(team, dict):
                    teams.append({"name": team.get("Name"), "display": team.get("DisplayName")})
        return {"objectives": objectives, "teams": teams}
    except Exception as exc:
        return {"objectives": [], "teams": [], "error": str(exc)}


def safe_extract_world(zip_path: Path, target: Path) -> Path:
    with zipfile.ZipFile(zip_path) as zf:
        for info in zf.infolist():
            if info.is_dir():
                continue
            dest = (target / info.filename).resolve()
            if target.resolve() not in dest.parents:
                raise SystemExit(f"unsafe ZIP path: {info.filename}")
        zf.extractall(target)

    level_files = list(target.rglob("level.dat"))
    if not level_files:
        raise SystemExit("ZIP contains no level.dat")
    level_files.sort(key=lambda value: len(value.parts))
    return level_files[0].parent


def audit(world: Path) -> dict[str, Any]:
    command_blocks, region_errors = scan_regions(world)
    functions = scan_functions(world)
    scoreboard = scan_scoreboard(world)
    return {
        "schemaVersion": 1,
        "world": world.name,
        "commandBlockCount": len(command_blocks),
        "functionFileCount": len(functions),
        "functionCommandCount": sum(len(item["commands"]) for item in functions),
        "commandBlocks": command_blocks,
        "functions": functions,
        "scoreboard": scoreboard,
        "regionErrors": region_errors,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Audit a Minecraft Java world for command-driven gameplay")
    parser.add_argument("world", type=Path, help="world directory or ZIP archive")
    parser.add_argument("--output", type=Path, help="write JSON report to this path")
    args = parser.parse_args()

    source = args.world.expanduser().resolve()
    if not source.exists():
        raise SystemExit(f"world not found: {source}")

    temp: tempfile.TemporaryDirectory[str] | None = None
    try:
        if source.is_file():
            temp = tempfile.TemporaryDirectory(prefix="ggo-world-audit-")
            world = safe_extract_world(source, Path(temp.name))
        else:
            world = source
            if not (world / "level.dat").is_file():
                raise SystemExit("directory does not look like a Minecraft world (level.dat missing)")
        report = audit(world)
    finally:
        if temp is not None:
            temp.cleanup()

    rendered = json.dumps(report, ensure_ascii=False, indent=2)
    if args.output:
        args.output.write_text(rendered + "\n", encoding="utf-8")
        print(f"report: {args.output}")
    else:
        print(rendered)
    print(f"command blocks: {report['commandBlockCount']}", file=__import__('sys').stderr)
    print(f"datapack commands: {report['functionCommandCount']}", file=__import__('sys').stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
