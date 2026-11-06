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

    def test_updates_both_branches_to_one_commit_and_is_idempotent(self):
        head = sync.git(self.repo, "rev-parse", "HEAD")
        develop = self.remote_git("rev-parse", "develop")
        self.assertEqual(["master", "develop"], sync.sync_versions(self.repo, 304006, "3.4.3-beta3"))
        self.assertEqual(self.remote_git("rev-parse", "master"), self.remote_git("rev-parse", "develop"))
        heads = {}
        for branch in sync.BRANCHES:
            text = self.remote_git("show", f"{branch}:{sync.GRADLE}")
            self.assertEqual(gradle(304006, "3.4.3-beta3").strip(), text)
            self.assertEqual("develop feature", self.remote_git("show", f"{branch}:feature.md"))
            self.assertEqual(sync.GRADLE, self.remote_git("diff", "--name-only", develop, branch))
            heads[branch] = self.remote_git("rev-parse", branch)
        self.assertEqual(heads["master"], heads["develop"])
        self.assertEqual([], sync.sync_versions(self.repo, 304006, "3.4.3-beta3"))
        self.assertEqual(heads, {branch: self.remote_git("rev-parse", branch) for branch in sync.BRANCHES})
        self.assertEqual(head, sync.git(self.repo, "rev-parse", "HEAD"))
        self.assertEqual(gradle(), self.gradle.read_text(encoding="utf-8"))
        self.assertEqual("", sync.git(self.repo, "status", "--porcelain"))

    def test_equal_trees_with_different_commits_create_one_two_parent_sync(self):
        (self.repo / "feature.md").write_text("develop feature\n", encoding="utf-8")
        sync.git(self.repo, "commit", "-am", "feat: equivalent master change")
        sync.git(self.repo, "push", "origin", "master")
        parents = [self.remote_git("rev-parse", branch) for branch in sync.BRANCHES]
        self.assertNotEqual(*parents)
        self.assertEqual(self.remote_git("rev-parse", "master^{tree}"),
                         self.remote_git("rev-parse", "develop^{tree}"))
        sync.sync_versions(self.repo, 304006, "3.4.3-beta3")
        self.assertEqual(self.remote_git("rev-parse", "master"), self.remote_git("rev-parse", "develop"))
        self.assertEqual(parents, self.remote_git("show", "-s", "--format=%P", "master").split())
        self.assertEqual(sync.GRADLE, self.remote_git("diff", "--name-only", parents[0], "master"))

    def test_divergent_nonconflicting_features_are_really_merged(self):
        (self.repo / "master-only.md").write_text("keep master\n", encoding="utf-8")
        sync.git(self.repo, "add", ".")
        sync.git(self.repo, "commit", "-m", "feat: master only")
        sync.git(self.repo, "push", "origin", "master")
        parents = [self.remote_git("rev-parse", branch) for branch in sync.BRANCHES]
        sync.sync_versions(self.repo, 304006, "3.4.3-beta3")
        self.assertEqual(self.remote_git("rev-parse", "master"), self.remote_git("rev-parse", "develop"))
        self.assertEqual(parents, self.remote_git("show", "-s", "--format=%P", "master").split())
        self.assertEqual("keep master", self.remote_git("show", "master:master-only.md"))
        self.assertEqual("develop feature", self.remote_git("show", "master:feature.md"))

    def test_conflicting_branch_content_fails_without_moving_either_ref(self):
        (self.repo / "feature.md").write_text("incompatible master feature\n", encoding="utf-8")
        sync.git(self.repo, "commit", "-am", "feat: conflicting master change")
        sync.git(self.repo, "push", "origin", "master")
        heads = {branch: self.remote_git("rev-parse", branch) for branch in sync.BRANCHES}
        with self.assertRaises((ValueError, subprocess.CalledProcessError)):
            sync.sync_versions(self.repo, 304006, "3.4.3-beta3")
        self.assertEqual(heads, {branch: self.remote_git("rev-parse", branch) for branch in sync.BRANCHES})
        self.assertEqual(1, len(sync.git(self.repo, "worktree", "list", "--porcelain").split("worktree ")) - 1)

    def test_newer_prepared_version_is_preserved_when_versions_conflict(self):
        self.gradle.write_text(gradle(305000, "3.5.0-debug1"), encoding="utf-8")
        sync.git(self.repo, "commit", "-am", "chore: prepare newer version")
        sync.git(self.repo, "push", "origin", "master")
        sync.git(self.repo, "switch", "develop")
        self.gradle.write_text(gradle(304007, "3.4.4-beta1"), encoding="utf-8")
        sync.git(self.repo, "commit", "-am", "chore: another prepared version")
        sync.git(self.repo, "push", "origin", "develop")
        sync.git(self.repo, "switch", "master")
        parents = [self.remote_git("rev-parse", branch) for branch in sync.BRANCHES]
        sync.sync_versions(self.repo, 304006, "3.4.3-beta3")
        self.assertEqual(self.remote_git("rev-parse", "master"), self.remote_git("rev-parse", "develop"))
        self.assertEqual(gradle(305000, "3.5.0-debug1").strip(), self.remote_git("show", f"master:{sync.GRADLE}"))
        self.assertEqual(parents, self.remote_git("show", "-s", "--format=%P", "master").split())
        self.assertEqual("develop feature", self.remote_git("show", "master:feature.md"))

    def test_same_newer_code_with_different_names_is_rejected(self):
        for branch, name in (("master", "3.5.0-beta1"), ("develop", "3.5.0-beta2")):
            sync.git(self.repo, "switch", branch)
            self.gradle.write_text(gradle(305000, name), encoding="utf-8")
            sync.git(self.repo, "commit", "-am", "chore: prepare conflicting identity")
            sync.git(self.repo, "push", "origin", branch)
        heads = {branch: self.remote_git("rev-parse", branch) for branch in sync.BRANCHES}
        with self.assertRaisesRegex(ValueError, "different versionName"):
            sync.sync_versions(self.repo, 304006, "3.4.3-beta3")
        self.assertEqual(heads, {branch: self.remote_git("rev-parse", branch) for branch in sync.BRANCHES})

    def test_conflicting_nonversion_gradle_content_is_never_overwritten(self):
        for branch, sdk in (("master", 35), ("develop", 36)):
            sync.git(self.repo, "switch", branch)
            self.gradle.write_text(gradle() + f"compileSdk = {sdk}\n", encoding="utf-8")
            sync.git(self.repo, "commit", "-am", "build: conflicting Gradle content")
            sync.git(self.repo, "push", "origin", branch)
        heads = {branch: self.remote_git("rev-parse", branch) for branch in sync.BRANCHES}
        with self.assertRaisesRegex(ValueError, "Non-version Gradle contents conflict"):
            sync.sync_versions(self.repo, 304006, "3.4.3-beta3")
        self.assertEqual(heads, {branch: self.remote_git("rev-parse", branch) for branch in sync.BRANCHES})

    def test_nonconflicting_gradle_edits_survive_version_conflict_resolution(self):
        self.gradle.write_text("// master Gradle content\n" + gradle(304007, "3.4.4-beta1"), encoding="utf-8")
        sync.git(self.repo, "commit", "-am", "build: master Gradle edit")
        sync.git(self.repo, "push", "origin", "master")
        sync.git(self.repo, "switch", "develop")
        self.gradle.write_text(gradle(304008, "3.4.4-beta2") + "// develop Gradle content\n", encoding="utf-8")
        sync.git(self.repo, "commit", "-am", "build: develop Gradle edit")
        sync.git(self.repo, "push", "origin", "develop")
        sync.sync_versions(self.repo, 304006, "3.4.3-beta3")
        self.assertEqual(self.remote_git("rev-parse", "master"), self.remote_git("rev-parse", "develop"))
        expected = "// master Gradle content\n" + gradle(304008, "3.4.4-beta2") + "// develop Gradle content"
        self.assertEqual(expected, self.remote_git("show", f"master:{sync.GRADLE}"))

    def test_already_newer_descendant_is_fast_forwarded_without_a_new_commit(self):
        sync.git(self.repo, "switch", "develop")
        self.gradle.write_text(gradle(305000, "3.5.0"), encoding="utf-8")
        sync.git(self.repo, "commit", "-am", "chore: newer published version")
        sync.git(self.repo, "push", "origin", "develop")
        descendant = self.remote_git("rev-parse", "develop")
        self.assertEqual(["master"], sync.sync_versions(self.repo, 304006, "3.4.3-beta3"))
        self.assertEqual(descendant, self.remote_git("rev-parse", "master"))
        self.assertEqual(descendant, self.remote_git("rev-parse", "develop"))

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
        self.assertEqual(self.remote_git("rev-parse", "master"), self.remote_git("rev-parse", "develop"))
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
