#!/usr/bin/env python3
"""Fail CI when obviously sensitive production material is accidentally committed.

This is intentionally conservative and deterministic; it is not a replacement for GitHub secret
scanning or credential rotation. It prevents the most damaging GGO-specific mistakes from landing
in tracked source/artifacts.
"""
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
tracked = subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT).decode().split("\0")
tracked = [p for p in tracked if p]

forbidden_names = {"auth.db", ".env", "id_rsa", "id_ed25519"}
text_patterns = [
    ("private_key", re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----")),
    ("server_key_literal", re.compile(r"GGO_SERVER_KEY\s*=\s*['\"]?[A-Za-z0-9_./+\-=]{16,}")),
    ("game_ticket_literal", re.compile(r"GGO_GAME_TICKET\s*=\s*['\"]?[A-Za-z0-9_\-]{32,}")),
    ("bearer_literal", re.compile(r"Authorization\s*:\s*Bearer\s+[A-Za-z0-9._\-]{24,}", re.I)),
]

violations = []
for rel in tracked:
    path = ROOT / rel
    name = path.name
    if name in forbidden_names and not name.endswith(".example"):
        violations.append(f"forbidden tracked secret file: {rel}")
        continue
    try:
        if path.stat().st_size > 2_000_000:
            continue
        data = path.read_text(encoding="utf-8")
    except (UnicodeDecodeError, OSError):
        continue
    for label, pattern in text_patterns:
        if pattern.search(data):
            violations.append(f"{label}: {rel}")

if violations:
    print("GGO repository security gate: FAILED", file=sys.stderr)
    for item in violations:
        print(f" - {item}", file=sys.stderr)
    print("Rotate any exposed credential; deleting it from the latest commit is not sufficient.", file=sys.stderr)
    raise SystemExit(1)

print(f"GGO repository security gate: PASS ({len(tracked)} tracked paths checked)")
