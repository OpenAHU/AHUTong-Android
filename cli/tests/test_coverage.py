from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import check_coverage


class CoverageTest(unittest.TestCase):
    def test_every_current_app_page_has_a_cli_entry_and_provider_is_debug_only(self):
        errors, pages, operations = check_coverage.check(Path(__file__).resolve().parents[2])
        self.assertEqual(errors, [])
        self.assertGreater(pages, 50)
        self.assertGreater(operations, 50)


if __name__ == "__main__":
    unittest.main()
