#!/usr/bin/env python3
"""Write the in-app self-update feed (update.json) for a release.

Usage: update-feed.py --code N --display NAME --asset FILE --sha256 HEX
       --bytes N --notes-file PATH

Notes are read from a file (safe for markdown content) and truncated to
4000 characters. This legacy feed is generated only for GitHub bridge releases
through 3.4.7; installed versions from 3.4.7 onward read the Update Ledger
appcast instead.
"""
import argparse
import json
import sys


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--code", type=int, required=True, help="versionCode")
    parser.add_argument("--display", required=True, help="versionName / display name")
    parser.add_argument("--asset", required=True, help="APK release-asset file name")
    parser.add_argument("--sha256", required=True, help="lowercase hex SHA-256 of the APK")
    parser.add_argument("--bytes", type=int, required=True, help="exact APK size")
    parser.add_argument("--notes-file", required=True, help="path to release notes")
    parser.add_argument("--min-code", type=int, default=0, help="force-update floor")
    args = parser.parse_args()

    with open(args.notes_file, encoding="utf-8") as fh:
        notes = fh.read().strip()

    feed = {
        "versionCode": args.code,
        "versionName": args.display,
        "apkAsset": args.asset,
        "sha256": args.sha256,
        "bytes": args.bytes,
        "notes": notes[:4000],
        "minVersionCode": args.min_code,
    }
    json.dump(feed, sys.stdout, indent=2)
    print()
    return 0


if __name__ == "__main__":
    sys.exit(main())
