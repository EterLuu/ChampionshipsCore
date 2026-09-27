import importlib.util
from pathlib import Path
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location("floor_migration", Path(__file__).parents[1] / "buildmart_floor_migration.py")
migration = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(migration)


class FloorMigrationTest(unittest.TestCase):
    def test_preflight_is_read_only_and_apply_is_idempotent(self):
        with tempfile.TemporaryDirectory() as name:
            root = Path(name)
            (root / "一.yml").write_text("before", encoding="utf-8")
            (root / "unrelated.yml").write_text("custom", encoding="utf-8")
            entries = [{"file": "一.yml", "before": "before", "after": "after"}]
            self.assertEqual(["一.yml"], migration.migrate(root, entries))
            self.assertEqual("before", (root / "一.yml").read_text())
            self.assertEqual(["一.yml"], migration.migrate(root, entries, True))
            self.assertEqual([], migration.migrate(root, entries, True))
            self.assertEqual("after", (root / "一.yml").read_text())
            self.assertEqual("custom", (root / "unrelated.yml").read_text())

    def test_a_conflict_in_the_last_file_prevents_every_write(self):
        with tempfile.TemporaryDirectory() as name:
            root = Path(name)
            (root / "one.yml").write_text("before")
            (root / "two.yml").write_text("user edited")
            entries = [{"file": file, "before": "before", "after": "after"}
                       for file in ("one.yml", "two.yml")]
            with self.assertRaisesRegex(ValueError, "no files written"):
                migration.migrate(root, entries, True)
            self.assertEqual("before", (root / "one.yml").read_text())
            self.assertEqual("user edited", (root / "two.yml").read_text())


if __name__ == "__main__":
    unittest.main()
