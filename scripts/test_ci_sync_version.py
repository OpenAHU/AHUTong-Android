"""Version-only writeback using temporary local Git repositories; no network."""

from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import ci_sync_version as sync


def gradle(code=304005, name="3.4.3-debug2"):
    return (f'    versionCode = {code} // keep code comment\n'
            f'    versionName = "{name}" // keep name comment\n'
            '    providers.gradleProperty("release.versionCode").orNull?.let { versionCode = it.toInt() }\n'
            '    versionNameSuffix = "-debug"\n')


class VersionEditTest(unittest.TestCase):
    def test_only_the_two_literals_change_and_crlf_is_preserved(self):
        before = '// versionCode = 1\r\n' + gradle().replace('\n', '\r\n')
        expected = before.replace('= 304005', '= 304006').replace('"3.4.3-debug2"', '"3.4.3-beta3"')
        self.assertEqual(expected, sync.update_gradle(before, 304006, "3.4.3-beta3"))

    def test_equal_and_newer_versions_are_unchanged(self):
        for text in (gradle(304006, "3.4.3-beta3"), gradle(305000, "3.5.0")):
            self.assertEqual(text, sync.update_gradle(text, 304006, "3.4.3-beta3"))

    def test_same_code_with_a_different_name_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "different versionName"):
            sync.update_gradle(gradle(), 304005, "3.4.3-beta3")

    def test_invalid_published_identity_is_rejected(self):
        for code, name in ((304006, "3.5.0"), (304006, "3.4.3-debug2"),
                           (304006, "3.4.3-release-beta2"), (True, "3.4.3"),
                           (304006, '3.4.3"\nmalicious'), (1000000, "10.0.0")):
            with self.subTest(code=code, name=name), self.assertRaises(ValueError):
                sync.update_gradle(gradle(), code, name)

    def test_missing_and_ambiguous_literals_are_rejected(self):
        for text in ("", gradle() + "versionCode = 304005\n", gradle() + 'versionName = "3.4.3"\n'):
            with self.subTest(text=text), self.assertRaisesRegex(ValueError, "exactly one"):
                sync.update_gradle(text, 304006, "3.4.3-beta3")


class GitSyncTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.remote = self.root / "remote.git"
        self.repo = self.root / "checkout"
        self.repo.mkdir()
        sync.git(self.root, "init", "--bare", str(self.remote))
        sync.git(self.repo, "init", "-b", "master")
        sync.git(self.repo, "config", "user.name", "Fixture")
        sync.git(self.repo, "config", "user.email", "fixture@example.invalid")
        self.gradle = self.repo / sync.GRADLE
        self.gradle.parent.mkdir()
        self.gradle.write_text(gradle(), encoding="utf-8")
        (self.repo / "feature.md").write_text("master feature\n", encoding="utf-8")
        sync.git(self.repo, "add", ".")
        sync.git(self.repo, "commit", "-m", "chore: initial fixture")
        sync.git(self.repo, "switch", "-c", "develop")
        (self.repo / "feature.md").write_text("develop feature\n", encoding="utf-8")
        sync.git(self.repo, "commit", "-am", "feat: develop only")
        sync.git(self.repo, "remote", "add", "origin", str(self.remote))
        sync.git(self.repo, "push", "origin", "master", "develop")
        sync.git(self.repo, "switch", "master")

    def remote_git(self, *args):
        return sync.git(self.root, "--git-dir", str(self.remote), *args)

    def test_updates_both_branches_without_merging_features_and_is_idempotent(self):
        head = sync.git(self.repo, "rev-parse", "HEAD")
        self.assertEqual(["master", "develop"], sync.sync_versions(self.repo, 304006, "3.4.3-beta3"))
        heads = {}
        for branch in sync.BRANCHES:
            text = self.remote_git("show", f"{branch}:{sync.GRADLE}")
            self.assertEqual(gradle(304006, "3.4.3-beta3").strip(), text)
            self.assertEqual(f"{branch} feature", self.remote_git("show", f"{branch}:feature.md"))
            self.assertEqual(sync.GRADLE, self.remote_git("diff-tree", "--no-commit-id", "--name-only", "-r", branch))
            heads[branch] = self.remote_git("rev-parse", branch)
        self.assertEqual([], sync.sync_versions(self.repo, 304006, "3.4.3-beta3"))
        self.assertEqual(heads, {branch: self.remote_git("rev-parse", branch) for branch in sync.BRANCHES})
        self.assertEqual(head, sync.git(self.repo, "rev-parse", "HEAD"))
        self.assertEqual(gradle(), self.gradle.read_text(encoding="utf-8"))
        self.assertEqual("", sync.git(self.repo, "status", "--porcelain"))

    def test_concurrent_push_is_preserved_and_atomic_retry_succeeds(self):
        original_git = sync.git
        pushes = 0
        old_master = self.remote_git("rev-parse", "master")

        def racing_git(repo, *args):
            nonlocal pushes
            if args[0] == "push" and "--atomic" in args:
                pushes += 1
                if pushes == 1:
                    other = self.root / "concurrent"
                    original_git(self.root, "clone", "--branch", "develop", str(self.remote), str(other))
                    original_git(other, "config", "user.name", "Concurrent")
                    original_git(other, "config", "user.email", "concurrent@example.invalid")
                    (other / "concurrent.md").write_text("keep this commit\n", encoding="utf-8")
                    original_git(other, "add", ".")
                    original_git(other, "commit", "-m", "feat: concurrent change")
                    original_git(other, "push", "origin", "develop")
                    with self.assertRaises(subprocess.CalledProcessError) as failure:
                        original_git(repo, *args)
                    self.assertEqual(old_master, self.remote_git("rev-parse", "master"))
                    raise failure.exception
            return original_git(repo, *args)

        with patch.object(sync, "git", side_effect=racing_git):
            self.assertEqual(["master", "develop"], sync.sync_versions(self.repo, 305000, "3.5.0"))
        self.assertEqual(2, pushes)
        self.assertEqual("keep this commit", self.remote_git("show", "develop:concurrent.md"))
        for branch in sync.BRANCHES:
            self.assertIn('versionCode = 305000', self.remote_git("show", f"{branch}:{sync.GRADLE}"))

    def test_exhausted_push_retries_fail_without_touching_remote_versions(self):
        heads = {branch: self.remote_git("rev-parse", branch) for branch in sync.BRANCHES}
        original_git = sync.git

        def rejecting_git(repo, *args):
            if args[0] == "push":
                raise subprocess.CalledProcessError(1, ["git", *args], stderr="branch protected")
            return original_git(repo, *args)

        with patch.object(sync, "git", side_effect=rejecting_git), self.assertRaises(subprocess.CalledProcessError):
            sync.sync_versions(self.repo, 304006, "3.4.3-beta3")
        self.assertEqual(heads, {branch: self.remote_git("rev-parse", branch) for branch in sync.BRANCHES})
        self.assertEqual(1, len(sync.git(self.repo, "worktree", "list", "--porcelain").split("worktree ")) - 1)


if __name__ == "__main__":
    unittest.main()
