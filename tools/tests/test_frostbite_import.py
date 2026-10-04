import importlib.util
import io
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location(
    "import_frostbite", Path(__file__).resolve().parents[1] / "import_frostbite.py"
)
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


class FrostbiteImportTest(unittest.TestCase):
    def test_nbt_preserves_all_used_types(self):
        obj = {
            "byte": (1, -1),
            "short": (2, 32000),
            "int": (3, 100000),
            "long": (4, 2**50),
            "float": (5, 1.5),
            "double": (6, -0.5),
            "bytes": (7, b"\0\xff"),
            "str": (8, "霜冻狂潮"),
            "list": (9, (10, [{"x": (3, 1)}])),
            "compound": (10, {"nested": (8, "yes")}),
            "ints": (11, [-1, 3]),
            "longs": (12, [-(2**63), 2**63 - 1]),
        }
        self.assertEqual(obj, m.decode(m.encode(obj)))

    def test_palette_lookup_uses_padded_longs_and_signed_y(self):
        palette = [{"Name": (8, "minecraft:air")}, {"Name": (8, "minecraft:stone")}]
        data = [0] * 256
        data[0] = 1 << 60
        chunk = {
            "sections": (
                9,
                (
                    10,
                    [
                        {
                            "Y": (1, -1),
                            "block_states": (
                                10,
                                {"palette": (9, (10, palette)), "data": (12, data)},
                            ),
                        }
                    ],
                ),
            )
        }
        self.assertEqual("minecraft:stone", m.block(chunk, 15, -16, 0))
        self.assertEqual("minecraft:air", m.block(chunk, 0, -16, 1))

    def test_output_refuses_to_overwrite_any_existing_world(self):
        with self.assertRaises(SystemExit):
            m.build("unused.zip", str(Path(__file__).parent))


if __name__ == "__main__":
    unittest.main()
