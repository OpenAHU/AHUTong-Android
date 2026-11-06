#!/usr/bin/env python3
"""Synchronize master and develop to one commit after an APK is published; never publish."""

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


def gradle_identity(text):
    if len(CODE.findall(text)) != 1 or len(NAME.findall(text)) != 1:
        raise ValueError("Gradle must contain exactly one literal versionCode and versionName")
    current_code, _ = _gradle_version(text)
    current_name = re.search(r'"([^"\r\n]+)"', NAME.search(text)[0])[1]
    return current_code, current_name


def replace_identity(text, code, name):
    gradle_identity(text)
    text = CODE.sub(lambda match: f"{match[1]}{code}", text)
    return NAME.sub(lambda match: f'{match[1]}"{name}"', text)


def update_gradle(text, code, name):
    validate_version(code, name)
    current_code, current_name = gradle_identity(text)
    if current_code > code:
        return text  # Preserve a newer release/prepared version already on this branch.
    if current_code == code:
        if current_name != name:
            raise ValueError("The same versionCode already has a different versionName")
        return text
    return replace_identity(text, code, name)


def git(repo, *args):
    return subprocess.run(["git", *args], cwd=repo, check=True, capture_output=True,
                          text=True, encoding="utf-8", errors="replace").stdout.strip()


def blob(repo, spec):
    # File data must retain its whitespace and line endings; git() returns trimmed command output.
    return subprocess.run(["git", "show", spec], cwd=repo, check=True,
                          capture_output=True).stdout.decode("utf-8")


def bot_git(repo, *args):
    return git(repo, "-c", "user.name=github-actions[bot]", "-c",
               "user.email=41898282+github-actions[bot]@users.noreply.github.com", *args)


def is_ancestor(repo, ancestor, descendant):
    try:
        git(repo, "merge-base", "--is-ancestor", ancestor, descendant)
        return True
    except subprocess.CalledProcessError as error:
        if error.returncode != 1:
            raise
        return False


def merge_branches(worktree, other, identity, folder):
    try:
        bot_git(worktree, "merge", "--no-commit", "--no-ff", other)
    except subprocess.CalledProcessError:
        conflicts = git(worktree, "diff", "--name-only", "--diff-filter=U").splitlines()
        if not conflicts:
            raise
        if conflicts != [GRADLE]:
            raise ValueError("Branch contents conflict; resolve before version sync: " + ", ".join(conflicts))
        # Both release jobs may have edited the version lines. Normalize only those two
        # literals before a genuine three-way merge, preserving every other Gradle change.
        sides = []
        for stage in (2, 1, 3):  # ours, base, theirs: git merge-file's argument order.
            side = Path(folder) / f"gradle-{stage}"
            with side.open("w", encoding="utf-8", newline="") as output:
                output.write(replace_identity(blob(worktree, f":{stage}:{GRADLE}"), *identity))
            sides.append(str(side))
        merged = subprocess.run(["git", "merge-file", "-p", *sides], cwd=worktree, capture_output=True)
        if merged.returncode != 0:
            raise ValueError("Non-version Gradle contents conflict; resolve before version sync")
        (worktree / GRADLE).write_bytes(merged.stdout)
        git(worktree, "add", "--", GRADLE)


def sync_versions(repo, code, name, attempts=3):
    validate_version(code, name)
    if type(attempts) is not int or attempts < 1:
        raise ValueError("Version sync requires at least one attempt")
    repo = Path(repo).resolve()
    for attempt in range(attempts):
        git(repo, "fetch", "--no-tags", "origin",
            *(f"refs/heads/{branch}:refs/remotes/origin/{branch}" for branch in BRANCHES))
        heads = {branch: git(repo, "rev-parse", f"refs/remotes/origin/{branch}") for branch in BRANCHES}
        versions = [(code, name)] + [gradle_identity(blob(repo, f"{head}:{GRADLE}")) for head in heads.values()]
        names = {}
        for version_code, version_name in versions:
            if version_code in names and names[version_code] != version_name:
                raise ValueError("The same versionCode already has a different versionName")
            names[version_code] = version_name
        identity = max(versions, key=lambda version: version[0])
        master, develop = (heads[branch] for branch in BRANCHES)
        base = develop if is_ancestor(repo, master, develop) else master
        merge_required = base == master and not is_ancestor(repo, develop, master)
        with tempfile.TemporaryDirectory(prefix="ahutong-version-sync-") as folder:
            worktree = Path(folder) / "sync"
            added = False
            try:
                git(repo, "worktree", "add", "--detach", str(worktree), base)
                added = True
                if merge_required:
                    merge_branches(worktree, develop, identity, folder)
                path = worktree / GRADLE
                with path.open(encoding="utf-8", newline="") as source:
                    before = source.read()
                after = replace_identity(before, *identity)
                if after != before:
                    with path.open("w", encoding="utf-8", newline="") as output:
                        output.write(after)
                git(worktree, "add", "--", GRADLE)
                if merge_required or after != before:
                    bot_git(worktree, "commit", "-m", f"chore(release): sync version {identity[1]} ({identity[0]})")
                commit = git(worktree, "rev-parse", "HEAD")
                changed = [branch for branch in BRANCHES if heads[branch] != commit]
                if not changed:
                    return []
                try:
                    # The candidate descends from both fetched heads. A concurrent update
                    # rejects this non-force push atomically; fetch and rebuild before retrying.
                    git(repo, "push", "--atomic", "origin",
                        *(f"{commit}:refs/heads/{branch}" for branch in BRANCHES))
                    return changed
                except subprocess.CalledProcessError:
                    if attempt + 1 == attempts:
                        raise
            finally:
                if added:
                    git(repo, "worktree", "remove", "--force", str(worktree))


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
    print("Branches synchronized to one commit: " + ", ".join(changed)
          if changed else "Branches already share this or a newer version")


if __name__ == "__main__":
    main()
