import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from verify_infinity_pack_inventory import missing_inventory

class PackInventoryTest(unittest.TestCase):
    def test_missing_files_are_reported(self):
        with tempfile.TemporaryDirectory() as tmp:
            missing = missing_inventory(Path(tmp))
            self.assertGreater(len(missing), 0)
            self.assertIn("app/src/main/java/com/infinity/core/InfinityFilesTransferPolicy.kt", missing)

    def test_complete_inventory_has_no_missing_files(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            from verify_infinity_pack_inventory import REQUIRED
            for relative in REQUIRED:
                target = root / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text("// inventory fixture", encoding="utf-8")
            self.assertEqual([], missing_inventory(root))

if __name__ == "__main__":
    unittest.main()
