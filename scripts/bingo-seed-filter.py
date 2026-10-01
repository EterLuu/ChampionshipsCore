#!/usr/bin/env python3
"""Pick a Bingo seed by sampling Overworld biomes around spawn.

The query executable is mc-worldgen-seed-lab's mcquery with the companion
--step patch. stdout is intentionally a single signed seed for the Worker;
diagnostics go to stderr.
"""

from __future__ import annotations

import argparse
import random
import subprocess
import sys
import time
from pathlib import Path


def arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--query", required=True, help="step-enabled mcquery executable")
    parser.add_argument("--radius-blocks", type=int, default=2000)
    parser.add_argument("--candidates", type=int, default=128)
    parser.add_argument("--sample-step-blocks", type=int, default=32)
    parser.add_argument("--version", default="26.2")
    parser.add_argument("--budget-seconds", type=float, default=13.5)
    parser.add_argument("--random-seed", type=int)
    return parser.parse_args()


def score(query: str, version: str, seed: int, radius: int, step: int, deadline: float) -> int:
    quart_radius = radius // 4
    quart_step = step // 4
    side = (quart_radius * 2) // quart_step + 1
    command = [
        query, version, "ow", str(seed), str(-quart_radius), str(-quart_radius),
        str(side), str(side), "16", "--step", str(quart_step),
    ]
    remaining = deadline - time.monotonic()
    if remaining <= 0:
        raise TimeoutError
    query_path = Path(query).expanduser().resolve()
    query_root = query_path.parent.parent if query_path.parent.name == "tools" else None
    result = subprocess.run(command, check=True, capture_output=True, text=True, timeout=remaining,
                            cwd=query_root)
    biomes: set[str] = set()
    for line in result.stdout.splitlines():
        fields = line.split()
        if len(fields) != 3:
            continue
        try:
            x = int(fields[0]) * 4
            z = int(fields[1]) * 4
        except ValueError:
            continue
        if x * x + z * z <= radius * radius:
            biomes.add(fields[2])
    return len(biomes)


def main() -> int:
    args = arguments()
    if args.radius_blocks < 4 or args.radius_blocks % 4:
        raise SystemExit("--radius-blocks must be a positive multiple of 4")
    if args.sample_step_blocks < 4 or args.sample_step_blocks % 4:
        raise SystemExit("--sample-step-blocks must be a positive multiple of 4")
    if args.candidates < 1:
        raise SystemExit("--candidates must be positive")

    rng = random.Random(args.random_seed) if args.random_seed is not None else random.SystemRandom()
    deadline = time.monotonic() + args.budget_seconds
    best_seed: int | None = None
    best_score = -1
    completed = 0
    for _ in range(args.candidates):
        seed = rng.getrandbits(64)
        if seed >= 1 << 63:
            seed -= 1 << 64
        try:
            current_score = score(args.query, args.version, seed, args.radius_blocks,
                                  args.sample_step_blocks, deadline)
        except subprocess.TimeoutExpired:
            break
        except subprocess.CalledProcessError as failure:
            print(f"mcquery failed with status {failure.returncode}", file=sys.stderr)
            return 1
        except TimeoutError:
            break
        completed += 1
        if current_score > best_score:
            best_seed, best_score = seed, current_score

    if best_seed is None:
        print("seed filter produced no completed candidate", file=sys.stderr)
        return 1
    print(f"seed filter completed: {completed} candidates, {best_score} biomes", file=sys.stderr)
    print(best_seed)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
