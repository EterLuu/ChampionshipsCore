#!/usr/bin/env python3
"""Remove the waxed_ prefix from block-state palette entries in Sponge schematics."""

from __future__ import annotations

import argparse
import gzip
import io
import os
import struct
import tempfile
from pathlib import Path


def read_exact(stream: io.BytesIO, size: int) -> bytes:
    value = stream.read(size)
    if len(value) != size:
        raise ValueError("truncated NBT payload")
    return value


def number(stream: io.BytesIO, fmt: str):
    return struct.unpack(">" + fmt, read_exact(stream, struct.calcsize(">" + fmt)))[0]


def nbt_string(stream: io.BytesIO) -> str:
    return read_exact(stream, number(stream, "H")).decode("utf-8")


def read_payload(stream: io.BytesIO, tag: int):
    if tag == 1:
        return number(stream, "b")
    if tag == 2:
        return number(stream, "h")
    if tag == 3:
        return number(stream, "i")
    if tag == 4:
        return number(stream, "q")
    if tag == 5:
        return number(stream, "f")
    if tag == 6:
        return number(stream, "d")
    if tag == 7:
        return read_exact(stream, number(stream, "i"))
    if tag == 8:
        return nbt_string(stream)
    if tag == 9:
        element = number(stream, "B")
        return element, [read_payload(stream, element) for _ in range(number(stream, "i"))]
    if tag == 10:
        entries = []
        while (child := number(stream, "B")) != 0:
            entries.append((nbt_string(stream), child, read_payload(stream, child)))
        return entries
    if tag == 11:
        return [number(stream, "i") for _ in range(number(stream, "i"))]
    if tag == 12:
        return [number(stream, "q") for _ in range(number(stream, "i"))]
    raise ValueError(f"unsupported NBT tag {tag}")


def write_number(stream: io.BytesIO, fmt: str, value) -> None:
    stream.write(struct.pack(">" + fmt, value))


def write_string(stream: io.BytesIO, value: str) -> None:
    encoded = value.encode("utf-8")
    write_number(stream, "H", len(encoded))
    stream.write(encoded)


def write_payload(stream: io.BytesIO, tag: int, value) -> None:
    if tag in {1, 2, 3, 4, 5, 6}:
        write_number(stream, {1: "b", 2: "h", 3: "i", 4: "q", 5: "f", 6: "d"}[tag], value)
    elif tag == 7:
        write_number(stream, "i", len(value)); stream.write(value)
    elif tag == 8:
        write_string(stream, value)
    elif tag == 9:
        element, values = value
        write_number(stream, "B", element); write_number(stream, "i", len(values))
        for child in values:
            write_payload(stream, element, child)
    elif tag == 10:
        names = [name for name, _, _ in value]
        if len(names) != len(set(names)):
            raise ValueError("normalization would create duplicate NBT compound keys")
        for name, child_tag, child in value:
            write_number(stream, "B", child_tag); write_string(stream, name)
            write_payload(stream, child_tag, child)
        write_number(stream, "B", 0)
    elif tag in {11, 12}:
        write_number(stream, "i", len(value))
        for child in value:
            write_number(stream, "i" if tag == 11 else "q", child)
    else:
        raise ValueError(f"unsupported NBT tag {tag}")


def normalize(value):
    changes = 0
    if isinstance(value, str):
        replaced = value.replace("minecraft:waxed_", "minecraft:")
        return replaced, int(replaced != value)
    if isinstance(value, tuple):
        element, children = value
        normalized = []
        for child in children:
            result, count = normalize(child)
            normalized.append(result); changes += count
        return (element, normalized), changes
    if isinstance(value, list) and value and isinstance(value[0], tuple) and len(value[0]) == 3:
        entries = []
        for name, tag, child in value:
            new_name = name.replace("minecraft:waxed_", "minecraft:")
            result, count = normalize(child)
            entries.append((new_name, tag, result))
            changes += count + int(new_name != name)
        return entries, changes
    if isinstance(value, list):
        normalized = []
        for child in value:
            result, count = normalize(child)
            normalized.append(result); changes += count
        return normalized, changes
    return value, 0


def migrate(path: Path) -> int:
    raw = gzip.decompress(path.read_bytes())
    stream = io.BytesIO(raw)
    root_tag = number(stream, "B")
    if root_tag == 0:
        raise ValueError("root tag may not be END")
    root_name = nbt_string(stream)
    root = read_payload(stream, root_tag)
    if stream.read(1):
        raise ValueError("unexpected trailing NBT data")
    normalized, changes = normalize(root)
    if not changes:
        return 0
    output = io.BytesIO()
    write_number(output, "B", root_tag); write_string(output, root_name)
    write_payload(output, root_tag, normalized)
    compressed = gzip.compress(output.getvalue(), mtime=0)
    fd, temporary = tempfile.mkstemp(prefix=path.name + ".", suffix=".tmp", dir=path.parent)
    try:
        with os.fdopen(fd, "wb") as handle:
            handle.write(compressed)
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)
    return changes


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path, help="schematic file or directory")
    args = parser.parse_args()
    paths = [args.input] if args.input.is_file() else sorted(args.input.rglob("*.schem"))
    files = changes = 0
    for path in paths:
        changed = migrate(path)
        if changed:
            files += 1; changes += changed
    print(f"schematic_files={files} palette_entries={changes}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
