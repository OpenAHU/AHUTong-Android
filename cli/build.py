"""Build a standard-library-only Python zipapp, excluding tests, caches and credentials."""
from pathlib import Path
import argparse
import tempfile
import zipapp


def build(output):
    source = Path(__file__).resolve().parent
    output = Path(output).resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="ahutong-cli-") as staging:
        root = Path(staging)
        (root / "ahutong_cli.py").write_bytes((source / "ahutong_cli.py").read_bytes())
        (root / "__main__.py").write_text("import sys\nfrom ahutong_cli import main\nsys.exit(main())\n", encoding="utf-8")
        zipapp.create_archive(root, output, interpreter="/usr/bin/env python3", compressed=True)
    return output


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("output", type=Path)
    print(build(parser.parse_args().output))
