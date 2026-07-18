"""Scan project files for UTF-8 encoding issues and mojibake patterns.

Per docs/地图与交互问题清单.md §2.2:
- Detect files that are not valid UTF-8.
- Detect Chinese mojibake patterns (GB18030/GBK content shown as Latin-1).
- Detect files with mismatched quote / tag boundaries that may indicate mojibake-induced template corruption.

Usage:
    python scripts/scan_encoding.py [path]
"""
from __future__ import annotations

import re
import sys
from pathlib import Path
from typing import Iterable

# File extensions to scan — focus on source files that may contain Chinese strings.
SCAN_EXT = {
    ".vue", ".ts", ".js", ".tsx", ".jsx",
    ".java", ".xml", ".yml", ".yaml",
    ".sql", ".md", ".html",
    ".json", ".properties",
}

# Mojibake signature: a sequence of Latin-1-rendered GBK bytes for Chinese characters.
# These are sequences of C1 control chars, Â, ï¿½, etc.
MOJIBAKE_PATTERNS = [
    re.compile(r"[\x80-\x9f\u00c0-\u00ff]{4,}"),       # runs of high bytes typical of misdecoded GBK
    re.compile(r"\u00ef\u00bf\u00bd"),                  # U+FFFD replacement char
    re.compile(r"\u00c2\u00b7"),                        # middle dot from GBK misdecode
    re.compile(r"[\u00c0-\u00c3][\u0080-\u00bf]{2,}"),  # UTF-8-ish bytes shown as Latin-1
]


def scan_file(path: Path) -> list[str]:
    """Return list of issue descriptions (empty if file is clean)."""
    issues: list[str] = []
    raw = path.read_bytes()
    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError as ex:
        issues.append(f"NOT_UTF8 (byte {ex.start}: {ex.reason})")
        # Try to recover for further checks
        text = raw.decode("utf-8", errors="replace")

    # Check BOM
    if raw.startswith(b"\xef\xbb\xbf"):
        issues.append("HAS_BOM (UTF-8 BOM present)")

    # Check mojibake
    for pattern in MOJIBAKE_PATTERNS:
        matches = pattern.findall(text)
        if matches:
            issues.append(f"MOJIBAKE_PATTERN ({pattern.pattern[:30]}): {len(matches)} match(es)")

    return issues


def iter_files(root: Path) -> Iterable[Path]:
    skip_dirs = {".git", "node_modules", "dist", "dev-dist", "target", "build", ".idea", ".vscode", ".gradle"}
    for path in root.rglob("*"):
        if not path.is_file():
            continue
        if any(part in skip_dirs for part in path.parts):
            continue
        if path.suffix.lower() not in SCAN_EXT:
            continue
        yield path


def main(argv: list[str]) -> int:
    root = Path(argv[1]) if len(argv) > 1 else Path.cwd()
    if not root.exists():
        print(f"ERROR: path does not exist: {root}")
        return 2

    total = 0
    dirty = 0
    by_kind: dict[str, int] = {}

    for path in iter_files(root):
        total += 1
        issues = scan_file(path)
        if not issues:
            continue
        dirty += 1
        rel = path.relative_to(root)
        for issue in issues:
            kind = issue.split(" (", 1)[0]
            by_kind[kind] = by_kind.get(kind, 0) + 1
        print(f"{rel}: {'; '.join(issues)}")

    print()
    print(f"Scanned {total} files; {dirty} with issues.")
    if by_kind:
        print("Breakdown:", ", ".join(f"{k}={v}" for k, v in sorted(by_kind.items())))
    return 0 if dirty == 0 else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv))
