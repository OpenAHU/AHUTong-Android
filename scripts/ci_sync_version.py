#!/usr/bin/env python3
"""Commit an already-published APK's version to master and develop; never publish."""

import argparse
from pathlib import Path
import re
import subprocess
import tempfile

from ci_release import _gradle_version, _version, _version_code


BRANCHES = ("master", "develop")
GRADLE = "app/build.gradle.kts"
CODE = re.compile(r"^([ \t]*versionCode[ \t]*=[ \t]*)\d+(?=[ \t]*(?://[^\r\n]*)?\r?$)", re.MULTILINE)
NAME = re.compile(r'^([ \t]*versionName[ \t]*=[ \t]*)"[^"\r\n]+"(?=[ \t]*(?://[^\r\n]*)?\r?$)', re.MULTILINE)


def validate_version(code, name):
    _version_code(code)
    (major, minor, _), _ = _version(name)
    if "-release-beta" in name or "-debug" in name:
        raise ValueError("Published version must use X.Y.Z or X.Y.Z-betaN")
    if not (0 <= major <= 9 and 0 <= minor <= 99 and code // 1000 == major * 100 + minor):
        raise ValueError("Published versionCode must match versionName in xyyzzz format")


def update_gradle(text, code, name):
    validate_version(code, name)
    if len(CODE.findall(text)) != 1 or len(NAME.findall(text)) != 1:
        raise ValueError("Gradle must contain exactly one literal versionCode and versionName")
    current_code, _ = _gradle_version(text)
    current_name = re.search(r'"([^"\r\n]+)"', NAME.search(text)[0])[1]
    if current_code > code:
        return text  # Preserve a newer release/prepared version already on this branch.
    if current_code == code:
        if current_name != name:
            raise ValueError("The same versionCode already has a different versionName")
        return text
    text = CODE.sub(lambda match: f"{match[1]}{code}", text)
    return NAME.sub(lambda match: f'{match[1]}"{name}"', text)


def git(repo, *args):
    return subprocess.run(["git", *args], cwd=repo, check=True, capture_output=True,
                          text=True, encoding="utf-8", errors="replace").stdout.strip()


def sync_versions(repo, code, name, attempts=3):
    validate_version(code, name)
    repo = Path(repo).resolve()
    for attempt in range(attempts):
        git(repo, "fetch", "--no-tags", "origin",
            *(f"refs/heads/{branch}:refs/remotes/origin/{branch}" for branch in BRANCHES))
        commits = {}
        with tempfile.TemporaryDirectory(prefix="ahutong-version-sync-") as folder:
            worktrees = []
            try:
                for branch in BRANCHES:
                    worktree = Path(folder) / branch
                    git(repo, "worktree", "add", "--detach", str(worktree), f"refs/remotes/origin/{branch}")
                    worktrees.append(worktree)
                    path = worktree / GRADLE
                    with path.open(encoding="utf-8", newline="") as source:
                        before = source.read()
                    after = update_gradle(before, code, name)
                    if after == before:
                        continue
                    with path.open("w", encoding="utf-8", newline="") as output:
                        output.write(after)
                    git(worktree, "add", "--", GRADLE)
                    git(worktree, "-c", "user.name=github-actions[bot]",
                        "-c", "user.email=41898282+github-actions[bot]@users.noreply.github.com",
                        "commit", "-m", f"chore(release): sync version {name} ({code})")
                    commits[branch] = git(worktree, "rev-parse", "HEAD")
                if not commits:
                    return []
                try:
                    # Both refs update together, preserving any concurrent branch commits.
                    git(repo, "push", "--atomic", "origin",
                        *(f"{commit}:refs/heads/{branch}" for branch, commit in commits.items()))
                    return list(commits)
                except subprocess.CalledProcessError:
                    if attempt + 1 == attempts:
                        raise
            finally:
                for worktree in reversed(worktrees):
                    git(repo, "worktree", "remove", "--force", str(worktree))
    raise ValueError("Version sync requires at least one attempt")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=Path("."))
    parser.add_argument("--version-code", type=int, required=True)
    parser.add_argument("--version-name", required=True)
    args = parser.parse_args()
    try:
        changed = sync_versions(args.repo, args.version_code, args.version_name)
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        detail = error.stderr if isinstance(error, subprocess.CalledProcessError) else str(error)
        parser.exit(1, f"Version sync failed (APK remains published): {detail}\n")
    print("Version committed to " + ", ".join(changed) if changed else "Branches already have this or a newer version")


if __name__ == "__main__":
    main()
