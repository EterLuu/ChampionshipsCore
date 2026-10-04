#!/usr/bin/env python3
"""Import Frostbite Frenzy terrain into four isolated identical arenas. Python stdlib only.
Original datapack, player data, scoreboards and entities are deliberately not installed.
Usage: python3 tools/import_frostbite.py SOURCE.zip OUTPUT_DIRECTORY [glacial_keep|frosty_fjord]
"""

import copy, gzip, io, json, math, re, struct, sys, time, zipfile, zlib
from pathlib import Path

FORMATS = {1: "b", 2: "h", 3: "i", 4: "q", 5: "f", 6: "d"}
MAPS = {
    "glacial_keep": {
        "source": "GlacialKeep",
        "chunks": (range(6, 14), range(52, 60)),
        "spacing": 256,
        "spawn": (165, 132, 899),
    },
    "frosty_fjord": {
        "source": "FrostyFjord",
        "chunks": (range(71, 84), range(-151, -137)),
        "spacing": 320,
        "spawn": (1235, 45, -2348),
    },
}


def read_tag(f, t):
    def num(fmt):
        return struct.unpack(">" + fmt, f.read(struct.calcsize(">" + fmt)))[0]

    def string():
        return f.read(num("H")).decode("utf-8")

    if t in FORMATS:
        return num(FORMATS[t])
    if t == 7:
        return f.read(num("i"))
    if t == 8:
        return string()
    if t == 9:
        child = num("B")
        return child, [read_tag(f, child) for _ in range(num("i"))]
    if t == 10:
        d = {}
        while True:
            child = num("B")
            if not child:
                return d
            name = string()
            d[name] = (child, read_tag(f, child))
    if t in (11, 12):
        return [num("i" if t == 11 else "q") for _ in range(num("i"))]
    raise ValueError(t)


def write_tag(f, t, v):
    def num(fmt, n):
        f.write(struct.pack(">" + fmt, n))

    def string(s):
        b = s.encode("utf-8")
        num("H", len(b))
        f.write(b)

    if t in FORMATS:
        num(FORMATS[t], v)
    elif t == 7:
        num("i", len(v))
        f.write(v)
    elif t == 8:
        string(v)
    elif t == 9:
        child, items = v
        num("B", child)
        num("i", len(items))
        for item in items:
            write_tag(f, child, item)
    elif t == 10:
        for name, (child, item) in v.items():
            num("B", child)
            string(name)
            write_tag(f, child, item)
        num("B", 0)
    elif t in (11, 12):
        num("i", len(v))
        for item in v:
            num("i" if t == 11 else "q", item)
    else:
        raise ValueError(t)


def decode(b):
    f = io.BytesIO(b)
    t = f.read(1)[0]
    n = struct.unpack(">H", f.read(2))[0]
    f.read(n)
    return read_tag(f, t)


def encode(v):
    f = io.BytesIO()
    f.write(b"\x0a\x00\x00")
    write_tag(f, 10, v)
    return f.getvalue()


def value(d, k, default=None):
    return d[k][1] if k in d else default


def source_chunk(z, cx, cz):
    b = z.read(f"frostbite-frenzy/region/r.{cx // 32}.{cz // 32}.mca")
    i = (cx % 32 + 32 * (cz % 32)) * 4
    offset = int.from_bytes(b[i : i + 3], "big") * 4096
    if not offset:
        raise ValueError(f"missing chunk {cx},{cz}")
    length = int.from_bytes(b[offset : offset + 4], "big")
    kind = b[offset + 4]
    payload = b[offset + 5 : offset + 4 + length]
    return decode(
        zlib.decompress(payload)
        if kind == 2
        else gzip.decompress(payload)
        if kind == 1
        else payload
    )


def block(chunk, x, y, z):
    for section in value(chunk, "sections", (10, []))[1]:
        if value(section, "Y") != y // 16:
            continue
        states = value(section, "block_states")
        palette = value(states, "palette")[1]
        if len(palette) == 1:
            return value(palette[0], "Name")
        bits = max(4, (len(palette) - 1).bit_length())
        per = 64 // bits
        index = (y % 16) * 256 + (z % 16) * 16 + x % 16
        data = value(states, "data")
        p = (data[index // per] >> ((index % per) * bits)) & ((1 << bits) - 1)
        return value(palette[p], "Name")
    return "minecraft:air"


def write_regions(out, chunks):
    grouped = {}
    for (cx, cz), chunk in chunks.items():
        grouped.setdefault((cx // 32, cz // 32), []).append((cx, cz, chunk))
    for (rx, rz), entries in grouped.items():
        header = bytearray(8192)
        body = bytearray()
        sector = 2
        for cx, cz, chunk in sorted(entries):
            payload = zlib.compress(encode(chunk))
            data = struct.pack(">I", len(payload) + 1) + b"\x02" + payload
            count = math.ceil(len(data) / 4096)
            index = (cx % 32 + (cz % 32) * 32) * 4
            header[index : index + 4] = (sector << 8 | count).to_bytes(4, "big")
            header[4096 + index : 4100 + index] = int(time.time()).to_bytes(4, "big")
            body.extend(data)
            body.extend(b"\0" * (4096 * count - len(data)))
            sector += count
        p = out / "region" / f"r.{rx}.{rz}.mca"
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_bytes(header + body)


def build(source, destination, map_name="glacial_keep"):
    if map_name not in MAPS:
        raise ValueError(f"Unknown map: {map_name}")
    spec = MAPS[map_name]
    out = Path(destination)
    if out.exists():
        raise SystemExit("Output must not exist; refusing to overwrite an existing world")
    with zipfile.ZipFile(source) as z:
        text = z.read(
            "frostbite-frenzy/datapacks/freezetag2/data/freeze/function/game/general/map/data.mcfunction"
        ).decode()
        match = re.search(r"(?ms)^  " + spec["source"] + r":\{(.*?)^  \}", text)
        if match is None:
            raise ValueError(f"Missing map data: {spec['source']}")
        section = match.group(1)
        spawns = re.findall(
            r'Pos:"([^"]+)",Rot:"([^"]+)",Team:', section.split("ItemGivers:", 1)[0]
        )
        items = re.findall(r'Pos:"([^"]+)",Cooldown:', section.split("ItemGivers:", 1)[1])
        chunks = {
            (cx, cz): source_chunk(z, cx, cz)
            for cx in spec["chunks"][0]
            for cz in spec["chunks"][1]
        }
        checked = []
        safe = []
        for pos, yaw in spawns:
            x, y, zz = map(float, pos.split())
            x = math.floor(x) + 0.5
            zz = math.floor(zz) + 0.5
            if map_name == "frosty_fjord":
                # Place feet above slabs; move one snow-layer spawn onto adjacent solid snow.
                if (x, zz) in ((1211.5, -2313.5), (1205.5, -2329.5)):
                    y = 13
                if (x, zz) == (1284.5, -2279.5):
                    y = 3
                if (x, zz) == (1237.5, -2314.5):
                    x, zz = 1236.5, -2315.5
            states = [
                block(
                    chunks[(math.floor(x) // 16, math.floor(zz) // 16)],
                    math.floor(x),
                    math.floor(y + d),
                    math.floor(zz),
                )
                for d in [-0.15, 0, 1]
            ]
            checked.append(dict(pos=[x, y, zz], blocks=states))
            if (
                states[0] in ("minecraft:air", "minecraft:water")
                or states[1] not in ("minecraft:air", "minecraft:cave_air")
                or states[2] not in ("minecraft:air", "minecraft:cave_air")
            ):
                continue
            safe.append(f"frostbite_{map_name}:{x:g}:{y:g}:{zz:g}:{float(yaw):g}:0")
        if len(safe) < 16:
            raise ValueError(f"Only {len(safe)} clear supported spawn points")
        result = {}
        stripped = 0
        for arena in range(4):
            dx = (arena % 2) * spec["spacing"]
            dz = (arena // 2) * spec["spacing"]
            for (cx, cz), original in chunks.items():
                chunk = copy.deepcopy(original)
                chunk["xPos"] = (3, cx + dx // 16)
                chunk["zPos"] = (3, cz + dz // 16)
                chunk["block_ticks"] = (9, (10, []))
                chunk["fluid_ticks"] = (9, (10, []))
                chunk["structures"] = (10, {"starts": (10, {}), "References": (10, {})})
                entities = value(chunk, "block_entities", (10, []))[1]
                retained = []
                for entity in entities:
                    if "command_block" in value(entity, "id", ""):
                        stripped += 1
                        continue
                    for k, d in [("x", dx), ("z", dz)]:
                        if k in entity:
                            entity[k] = (3, value(entity, k) + d)
                    entity.pop("Items", None)
                    entity.pop("LootTable", None)
                    entity.pop("LootTableSeed", None)
                    retained.append(entity)
                chunk["block_entities"] = (9, (10, retained))
                for sec in value(chunk, "sections", (10, []))[1]:
                    states = value(sec, "block_states")
                    if not states:
                        continue
                    for entry in value(states, "palette")[1]:
                        if (
                            "command_block" in value(entry, "Name", "")
                            or value(entry, "Name") == "minecraft:structure_block"
                        ):
                            entry.clear()
                            entry["Name"] = (8, "minecraft:air")
                result[(cx + dx // 16, cz + dz // 16)] = chunk
        level = decode(gzip.decompress(z.read("frostbite-frenzy/level.dat")))
        data = value(level, "Data")
        for k in [
            "Player",
            "DragonFight",
            "CustomBossEvents",
            "ScheduledEvents",
            "WanderingTraderId",
        ]:
            data.pop(k, None)
        data["LevelName"] = (8, "frostbite_" + map_name)
        for key, coordinate in zip(("SpawnX", "SpawnY", "SpawnZ"), spec["spawn"]):
            data[key] = (3, coordinate)
        data["DataPacks"] = (10, {"Enabled": (9, (8, ["vanilla"])), "Disabled": (9, (8, []))})
        data["GameType"] = (3, 2)
        data["Difficulty"] = (1, 0)
        data["allowCommands"] = (1, 0)
        out.mkdir(parents=True)
        write_regions(out, result)
        (out / "level.dat").write_bytes(gzip.compress(encode(level)))
        config = {
            "configuration_version": 2,
            "world": f"frostbite_{map_name}",
            "spawns": safe,
            "items": [
                f"frostbite_{map_name}:" + ":".join(point.split()) + ":0:0" for point in items
            ],
            "spawn_audit": checked,
            "copied_chunks": len(result),
            "stripped_command_blocks": stripped,
            "source": "Frostbite Frenzy v1.3.1 [1.21.11] / Quillmark",
            "original_spawn_count": len(spawns),
        }
        (out.parent / f"frostbite-import-{map_name}.json").write_text(
            json.dumps(config, ensure_ascii=False, indent=2) + "\n"
        )
        print(
            json.dumps(
                {k: v for k, v in config.items() if k not in ["spawns", "items", "spawn_audit"]},
                ensure_ascii=False,
            )
        )
        print("Safe spawns", len(safe), "item points", len(items))


if __name__ == "__main__":
    build(*sys.argv[1:])
