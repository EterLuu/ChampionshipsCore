import sys, unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from apertures import area, difficulty, repair, unpack, repack, measure
from build_catalog import schematic


class ApertureTest(unittest.TestCase):
    def window(self, width, feet, ceiling):
        return [
            (-3.5, 0, -width / 2, 5),
            (width / 2, 0, 3.5, 5),
            (-width / 2, ceiling, width / 2, 5),
        ] + ([(-width / 2, 0, width / 2, feet)] if feet else [])

    def test_ground_crouch_counts_but_one_block_raised_crouch_does_not(self):
        self.assertEqual(1.5, area(self.window(1, 0, 1.5)))
        self.assertEqual(0, area(self.window(1, 1, 2.5)))
        self.assertEqual(2, area(self.window(1, 1, 3)))

    def test_half_block_step_can_be_walked_while_crouching(self):
        self.assertEqual(3, area(self.window(2, 0.5, 2)))

    def test_thin_gaps_and_unreachable_high_windows_do_not_count(self):
        self.assertEqual(0, area(self.window(0.5, 0, 3)))
        self.assertEqual(0, area(self.window(2, 2, 4)))
        self.assertEqual(2.25, area(self.window(0.75, 0, 3)))

    def test_larger_total_opening_is_easier(self):
        self.assertEqual([3, 2, 1], [difficulty(area(self.window(w, 1, 3))) for w in (1, 2, 3)])

    def test_repair_opens_only_route_and_preserves_ground_slab(self):
        c = {
            (x, y, 0): "minecraft:stripped_spruce_wood[axis=x]"
            for x in range(-4, 5)
            for y in range(4)
        }
        c[0, 1, 0] = "minecraft:air"
        c[0, 2, 0] = "minecraft:spruce_slab[type=top,waterlogged=false]"
        self.assertEqual(1, len(repair(c)))
        self.assertEqual("minecraft:air", c[0, 2, 0])
        self.assertEqual(2, measure(c))
        self.assertEqual([], repair(c))
        c[1, 1, 0] = "minecraft:spruce_slab[type=top,waterlogged=false]"
        repair(c)
        self.assertIn("slab", c[1, 1, 0])

    def test_repair_closes_misleading_hole_if_other_openings_are_sufficient(self):
        c = {(x, y, 0): "minecraft:air" for x in range(-4, 5) for y in range(4)}
        c[0, 0, 0] = "minecraft:stripped_spruce_wood[axis=y]"
        c[0, 2, 0] = "minecraft:spruce_slab[type=top,waterlogged=false]"
        self.assertEqual(1, len(repair(c)))
        self.assertNotIn("slab", c[0, 2, 0])
        self.assertNotEqual("minecraft:air", c[0, 2, 0])

    def test_nbt_roundtrip_preserves_unmodified_tags_and_handles_large_palette(self):
        payload, _ = schematic([["wood"] * 12, ["air"] * 12, ["top"] * 12])
        root, c = unpack(payload)
        # Include unrelated NBT of every collection kind and enough states for multibyte VarInts.
        root[2]["extra"] = (
            10,
            {
                "list": (9, (8, ["hello", "world"])),
                "ints": (11, [1, 2]),
                "longs": (12, [3, 4]),
                "bytes": (7, b"abc"),
            },
        )
        for i, p in enumerate(list(c)[:140]):
            c[p] = "minecraft:test_" + str(i)
        result = repack(root, c)
        new, decoded = unpack(result)
        self.assertEqual(c, decoded)
        self.assertEqual(root, new)

    def test_all_bundled_snapshots_have_current_rating_and_no_raised_half_gap(self):
        import json, copy

        resource = (
            Path(__file__).resolve().parents[3]
            / "championships-core/src/main/resources/riptiderush/hitw-walls.json"
        )
        for row in json.loads(resource.read_text())["pool"]:
            _, cells = unpack(row["building"]["schematic"])
            self.assertEqual([], repair(copy.deepcopy(cells)), row["id"])
            self.assertGreater(measure(cells), 0, row["id"])
            self.assertEqual(row["passable-area"], measure(cells), row["id"])
            self.assertEqual(row["difficulty"], difficulty(measure(cells)), row["id"])


if __name__ == "__main__":
    unittest.main()
