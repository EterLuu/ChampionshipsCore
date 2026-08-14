#!/usr/bin/env python3
"""Audit Build Mart blueprint difficulty and resource-hall coverage.

The script intentionally uses only Python's standard library. It accepts either one
blueprint YAML file or a directory of YAML files, consumes the generated Build Mart
material manifest, and can emit Markdown, CSV, and JSON reports.
"""

from __future__ import annotations

import argparse
import csv
import json
import math
import re
from collections import Counter, deque
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Iterable


BLOCK_RE = re.compile(
    r"^\s*-\s*(-?\d+)\s*,\s*(-?\d+)\s*,\s*(-?\d+)\s*=\s*"
    r"(?:minecraft:)?([a-z0-9_]+)(?:\[([^]]*)\])?\s*$"
)
COUNT_RE = re.compile(r"^\s{4}minecraft:([^:\[]+)(?:\[[^]]*\])?:\s*(\d+)\s*$")
COORD_RE = re.compile(r"^\s{4}([xyz]):\s*(-?[\d.]+)\s*$")

DIRECTION_KEYS = {"axis", "face", "facing", "half", "hinge", "orientation", "rotation", "shape", "type"}
COMPLEX_KEYS = DIRECTION_KEYS | {
    "attached", "hanging", "in_wall", "open", "part", "side_chain", "signal_fire", "waterlogged"
}
IGNORED_KEYS = {"age"}
CONNECTABLE_SUFFIXES = ("_pane", "_bars", "_wall")
WOOD_TYPES = (
    "pale_oak", "dark_oak", "mangrove", "cherry", "spruce", "birch", "jungle", "acacia", "oak",
    "bamboo", "crimson", "warped",
)
STONE_BASES = (
    "polished_blackstone_bricks", "mossy_stone_bricks", "mossy_cobblestone", "deepslate_bricks",
    "deepslate_tiles", "polished_deepslate", "end_stone_bricks", "prismarine_bricks", "dark_prismarine",
    "smooth_red_sandstone", "red_sandstone", "smooth_sandstone", "sandstone", "smooth_quartz",
    "quartz_block", "polished_andesite", "polished_diorite", "polished_granite", "polished_tuff",
    "mud_bricks", "nether_bricks", "stone_bricks", "blackstone", "cobblestone", "prismarine",
    "andesite", "diorite", "granite", "deepslate", "tuff", "bricks", "stone", "purpur_block",
)
COLORS = (
    "light_blue", "light_gray", "white", "orange", "magenta", "yellow", "lime", "pink", "gray",
    "cyan", "purple", "blue", "brown", "green", "red", "black",
)


@dataclass(frozen=True)
class Block:
    x: int
    y: int
    z: int
    material: str
    properties: dict[str, str]


@dataclass
class Audit:
    blueprint: str
    configured_stars: int
    suggested_stars: int
    difficulty_score: float
    segment_assessment: str
    blocks: int
    unique_materials: int
    dimensions: str
    fill_percent: float
    height: int
    components: int
    stateful_blocks: int
    directional_blocks: int
    complex_state_blocks: int
    strict_connectable_blocks: int
    material_regions: int
    region_names: str
    direct_materials: int
    craftable_materials: int
    uncovered_materials: int
    uncovered_list: str
    coverage: str
    warnings: str


def without_copper_wax(material: str) -> str:
    return material.removeprefix("waxed_")


def parse_properties(raw: str | None) -> dict[str, str]:
    result: dict[str, str] = {}
    if not raw:
        return result
    for item in raw.split(","):
        if "=" in item:
            key, value = item.split("=", 1)
            result[key.strip()] = value.strip()
    return result


def parse_blueprint(path: Path) -> tuple[str, int, list[Block], list[str]]:
    name = path.stem
    stars = 1
    blocks: list[Block] = []
    invalid: list[str] = []
    in_blocks = False
    for line_number, line in enumerate(path.read_text(encoding="utf-8-sig").splitlines(), 1):
        stripped = line.strip()
        if stripped.startswith("name:"):
            name = stripped.split(":", 1)[1].strip().strip("\"'") or path.stem
        elif stripped.startswith("stars:"):
            try:
                stars = int(stripped.split(":", 1)[1].strip())
            except ValueError:
                invalid.append(f"line {line_number}: invalid stars")
        elif stripped == "blocks:":
            in_blocks = True
        elif in_blocks and stripped.startswith("-"):
            match = BLOCK_RE.match(line)
            if not match:
                invalid.append(f"line {line_number}: invalid block entry")
                continue
            x, y, z = (int(match.group(i)) for i in range(1, 4))
            blocks.append(Block(x, y, z, without_copper_wax(match.group(4)), parse_properties(match.group(5))))
    return name, stars, blocks, invalid


def parse_manifest(path: Path) -> tuple[dict[str, int], list[dict]]:
    totals: dict[str, int] = {}
    zones: list[dict] = []
    current: dict | None = None
    section = ""
    in_totals = False
    in_total_materials = False
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        if line == "totals:":
            if current:
                zones.append(current)
                current = None
            in_totals = True
            section = ""
            continue
        if in_totals:
            if line == "  materials:":
                in_total_materials = True
                continue
            if line == "  block-data:":
                in_total_materials = False
                continue
            if in_total_materials and (match := COUNT_RE.match(line)):
                material = without_copper_wax(match.group(1))
                totals[material] = totals.get(material, 0) + int(match.group(2))
            continue
        if line.startswith("- snapshot-id:"):
            if current:
                zones.append(current)
            current = {"pos1": {}, "pos2": {}, "materials": set()}
            section = ""
        elif current is not None:
            if line == "  pos1:":
                section = "pos1"
            elif line == "  pos2:":
                section = "pos2"
            elif line == "  materials:":
                section = "materials"
            elif line == "  block-data:":
                section = "block-data"
            elif section in {"pos1", "pos2"} and (match := COORD_RE.match(line)):
                current[section][match.group(1)] = float(match.group(2))
            elif section == "materials" and (match := COUNT_RE.match(line)):
                current["materials"].add(without_copper_wax(match.group(1)))
    return totals, zones


def parse_islands(path: Path) -> list[tuple[str, float, float]]:
    islands: list[tuple[str, float, float]] = []
    current_name: str | None = None
    coordinates: dict[str, float] = {}
    active = False
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        if line == "material-islands:":
            active = True
            continue
        if active and line == "material-zones:":
            break
        if not active:
            continue
        if match := re.match(r"^- id:\s*(.+?)\s*$", line):
            if current_name and "x" in coordinates and "z" in coordinates:
                islands.append((current_name, coordinates["x"], coordinates["z"]))
            current_name = match.group(1)
            coordinates = {}
        elif match := COORD_RE.match(line):
            coordinates[match.group(1)] = float(match.group(2))
    if current_name and "x" in coordinates and "z" in coordinates:
        islands.append((current_name, coordinates["x"], coordinates["z"]))
    return islands


def material_regions(zones: list[dict], islands: list[tuple[str, float, float]]) -> dict[str, set[str]]:
    result: dict[str, set[str]] = {}
    if not islands:
        return result
    for zone in zones:
        if not all(axis in zone[corner] for corner in ("pos1", "pos2") for axis in ("x", "z")):
            continue
        x = (zone["pos1"]["x"] + zone["pos2"]["x"]) / 2
        z = (zone["pos1"]["z"] + zone["pos2"]["z"]) / 2
        island = min(islands, key=lambda row: (row[1] - x) ** 2 + (row[2] - z) ** 2)[0]
        for material in zone["materials"]:
            result.setdefault(material, set()).add(island)
    return result


def wood_source(material: str, available: set[str]) -> set[str] | None:
    for wood in WOOD_TYPES:
        if not (material.startswith(wood + "_") or material.startswith("stripped_" + wood + "_")):
            continue
        if wood == "bamboo":
            options = ("bamboo_block", "bamboo")
        elif wood in {"crimson", "warped"}:
            options = (wood + "_stem",)
        else:
            options = (wood + "_log",)
        for source in options:
            if source in available:
                return {source}
    return None


def stone_source(material: str, available: set[str]) -> set[str] | None:
    aliases = (
        ("polished_blackstone", "blackstone"),
        ("mossy_stone_brick", "stone_bricks"),
        ("deepslate_brick", "deepslate"),
        ("deepslate_tile", "deepslate"),
        ("end_stone_brick", "end_stone"),
        ("quartz", "quartz_block"),
        ("purpur", "purpur_block"),
        ("mud_brick", "mud_bricks"),
        ("nether_brick", "nether_bricks"),
        ("brick", "bricks"),
        ("smooth_stone", "stone"),
    )
    for prefix, source in aliases:
        if (material == prefix or material.startswith(prefix + "_")) and source in available:
            return {source}
    for base in STONE_BASES:
        if material == base or material.startswith(base + "_"):
            if base in available:
                return {base}
            fallback = {
                "smooth_quartz": "quartz_block", "quartz_block": "quartz_block",
                "smooth_red_sandstone": "red_sand", "red_sandstone": "red_sand",
                "smooth_sandstone": "sand", "sandstone": "sand",
                "polished_blackstone_bricks": "blackstone", "blackstone": "blackstone",
                "mossy_cobblestone": "cobblestone", "mossy_stone_bricks": "stone_bricks",
                "deepslate_bricks": "deepslate", "deepslate_tiles": "deepslate",
                "polished_deepslate": "deepslate", "end_stone_bricks": "end_stone",
                "polished_andesite": "andesite", "polished_diorite": "diorite",
                "polished_granite": "granite", "polished_tuff": "tuff",
            }.get(base)
            if fallback in available:
                return {fallback}
    return None


def copper_stage(material: str) -> str | None:
    if (material == "copper_block" or material.startswith("copper_")
            or material.startswith("cut_copper") or material.startswith("chiseled_copper")):
        return ""
    for stage in ("exposed_", "weathered_", "oxidized_"):
        if (material.startswith(stage + "copper") or material.startswith(stage + "cut_copper")
                or material.startswith(stage + "chiseled_copper")):
            return stage
    return None


def is_stonecut_copper(material: str, stage: str) -> bool:
    return material in {
        stage + "cut_copper", stage + "cut_copper_slab", stage + "cut_copper_stairs",
        stage + "chiseled_copper", stage + "copper_grate",
    }


def copper_sources(material: str, available: set[str]) -> set[str] | None:
    """Resolve only immediate crafting/stonecutting/waxing paths; never assume match-time oxidation."""
    if material in available:
        return {material}
    if material.startswith("waxed_"):
        unwaxed = material.removeprefix("waxed_")
        stage = copper_stage(unwaxed)
        if stage is not None and is_stonecut_copper(unwaxed, stage):
            waxed_base = "waxed_copper_block" if not stage else "waxed_" + stage + "copper"
            if waxed_base in available:
                return {waxed_base}
        if "honeycomb" not in available:
            return None
        sources = copper_sources(unwaxed, available)
        return sources | {"honeycomb"} if sources else None

    stage = copper_stage(material)
    if stage is not None and is_stonecut_copper(material, stage):
        base = "copper_block" if not stage else stage + "copper"
        if base in available:
            return {base}
    if material in {"copper_bars", "copper_chain", "copper_trapdoor", "copper_door", "lightning_rod"}:
        return {"copper_block"} if "copper_block" in available else None
    if material == "copper_chest":
        return {"copper_block", "oak_log"} if {"copper_block", "oak_log"} <= available else None
    if material == "copper_lantern":
        sources = {"copper_block", "coal_block", "oak_log"}
        return sources if sources <= available else None
    return None


def recipe_sources(material: str, available: set[str]) -> set[str] | None:
    """Return resource-hall source blocks for a known vanilla crafting/conversion path."""
    if material in available:
        return {material}
    if source := wood_source(material, available):
        return source
    if source := stone_source(material, available):
        return source

    if material.endswith("_stained_glass_pane"):
        base = material.removesuffix("_pane")
        return {base} if base in available else None
    if material == "glass_pane" and "glass" in available:
        return {"glass"}
    if material.endswith("_carpet"):
        base = material.removesuffix("_carpet") + "_wool"
        if base in available:
            return {base}
    if material.endswith(("_banner", "_wall_banner")):
        color = next((color for color in COLORS if material.startswith(color + "_")), None)
        return {color + "_wool", "oak_log"} if color and color + "_wool" in available else None
    if material.endswith("_bed"):
        color = next((color for color in COLORS if material.startswith(color + "_")), None)
        return {color + "_wool", "oak_log"} if color and color + "_wool" in available else None

    fixed: dict[str, set[str]] = {
        "anvil": {"iron_block"}, "cauldron": {"iron_block"}, "hopper": {"iron_block", "oak_log"},
        "iron_bars": {"iron_block"}, "iron_chain": {"iron_block"}, "iron_trapdoor": {"iron_block"},
        "heavy_weighted_pressure_plate": {"iron_block"}, "light_weighted_pressure_plate": {"gold_block"},
        "lantern": {"iron_block", "coal_block", "oak_log"}, "rail": {"iron_block", "oak_log"},
        "tripwire_hook": {"iron_block", "oak_log"}, "lever": {"stone", "oak_log"},
        "redstone_torch": {"redstone_block", "oak_log"}, "redstone_lamp": {"redstone_block", "glowstone"},
        "comparator": {"redstone_block", "quartz_block", "stone"},
        "note_block": {"redstone_block", "oak_log"}, "jukebox": {"diamond_block", "oak_log"},
        "target": {"redstone_block", "hay_block"}, "crafter": {"redstone_block", "iron_block", "stone", "oak_log"},
        "furnace": {"stone"}, "blast_furnace": {"stone", "iron_block"},
        "smoker": {"stone", "oak_log"}, "grindstone": {"stone", "oak_log"},
        "stonecutter": {"stone", "iron_block"}, "campfire": {"coal_block", "oak_log"},
        "barrel": {"oak_log"}, "chest": {"oak_log"}, "crafting_table": {"oak_log"},
        "composter": {"oak_log"}, "ladder": {"oak_log"}, "flower_pot": {"clay"},
        "decorated_pot": {"clay"}, "piston_head": {"stone", "oak_log", "iron_block", "redstone_block"},
        "sticky_piston": {"stone", "oak_log", "iron_block", "redstone_block", "slime_block"},
        "carved_pumpkin": {"pumpkin"}, "moss_carpet": {"moss_block"},
    }
    if material in fixed:
        return fixed[material] if fixed[material] <= available else None
    if material.startswith("potted_"):
        plant = material.removeprefix("potted_")
        return {"clay", plant} if {"clay", plant} <= available else None
    if "copper" in material or material.endswith("lightning_rod"):
        return copper_sources(material, available)
    if material == "dirt_path":
        source = next((candidate for candidate in ("dirt", "grass_block") if candidate in available), None)
        return {source} if source else None
    if material.startswith("chiseled_") or material.startswith("cracked_"):
        tail = material.removeprefix("chiseled_").removeprefix("cracked_")
        return stone_source(tail, available)
    return None


def connected_components(blocks: list[Block]) -> int:
    remaining = {(b.x, b.y, b.z) for b in blocks}
    components = 0
    while remaining:
        components += 1
        queue = deque([remaining.pop()])
        while queue:
            x, y, z = queue.popleft()
            for neighbour in ((x + 1, y, z), (x - 1, y, z), (x, y + 1, z),
                              (x, y - 1, z), (x, y, z + 1), (x, y, z - 1)):
                if neighbour in remaining:
                    remaining.remove(neighbour)
                    queue.append(neighbour)
    return components


def suggested_stars(score: float) -> int:
    if score < 20:
        return 1
    if score < 40:
        return 2
    if score < 60:
        return 3
    if score < 80:
        return 4
    return 5


def audit_blueprint(path: Path, available_counts: dict[str, int], regions_by_material: dict[str, set[str]]) -> Audit:
    name, stars, blocks, invalid = parse_blueprint(path)
    warnings = list(invalid)
    if not blocks:
        warnings.append("no valid blocks")
        return Audit(name, stars, 1, 0, "invalid", 0, 0, "0x0x0", 0, 0, 0, 0, 0, 0, 0,
                     0, "", 0, 0, 0, "", "invalid", "; ".join(warnings))

    coords = [(b.x, b.y, b.z) for b in blocks]
    unique_coords = set(coords)
    duplicate_count = len(coords) - len(unique_coords)
    if duplicate_count:
        warnings.append(f"{duplicate_count} duplicate coordinates")
    if stars < 1 or stars > 5:
        warnings.append("stars outside 1-5")
    out_of_bounds = sum(not (0 <= b.x <= 6 and 0 <= b.y <= 6 and 0 <= b.z <= 6) for b in blocks)
    if out_of_bounds:
        warnings.append(f"{out_of_bounds} blocks outside 7x7x7")

    xs, ys, zs = ([getattr(b, axis) for b in blocks] for axis in ("x", "y", "z"))
    dimensions_tuple = (max(xs) - min(xs) + 1, max(ys) - min(ys) + 1, max(zs) - min(zs) + 1)
    volume = math.prod(dimensions_tuple)
    material_counts = Counter(block.material for block in blocks)
    stateful = sum(bool(block.properties) for block in blocks)
    directional = sum(bool(DIRECTION_KEYS & block.properties.keys()) for block in blocks)
    complex_blocks = sum(bool((COMPLEX_KEYS - IGNORED_KEYS) & block.properties.keys()) for block in blocks)
    strict_connectable = sum(
        block.material.endswith(CONNECTABLE_SUFFIXES) and not block.material.endswith("_fence") for block in blocks
    )
    components = connected_components(blocks)

    available = set(available_counts)
    direct = 0
    craftable = 0
    uncovered: list[str] = []
    regions: set[str] = set()
    for material in material_counts:
        if material in available:
            direct += 1
            sources = {material}
        else:
            sources = recipe_sources(material, available)
            if sources:
                craftable += 1
            else:
                uncovered.append(material)
                continue
        for source in sources:
            regions.update(regions_by_material.get(source, set()))

    region_count = len(regions)
    height = dimensions_tuple[1]
    raw = (
        len(blocks) * 0.55
        + max(0, len(material_counts) - 1) * 2.0
        + stateful * 0.12
        + directional * 0.30
        + complex_blocks * 0.18
        + strict_connectable * 0.18
        + max(0, height - 1) * 2.5
        + max(0, components - 1) * 1.5
        + max(0, region_count - 1) * 2.8
    )
    score = round(min(100.0, raw * 100.0 / 115.0), 1)
    suggested = suggested_stars(score)
    assessment = "reasonable"
    if stars < suggested:
        assessment = "possibly underrated"
    elif stars > suggested:
        assessment = "possibly overrated"
    if uncovered:
        warnings.append("resource hall does not cover every material")
    if strict_connectable:
        warnings.append(f"{strict_connectable} strict auto-connect blocks")

    return Audit(
        blueprint=name,
        configured_stars=stars,
        suggested_stars=suggested,
        difficulty_score=score,
        segment_assessment=assessment,
        blocks=len(blocks),
        unique_materials=len(material_counts),
        dimensions="x".join(map(str, dimensions_tuple)),
        fill_percent=round(len(unique_coords) * 100.0 / volume, 1),
        height=height,
        components=components,
        stateful_blocks=stateful,
        directional_blocks=directional,
        complex_state_blocks=complex_blocks,
        strict_connectable_blocks=strict_connectable,
        material_regions=region_count,
        region_names=", ".join(sorted(regions)),
        direct_materials=direct,
        craftable_materials=craftable,
        uncovered_materials=len(uncovered),
        uncovered_list=", ".join(sorted(uncovered)),
        coverage="covered" if not uncovered else "uncovered",
        warnings="; ".join(warnings),
    )


def blueprint_paths(input_path: Path) -> list[Path]:
    if input_path.is_file():
        return [input_path]
    return sorted(input_path.glob("*.yml"), key=lambda path: path.name.casefold())


def write_csv(path: Path, audits: list[Audit]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fields = list(Audit.__dataclass_fields__)
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields)
        writer.writeheader()
        writer.writerows(asdict(audit) for audit in audits)


def write_markdown(path: Path, audits: list[Audit], input_path: Path, manifest: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    star_counts = Counter(audit.configured_stars for audit in audits)
    covered = sum(audit.coverage == "covered" for audit in audits)
    lines = [
        "# Build Mart blueprint audit",
        "",
        f"- Input: `{input_path}`",
        f"- Material manifest: `{manifest}`",
        f"- Blueprints: {len(audits)}",
        f"- Star distribution: " + ", ".join(f"{star}★={star_counts[star]}" for star in range(1, 6)),
        f"- Fully covered: {covered}/{len(audits)}",
        "- Difficulty score: 0-100; combines block count, material/region count, height, disconnected parts, "
        "directional and complex BlockData. Missing resources do not inflate difficulty.",
        "- Suggested-star thresholds: <20=1★, <40=2★, <60=3★, <80=4★, otherwise 5★.",
        "- Coverage treats known vanilla conversions/crafting as covered and reports materials whose required "
        "source blocks are absent from the generated material manifest.",
        "",
        "| Blueprint | Stars | Suggested | Score | Assessment | Blocks | Materials | Size | Stateful | "
        "Directional | Complex | Regions | Coverage | Uncovered | Warnings |",
        "|---|---:|---:|---:|---|---:|---:|---|---:|---:|---:|---:|---|---|---|",
    ]
    for audit in sorted(audits, key=lambda row: (row.configured_stars, row.difficulty_score, row.blueprint)):
        values = [
            audit.blueprint, str(audit.configured_stars), str(audit.suggested_stars), f"{audit.difficulty_score:.1f}",
            audit.segment_assessment, str(audit.blocks), str(audit.unique_materials), audit.dimensions,
            str(audit.stateful_blocks), str(audit.directional_blocks), str(audit.complex_state_blocks),
            str(audit.material_regions), audit.coverage, audit.uncovered_list or "-", audit.warnings or "-",
        ]
        lines.append("| " + " | ".join(value.replace("|", "\\|") for value in values) + " |")
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path, help="blueprint YAML file or directory")
    parser.add_argument("--manifest", type=Path, required=True, help="generated material-manifest YAML")
    parser.add_argument("--area-config", type=Path, help="area YAML containing material-islands")
    parser.add_argument("--markdown", type=Path, help="write a Markdown report")
    parser.add_argument("--csv", type=Path, help="write a CSV report")
    parser.add_argument("--json", type=Path, help="write a JSON report")
    args = parser.parse_args()

    if not args.input.exists():
        parser.error(f"input does not exist: {args.input}")
    if not args.manifest.is_file():
        parser.error(f"manifest does not exist: {args.manifest}")
    if args.area_config and not args.area_config.is_file():
        parser.error(f"area config does not exist: {args.area_config}")

    available, zones = parse_manifest(args.manifest)
    islands = parse_islands(args.area_config) if args.area_config else []
    regions = material_regions(zones, islands)
    audits = [audit_blueprint(path, available, regions) for path in blueprint_paths(args.input)]
    if not audits:
        parser.error("no blueprint YAML files found")

    if args.markdown:
        write_markdown(args.markdown, audits, args.input, args.manifest)
    if args.csv:
        write_csv(args.csv, audits)
    if args.json:
        args.json.parent.mkdir(parents=True, exist_ok=True)
        args.json.write_text(json.dumps([asdict(audit) for audit in audits], ensure_ascii=False, indent=2) + "\n",
                             encoding="utf-8")
    if not any((args.markdown, args.csv, args.json)):
        print(json.dumps([asdict(audit) for audit in audits], ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
