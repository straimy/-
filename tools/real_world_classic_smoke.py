#!/usr/bin/env python3
"""Disposable imported-world startup smoke for GunGloryOnline Classic Arena.

Inputs are never modified. The tool extracts a temporary server copy, replaces only the GGO Core
with the supplied hardening artifact, disables command blocks, binds the server to localhost, starts
Forge with -Dggo.classic.smoke=true, requires the exact startup-smoke PASS line and readiness marker,
and lets the one-shot smoke halt the disposable server itself.
"""

from __future__ import annotations

import argparse
from pathlib import Path
import queue
import shutil
import subprocess
import sys
import tarfile
import tempfile
import threading
import time
import zipfile

KEEP_PREFIXES = ("world/", "mods/", "config/", "libraries/", ".java/")
KEEP_FILES = {
    "server.properties", "eula.txt", "user_jvm_args.txt", "run.sh",
    "ops.json", "whitelist.json", "banned-players.json", "banned-ips.json",
    "usercache.json", "usernamecache.json",
}
SMOKE_PREFIX = "[GGO-CLASSIC-REALWORLD-SMOKE]"
READY_MARKER = ".ggo-classic-ready"


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser()
    p.add_argument("--server-archive", required=True, type=Path)
    p.add_argument("--hardening-artifact", required=True, type=Path)
    p.add_argument("--java", default="java")
    p.add_argument("--timeout", type=int, default=180)
    p.add_argument("--keep-workdir", action="store_true")
    return p.parse_args()


def safe_member(member: tarfile.TarInfo) -> bool:
    name = member.name.replace("\\", "/")
    if name.startswith("/") or ".." in Path(name).parts:
        return False
    return name in KEEP_FILES or any(name.startswith(prefix) for prefix in KEEP_PREFIXES)


def extract_server(archive: Path, target: Path) -> None:
    with tarfile.open(archive, "r:gz") as tf:
        members = [m for m in tf.getmembers() if safe_member(m)]
        tf.extractall(target, members=members, filter="data")
    if not (target / "world" / "level.dat").is_file():
        raise RuntimeError("imported archive has no world/level.dat")
    if not (target / "libraries" / "net" / "minecraftforge" / "forge" / "1.20.1-47.4.10" / "unix_args.txt").is_file():
        raise RuntimeError("imported archive is not the expected Forge 1.20.1 / 47.4.10 server")


def install_core(artifact: Path, target: Path) -> Path:
    mods = target / "mods"
    mods.mkdir(parents=True, exist_ok=True)
    for old in mods.glob("gungloryonline-core-*.jar"):
        old.unlink()
    with zipfile.ZipFile(artifact) as zf:
        names = [n for n in zf.namelist() if n.startswith("server/mods/") and n.endswith(".jar")]
        if len(names) != 1:
            raise RuntimeError(f"hardening artifact must contain exactly one server Core JAR, got {len(names)}")
        out = mods / "gungloryonline-core-realworld-smoke.jar"
        with zf.open(names[0]) as src, out.open("wb") as dst:
            shutil.copyfileobj(src, dst)
        return out


def rewrite_property(path: Path, key: str, value: str) -> None:
    lines = path.read_text(encoding="utf-8", errors="replace").splitlines() if path.exists() else []
    prefix = key + "="
    replaced = False
    out: list[str] = []
    for line in lines:
        if line.startswith(prefix):
            out.append(prefix + value)
            replaced = True
        else:
            out.append(line)
    if not replaced:
        out.append(prefix + value)
    path.write_text("\n".join(out) + "\n", encoding="utf-8")


def reader_thread(stream, lines: queue.Queue[str], transcript: list[str]) -> None:
    for raw in iter(stream.readline, ""):
        transcript.append(raw)
        lines.put(raw)


def wait_for(lines: queue.Queue[str], needle: str, deadline: float) -> str:
    while time.monotonic() < deadline:
        try:
            line = lines.get(timeout=min(0.5, max(0.01, deadline - time.monotonic())))
        except queue.Empty:
            continue
        if needle in line:
            return line
    raise TimeoutError(f"timed out waiting for {needle!r}")


def verify_readiness_marker(root: Path) -> str:
    marker = root / "world" / READY_MARKER
    if not marker.is_file():
        raise RuntimeError(f"startup smoke reported PASS but {READY_MARKER} was not written")
    text = marker.read_text(encoding="utf-8", errors="replace")
    required = ("result=PASS", "cells=64", "ammo=4/3/3", "health=4")
    missing = [entry for entry in required if entry not in text]
    if missing:
        raise RuntimeError(f"invalid {READY_MARKER}; missing {', '.join(missing)}")
    return text


def run_smoke(root: Path, java: str, timeout: int) -> None:
    (root / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    props = root / "server.properties"
    rewrite_property(props, "enable-command-block", "false")
    rewrite_property(props, "server-ip", "127.0.0.1")

    cmd = [
        java,
        "-Dggo.classic.smoke=true",
        "@user_jvm_args.txt",
        "@libraries/net/minecraftforge/forge/1.20.1-47.4.10/unix_args.txt",
        "nogui",
    ]
    proc = subprocess.Popen(
        cmd,
        cwd=root,
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        bufsize=1,
    )
    assert proc.stdin is not None and proc.stdout is not None
    q: queue.Queue[str] = queue.Queue()
    transcript: list[str] = []
    thread = threading.Thread(target=reader_thread, args=(proc.stdout, q, transcript), daemon=True)
    thread.start()
    deadline = time.monotonic() + timeout

    try:
        result = wait_for(q, SMOKE_PREFIX, deadline)
        print(result.rstrip())
        if "result=PASS" not in result:
            raise RuntimeError("Classic startup smoke returned FAIL")

        marker_text = verify_readiness_marker(root)
        print(f"{READY_MARKER}=PASS")
        for line in marker_text.splitlines():
            if line.startswith(("generator=", "cells=", "ammo=", "health=", "respawn=", "jumpPads=")):
                print(f"ready.{line}")

        remaining = max(1.0, deadline - time.monotonic())
        rc = proc.wait(timeout=remaining)
        if rc != 0:
            raise RuntimeError(f"server exited with {rc} after startup smoke PASS")
    finally:
        if proc.poll() is None:
            try:
                proc.stdin.write("stop\n")
                proc.stdin.flush()
                proc.wait(timeout=15)
            except Exception:
                proc.kill()
        log = root / "real-world-smoke-console.log"
        log.write_text("".join(transcript), encoding="utf-8")


def main() -> int:
    args = parse_args()
    for path in (args.server_archive, args.hardening_artifact):
        if not path.is_file():
            print(f"missing input: {path}", file=sys.stderr)
            return 2

    temp = Path(tempfile.mkdtemp(prefix="ggo-realworld-smoke-"))
    try:
        extract_server(args.server_archive, temp)
        core = install_core(args.hardening_artifact, temp)
        print(f"workdir={temp}")
        print(f"core={core.name}")
        print("enable-command-block=false")
        print("ggo.classic.smoke=true")
        run_smoke(temp, args.java, args.timeout)
        print("REAL_WORLD_CLASSIC_SMOKE=PASS")
        return 0
    except Exception as exc:
        print(f"REAL_WORLD_CLASSIC_SMOKE=FAIL: {exc}", file=sys.stderr)
        print(f"diagnostics={temp / 'real-world-smoke-console.log'}", file=sys.stderr)
        return 1
    finally:
        if not args.keep_workdir:
            shutil.rmtree(temp, ignore_errors=True)


if __name__ == "__main__":
    raise SystemExit(main())
