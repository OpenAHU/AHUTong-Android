"""Include CLI safety, transport and App coverage tests in the existing CI discovery."""
from pathlib import Path
import unittest


def load_tests(loader, tests, pattern):
    directory = Path(__file__).resolve().parents[1] / "cli" / "tests"
    return unittest.TestLoader().discover(str(directory), pattern="test_*.py")
