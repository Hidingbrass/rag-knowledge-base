#!/usr/bin/env python3
"""Validate local Markdown links in README and docs."""

from __future__ import annotations

import re
import sys
from pathlib import Path
from urllib.parse import unquote


ROOT = Path(__file__).resolve().parents[1]
TARGETS = [ROOT / "README.md", ROOT / "docs"]
LINK_RE = re.compile(r"!?\[[^\]]+\]\(([^)]+)\)")
FENCE_RE = re.compile(r"```.*?```", re.DOTALL)
SKIP_PREFIXES = ("http://", "https://", "mailto:", "#")


def iter_markdown_files() -> list[Path]:
    files: list[Path] = []
    for target in TARGETS:
        if target.is_file():
            files.append(target)
        elif target.is_dir():
            files.extend(sorted(target.rglob("*.md")))
    return files


def strip_optional_title(raw_link: str) -> str:
    link = raw_link.strip()
    if link.startswith("<") and ">" in link:
        return link[1 : link.index(">")]
    return link.split()[0] if link else link


def is_external_or_anchor(link: str) -> bool:
    return not link or link.startswith(SKIP_PREFIXES)


def local_path_for(markdown_file: Path, link: str) -> Path:
    link_without_anchor = link.split("#", 1)[0]
    decoded = unquote(link_without_anchor)
    return (markdown_file.parent / decoded).resolve()


def main() -> int:
    errors: list[str] = []

    for markdown_file in iter_markdown_files():
        text = markdown_file.read_text(encoding="utf-8")
        text_without_fences = FENCE_RE.sub("", text)
        for match in LINK_RE.finditer(text_without_fences):
            link = strip_optional_title(match.group(1))
            if is_external_or_anchor(link):
                continue
            if not local_path_for(markdown_file, link).exists():
                rel_file = markdown_file.relative_to(ROOT)
                errors.append(f"{rel_file}: broken local link: {link}")

    if errors:
        print("Broken Markdown links found:")
        for error in errors:
            print(f"- {error}")
        return 1

    print("Markdown local links passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
