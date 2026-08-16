#!/usr/bin/env python3
"""Print the release-notes section for a version from the changelog.

Usage: extract-release-notes.py VERSION [changelog_path]

Prints the body of the `## v<VERSION>` (or `## <VERSION>`) section, or
nothing when the section is missing/empty — callers fall back to the
commit log. Shared by the release workflow and publish-update.sh.
"""
import re
import sys


def section_for(version: str, text: str) -> str:
    # Exact version match only ("## v2.0.0" or "## 2.0.0"), bounded by the
    # next "## " heading or end of file. Negative lookaheads keep 2.0 from
    # matching 2.0.1 and reject suffixed headings like "## v2.0.0-rc1".
    pattern = re.compile(
        r"^## (?:v)?" + re.escape(version) + r"(?=\s|$)(.*?)(?=^## |\Z)",
        re.M | re.S,
    )
    match = pattern.search(text)
    return match.group(1).strip() if match else ""


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__, file=sys.stderr)
        return 2
    version = sys.argv[1]
    path = sys.argv[2] if len(sys.argv) > 2 else "assets/changelog/changelog.md"
    try:
        with open(path, encoding="utf-8") as fh:
            text = fh.read()
    except FileNotFoundError:
        return 0
    section = section_for(version, text)
    if section:
        sys.stdout.write(section + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
