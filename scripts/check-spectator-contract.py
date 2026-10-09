#!/usr/bin/env python3
"""Report direct spectator-mode changes outside the unified spectator manager.

The default mode is advisory so the existing codebase can adopt the check without
breaking builds. Use --strict after migrating all callers.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


SPECTATOR_CALL = re.compile(r"\bsetGameMode\s*\([^;]*?\bGameMode\.SPECTATOR\b[^;]*?\);")
VISIBILITY_CALL = re.compile(r"\b(?:hidePlayer|showPlayer|hideEntity|showEntity)\s*\([^;]*?\);")
VISIBILITY_OWNERS = {"PlayerVisibilityManager.java", "WorkerVisibilityManager.java"}
ALLOWED_NAMES = {"SpectatorManager.java", "SpectatorStateService.java"}


def find_violations(root: Path) -> list[tuple[Path, int, str]]:
    source_roots = [
        root / "championships-core/src/main/java",
        root / "championships-platform-bukkit/src/main/java",
        root / "championships-bingo-worker/src/main/java",
    ]
    violations: list[tuple[Path, int, str]] = []
    for source_root in source_roots:
        if not source_root.exists():
            continue
        for path in sorted(source_root.rglob("*.java")):
            text = path.read_text(encoding="utf-8")
            if path.name not in ALLOWED_NAMES:
                for match in SPECTATOR_CALL.finditer(text):
                    number = text.count("\n", 0, match.start()) + 1
                    violations.append((path.relative_to(root), number, " ".join(match[0].split())))
            if path.name not in VISIBILITY_OWNERS:
                for match in VISIBILITY_CALL.finditer(text):
                    call = " ".join(match[0].split())
                    # Ace Race's private name displays are TextDisplay entities, not player bodies.
                    if path.name == "AceRaceArea.java" and call in {
                        "showEntity(plugin, ensureRacerNameDisplay(target));",
                        "hideEntity(plugin, display);",
                    }:
                        continue
                    number = text.count("\n", 0, match.start()) + 1
                    violations.append((path.relative_to(root), number, call))
    return violations


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--strict", action="store_true")
    args = parser.parse_args()

    root = Path(__file__).resolve().parents[1]
    violations = find_violations(root)
    print(f"Spectator ownership violations: {len(violations)}")
    for path, number, line in violations:
        print(f"{path}:{number}: {line}")
    if violations and args.strict:
        return 1
    if violations:
        print("Advisory mode: migrate callers before enabling --strict.")
    else:
        print("Spectator contract check passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
