#!/usr/bin/env python3
"""Static repository hygiene and governance checks for AHUTong."""

from __future__ import annotations

import pathlib
import re
import subprocess
import sys

MAX_AGENTS_MD_BYTES = 24_000
FORBIDDEN_FILE_NAMES = {"keystore.properties", "local.properties", "config.local.json"}
FORBIDDEN_SUFFIXES = {".jks", ".keystore", ".ahup"}
CREDENTIALS_RE = re.compile(
    r"(?:sntry[su]_[A-Za-z0-9]{40,}"
    r"|gh[pousr]_[A-Za-z0-9]{30,}"
    r"|github_pat_[A-Za-z0-9_]{40,}"
    r"|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----)"
)
PERSONAL_PATH_RE = re.compile(
    r"(?:[A-Za-z]:\\\\?Users\\\\?[^\\/\s\"']+|/Users/[^/\s\"']+|/home/[^/\s\"']+)"
)
PERSONAL_PATH_EXTENSIONS = {".py", ".kts", ".yml", ".yaml", ".sh", ".ps1", ".bat"}
PERSONAL_PATH_ALLOWLIST: set[str] = set()


def tracked_files(root: pathlib.Path) -> list[str]:
    output = subprocess.check_output(["git", "ls-files", "-z"], cwd=root)
    return [item for item in output.decode("utf-8").split("\0") if item]


def parse_toml_versions(toml_text: str) -> dict[str, str]:
    versions: dict[str, str] = {}
    in_versions = False
    for raw_line in toml_text.splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("["):
            in_versions = line == "[versions]"
            continue
        if in_versions and "=" in line:
            key, _, val = line.partition("=")
            versions[key.strip()] = val.split("#", 1)[0].strip().strip('"').strip("'")
    return versions


def check_repo(root: pathlib.Path, paths: list[str] | None = None) -> list[str]:
    errors: list[str] = []
    tracked = tracked_files(root) if paths is None else paths

    for rel in tracked:
        path = root / rel
        if path.name in FORBIDDEN_FILE_NAMES or path.suffix in FORBIDDEN_SUFFIXES:
            errors.append(f"{rel}: tracked local credential or binary package is forbidden")
        if rel.startswith("dist/ahup/"):
            errors.append(f"{rel}: tracked .ahup build output is forbidden")
        if not path.is_file():
            continue
        try:
            content = path.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        if CREDENTIALS_RE.search(content):
            errors.append(f"{rel}: possible committed credential; remove and rotate it")
        if path.suffix in {".kt", ".java"} and "2c2ccadcad" in content:
            errors.append(f"{rel}: use BuildConfig.BUGLY_APP_ID instead of a source literal")
        if (
            path.suffix in PERSONAL_PATH_EXTENSIONS
            and rel not in PERSONAL_PATH_ALLOWLIST
            and PERSONAL_PATH_RE.search(content)
        ):
            errors.append(f"{rel}: hardcoded personal machine path is forbidden")

    agents_md = root / "AGENTS.md"
    if not agents_md.is_file():
        errors.append("AGENTS.md: missing root development specification")
    elif agents_md.stat().st_size > MAX_AGENTS_MD_BYTES:
        errors.append(
            f"AGENTS.md: size {agents_md.stat().st_size} exceeds {MAX_AGENTS_MD_BYTES} bytes budget"
        )

    skills_dir = root / ".agents" / "skills"
    if skills_dir.is_dir():
        for entry in sorted(skills_dir.iterdir()):
            if not entry.is_dir():
                continue
            skill_md = entry / "SKILL.md"
            if not skill_md.is_file():
                errors.append(f".agents/skills/{entry.name}: missing SKILL.md")
                continue
            text = skill_md.read_text(encoding="utf-8")
            match = re.match(r"^---\r?\n(.*?)\r?\n---", text, re.DOTALL)
            if not match:
                errors.append(f".agents/skills/{entry.name}/SKILL.md: missing YAML frontmatter")
                continue
            fm = match.group(1)
            name_match = re.search(r"^name:\s*([^\s#]+)\s*$", fm, re.MULTILINE)
            desc_match = re.search(r"^description:\s*(.+)$", fm, re.MULTILINE)
            if not name_match or name_match.group(1).strip("'\"") != entry.name:
                errors.append(
                    f".agents/skills/{entry.name}/SKILL.md: frontmatter name must match folder"
                )
            if not desc_match or not desc_match.group(1).strip():
                errors.append(
                    f".agents/skills/{entry.name}/SKILL.md: frontmatter description is required"
                )

    settings_file = root / "settings.gradle.kts"
    if settings_file.is_file():
        settings_text = settings_file.read_text(encoding="utf-8")
        if not (root / "settings-gradle.lockfile").is_file():
            errors.append("settings-gradle.lockfile: missing root settings lockfile")
        for call in re.finditer(r"include\s*\(([^)]+)\)", settings_text):
            for mod_match in re.finditer(r'"(:[^"]+)"', call.group(1)):
                module = mod_match.group(1)
                if module == ":core:plugin-platform":
                    continue
                lockfile = root / module.lstrip(":").replace(":", "/") / "gradle.lockfile"
                if not lockfile.is_file():
                    errors.append(f"{module}: missing {lockfile.relative_to(root).as_posix()}")

    toml_file = root / "gradle" / "libs.versions.toml"
    platform_file = root / "core" / "plugin-platform" / "build.gradle.kts"
    if toml_file.is_file() and platform_file.is_file():
        versions = parse_toml_versions(toml_file.read_text(encoding="utf-8"))
        platform_text = platform_file.read_text(encoding="utf-8")
        expected_pairs = [
            ("androidx.compose:compose-bom", versions.get("composeBom")),
            ("org.jetbrains.kotlin:kotlin-stdlib", versions.get("kotlin")),
            ("org.jetbrains.kotlinx:kotlinx-coroutines-android", versions.get("coroutines")),
            ("io.coil-kt:coil-compose", versions.get("coilVersion")),
        ]
        for coord, ver in expected_pairs:
            if ver and f'"{coord}:{ver}"' not in platform_text:
                errors.append(
                    f"core/plugin-platform/build.gradle.kts: expected {coord}:{ver} to match libs.versions.toml"
                )

    proguard_file = root / "app" / "proguard-rules.pro"
    if proguard_file.is_file():
        seen_rules: set[str] = set()
        for raw_line in proguard_file.read_text(encoding="utf-8").splitlines():
            stripped = raw_line.strip()
            if not stripped.startswith("-keep"):
                continue
            normalized = " ".join(stripped.split())
            if normalized in seen_rules:
                errors.append(f"app/proguard-rules.pro: duplicate keep rule: {normalized}")
            seen_rules.add(normalized)

    return errors


def main() -> int:
    root = pathlib.Path(__file__).resolve().parent.parent
    errors = check_repo(root)
    for err in errors:
        print(f"::error::{err}", file=sys.stderr)
    if errors:
        return 1
    print("Repository hygiene checks passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
