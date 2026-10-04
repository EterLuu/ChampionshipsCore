#!/usr/bin/env python3
"""Apply the individually reviewed 2026-09-20 Build Mart floor edits once.

No floor is detected or deleted heuristically. Every before/after document is
recorded in the adjacent manifest; unknown or subsequently edited files fail
the complete preflight before any file is written. Re-running is a no-op.
"""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import tempfile


MANIFEST = Path(__file__).with_name("buildmart-floor-2026-09-20.json")


def migrate(directory: Path, entries: list[dict], apply: bool = False) -> list[str]:
    pending: list[tuple[Path, bytes]] = []
    for entry in entries:
        name = entry["file"]
        if Path(name).name != name or not name.endswith(".yml"):
            raise ValueError(f"Invalid blueprint filename: {name}")
        path = directory / name
        current = path.read_bytes()
        after = entry["after"].encode("utf-8")
        if current == after:
            continue
        if current != entry["before"].encode("utf-8"):
            raise ValueError(f"Blueprint has changed since review; no files written: {path}")
        pending.append((path, after))
    if apply:
        for path, data in pending:
            temporary: str | None = None
            try:
                with tempfile.NamedTemporaryFile(
                    dir=directory, prefix=".floor-migration-", delete=False
                ) as out:
                    temporary = out.name
                    os.fchmod(out.fileno(), path.stat().st_mode & 0o777)
                    out.write(data)
                    out.flush()
                    os.fsync(out.fileno())
                os.replace(temporary, path)
            finally:
                if temporary and os.path.exists(temporary):
                    os.unlink(temporary)
    return [path.name for path, _ in pending]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("blueprints", type=Path)
    parser.add_argument(
        "--apply", action="store_true", help="Write the reviewed changes (default: preflight only)"
    )
    parser.add_argument(
        "--manifest", type=Path, default=MANIFEST, help="Reviewed before/after manifest"
    )
    args = parser.parse_args()
    entries = json.loads(args.manifest.read_text(encoding="utf-8"))["changes"]
    pending = migrate(args.blueprints, entries, args.apply)
    print(
        f"{'Applied' if args.apply else 'Pending'}: {len(pending)} / {len(entries)} reviewed blueprints"
    )
    for name in pending:
        print(name)


if __name__ == "__main__":
    main()
